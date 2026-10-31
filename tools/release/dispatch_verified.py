#!/usr/bin/env python3
"""User-session preflight and opt-in dispatch for the existing protected release.

This helper never signs, builds, publishes, merges or changes GitHub settings.
The official production-release.yml remains the only publication authority.
"""

import argparse
import base64
import json
import os
import re
import subprocess


REPO = "h516df/liuzheba"
WORKFLOW = "production-release.yml"
REQUIRED_CHECKS = (
    "cloud-development.yml",
    "android-device-checks.yml",
    "secret-checks.yml",
    "android.yml",
)


def command(*args):
    try:
        result = subprocess.run(args, capture_output=True, text=True, timeout=60, check=False)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise RuntimeError(f"GitHub CLI unavailable or timed out: {args[0]}") from exc
    return result


def github_api(path):
    result = command("gh", "api", path)
    if result.returncode:
        raise RuntimeError(f"GitHub read failed for {path}; verify the logged-in account and connection")
    try:
        return json.loads(result.stdout)
    except ValueError as exc:
        raise RuntimeError(f"Invalid GitHub JSON response for {path}") from exc


def tag_is_free(tag):
    result = command("gh", "api", f"repos/{REPO}/git/ref/tags/{tag}")
    if result.returncode == 0:
        return False
    if not re.search(r"\bHTTP 404\b", result.stderr):
        raise RuntimeError("Git tag status is unknown; refusing to dispatch")
    return True


def preflight(expected_sha, tag, *, api=github_api, tag_available=tag_is_free):
    """Use only fresh, exact-source GitHub reads; fail closed on missing evidence."""
    if not re.fullmatch(r"[0-9a-f]{40}", expected_sha):
        raise ValueError("--expected-sha must be a full 40-character lowercase commit SHA")
    if not re.fullmatch(r"v\d+\.\d+(?:\.\d+)?", tag):
        raise ValueError("--tag must be a version such as v0.23")

    if (api("user") or {}).get("login") != "h516df":
        raise RuntimeError("Expected the existing h516df GitHub user session")
    repository = api(f"repos/{REPO}")
    if repository.get("full_name") != REPO or repository.get("default_branch") != "main" or repository.get("private") is not False:
        raise RuntimeError("Repository identity, branch or public access changed")
    main = api(f"repos/{REPO}/branches/main")
    if (main.get("commit") or {}).get("sha") != expected_sha:
        raise RuntimeError("main changed; review the new SHA and checks before dispatch")

    content = api(f"repos/{REPO}/contents/app/src/main/assets/release-notes.json?ref={expected_sha}")
    if content.get("encoding") != "base64" or not isinstance(content.get("content"), str):
        raise RuntimeError("Cannot read release version from exact-source metadata")
    try:
        raw = base64.b64decode(content["content"], validate=False)
        if len(raw) > 32768:
            raise ValueError("release notes are unexpectedly large")
        notes = json.loads(raw)
    except (ValueError, TypeError) as exc:
        raise RuntimeError("Invalid release metadata") from exc
    if not isinstance(notes, dict) or notes.get("versionName") != tag[1:] or type(notes.get("versionCode")) is not int or notes["versionCode"] <= 0:
        raise RuntimeError("Requested tag differs from the application release metadata")

    releases = api(f"repos/{REPO}/releases?per_page=100")
    if not isinstance(releases, list) or len(releases) >= 100:
        raise RuntimeError("Release list is incomplete; check existing drafts manually")
    if any(item.get("tag_name") == tag for item in releases):
        raise RuntimeError("Release tag is already occupied (possibly by a draft)")
    if not tag_available(tag):
        raise RuntimeError("Git tag is already occupied")

    passed = {}
    for filename in REQUIRED_CHECKS:
        response = api(f"repos/{REPO}/actions/workflows/{filename}/runs?head_sha={expected_sha}&per_page=30")
        runs = response.get("workflow_runs")
        if not isinstance(runs, list):
            raise RuntimeError(f"Incomplete workflow evidence: {filename}")
        verified = [run for run in runs if run.get("head_sha") == expected_sha
                    and run.get("head_branch") == "main" and run.get("event") == "push"
                    and run.get("status") == "completed" and run.get("conclusion") == "success"]
        if not verified:
            raise RuntimeError(f"Exact main push check has not passed: {filename}")
        passed[filename] = verified[0].get("id")

    # Avoid a second dispatch while a first run has yet to publish (or failed
    # midway). A release owner must inspect that run before any explicit retry.
    existing_runs = api(f"repos/{REPO}/actions/runs?head_sha={expected_sha}&event=workflow_dispatch&per_page=50")
    runs = existing_runs.get("workflow_runs")
    if not isinstance(runs, list) or len(runs) >= 50:
        raise RuntimeError("Cannot exclude an existing official dispatch")
    if any(run.get("head_sha") == expected_sha and run.get("path") == f".github/workflows/{WORKFLOW}"
           for run in runs):
        raise RuntimeError("Official workflow was already dispatched for this SHA; inspect it before retrying")

    return {"sourceSha": expected_sha, "tag": tag, "checks": passed,
            "workflow": f"https://github.com/{REPO}/actions/workflows/{WORKFLOW}"}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expected-sha", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--dispatch", action="store_true", help="explicitly start the existing protected workflow")
    args = parser.parse_args(argv)
    if os.environ.get("GITHUB_ACTIONS", "").lower() == "true":
        parser.error("Use an existing user GitHub session, never GitHub Actions GITHUB_TOKEN")

    try:
        receipt = preflight(args.expected_sha, args.tag)
        print(json.dumps({"preflight": "passed", **receipt}, ensure_ascii=False))
        if not args.dispatch:
            print("Read-only. After explicit release authorization, rerun with --dispatch.")
            return 0
        result = command("gh", "workflow", "run", WORKFLOW, "--repo", REPO,
                         "--ref", "main", "-f", f"expected_sha={args.expected_sha}")
        if result.returncode:
            raise RuntimeError("Dispatch result is unknown; inspect workflow runs before retrying")
        print(result.stdout.strip() or "Dispatched; inspect the workflow page for its run ID")
        return 0
    except (RuntimeError, ValueError) as exc:
        print(f"BLOCKED: {exc}")
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
