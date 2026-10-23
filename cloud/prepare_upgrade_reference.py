#!/usr/bin/env python3
"""Validate reference provenance; grants no signing or publication privileges."""
import json
import os
import re
import subprocess


def main():
    run = os.environ["REFERENCE_RUN"]
    sha = os.environ["REFERENCE_SHA"]
    if not re.fullmatch(r"[0-9]+", run) or not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise SystemExit("Both the numeric reference run and exact candidate SHA are required")
    data = json.loads(subprocess.check_output([
        "gh", "api", f"repos/h516df/liuzheba/actions/runs/{run}"
    ], text=True))
    if (data["head_sha"] != sha or data["head_branch"] != "main"
            or data["event"] != "workflow_dispatch"
            or data["path"] != ".github/workflows/production-release.yml"):
        raise SystemExit("Reference must originate from the exact main production-release source")
    print(json.dumps({"harnessSourceSha": os.environ["GITHUB_SHA"],
                      "candidateSourceSha": sha, "referenceRun": int(run),
                      "purpose": "harness validation only; not new-source release acceptance"}))


if __name__ == "__main__":
    main()
