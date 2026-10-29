"""Offline tests of safe PR merging criteria; no network or mutation."""

import copy
import contextlib
import io
import unittest
from unittest.mock import patch

import finish_green_pr as helper


SHA = "a" * 40


def fixture():
    pr = {
        "number": 46, "state": "open", "draft": False,
        "user": {"login": "h516df"},
        "head": {"sha": SHA, "repo": {"full_name": helper.REPO}},
        "base": {"ref": "main"},
    }
    checks = [
        {"name": name, "id": i, "started_at": "2026-10-29T01:00:00Z",
         "status": "completed", "conclusion": "success"}
        for i, name in enumerate(sorted(helper.CORE_CHECKS | {helper.DEVICE_CHECK}), start=1)
    ]
    return pr, [{"filename": "app/src/main/AndroidManifest.xml"}], checks, SHA


class FinishGuardTest(unittest.TestCase):
    def test_complete_app_pr_is_ready(self):
        self.assertEqual(helper.guard(*fixture()), (True, "all required checks passed"))

    def test_docs_only_pr_does_not_require_device_run(self):
        pr, files, checks, sha = copy.deepcopy(fixture())
        files[0]["filename"] = "README.md"
        checks = [x for x in checks if x["name"] != helper.DEVICE_CHECK]
        self.assertTrue(helper.guard(pr, files, checks, sha)[0])

    def test_rejects_untrusted_pr_and_changed_sha(self):
        for kind in ("fork", "author", "base", "draft", "sha"):
            pr, files, checks, sha = copy.deepcopy(fixture())
            if kind == "fork":
                pr["head"]["repo"]["full_name"] = "other/repo"
            elif kind == "author":
                pr["user"]["login"] = "other"
            elif kind == "base":
                pr["base"]["ref"] = "production"
            elif kind == "draft":
                pr["draft"] = True
            else:
                pr["head"]["sha"] = "b" * 40
            self.assertFalse(helper.guard(pr, files, checks, sha)[0], kind)

    def test_never_merges_partial_or_failed_checks(self):
        for kind in ("missing", "pending", "failure"):
            pr, files, checks, sha = copy.deepcopy(fixture())
            if kind == "missing":
                checks.pop()
            elif kind == "pending":
                checks[0]["status"] = "in_progress"
            else:
                checks[0]["conclusion"] = "failure"
            self.assertFalse(helper.guard(pr, files, checks, sha)[0], kind)

    def test_newer_failed_rerun_overrides_old_success(self):
        pr, files, checks, sha = copy.deepcopy(fixture())
        checks.append({"name": "check", "id": 99, "started_at": "2026-10-29T02:00:00Z",
                       "status": "completed", "conclusion": "failure"})
        self.assertFalse(helper.guard(pr, files, checks, sha)[0])

    def test_sensitive_paths_require_manual_merging(self):
        for filename in (".github/workflows/android.yml", "cloud/release_gate.py"):
            pr, files, checks, sha = copy.deepcopy(fixture())
            files[0]["filename"] = filename
            self.assertFalse(helper.guard(pr, files, checks, sha)[0], filename)

    def test_incomplete_file_listing_cannot_be_ready(self):
        pr, files, checks, sha = copy.deepcopy(fixture())
        self.assertFalse(helper.guard(pr, [], checks, sha)[0])
        self.assertFalse(helper.guard(pr, files * 100, checks, sha)[0])

    def test_waiting_polls_only_checks_and_revalidates_before_merge(self):
        pr, files, checks, sha = copy.deepcopy(fixture())
        waiting = copy.deepcopy(checks)
        waiting[0].update(status="in_progress", conclusion=None)
        calls = []
        polls = iter((waiting, waiting, checks, checks))

        def api(endpoint, *, method="GET", fields=()):
            calls.append((endpoint, method))
            if method == "PUT":
                self.assertIn(f"sha={sha}", fields)
                return {"merged": True, "sha": "b" * 40}
            if endpoint.endswith("/files?per_page=100"):
                return files
            if endpoint.endswith("/check-runs?per_page=100"):
                return {"total_count": len(checks), "check_runs": next(polls)}
            return pr

        with patch.object(helper, "gh_api", side_effect=api), \
                patch.object(helper.time, "sleep") as sleep, \
                contextlib.redirect_stdout(io.StringIO()):
            result = helper.finish_pr(46, sha, wait_seconds=1800, merge=True)
        self.assertEqual(result, 0)
        self.assertEqual(sum("/files?" in path for path, _ in calls), 2)
        self.assertEqual(sum(path.endswith("/check-runs?per_page=100") for path, _ in calls), 4)
        self.assertEqual(sum(path.endswith("/pulls/46") for path, _ in calls), 2)
        self.assertEqual(sum(method == "PUT" for _, method in calls), 1)
        self.assertEqual(sleep.call_count, 2)

    def test_changed_pr_after_green_cannot_merge(self):
        pr, files, checks, sha = copy.deepcopy(fixture())
        changed = copy.deepcopy(pr)
        changed["head"]["sha"] = "b" * 40
        reads = iter((pr, changed))
        mutations = []

        def api(endpoint, *, method="GET", fields=()):
            if method != "GET":
                mutations.append(endpoint)
                return {"merged": True}
            if endpoint.endswith("/files?per_page=100"):
                return files
            if endpoint.endswith("/check-runs?per_page=100"):
                return {"total_count": len(checks), "check_runs": checks}
            return next(reads)

        with patch.object(helper, "gh_api", side_effect=api), \
                contextlib.redirect_stdout(io.StringIO()):
            result = helper.finish_pr(46, sha, merge=True)
        self.assertEqual(result, 2)
        self.assertEqual(mutations, [])


if __name__ == "__main__":
    unittest.main()
