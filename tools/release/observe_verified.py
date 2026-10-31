#!/usr/bin/env python3
"""Read-only production release observer for an existing authenticated gh session.

Unlike a second dispatch attempt, this is safe to rerun after a timeout. It
checks the original run, its four jobs and the three official Release assets.
It does not download an APK or claim that an Android phone was upgraded.
"""

import argparse
import json
import os
import re
import time

from dispatch_verified import REPO, WORKFLOW, github_api


JOBS = frozenset({"build", "sign", "upgrade", "publish"})


def find_run(expected_sha, *, api=github_api):
    """Find the unique official dispatch for this main SHA, if it exists."""
    data = api(f"repos/{REPO}/actions/runs?head_sha={expected_sha}&event=workflow_dispatch&per_page=50")
    runs = data.get("workflow_runs")
    if not isinstance(runs, list) or len(runs) >= 50:
        raise RuntimeError("Dispatch run list is missing or incomplete")
    matching = [r for r in runs if r.get("head_sha") == expected_sha
                and r.get("event") == "workflow_dispatch" and r.get("head_branch") == "main"
                and r.get("path") == f".github/workflows/{WORKFLOW}"]
    if len(matching) > 1:
        raise RuntimeError("Multiple production runs for this SHA; inspect them individually")
    if not matching:
        return None
    run_id = matching[0].get("id")
    if type(run_id) is not int or run_id <= 0:
        raise RuntimeError("Production run ID is invalid")
    return run_id


def verify_run(run_id, expected_sha, tag, *, api=github_api):
    """Return a publish receipt only for a completed, genuine source-bound run."""
    run = api(f"repos/{REPO}/actions/runs/{run_id}")
    if (run.get("id") != run_id or run.get("head_sha") != expected_sha
            or run.get("event") != "workflow_dispatch" or run.get("head_branch") != "main"
            or run.get("path") != f".github/workflows/{WORKFLOW}"):
        raise RuntimeError("Run does not match the official workflow and exact main source")
    if run.get("status") != "completed":
        raise RuntimeError(f"Production run {run_id} is not complete")
    if run.get("conclusion") != "success":
        raise RuntimeError(f"Production run {run_id} failed: {run.get('conclusion')}")

    jobs = api(f"repos/{REPO}/actions/runs/{run_id}/jobs?per_page=100")
    items = jobs.get("jobs")
    if (not isinstance(items, list) or jobs.get("total_count") != len(items)
            or len(items) >= 100):
        raise RuntimeError("Production job evidence is incomplete")
    observed = {j.get("name") for j in items
                if j.get("status") == "completed" and j.get("conclusion") == "success"}
    if not JOBS <= observed or len(items) < len(JOBS):
        raise RuntimeError("Build, sign, upgrade and publish did not all pass")

    published = api(f"repos/{REPO}/releases/tags/{tag}")
    if (published.get("tag_name") != tag or published.get("draft") is not False
            or published.get("prerelease") is not False
            or published.get("target_commitish") != expected_sha):
        raise RuntimeError("Public Release is missing, unpublished or bound to another SHA")
    expected_names = {f"shicang-{tag}-universal.apk",
                      f"shicang-{tag}-release-notes.json", f"shicang-{tag}-SHA256.txt"}
    assets = published.get("assets")
    if not isinstance(assets, list) or {a.get("name") for a in assets} != expected_names:
        raise RuntimeError("Public Release assets are missing or unexpected")
    receipt_assets = {}
    for asset in assets:
        digest = asset.get("digest")
        size = asset.get("size")
        if type(size) is not int or size <= 0 or not isinstance(digest, str) or not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
            raise RuntimeError("Release asset size or GitHub SHA256 digest is unavailable")
        receipt_assets[asset["name"]] = {"size": size, "sha256": digest.removeprefix("sha256:")}

    latest = api(f"repos/{REPO}/releases/latest")
    if latest.get("tag_name") != tag:
        raise RuntimeError("Published tag is not the latest stable Release")
    return {"status": "verified", "sourceSha": expected_sha, "tag": tag,
            "run": f"https://github.com/{REPO}/actions/runs/{run_id}",
            "release": f"https://github.com/{REPO}/releases/tag/{tag}",
            "assets": receipt_assets, "phoneUpgrade": "not_tested"}


def observe(expected_sha, tag, *, wait_seconds=0, api=github_api, clock=time.monotonic,
            sleep=time.sleep):
    if not re.fullmatch(r"[0-9a-f]{40}", expected_sha):
        raise ValueError("--expected-sha must be a full 40-character lowercase commit SHA")
    if not re.fullmatch(r"v\d+\.\d+(?:\.\d+)?", tag):
        raise ValueError("--tag must be a version such as v0.23")
    if not isinstance(wait_seconds, int) or not 0 <= wait_seconds <= 1800:
        raise ValueError("--wait-seconds must be between 0 and 1800")
    if (api("user") or {}).get("login") != "h516df":
        raise RuntimeError("Expected the existing h516df GitHub user session")
    deadline = clock() + wait_seconds
    while True:
        run_id = find_run(expected_sha, api=api)
        if run_id is not None:
            try:
                return verify_run(run_id, expected_sha, tag, api=api)
            except RuntimeError as exc:
                if not str(exc).startswith(f"Production run {run_id} is not complete"):
                    raise
        if clock() >= deadline:
            raise RuntimeError("Release not yet verified; inspect the existing run before any new dispatch")
        sleep(min(20, max(0, deadline - clock())))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expected-sha", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--wait-seconds", type=int, default=0)
    args = parser.parse_args(argv)
    if os.environ.get("GITHUB_ACTIONS", "").lower() == "true":
        parser.error("Use an existing user GitHub session, never GitHub Actions GITHUB_TOKEN")
    try:
        print(json.dumps(observe(args.expected_sha, args.tag, wait_seconds=args.wait_seconds),
                         ensure_ascii=False))
        return 0
    except (ValueError, RuntimeError) as exc:
        print(f"BLOCKED: {exc}")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
