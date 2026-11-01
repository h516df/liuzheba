#!/usr/bin/env python3
"""The required Cloud `check` job must fail if any prerequisite is unsafe."""

import os
import sys


def verify_gate(scope: str, build: str, tests: str, tool_only: str, social_only: str):
    if scope != "success" or build != "success":
        raise ValueError(f"Prerequisite failed: scope={scope}, app-build={build}")
    if tool_only not in ("", "true", "false") or social_only not in ("", "true", "false"):
        raise ValueError("Unknown conservative fast-lane classification")
    if tool_only == "true" and social_only == "true":
        raise ValueError("Mutually exclusive fast lanes both selected")
    fast_lane = tool_only == "true" or social_only == "true"
    required = "skipped" if fast_lane else "success"
    if tests != required:
        raise ValueError(f"Unit shards were {tests}, expected {required}")


def main():
    try:
        verify_gate(
            os.environ.get("SCOPE_RESULT", ""),
            os.environ.get("BUILD_RESULT", ""),
            os.environ.get("TEST_RESULT", ""),
            os.environ.get("TOOL_ONLY", ""),
            os.environ.get("SOCIAL_ONLY", ""),
        )
    except ValueError as exc:
        print(f"Cloud check blocked: {exc}", file=sys.stderr)
        return 1
    print("Cloud required check: all applicable jobs passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
