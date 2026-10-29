"""Offline security and readiness tests for opt-in PR auto-merge."""

import copy
import unittest

import auto_merge_green as watcher


SHA = "a" * 40


def fixture():
    event = {
        "repository": {"full_name": watcher.REPOSITORY},
        "workflow_run": {
            "event": "pull_request", "conclusion": "success", "head_sha": SHA,
            "head_repository": {"full_name": watcher.REPOSITORY},
        },
    }
    pr = {
        "number": 46, "state": "open", "draft": False,
        "user": {"login": "h516df"},
        "head": {"sha": SHA, "repo": {"full_name": watcher.REPOSITORY}},
        "base": {"ref": "main"}, "body": watcher.OPT_IN,
    }
    checks = [
        {"name": name, "id": i, "started_at": "2026-10-29T01:00:00Z",
         "status": "completed", "conclusion": "success"}
        for i, name in enumerate(sorted(watcher.REQUIRED_CHECKS), start=1)
    ]
    return event, [pr], pr, [{"filename": "app/src/main/AndroidManifest.xml"}], checks


class ReadyGuardTest(unittest.TestCase):
    def test_complete_authorized_pr_can_merge(self):
        self.assertEqual(watcher.guard(*fixture()), (True, "all guards passed"))

    def test_missing_explicit_opt_in_is_safe(self):
        event, prs, pr, files, checks = copy.deepcopy(fixture())
        pr["body"] = "Normal PR"
        self.assertFalse(watcher.guard(event, prs, pr, files, checks)[0])

    def test_fork_and_unauthorized_user_are_rejected(self):
        for kind in ("fork", "author", "base", "draft"):
            event, prs, pr, files, checks = copy.deepcopy(fixture())
            if kind == "fork":
                pr["head"]["repo"]["full_name"] = "other/repo"
            elif kind == "author":
                pr["user"]["login"] = "other"
            elif kind == "base":
                pr["base"]["ref"] = "production"
            else:
                pr["draft"] = True
            self.assertFalse(watcher.guard(event, prs, pr, files, checks)[0], kind)

    def test_stale_commit_is_rejected(self):
        event, prs, pr, files, checks = copy.deepcopy(fixture())
        pr["head"]["sha"] = "b" * 40
        self.assertFalse(watcher.guard(event, prs, pr, files, checks)[0])

    def test_missing_pending_or_failing_check_is_rejected(self):
        for kind in ("missing", "pending", "failure"):
            event, prs, pr, files, checks = copy.deepcopy(fixture())
            if kind == "missing":
                checks.pop()
            elif kind == "pending":
                checks[0]["status"] = "in_progress"
            else:
                checks[0]["conclusion"] = "failure"
            self.assertFalse(watcher.guard(event, prs, pr, files, checks)[0], kind)

    def test_later_failed_rerun_overrides_old_success(self):
        event, prs, pr, files, checks = copy.deepcopy(fixture())
        checks.append({"name": "check", "id": 99,
                       "started_at": "2026-10-29T02:00:00Z",
                       "status": "completed", "conclusion": "failure"})
        self.assertFalse(watcher.guard(event, prs, pr, files, checks)[0])

    def test_workflow_and_release_paths_are_not_auto_merged(self):
        for filename in (".github/workflows/android.yml", "cloud/publish_release.py"):
            event, prs, pr, files, checks = copy.deepcopy(fixture())
            files[0]["filename"] = filename
            self.assertFalse(watcher.guard(event, prs, pr, files, checks)[0], filename)

    def test_incomplete_file_listing_is_rejected(self):
        event, prs, pr, files, checks = copy.deepcopy(fixture())
        self.assertFalse(watcher.guard(event, prs, pr, [], checks)[0])
        self.assertFalse(watcher.guard(event, prs, pr, files * 100, checks)[0])


if __name__ == "__main__":
    unittest.main()
