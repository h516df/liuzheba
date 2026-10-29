"""Offline tests of safe PR merging criteria; no network or mutation."""

import copy
import contextlib
import io
import threading
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
    def test_snapshot_fetches_independent_evidence_concurrently(self):
        pr, files, checks, sha = fixture()
        rendezvous = threading.Barrier(3)

        def api(endpoint, *, method="GET", fields=()):
            self.assertEqual(method, "GET")
            rendezvous.wait(timeout=3)
            if endpoint.endswith("/files?per_page=100"):
                return files
            if endpoint.endswith("/check-runs?per_page=100"):
                return {"total_count": len(checks), "check_runs": checks}
            return pr

        with patch.object(helper, "gh_api", side_effect=api):
            self.assertEqual(
                helper.read_snapshot("repos/h516df/liuzheba/pulls/46",
                                     "repos/h516df/liuzheba/pulls/46/files?per_page=100", sha),
                (pr, files, checks),
            )

    def test_snapshot_failure_is_not_retried_or_merged(self):
        pr, files, checks, sha = fixture()
        mutations = []

        def api(endpoint, *, method="GET", fields=()):
            if method != "GET":
                mutations.append(endpoint)
            if endpoint.endswith("/files?per_page=100"):
                raise OSError("read failed")
            if endpoint.endswith("/check-runs?per_page=100"):
                return {"total_count": len(checks), "check_runs": checks}
            return pr

        with patch.object(helper, "gh_api", side_effect=api):
            with self.assertRaisesRegex(OSError, "read failed"):
                helper.finish_pr(46, sha, merge=True)
        self.assertEqual(mutations, [])

    def test_complete_app_pr_is_ready(self):
        self.assertEqual(helper.guard(*fixture()), (True, "all required checks passed"))

    def test_docs_only_pr_does_not_require_device_run(self):
        pr, files, checks, sha = copy.deepcopy(fixture())
        files[0]["filename"] = "README.md"
        checks = [x for x in checks if x["name"] != helper.DEVICE_CHECK]
        self.assertTrue(helper.guard(pr, files, checks, sha)[0])

    def test_pr_paths_excluded_by_device_workflow_do_not_wait_for_absent_check(self):
        for filename in ('app/src/test/java/dev/h516/shicang/ProductJourneyUiTest.kt',
                         'app/src/main/python/media_bridge.py', 'tools/tests/test_media_bridge.py'):
            pr, files, checks, sha = copy.deepcopy(fixture())
            files[0]['filename'] = filename
            checks = [x for x in checks if x['name'] != helper.DEVICE_CHECK]
            self.assertEqual(helper.guard(pr, files, checks, sha),
                             (True, 'all required checks passed'), filename)

    def test_mixed_native_changes_still_require_device_check(self):
        pr, files, checks, sha = copy.deepcopy(fixture())
        files.append({'filename': 'app/src/test/java/ExampleTest.kt'})
        checks = [x for x in checks if x['name'] != helper.DEVICE_CHECK]
        self.assertEqual(helper.guard(pr, files, checks, sha),
                         (False, 'waiting for required checks'))

    def test_background_build_helpers_require_their_device_check(self):
        for filename in ('cloud/isolated_build.py', 'cloud/test_isolated_build.py'):
            pr, files, checks, sha = copy.deepcopy(fixture())
            files[0]['filename'] = filename
            checks = [x for x in checks if x['name'] != helper.DEVICE_CHECK]
            self.assertEqual(helper.guard(pr, files, checks, sha),
                             (False, 'waiting for required checks'), filename)

    def test_device_exemption_does_not_ignore_an_existing_failed_device_run(self):
        pr, files, checks, sha = copy.deepcopy(fixture())
        files[0]['filename'] = 'app/src/test/java/ExampleTest.kt'
        next(x for x in checks if x['name'] == helper.DEVICE_CHECK)['conclusion'] = 'failure'
        self.assertFalse(helper.guard(pr, files, checks, sha)[0])

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

        output = io.StringIO()
        with patch.object(helper, "gh_api", side_effect=api), \
                patch.object(helper.time, "sleep") as sleep, \
                contextlib.redirect_stdout(output):
            result = helper.finish_pr(46, sha, wait_seconds=1800, merge=True)
        self.assertEqual(result, 0)
        self.assertEqual(sum("/files?" in path for path, _ in calls), 2)
        self.assertEqual(sum(path.endswith("/check-runs?per_page=100") for path, _ in calls), 4)
        self.assertEqual(sum(path.endswith("/pulls/46") for path, _ in calls), 2)
        self.assertEqual(sum(method == "PUT" for _, method in calls), 1)
        self.assertEqual(sleep.call_count, 2)
        self.assertEqual(output.getvalue().count("waiting for "), 1)
        self.assertEqual(output.getvalue().count("all required checks passed"), 1)

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

    def test_sensitive_file_appearing_on_refresh_cannot_merge(self):
        pr, files, checks, sha = copy.deepcopy(fixture())
        files[0]["filename"] = "README.md"
        refreshed = [{"filename": ".github/workflows/android.yml"}]
        reads = iter((files, refreshed))
        mutations = []

        def api(endpoint, *, method="GET", fields=()):
            if method != "GET":
                mutations.append(endpoint)
                return {"merged": True}
            if endpoint.endswith("/files?per_page=100"):
                return next(reads)
            if endpoint.endswith("/check-runs?per_page=100"):
                return {"total_count": len(checks), "check_runs": checks}
            return pr

        with patch.object(helper, "gh_api", side_effect=api), \
                contextlib.redirect_stdout(io.StringIO()):
            result = helper.finish_pr(46, sha, merge=True)
        self.assertEqual(result, 2)
        self.assertEqual(mutations, [])


if __name__ == "__main__":
    unittest.main()
