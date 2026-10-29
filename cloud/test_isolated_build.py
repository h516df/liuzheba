"""Offline build/identity gates: no SDK, emulator, credentials or network."""
import contextlib
import io
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch

import isolated_build as helper


class BuildGateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.state = Path(self.tmp.name)
        self.env = patch.dict(os.environ, {'GITHUB_SHA': 'a' * 40})
        self.env.start()
        self.addCleanup(self.env.stop)
        (self.state / 'build.log').write_text('synthetic build log', encoding='utf-8')

    def test_success_requires_build_and_identity_before_receipt(self):
        with patch.object(helper.subprocess, 'run', return_value=Mock(returncode=0)) as run:
            self.assertEqual(helper.build(self.state), 0)
        self.assertEqual(run.call_count, 2)
        self.assertIn('cloud/check_apk_identity.py', run.call_args_list[1].args[0])
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(helper.wait(self.state), 0)

    def test_failed_build_is_not_allowed_to_start_acceptance(self):
        with patch.object(helper.subprocess, 'run', return_value=Mock(returncode=17)) as run:
            self.assertEqual(helper.build(self.state), 17)
        self.assertEqual(run.call_count, 1)
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(helper.wait(self.state), 17)

    def test_identity_failure_is_preserved(self):
        with patch.object(helper.subprocess, 'run', side_effect=[Mock(returncode=0), Mock(returncode=9)]):
            self.assertEqual(helper.build(self.state), 9)
        self.assertEqual(json.loads((self.state / 'result.json').read_text())['code'], 9)

    def test_identity_process_cannot_start_is_a_failed_receipt(self):
        with patch.object(helper.subprocess, 'run', side_effect=[Mock(returncode=0), OSError('synthetic launch failure')]):
            with self.assertRaises(OSError):
                helper.build(self.state)
        self.assertNotEqual(json.loads((self.state / 'result.json').read_text())['code'], 0)

    def test_wrong_commit_and_missing_receipt_fail_closed(self):
        with self.assertRaisesRegex(RuntimeError, 'no device tests'):
            helper.wait(self.state, timeout=0)
        (self.state / 'result.json').write_text(json.dumps({'sha': 'b' * 40, 'code': 0}))
        with self.assertRaisesRegex(RuntimeError, 'different commit'):
            helper.wait(self.state)

    def test_existing_state_cannot_launch_duplicate_build(self):
        with patch.object(helper.subprocess, 'Popen') as launch:
            with self.assertRaises(FileExistsError):
                helper.start(self.state)
        launch.assert_not_called()


if __name__ == '__main__':
    unittest.main()
