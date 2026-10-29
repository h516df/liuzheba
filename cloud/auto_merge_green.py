#!/usr/bin/env python3
"""Conservatively finish an explicitly opted-in, green first-party PR.

This runs only from the trusted default branch in a workflow_run job. It never
checks out or executes pull-request code and never publishes a release.
"""

import json
import os
import subprocess
import sys
from pathlib import Path


REPOSITORY = "h516df/liuzheba"
OPT_IN = "<!-- liuzheba:auto-merge-on-green -->"
REQUIRED_CHECKS = frozenset(
    {"check", "build", "secret-scan", "API 35 isolated device acceptance"}
)
BLOCKED_PATHS = (
    ".github/workflows/",
    "cloud/auto_merge_green.py",
    "cloud/publish_release.py",
    "cloud/run_official_upgrade.py",
    "docs/SIGNING_PREPARATION.md",
)


def guard(event, prs, pr, changed_files, checks):
    """Return (safe_to_merge, reason). Fail closed on incomplete evidence."""
    run = event.get("workflow_run") or {}
    repo = (event.get("repository") or {}).get("full_name")
    sha = run.get("head_sha")
    source = (run.get("head_repository") or {}).get("full_name")
    if repo != REPOSITORY or source != REPOSITORY:
        return False, "foreign repository"
    if run.get("event") != "pull_request" or run.get("conclusion") != "success":
        return False, "not a successful PR workflow"
    if not isinstance(sha, str) or len(sha) != 40:
        return False, "missing exact commit"

    matches = [p for p in prs if (p.get("head") or {}).get("sha") == sha]
    if len(matches) != 1 or matches[0].get("number") != pr.get("number"):
        return False, "ambiguous source PR"
    if pr.get("state") != "open" or pr.get("draft"):
        return False, "closed or draft PR"
    if (pr.get("user") or {}).get("login") != "h516df":
        return False, "PR author is not repository owner"
    if (pr.get("head") or {}).get("sha") != sha:
        return False, "PR head changed while checking"
    if (pr.get("head") or {}).get("repo", {}).get("full_name") != REPOSITORY:
        return False, "fork PR"
    if (pr.get("base") or {}).get("ref") != "main":
        return False, "not targeting main"
    if OPT_IN not in (pr.get("body") or ""):
        return False, "no per-PR opt-in"

    if not changed_files or len(changed_files) >= 100:
        return False, "unavailable or incomplete file list"
    for row in changed_files:
        path = row.get("filename", "")
        if any(path.startswith(prefix) for prefix in BLOCKED_PATHS):
            return False, f"sensitive path: {path}"

    # The same name can appear in a superseded run. Never accept an earlier
    # successful job while a newer one is pending or failed.
    latest = {}
    for check in checks:
        name = check.get("name")
        if name not in REQUIRED_CHECKS:
            continue
        rank = (check.get("started_at") or "", check.get("id") or 0)
        if name not in latest or rank > latest[name][0]:
            latest[name] = (rank, check)
    if set(latest) != REQUIRED_CHECKS:
        return False, "required check missing"
    for name, (_, check) in latest.items():
        if check.get("status") != "completed" or check.get("conclusion") != "success":
            return False, f"required check not green: {name}"
    return True, "all guards passed"


def gh_api(endpoint, *, method="GET", fields=()):
    args = ["gh", "api", "-X", method, endpoint]
    for field in fields:
        args.extend(["-f", field])
    result = subprocess.run(args, capture_output=True, text=True, check=True)
    return json.loads(result.stdout)


def main():
    event_path = os.environ.get("GITHUB_EVENT_PATH")
    if not event_path:
        raise RuntimeError("GITHUB_EVENT_PATH is required")
    event = json.loads(Path(event_path).read_text(encoding="utf-8"))
    run = event.get("workflow_run") or {}
    sha = run.get("head_sha", "")
    if not (run.get("event") == "pull_request" and run.get("conclusion") == "success"):
        print("Skip: not a completed successful PR run")
        return
    if (event.get("repository") or {}).get("full_name") != REPOSITORY:
        print("Skip: foreign repository")
        return
    prs = gh_api(f"repos/{REPOSITORY}/commits/{sha}/pulls?per_page=100")
    matches = [p for p in prs if (p.get("head") or {}).get("sha") == sha]
    if len(matches) != 1:
        print("Skip: no unique PR for exact head")
        return
    number = matches[0]["number"]
    pr = gh_api(f"repos/{REPOSITORY}/pulls/{number}")
    if OPT_IN not in (pr.get("body") or ""):
        print("Skip: no opt-in")
        return
    files = gh_api(f"repos/{REPOSITORY}/pulls/{number}/files?per_page=100")
    checks = gh_api(f"repos/{REPOSITORY}/commits/{sha}/check-runs?per_page=100")
    approved, reason = guard(event, prs, pr, files, checks.get("check_runs") or [])
    print(f"PR #{number}: {reason}")
    if not approved:
        return
    # GitHub rechecks the exact head and branch protection at merge time.
    result = gh_api(
        f"repos/{REPOSITORY}/pulls/{number}/merge",
        method="PUT",
        fields=(f"sha={sha}", "merge_method=squash"),
    )
    if not result.get("merged"):
        raise RuntimeError(f"PR #{number} merge was not confirmed")
    print(f"Merged opted-in PR #{number} at {result['sha']}")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.CalledProcessError) as exc:
        print(f"Auto-merge watcher error: {type(exc).__name__}", file=sys.stderr)
        raise SystemExit(1) from exc
