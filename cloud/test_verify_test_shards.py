import tempfile
from pathlib import Path
import unittest
from xml.etree import ElementTree as ET

from verify_test_shards import verify


SHA = "a" * 40


def report(root: Path, shard: str, tests: list[tuple[str, str]], result: str = ""):
    folder = root / f"unit-tests-{shard}-{SHA}"
    folder.mkdir(parents=True, exist_ok=True)
    suite = ET.Element("testsuite")
    for classname, name in tests:
        case = ET.SubElement(suite, "testcase", classname=classname, name=name)
        if result:
            ET.SubElement(case, result)
    ET.ElementTree(suite).write(folder / "TEST-example.xml", encoding="utf-8")


class ShardReportsTests(unittest.TestCase):
    def test_complementary_reports_pass(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            report(root, "ui", [("a.UiTest", "one"), ("a.UiTest", "two")])
            report(root, "other", [("a.StoreTest", "one")])
            self.assertEqual(verify(root, SHA, 3), {"ui": 2, "other": 1})

    def test_missing_shard_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            report(root, "ui", [("a.UiTest", "one")])
            with self.assertRaisesRegex(ValueError, "Missing JUnit XML"):
                verify(root, SHA, 1)

    def test_duplicated_case_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            report(root, "ui", [("a.UiTest", "one"), ("a.UiTest", "one")])
            report(root, "other", [("a.StoreTest", "one")])
            with self.assertRaisesRegex(ValueError, "Duplicated testcase"):
                verify(root, SHA, 2)

    def test_class_partition_is_rejected_if_gradle_filter_is_wrong(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            report(root, "ui", [("a.StoreTest", "one")])
            report(root, "other", [("a.GrowthTest", "one")])
            with self.assertRaisesRegex(ValueError, "wrong ui shard"):
                verify(root, SHA, 2)

    def test_insufficient_coverage_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            report(root, "ui", [("a.UiTest", "one")])
            report(root, "other", [("a.StoreTest", "one")])
            with self.assertRaisesRegex(ValueError, "Only 2 tests"):
                verify(root, SHA, 330)

    def test_failed_and_skipped_cases_are_rejected(self):
        for result in ("failure", "error", "skipped"):
            with self.subTest(result=result), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                report(root, "ui", [("a.UiTest", "one")], result)
                report(root, "other", [("a.StoreTest", "one")])
                with self.assertRaisesRegex(ValueError, "Failed or skipped"):
                    verify(root, SHA, 2)

    def test_sha_and_minimum_must_be_valid(self):
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):
                verify(Path(tmp), "partialsha", 1)
            with self.assertRaises(ValueError):
                verify(Path(tmp), SHA, 0)


if __name__ == "__main__":
    unittest.main()
