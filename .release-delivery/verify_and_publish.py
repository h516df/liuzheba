from pathlib import Path
import gzip,hashlib,json,os,re,subprocess,zipfile
from release_assets import execute
root=Path(__file__).resolve().parent
meta=json.loads((root/'delivery.json').read_text())
spec=json.loads((root/'release.json').read_text())
def sha(path):
    with Path(path).open('rb') as file:return hashlib.file_digest(file,'sha256').hexdigest()
assert meta['sourceCommit']==spec['target']
assert os.environ['GITHUB_REPOSITORY']==spec['repo']=='h516df/liuzheba'
assert os.environ['GITHUB_REF']=='refs/heads/'+meta['branch']
assert Path(meta['apk']).name==meta['apk'] and Path(meta['notes']).name==meta['notes']
changed=subprocess.check_output(['git','diff','--name-only',spec['target'],'HEAD'],text=True).splitlines()
assert all(p.startswith('.release-delivery/') or p in (meta['workflow'],'.github/workflows/android.yml') for p in changed)
assert subprocess.check_output(['git','rev-parse',spec['target']+'^{tree}'],text=True).strip()==meta['releaseTree']
assert sha(root/'reconstruction.json')==meta['reconstructionSha256']
assert sha(root/'signed-literals.bin.gz')==meta['literalsSha256']
recipe=json.loads((root/'reconstruction.json').read_text())
base=root/'base/app-release-unsigned.apk'
assert sha(base)==recipe['baseSha256']==meta['unsignedApkSha256']
literal=gzip.decompress((root/'signed-literals.bin.gz').read_bytes())
assert len(literal)<1024*1024 and len(recipe['pieces'])<10000
apk=root/meta['apk']
with base.open('rb') as source,apk.open('xb') as target:
    for part in recipe['pieces']:
        length=part['length'];assert isinstance(length,int) and length>=0
        if 'copy' in part:
            offset=part['copy'];assert isinstance(offset,int) and 0<=offset<=base.stat().st_size-length
            source.seek(offset);data=source.read(length)
        else:
            offset=part['literal'];assert isinstance(offset,int) and 0<=offset<=len(literal)-length
            data=literal[offset:offset+length]
        assert len(data)==length;target.write(data)
assert apk.stat().st_size==meta['bytes']==recipe['targetBytes']
assert sha(apk)==meta['sha256']==recipe['targetSha256']
sdk=Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
tool=sdk/'build-tools/36.0.0'
certs=subprocess.check_output([str(tool/'apksigner'),'verify','--verbose','--print-certs',str(apk)],text=True)
fingerprints=re.findall(r'certificate SHA-256 digest: ([0-9a-fA-F]{64})',certs)
assert [fp.lower() for fp in fingerprints]==[meta['certificateSha256'].lower()]
assert re.search(r'Verified using v2 scheme.*:\s*true',certs)
subprocess.check_call([str(tool/'zipalign'),'-c','-P','16','4',str(apk)])
badging=subprocess.check_output([str(tool/'aapt2'),'dump','badging',str(apk)],text=True)
match=re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",badging,re.M)
assert match and match.groups()==(meta['packageName'],str(meta['versionCode']),meta['versionName'])
assert 'application-debuggable' not in badging
with zipfile.ZipFile(apk) as archive:
    assert sorted({n.split('/')[1] for n in archive.namelist() if n.startswith('lib/') and n.endswith('.so')})==meta['nativeAbis']
    assert archive.read('assets/release-notes.json')==(root/meta['notes']).read_bytes()
print('Original local-signed APK reconstructed and verified; no app build or signing in cloud',flush=True)
result=execute(root/'release.json',root/'state',publish=True,public_release=True,verify_downloads=True)
assert result['downloadPerformedThisRun'] and all(x['downloadedThisRun'] for x in result['downloads'])
result.update(localSignedApkSha256=meta['sha256'],sourceCommit=meta['sourceCommit'],releaseTree=meta['releaseTree'],publicRelease=True)
(root/'state/cloud-audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
Path(os.environ['GITHUB_STEP_SUMMARY']).write_text('正式发布及三个附件匿名实际回读通过：'+result['url']+'\nAPK SHA256: '+meta['sha256']+'\n',encoding='utf-8')
print('FINAL_AUDIT_BEGIN',flush=True)
print(json.dumps(result,ensure_ascii=False),flush=True)
print('FINAL_AUDIT_END',flush=True)
