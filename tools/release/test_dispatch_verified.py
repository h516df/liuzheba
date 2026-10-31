"""Synthetic, network-free acceptance of the user-session release helper."""

import base64
import contextlib
import io
import json
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
import dispatch_verified as release


SHA = "a" * 40
TAG = "v0.23"


def fixture():
    api = {
        "user": {"login": "h516df"},
        f"repos/{release.REPO}": {"full_name": release.REPO, "default_branch": "main", "private": False},
        f"repos/{release.REPO}/branches/main": {"commit": {"sha": SHA}},
        f"repos/{release.REPO}/contents/app/src/main/assets/release-notes.json?ref={SHA}": {
            "encoding": "base64", "content": base64.b64encode(json.dumps({
                "versionName": "0.23", "versionCode": 24}).encode()).decode()},
        f"repos/{release.REPO}/releases?per_page=100": [],
        f"repos/{release.REPO}/actions/runs?head_sha={SHA}&event=workflow_dispatch&per_page=50": {
            "workflow_runs": []},
    }
    for i, filename in enumerate(release.REQUIRED_CHECKS, 1):
        api[f"repos/{release.REPO}/actions/workflows/{filename}/runs?head_sha={SHA}&per_page=30"] = {
            "workflow_runs": [{"id": i, "head_sha": SHA, "head_branch": "main",
                               "event": "push", "status": "completed", "conclusion": "success"}]}
    return api


class ReleasePreflightTests(unittest.TestCase):
    def setUp(self):
        self.responses = fixture()
        self.tag_available = lambda tag: True

    def check(self, sha=SHA, tag=TAG):
        return release.preflight(sha, tag, api=lambda path: self.responses[path],
                                 tag_available=self.tag_available)

    def test_exact_source_four_green_push_checks(self):
        result = self.check()
        self.assertEqual(result["sourceSha"], SHA)
        self.assertEqual(len(result["checks"]), 4)

    def test_rejects_non_full_sha(self):
        with self.assertRaisesRegex(ValueError, "40-character"):
            self.check("a" * 7)

    def test_rejects_invalid_tag(self):
        with self.assertRaisesRegex(ValueError, "--tag"):
            self.check(tag="latest")

    def test_rejects_wrong_account(self):
        self.responses["user"]["login"] = "another-user"
        with self.assertRaisesRegex(RuntimeError, "h516df"):
            self.check()

    def test_rejects_private_or_wrong_repo(self):
        self.responses[f"repos/{release.REPO}"]["private"] = True
        with self.assertRaisesRegex(RuntimeError, "identity"):
            self.check()

    def test_rejects_main_moving(self):
        self.responses[f"repos/{release.REPO}/branches/main"]["commit"]["sha"] = "b" * 40
        with self.assertRaisesRegex(RuntimeError, "main changed"):
            self.check()

    def test_rejects_stale_release_notes(self):
        path = f"repos/{release.REPO}/contents/app/src/main/assets/release-notes.json?ref={SHA}"
        self.responses[path]["content"] = base64.b64encode(b'{"versionName":"0.22","versionCode":23}').decode()
        with self.assertRaisesRegex(RuntimeError, "release metadata"):
            self.check()

    def test_rejects_existing_draft(self):
        self.responses[f"repos/{release.REPO}/releases?per_page=100"] = [{"tag_name": TAG, "draft": True}]
        with self.assertRaisesRegex(RuntimeError, "occupied"):
            self.check()

    def test_rejects_incomplete_release_listing(self):
        self.responses[f"repos/{release.REPO}/releases?per_page=100"] = [{}] * 100
        with self.assertRaisesRegex(RuntimeError, "incomplete"):
            self.check()

    def test_rejects_existing_git_tag(self):
        self.tag_available = lambda tag: False
        with self.assertRaisesRegex(RuntimeError, "Git tag"):
            self.check()

    def test_rejects_checks_from_pr_not_push(self):
        path = f"repos/{release.REPO}/actions/workflows/{release.REQUIRED_CHECKS[0]}/runs?head_sha={SHA}&per_page=30"
        self.responses[path]["workflow_runs"][0]["event"] = "pull_request"
        with self.assertRaisesRegex(RuntimeError, "has not passed"):
            self.check()

    def test_rejects_failed_or_running_main_check(self):
        path = f"repos/{release.REPO}/actions/workflows/{release.REQUIRED_CHECKS[2]}/runs?head_sha={SHA}&per_page=30"
        self.responses[path]["workflow_runs"][0]["status"] = "in_progress"
        with self.assertRaisesRegex(RuntimeError, "has not passed"):
            self.check()

    def test_rejects_check_of_other_commit(self):
        path = f"repos/{release.REPO}/actions/workflows/{release.REQUIRED_CHECKS[3]}/runs?head_sha={SHA}&per_page=30"
        self.responses[path]["workflow_runs"][0]["head_sha"] = "b" * 40
        with self.assertRaisesRegex(RuntimeError, "has not passed"):
            self.check()

    def test_rejects_duplicate_official_dispatch(self):
        path = f"repos/{release.REPO}/actions/runs?head_sha={SHA}&event=workflow_dispatch&per_page=50"
        self.responses[path]["workflow_runs"] = [{"head_sha": SHA,
            "path": f".github/workflows/{release.WORKFLOW}", "status": "in_progress"}]
        with self.assertRaisesRegex(RuntimeError, "already dispatched"):
            self.check()


class DispatchTests(unittest.TestCase):
    def test_tag_lookup_only_treats_http_404_as_available(self):
        result = type("Result", (), {"returncode": 1, "stderr": "gh: Not Found (HTTP 404)"})()
        with patch.object(release, "command", return_value=result):
            self.assertTrue(release.tag_is_free(TAG))

    def test_tag_network_error_is_not_absence(self):
        result = type("Result", (), {"returncode": 1, "stderr": "unexpected EOF"})()
        with patch.object(release, "command", return_value=result):
            with self.assertRaisesRegex(RuntimeError, "unknown"):
                release.tag_is_free(TAG)

    def test_read_only_default_does_not_call_gh_workflow(self):
        with patch.dict(os.environ, {"GITHUB_ACTIONS": "false"}), \
             patch.object(release, "preflight", return_value={"sourceSha": SHA, "tag": TAG}), \
             patch.object(release, "command") as command, contextlib.redirect_stdout(io.StringIO()) as output:
            self.assertEqual(release.main(["--expected-sha", SHA, "--tag", TAG]), 0)
        command.assert_not_called()
        self.assertIn("Read-only", output.getvalue())

    def test_dispatch_requires_explicit_flag(self):
        result = type("Result", (), {"returncode": 0, "stdout": "https://github.com/h516df/liuzheba/actions/runs/123"})()
        with patch.dict(os.environ, {"GITHUB_ACTIONS": "false"}), \
             patch.object(release, "preflight", return_value={"sourceSha": SHA, "tag": TAG}), \
             patch.object(release, "command", return_value=result) as command, \
             contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(release.main(["--expected-sha", SHA, "--tag", TAG, "--dispatch"]), 0)
        self.assertEqual(command.call_args.args[-1], f"expected_sha={SHA}")

    def test_ci_cannot_dispatch(self):
        with patch.dict(os.environ, {"GITHUB_ACTIONS": "true"}), \
             patch.object(release, "preflight") as preflight, \
             contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit) as error:
                release.main(["--expected-sha", SHA, "--tag", TAG, "--dispatch"])
        self.assertEqual(error.exception.code, 2)
        preflight.assert_not_called()


if __name__ == "__main__":
    unittest.main()
