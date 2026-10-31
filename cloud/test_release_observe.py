"""Offline, fail-closed checks of production release observation and resumption."""

import contextlib
import io
import os
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent.parent / "tools" / "release"))
import observe_verified as obs
import dispatch_verified as dispatch


SHA = "a" * 40
TAG = "v0.23"
RUN = 12345


def fixture():
    return {
        "user": {"login": "h516df"},
        f"repos/{obs.REPO}/actions/runs?head_sha={SHA}&event=workflow_dispatch&per_page=50": {
            "workflow_runs": [{"id": RUN, "head_sha": SHA, "head_branch": "main",
                               "event": "workflow_dispatch", "path": f".github/workflows/{obs.WORKFLOW}"}]},
        f"repos/{obs.REPO}/actions/runs/{RUN}": {
            "id": RUN, "head_sha": SHA, "head_branch": "main", "event": "workflow_dispatch",
            "path": f".github/workflows/{obs.WORKFLOW}", "status": "completed", "conclusion": "success"},
        f"repos/{obs.REPO}/actions/runs/{RUN}/jobs?per_page=100": {
            "total_count": 4, "jobs": [{"name": name, "status": "completed", "conclusion": "success"}
                                       for name in ("build", "sign", "upgrade", "publish")]},
        f"repos/{obs.REPO}/releases/tags/{TAG}": {
            "tag_name": TAG, "target_commitish": SHA, "draft": False, "prerelease": False,
            "assets": [{"name": f"shicang-{TAG}-{name}", "size": 100,
                        "digest": "sha256:" + "c" * 64}
                       for name in ("universal.apk", "release-notes.json", "SHA256.txt")]},
        f"repos/{obs.REPO}/releases/latest": {"tag_name": TAG},
    }


class ReleaseObserverTests(unittest.TestCase):
    def setUp(self):
        self.data = fixture()
        self.api = lambda path: self.data[path]

    def verify(self):
        return obs.observe(SHA, TAG, api=self.api)

    def test_success_receipt(self):
        result = self.verify()
        self.assertEqual(result["status"], "verified")
        self.assertEqual(result["sourceSha"], SHA)
        self.assertEqual(result["phoneUpgrade"], "not_tested")
        self.assertEqual(len(result["assets"]), 3)

    def test_wrong_user_rejected(self):
        self.data["user"]["login"] = "other"
        with self.assertRaisesRegex(RuntimeError, "h516df"):
            self.verify()

    def test_wrong_workflow_rejected(self):
        self.data[f"repos/{obs.REPO}/actions/runs/{RUN}"]["path"] = ".github/workflows/android.yml"
        with self.assertRaisesRegex(RuntimeError, "official workflow"):
            self.verify()

    def test_wrong_source_rejected(self):
        self.data[f"repos/{obs.REPO}/actions/runs/{RUN}"]["head_sha"] = "b" * 40
        with self.assertRaisesRegex(RuntimeError, "exact main source"):
            self.verify()

    def test_failed_job_rejected(self):
        jobs = self.data[f"repos/{obs.REPO}/actions/runs/{RUN}/jobs?per_page=100"]["jobs"]
        jobs[2]["conclusion"] = "failure"
        with self.assertRaisesRegex(RuntimeError, "did not all pass"):
            self.verify()

    def test_incomplete_job_evidence_rejected(self):
        self.data[f"repos/{obs.REPO}/actions/runs/{RUN}/jobs?per_page=100"]["total_count"] = 5
        with self.assertRaisesRegex(RuntimeError, "incomplete"):
            self.verify()

    def test_failed_workflow_rejected(self):
        self.data[f"repos/{obs.REPO}/actions/runs/{RUN}"]["conclusion"] = "failure"
        with self.assertRaisesRegex(RuntimeError, "failed"):
            self.verify()

    def test_draft_or_wrong_source_rejected(self):
        r = self.data[f"repos/{obs.REPO}/releases/tags/{TAG}"]
        for field, value in (("draft", True), ("target_commitish", "b" * 40)):
            original = r[field]
            r[field] = value
            with self.subTest(field=field), self.assertRaisesRegex(RuntimeError, "Public Release"):
                self.verify()
            r[field] = original

    def test_missing_asset_rejected(self):
        self.data[f"repos/{obs.REPO}/releases/tags/{TAG}"]["assets"].pop()
        with self.assertRaisesRegex(RuntimeError, "assets"):
            self.verify()

    def test_missing_digest_rejected(self):
        self.data[f"repos/{obs.REPO}/releases/tags/{TAG}"]["assets"][0]["digest"] = None
        with self.assertRaisesRegex(RuntimeError, "digest"):
            self.verify()

    def test_not_latest_rejected(self):
        self.data[f"repos/{obs.REPO}/releases/latest"]["tag_name"] = "v0.22"
        with self.assertRaisesRegex(RuntimeError, "latest"):
            self.verify()

    def test_duplicate_run_requires_manual_inspection(self):
        runs = self.data[f"repos/{obs.REPO}/actions/runs?head_sha={SHA}&event=workflow_dispatch&per_page=50"]["workflow_runs"]
        runs.append(dict(runs[0], id=777))
        with self.assertRaisesRegex(RuntimeError, "Multiple"):
            self.verify()

    def test_no_run_fails_without_dispatch(self):
        self.data[f"repos/{obs.REPO}/actions/runs?head_sha={SHA}&event=workflow_dispatch&per_page=50"]["workflow_runs"] = []
        with self.assertRaisesRegex(RuntimeError, "inspect the existing run"):
            self.verify()

    def test_wait_until_run_completes(self):
        key = f"repos/{obs.REPO}/actions/runs/{RUN}"
        self.data[key]["status"] = "in_progress"
        ticks = [0]

        def fake_sleep(_seconds):
            ticks[0] += 1
            self.data[key]["status"] = "completed"

        result = obs.observe(SHA, TAG, wait_seconds=30, api=self.api,
                             clock=lambda: ticks[0] * 10, sleep=fake_sleep)
        self.assertEqual(result["status"], "verified")
        self.assertEqual(ticks[0], 1)

    def test_main_is_read_only(self):
        with patch.dict(os.environ, {"GITHUB_ACTIONS": "false"}), \
             patch.object(obs, "observe", return_value={"status": "verified"}) as observe, \
             contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(obs.main(["--expected-sha", SHA, "--tag", TAG]), 0)
        observe.assert_called_once()

    def test_wait_requires_dispatch(self):
        with patch.dict(os.environ, {"GITHUB_ACTIONS": "false"}), \
             contextlib.redirect_stderr(io.StringIO()):
            with self.assertRaises(SystemExit):
                dispatch.main(["--expected-sha", SHA, "--tag", TAG, "--wait-seconds", "60"])


if __name__ == "__main__":
    unittest.main()
