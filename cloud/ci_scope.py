#!/usr/bin/env python3
"""Conservative PR-only build scope for the two Android-oriented workflows.

Only a genuine GitHub pull-request merge commit can select a fast lane. Unknown
changes, missing Git history, and any edits to this classifier build fully.
Main pushes and protected release branches never call this entry point.
"""

import json
import os
import subprocess


# Exact paths only: release notes, application code, workflow rules and
# verification policy must never acquire a fast lane through a broad glob.
MAINTENANCE_PATHS = frozenset({
    "AGENTS.md",
    "CLOUD_DEVELOPMENT.md",
    "HANDOFF.md",
    "README.md",
    "docs/RELEASING.md",
    "docs/CI_OPTIMIZATION.md",
    "cloud/finish_green_pr.py",
    "cloud/test_finish_green_pr.py",
    "cloud/test_user_release_dispatch.py",
    "tools/release/dispatch_verified.py",
    "tools/release/test_dispatch_verified.py",
    "tools/release/observe_verified.py",
    "cloud/test_release_observe.py",
})


def classify_paths(paths):
    """Return maintenance, social or full (unknown changes always full)."""
    changed = set(paths) if paths is not None else set()
    if not changed or not all(isinstance(p, str) and p and not p.startswith("/")
                              and ".." not in p.split("/") for p in changed):
        return "full"
    if changed <= MAINTENANCE_PATHS:
        return "maintenance"
    if all((p.startswith("app/src/main/python/") and p.endswith(".py"))
           or p == "tools/tests/test_media_bridge.py" for p in changed):
        return "social"
    return "full"


def changed_paths_from_pr_merge():
    """The first parent of an ephemeral PR merge commit is the trusted base."""
    try:
        parents = subprocess.check_output(
            ["git", "rev-list", "--parents", "-n", "1", "HEAD"], timeout=15,
        ).decode("ascii").split()
        if len(parents) != 3 or any(len(parent) != 40 for parent in parents):
            return None
        raw = subprocess.check_output(
            ["git", "diff", "--name-only", "--no-renames", "-z", "HEAD^1", "HEAD"],
            timeout=15,
        )
        if not raw or not raw.endswith(b"\0"):
            return None
        paths = raw[:-1].decode("utf-8").split("\0")
        if len(paths) > 1000:
            return None
        return paths
    except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired,
            UnicodeDecodeError):
        return None


def main():
    paths = changed_paths_from_pr_merge()
    scope = classify_paths(paths)
    result = {
        "tool_only": str(scope == "maintenance").lower(),
        "social_only": str(scope == "social").lower(),
    }
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as stream:
            for key, value in result.items():
                stream.write(f"{key}={value}\n")
    print(json.dumps({"ci_scope": scope, "changed_files": len(paths or []), **result}))


if __name__ == "__main__":
    main()
