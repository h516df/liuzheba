import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from verify_upgrade import verify

class UpgradeComparison(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        self.files={f'photos/{i}.original':f'synthetic-{i}'.encode() for i in range(3)}
        self.data={'format':6,'archives':[{'name':'升级验收：文字与混合媒体','photos':[{'file':f'{i}.original','mime':'image/png' if i==0 else 'video/mp4','sha256':hashlib.sha256(self.files[f'photos/{i}.original']).hexdigest()} for i in range(3)]}], 'notes':[{'body':'从正式 0.16 保留的合成文字'}], 'drafts':[]}
        self.write('before.zip',self.data,self.files)
    def tearDown(self):self.temp.cleanup()
    def write(self,name,data,files):
        with zipfile.ZipFile(self.root/name,'w') as z:
            z.writestr('manifest.json',json.dumps(data))
            for key,value in files.items():z.writestr(key,value)
    def check_after(self,data=None,files=None):
        self.write('after.zip',data or self.data,files or self.files)
        return verify(self.root/'before.zip',self.root/'after.zip','a'*40,'b'*64)
    def test_unchanged_passes(self):self.assertEqual('passed',self.check_after()['status'])
    def test_reordered_media_rejected(self):
        changed=copy.deepcopy(self.data);changed['archives'][0]['photos'].reverse()
        with self.assertRaises(ValueError):self.check_after(data=changed)
    def test_changed_note_rejected(self):
        changed=copy.deepcopy(self.data);changed['notes'][0]['body']='changed'
        with self.assertRaises(ValueError):self.check_after(data=changed)
    def test_changed_original_rejected(self):
        changed=dict(self.files);changed['photos/1.original']=b'changed'
        with self.assertRaises(ValueError):self.check_after(files=changed)
    def test_missing_original_rejected(self):
        changed=dict(self.files);del changed['photos/1.original']
        with self.assertRaises(ValueError):self.check_after(files=changed)

if __name__=='__main__':unittest.main()
