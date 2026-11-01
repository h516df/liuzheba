#!/usr/bin/env python3
"""Fail closed if the two Gradle test shards lose or duplicate JUnit cases.

This checks GitHub artifact evidence, not a claimed Gradle task status. New test
classes are assigned automatically by the complementary Gradle name filters.
"""

import os
import fnmatch
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


SHARDS = ("ui", "other")


def verify(root: Path, sha: str, minimum_tests: int = 330) -> dict[str, int]:
    if len(sha) != 40 or any(ch not in "0123456789abcdef" for ch in sha):
        raise ValueError("Expected the full lowercase 40-character commit SHA")
    if minimum_tests < 1:
        raise ValueError("The minimum test count must be positive")

    counts = {}
    seen = set()
    for shard in SHARDS:
        folder = root / f"unit-tests-{shard}-{sha}"
        files = sorted(folder.rglob("TEST-*.xml")) if folder.is_dir() else []
        if not files:
            raise ValueError(f"Missing JUnit XML for {shard} shard")
        count = 0
        for file in files:
            try:
                testsuite = ET.parse(file)
            except ET.ParseError as exc:
                raise ValueError(f"Invalid XML report: {file}") from exc
            cases = testsuite.findall(".//testcase")
            if not cases:
                raise ValueError(f"Empty XML report: {file}")
            for case in cases:
                key = (case.get("classname"), case.get("name"))
                if not all(key):
                    raise ValueError(f"Testcase without classname or name: {file}")
                # Match the class partition independently from Gradle. A
                # missing Gradle filter must not silently pass as full coverage.
                ui_class = fnmatch.fnmatchcase(key[0].rsplit(".", 1)[-1], "*Ui*Test")
                if ui_class != (shard == "ui"):
                    raise ValueError(f"Testcase in wrong {shard} shard: {key}")
                if key in seen:
                    raise ValueError(f"Duplicated testcase across reports: {key}")
                seen.add(key)
                if any(case.find(kind) is not None for kind in ("failure", "error", "skipped")):
                    raise ValueError(f"Failed or skipped testcase in {shard}: {key}")
                count += 1
        counts[shard] = count

    total = sum(counts.values())
    if total < minimum_tests:
        raise ValueError(f"Only {total} tests, expected at least {minimum_tests}")
    return counts


def main() -> int:
    if len(sys.argv) != 4:
        print("usage: verify_test_shards.py ROOT FULL_SHA MIN_TESTS", file=sys.stderr)
        return 2
    try:
        counts = verify(Path(sys.argv[1]), sys.argv[2], int(sys.argv[3]))
    except (ValueError, OSError) as exc:
        print(f"shard verification failed: {exc}", file=sys.stderr)
        return 1
    message = f"JVM test coverage: ui={counts['ui']}, other={counts['other']}, total={sum(counts.values())}"
    print(message)
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a", encoding="utf-8") as stream:
            stream.write(f"### Sharded JVM test coverage\n\n{message}\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
