import unittest

from verify_cloud_gate import verify_gate


class CloudGateTests(unittest.TestCase):
    def test_full_check_passes_only_when_both_succeed(self):
        verify_gate("success", "success", "success", "", "")
        verify_gate("success", "success", "success", "false", "false")
        for unit_result in ("skipped", "failure", "cancelled", ""):
            with self.subTest(unit_result=unit_result), self.assertRaises(ValueError):
                verify_gate("success", "success", unit_result, "", "")

    def test_maintenance_and_social_require_tests_to_be_skipped(self):
        for tool_only, social_only in (("true", "false"), ("false", "true")):
            with self.subTest(tool_only=tool_only, social_only=social_only):
                verify_gate("success", "success", "skipped", tool_only, social_only)
                with self.assertRaises(ValueError):
                    verify_gate("success", "success", "success", tool_only, social_only)

    def test_missing_or_failed_prerequisites_are_rejected(self):
        for result in ("failure", "skipped", "cancelled", ""):
            with self.subTest(result=result):
                with self.assertRaises(ValueError):
                    verify_gate(result, "success", "success", "", "")
                with self.assertRaises(ValueError):
                    verify_gate("success", result, "success", "", "")

    def test_invalid_fast_lane_flags_are_rejected(self):
        for tool, social in (("true", "true"), ("unexpected", "false")):
            with self.assertRaises(ValueError):
                verify_gate("success", "success", "success", tool, social)


if __name__ == "__main__":
    unittest.main()
