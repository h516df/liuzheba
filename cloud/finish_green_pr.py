#!/usr/bin/env python3
"""Bounded PR finish helper for an already authorized human/agent GitHub login.

Never call this from GitHub Actions: merging with GITHUB_TOKEN would suppress the
main push workflows required by the official release gate.
"""

import argparse
import json
import os
import re
import subprocess
import time


REPO = "h516df/liuzheba"
CORE_CHECKS = frozenset({"check", "build", "secret-scan"})
DEVICE_CHECK = "API 35 isolated device acceptance"
BLOCKED_PATHS = (
    ".github/workflows/",
    "cloud/finish_green_pr.py",
    "cloud/release_gate.py",
    "cloud/publish_release.py",
    "cloud/run_official_upgrade.py",
    "docs/SIGNING_PREPARATION.md",
)
DEVICE_PATHS = (
    "app/", "gradle/", "cloud/device_acceptance.sh",
    "cloud/prepare_upgrade_reference.py", "cloud/run_official_upgrade.py",
    "cloud/verify_upgrade.py", "tools/verification.init.gradle",
    "tools/release/verify_apk.py", "gradle.properties",
)


def guard(pr, changed_files, checks, expected_head):
    """Return (ready, reason). Requires current exact PR and CI evidence."""
    if not re.fullmatch(r"[0-9a-f]{40}", expected_head):
        return False, "invalid expected SHA"
    if pr.get("state") != "open" or pr.get("draft"):
        return False, "PR is closed or draft"
    if (pr.get("user") or {}).get("login") != "h516df":
        return False, "untrusted PR author"
    head = pr.get("head") or {}
    if head.get("sha") != expected_head:
        return False, "PR head changed"
    if (head.get("repo") or {}).get("full_name") != REPO:
        return False, "fork PR"
    if (pr.get("base") or {}).get("ref") != "main":
        return False, "not targeting main"
    if not changed_files or len(changed_files) >= 100:
        return False, "incomplete changed-file evidence"

    names = [item.get("filename", "") for item in changed_files]
    for name in names:
        if any(name.startswith(prefix) for prefix in BLOCKED_PATHS):
            return False, f"sensitive change requires manual review: {name}"
    needs_device = any(
        name.startswith(DEVICE_PATHS) or name.endswith(".gradle.kts") for name in names
    )
    required = CORE_CHECKS | ({DEVICE_CHECK} if needs_device else set())

    latest = {}
    for check in checks:
        name = check.get("name")
        if name not in (CORE_CHECKS | {DEVICE_CHECK}):
            continue
        rank = (check.get("started_at") or "", check.get("id") or 0)
        if name not in latest or rank > latest[name][0]:
            latest[name] = (rank, check)
    if not required.issubset(latest):
        return False, "waiting for required checks"
    # If an optional device check did run, it must be green as well.
    for name in required | ({DEVICE_CHECK} if DEVICE_CHECK in latest else set()):
        check = latest[name][1]
        if check.get("status") != "completed":
            return False, f"waiting for {name}"
        if check.get("conclusion") != "success":
            return False, f"check did not succeed: {name}"
    return True, "all required checks passed"


def gh_api(endpoint, *, method="GET", fields=()):
    args = ["gh", "api", "-X", method, endpoint]
    for field in fields:
        args.extend(["-f", field])
    result = subprocess.run(args, check=True, capture_output=True, text=True)
    return json.loads(result.stdout)


def read_checks(expected_head):
    result = gh_api(f"repos/{REPO}/commits/{expected_head}/check-runs?per_page=100")
    checks = result.get("check_runs") or []
    if result.get("total_count", len(checks)) > len(checks):
        raise RuntimeError("incomplete check evidence; review manually")
    return checks


def finish_pr(pr_number, expected_head, wait_seconds=0, merge=False):
    """Poll only mutable check results; revalidate the full PR before a finish."""
    pr_endpoint = f"repos/{REPO}/pulls/{pr_number}"
    files_endpoint = f"{pr_endpoint}/files?per_page=100"
    deadline = time.monotonic() + wait_seconds
    pr = gh_api(pr_endpoint)
    files = gh_api(files_endpoint)
    waited = False
    while True:
        checks = read_checks(expected_head)
        ready, reason = guard(pr, files, checks, expected_head)
        if ready and (waited or merge):
            # The PR can change while checks run. Never merge using cached
            # ownership, branch, file scope or check-run evidence.
            pr = gh_api(pr_endpoint)
            files = gh_api(files_endpoint)
            checks = read_checks(expected_head)
            ready, reason = guard(pr, files, checks, expected_head)
        print(f"PR #{pr_number} {expected_head[:12]}: {reason}", flush=True)
        if ready:
            if not merge:
                print("Read-only: use --merge after authorization and review")
                return 0
            merged = gh_api(
                f"{pr_endpoint}/merge", method="PUT",
                fields=(f"sha={expected_head}", "merge_method=squash"),
            )
            if not merged.get("merged"):
                raise RuntimeError("GitHub did not confirm merge")
            print(f"Merged at {merged['sha']}; now verify main push checks", flush=True)
            return 0
        if not reason.startswith("waiting for ") or time.monotonic() >= deadline:
            return 2
        waited = True
        time.sleep(min(30, max(0, deadline - time.monotonic())))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pr", type=int, required=True)
    parser.add_argument("--expected-head", required=True)
    parser.add_argument("--wait-seconds", type=int, default=0)
    parser.add_argument("--merge", action="store_true", help="opt in to squash merge after green")
    args = parser.parse_args()
    if args.pr <= 0 or not (0 <= args.wait_seconds <= 3600):
        parser.error("PR must be positive and wait must be 0–3600 seconds")
    if os.environ.get("GITHUB_ACTIONS", "").lower() == "true":
        parser.error("run outside Actions with the existing user GitHub identity")

    return finish_pr(args.pr, args.expected_head, args.wait_seconds, args.merge)


if __name__ == "__main__":
    raise SystemExit(main())
