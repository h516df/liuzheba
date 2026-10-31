"""Protect the PR CI fast lane from accidentally swallowing app/release changes."""

import pathlib
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import ci_scope


class CiScopeTests(unittest.TestCase):
    def test_docs_only_uses_light_check(self):
        self.assertEqual(ci_scope.classify_paths(["README.md", "HANDOFF.md"]), "maintenance")

    def test_release_helper_and_tests_use_light_check(self):
        self.assertEqual(ci_scope.classify_paths([
            "tools/release/dispatch_verified.py", "tools/release/test_dispatch_verified.py",
            "cloud/test_user_release_dispatch.py", "docs/RELEASING.md",
        ]), "maintenance")

    def test_existing_finisher_tool_remains_light(self):
        self.assertEqual(ci_scope.classify_paths(["cloud/finish_green_pr.py"]), "maintenance")

    def test_social_python_only_is_fast(self):
        self.assertEqual(ci_scope.classify_paths([
            "app/src/main/python/social/download.py", "tools/tests/test_media_bridge.py",
        ]), "social")

    def test_python_binary_resource_forces_full(self):
        self.assertEqual(ci_scope.classify_paths(["app/src/main/python/lib/runtime.so"]), "full")

    def test_any_app_change_forces_full(self):
        self.assertEqual(ci_scope.classify_paths(["README.md", "app/build.gradle.kts"]), "full")

    def test_release_notes_are_not_docs_only(self):
        for path in ("docs/releases/v0.23.md", "app/src/main/assets/release-notes.json",
                     "release/signing-identity.json"):
            with self.subTest(path=path):
                self.assertEqual(ci_scope.classify_paths([path]), "full")

    def test_actions_or_classifier_change_forces_full(self):
        for path in ("cloud/ci_scope.py", ".github/workflows/android.yml",
                     "cloud/device_acceptance.sh", "cloud/check.sh"):
            with self.subTest(path=path):
                self.assertEqual(ci_scope.classify_paths([path]), "full")

    def test_unsafe_or_empty_paths_force_full(self):
        for paths in (None, [], [""], ["../README.md"], ["/README.md"]):
            with self.subTest(paths=paths):
                self.assertEqual(ci_scope.classify_paths(paths), "full")

    @patch("ci_scope.subprocess.check_output")
    def test_non_merge_commit_never_fast_tracks(self, check_output):
        check_output.return_value = b"a" * 40
        self.assertIsNone(ci_scope.changed_paths_from_pr_merge())
        check_output.assert_called_once()

    @patch("ci_scope.subprocess.check_output")
    def test_merge_commit_reads_changes(self, check_output):
        check_output.side_effect = [
            b"a" * 40 + b" " + b"b" * 40 + b" " + b"c" * 40 + b"\n",
            b"README.md\0tools/release/dispatch_verified.py\0",
        ]
        self.assertEqual(ci_scope.changed_paths_from_pr_merge(),
                         ["README.md", "tools/release/dispatch_verified.py"])

    @patch("ci_scope.subprocess.check_output", side_effect=OSError("unavailable"))
    def test_git_failure_never_fast_tracks(self, _check_output):
        self.assertIsNone(ci_scope.changed_paths_from_pr_merge())

    @patch("ci_scope.subprocess.check_output")
    def test_invalid_git_encoding_never_fast_tracks(self, check_output):
        check_output.side_effect = [
            b"a" * 40 + b" " + b"b" * 40 + b" " + b"c" * 40 + b"\n",
            b"README.md\0\xff\0",
        ]
        self.assertIsNone(ci_scope.changed_paths_from_pr_merge())


if __name__ == "__main__":
    unittest.main()
