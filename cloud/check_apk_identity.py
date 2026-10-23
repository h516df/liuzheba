#!/usr/bin/env python3
"""Inspect the actual APK, including provider authorities and component classes."""
import argparse,hashlib,json,os,pathlib,re,subprocess

def inspect(apk,package):
    sdk=pathlib.Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
    aapt=sdk/'build-tools/36.0.0'/('aapt2.exe' if os.name=='nt' else 'aapt2')
    def dump(*args):return subprocess.run([str(aapt),'dump',*args,str(apk)],check=True,capture_output=True,text=True).stdout
    badging=dump('badging')
    actual=re.search(r"package: name='([^']+)'",badging)
    if not actual or actual[1]!=package:raise ValueError('Actual APK package differs from required variant.')
    # XML tree requires --file before the APK; inspect compiled, merged manifest.
    tree=subprocess.run([str(aapt),'dump','xmltree',str(apk),'--file','AndroidManifest.xml'],check=True,capture_output=True,text=True).stdout
    providers=[]
    lines=tree.splitlines()
    for index,line in enumerate(lines):
        if line.strip().startswith('E: provider '):
            indent=len(line)-len(line.lstrip());block=[]
            for rest in lines[index+1:]:
                if len(rest)-len(rest.lstrip())<=indent:break
                block.append(rest)
            text='\n'.join(block)
            authority=re.search(r'android:authorities[^=]*="([^"]+)"',text)
            if authority:
                if not authority[1].startswith(package+'.'):raise ValueError('Provider collides with another application ID.')
                providers.append(authority[1])
            if authority and authority[1] in [package+'.files',package+'.updates']:
                if not re.search(r'android:exported.*(?:0x0|false)',text):raise ValueError('FileProvider must be private.')
                if not re.search(r'android:grantUriPermissions.*(?:0xffffffff|true)',text):raise ValueError('FileProvider URI grants missing.')
    if not {package+'.files',package+'.updates'}.issubset(providers):raise ValueError('Expected FileProviders missing.')
    launcher=re.search(r"launchable-activity: name='([^']+)'",badging)
    if not launcher or launcher[1]!='dev.h516.shicang.MainActivity':raise ValueError('Launcher component class changed unexpectedly.')
    if 'dev.h516.shicang.MediaBrowserActivity' not in tree:raise ValueError('Media browser component missing.')
    debug='application-debuggable' in badging
    if debug!=(package!='dev.h516.shicang'):raise ValueError('Unexpected debuggable status.')
    return {'packageName':package,'debuggable':debug,'providerAuthorities':providers,'launcher':launcher[1],'sha256':hashlib.sha256(apk.read_bytes()).hexdigest()}

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('apk',type=pathlib.Path);p.add_argument('package');a=p.parse_args()
    print(json.dumps(inspect(a.apk,a.package),sort_keys=True))
