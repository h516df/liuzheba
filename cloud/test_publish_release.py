"""Offline publisher regression: synthetic bytes, mocked GitHub, never installs or signs."""
import contextlib
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import publish_release as publisher


class PublisherPolicy(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.previous = Path.cwd()
        self.product = self.root / 'product'
        self.product.mkdir()
        self.sha = 'a' * 40
        self.version = '0.17'
        self.occupied = False
        self.tag_exists = False
        self.list_error = False
        self.tag_error = False
        self.bad_digest = False
        self.extra_asset = False
        self.bad_created_source = False
        self.bad_draft_source = False
        self.published = False
        self.calls = []
        self.draft = None
        self.prefix = 'repos/h516df/liuzheba/'
        (self.root / 'release').mkdir()
        (self.root / 'release/signing-identity.json').write_text(json.dumps({
            'certificateSha256': 'f' * 64, 'packageName': 'dev.h516.shicang'}))
        self.write_notes()
        apk = self.product / 'official.apk'
        apk.write_bytes(b'Synthetic publisher fixture; not an APK and never installed')
        digest = hashlib.sha256(apk.read_bytes()).hexdigest()
        (self.product / 'signature.json').write_text(json.dumps(dict(
            sha256=digest, certificateSha256='f' * 64, packageName='dev.h516.shicang',
            syntheticProbe=False, apkSignatureVerified=True)))
        (self.product / 'official-upgrade.json').write_text(json.dumps(dict(
            status='passed', sourceSha=self.sha, candidateSha256=digest)))
        os.chdir(self.root)

    def tearDown(self):
        os.chdir(self.previous)
        self.temp.cleanup()

    def write_notes(self):
        assets = self.root / 'app/src/main/assets'
        assets.mkdir(parents=True, exist_ok=True)
        (assets / 'release-notes.json').write_text(json.dumps(dict(
            versionName=self.version, versionCode=18)))
        docs = self.root / 'docs/releases'
        docs.mkdir(parents=True, exist_ok=True)
        (docs / f'v{self.version}.md').write_text('Version-specific body ' + self.version)

    def result(self, args, value=None, error=None):
        return subprocess.CompletedProcess(args, 1 if error else 0,
            stdout='' if value is None else json.dumps(value), stderr=error or '')

    def github(self, *values, check=True):
        args = tuple(map(str, values))
        self.calls.append(args)
        tag = 'v' + self.version
        if args[:2] == ('api', '--paginate'):
            if self.list_error:
                raise RuntimeError('Synthetic authorization failure')
            return subprocess.CompletedProcess(args, 0, stdout='123\n' if self.occupied else '', stderr='')
        if args[:2] == ('api', self.prefix + 'git/ref/tags/' + tag):
            if self.published or self.tag_exists:
                return self.result(args, {'object': {'type': 'commit', 'sha': self.sha}})
            return self.result(args, error='HTTP 403' if self.tag_error else 'HTTP 404')
        if args[:2] == ('api', self.prefix + 'releases') and '--method' in args:
            payload = json.loads(Path(args[args.index('--input') + 1]).read_text())
            self.assertEqual('POST', args[args.index('--method') + 1])
            self.assertEqual(tag, payload['tag_name'])
            self.assertEqual(self.sha, payload['target_commitish'])
            self.assertTrue(payload['draft'])
            self.assertFalse(payload['prerelease'])
            self.assertIn('Version-specific body ' + self.version, payload['body'])
            self.draft = dict(payload, id=123, assets=[], html_url='https://github.com/h516df/liuzheba/releases/tag/' + tag)
            if self.bad_created_source:
                self.draft['target_commitish'] = 'b' * 40
            return self.result(args, self.draft)
        if args[:2] == ('release', 'upload'):
            self.assertEqual(tag, args[2])
            self.assertNotIn('--clobber', args)
            self.assertIsNotNone(self.draft)
            paths = list(map(Path, args[3:-2]))
            self.assertEqual(3, len(paths))
            self.draft['assets'] = [dict(name=p.name, size=p.stat().st_size,
                digest='sha256:' + hashlib.sha256(p.read_bytes()).hexdigest(), state='uploaded',
                browser_download_url='https://github.com/h516df/liuzheba/releases/download/' + tag + '/' + p.name)
                for p in paths]
            if self.bad_digest:
                self.draft['assets'][0]['digest'] = 'sha256:' + '0' * 64
            if self.extra_asset:
                self.draft['assets'].append(dict(name='unexpected.apk'))
            if self.bad_draft_source:
                self.draft['target_commitish'] = 'b' * 40
            return self.result(args)
        if args[:2] == ('api', self.prefix + 'releases/123'):
            if '--method' in args:
                self.assertEqual('PATCH', args[args.index('--method') + 1])
                payload = json.loads(Path(args[args.index('--input') + 1]).read_text())
                self.assertEqual(dict(draft=False, prerelease=False, make_latest='true'), payload)
                self.published = True
                self.draft['draft'] = False
            return self.result(args, self.draft)
        if args[:2] == ('api', self.prefix + 'releases/latest'):
            self.assertTrue(self.published)
            return self.result(args, self.draft)
        if args[0] == 'api' and '/releases/tags/' in args[1]:
            if self.published:
                return self.result(args, self.draft)
            if check:
                raise RuntimeError('GitHub hides unpublished drafts by tag: HTTP 404')
            return self.result(args, error='HTTP 404')
        raise AssertionError('Unexpected GitHub operation: ' + repr(args))

    def run_publisher(self):
        with patch.object(publisher, 'gh', side_effect=self.github), \
             patch('sys.argv', ['publisher', '--directory', str(self.product)]), \
             patch.dict(os.environ, dict(GITHUB_REPOSITORY='h516df/liuzheba', GITHUB_SHA=self.sha,
                 GITHUB_REF='refs/heads/main', GITHUB_RUN_ID='123')), \
             contextlib.redirect_stdout(io.StringIO()):
            publisher.main()

    def assert_not_published(self):
        self.assertFalse(self.published)
        self.assertFalse((self.product / 'publication.json').exists())

    def test_created_draft_is_verified_by_id_then_published(self):
        self.run_publisher()
        receipt = json.loads((self.product / 'publication.json').read_text())
        self.assertEqual(self.sha, receipt['sourceSha'])
        self.assertTrue(receipt['latest'])
        self.assertFalse(receipt['draft'])
        self.assertEqual(3, len(receipt['assets']))
        self.assertTrue(self.published)

    def test_existing_unpublished_draft_never_creates_or_overwrites(self):
        self.occupied = True
        with self.assertRaises(SystemExit): self.run_publisher()
        self.assertIsNone(self.draft)
        self.assert_not_published()

    def test_existing_git_tag_without_release_is_also_occupied(self):
        self.tag_exists = True
        with self.assertRaises(SystemExit): self.run_publisher()
        self.assertIsNone(self.draft)
        self.assert_not_published()

    def test_release_listing_failure_stops_before_creation(self):
        self.list_error = True
        with self.assertRaises(RuntimeError): self.run_publisher()
        self.assertIsNone(self.draft)
        self.assert_not_published()

    def test_git_tag_authorization_failure_is_not_absence(self):
        self.tag_error = True
        with self.assertRaises(SystemExit): self.run_publisher()
        self.assertIsNone(self.draft)
        self.assert_not_published()

    def test_uploaded_digest_mismatch_leaves_draft_unpublished(self):
        self.bad_digest = True
        with self.assertRaises(SystemExit): self.run_publisher()
        self.assert_not_published()

    def test_unexpected_asset_leaves_draft_unpublished(self):
        self.extra_asset = True
        with self.assertRaises(SystemExit): self.run_publisher()
        self.assert_not_published()

    def test_wrong_created_draft_source_stops_before_upload(self):
        self.bad_created_source = True
        with self.assertRaises(SystemExit): self.run_publisher()
        self.assertFalse(any(c[:2] == ('release', 'upload') for c in self.calls))
        self.assert_not_published()

    def test_draft_source_changed_after_upload_cannot_publish(self):
        self.bad_draft_source = True
        with self.assertRaises(SystemExit): self.run_publisher()
        self.assert_not_published()

    def test_receipt_for_other_source_cannot_call_github(self):
        path = self.product / 'official-upgrade.json'
        data = json.loads(path.read_text()); data['sourceSha'] = 'b' * 40
        path.write_text(json.dumps(data))
        with self.assertRaises(SystemExit): self.run_publisher()
        self.assertEqual([], self.calls)
        self.assert_not_published()

    def test_next_version_uses_its_own_release_body(self):
        self.version = '0.18'
        self.write_notes()
        self.run_publisher()
        self.assertEqual('v0.18', json.loads((self.product / 'publication.json').read_text())['tag'])


if __name__ == '__main__':
    unittest.main()
