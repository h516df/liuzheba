#!/usr/bin/env python3
"""Require successful push checks on the exact main commit before official signing."""
import json
import os
import re
import subprocess

def main():
    repo=os.environ["GITHUB_REPOSITORY"];sha=os.environ["GITHUB_SHA"]
    if repo!="h516df/liuzheba" or os.environ["GITHUB_REF"]!="refs/heads/main" or os.environ["EXPECTED_SHA"]!=sha or not re.fullmatch("[0-9a-f]{40}",sha):
        raise SystemExit("Release requires an explicitly selected, exact main commit")
    runs={}
    for workflow in ("cloud-development.yml","android-device-checks.yml","secret-checks.yml"):
        data=json.loads(subprocess.check_output(["gh","api",f"repos/{repo}/actions/workflows/{workflow}/runs?head_sha={sha}&per_page=30"],text=True))
        successful=[r for r in data["workflow_runs"] if r["head_sha"]==sha and r["event"]=="push" and r["conclusion"]=="success"]
        if not successful:raise SystemExit(f"Exact-source check has not passed: {workflow}")
        runs[workflow]=successful[0]["id"]
    with open(os.environ["GITHUB_OUTPUT"],"a") as out:out.write(f"cloud_run={runs['cloud-development.yml']}\n")
    print(json.dumps({"sourceSha":sha,"passedRuns":runs}))

if __name__=="__main__":main()
