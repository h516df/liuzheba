#!/usr/bin/env python3
"""Explicit official publication after the isolated upgrade job. Never replaces an asset."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

def gh(*args,check=True):
    result=subprocess.run(["gh",*map(str,args)],capture_output=True,text=True,timeout=180)
    if check and result.returncode:raise RuntimeError("GitHub publication step failed: "+result.stderr)
    return result

def digest(path):
    h=hashlib.sha256()
    with path.open("rb") as stream:
        for data in iter(lambda:stream.read(1024*1024),b""):h.update(data)
    return h.hexdigest()

def main():
    p=argparse.ArgumentParser();p.add_argument("--directory",type=Path,required=True);args=p.parse_args();directory=args.directory
    repo=os.environ["GITHUB_REPOSITORY"];sha=os.environ["GITHUB_SHA"]
    if repo!="h516df/liuzheba" or os.environ["GITHUB_REF"]!="refs/heads/main":raise SystemExit("Only the reviewed official main may publish")
    signature=json.loads((directory/"signature.json").read_text());upgrade=json.loads((directory/"official-upgrade.json").read_text())
    identity=json.loads(Path("release/signing-identity.json").read_text());notes=json.loads(Path("app/src/main/assets/release-notes.json").read_text())
    apk=directory/"official.apk";apk_digest=digest(apk)
    if not (upgrade["status"]=="passed" and upgrade["sourceSha"]==sha and upgrade["candidateSha256"]==apk_digest
        and signature["sha256"]==apk_digest and signature["certificateSha256"]==identity["certificateSha256"]
        and signature["packageName"]==identity["packageName"] and signature["syntheticProbe"] is False and signature["apkSignatureVerified"]):
        raise SystemExit("The signed candidate and exact-source upgrade receipt do not agree")
    version=notes["versionName"]
    if not re.fullmatch(r"\d+\.\d+(?:\.\d+)?",version):raise SystemExit("Invalid release version")
    tag="v"+version
    # Drafts may have no Git tag yet and are invisible to GET /releases/tags/{tag}.
    # List releases with authenticated pagination so a retry cannot create a second draft.
    occupied=gh("api","--paginate",f"repos/{repo}/releases?per_page=100",
        "--jq",f'.[] | select(.tag_name == "{tag}") | .id').stdout.strip()
    if occupied:raise SystemExit("Release tag is occupied, including an unpublished draft")
    existing=gh("api",f"repos/{repo}/git/ref/tags/{tag}",check=False)
    if existing.returncode==0 or not re.search(r"\bHTTP 404\b",existing.stderr):raise SystemExit("Git tag is occupied or cannot be safely checked")
    official=directory/f"shicang-{tag}-universal.apk";shutil.copyfile(apk,official)
    notes_path=directory/f"shicang-{tag}-release-notes.json";shutil.copyfile("app/src/main/assets/release-notes.json",notes_path)
    checksums=directory/f"shicang-{tag}-SHA256.txt"
    checksums.write_text("".join(f"{digest(path)}  {path.name}\n" for path in (official,notes_path)))
    body=directory/"release-body.md"
    body.write_text(Path(f"docs/releases/{tag}.md").read_text()+f"\n\n源码：`{sha}`。\n\n[云端验证与发布记录](https://github.com/{repo}/actions/runs/{os.environ['GITHUB_RUN_ID']})。\n")
    request=directory/"release-request.json"
    request.write_text(json.dumps(dict(tag_name=tag,target_commitish=sha,
        name=f"留着吧 {tag} · 档案与沉浸浏览重构",body=body.read_text(),draft=True,prerelease=False),ensure_ascii=False))
    created=json.loads(gh("api",f"repos/{repo}/releases","--method","POST","--input",request).stdout)
    release_id=created.get("id")
    if not (type(release_id) is int and release_id>0 and created.get("draft") is True
        and created.get("prerelease") is False and created.get("tag_name")==tag and created.get("target_commitish")==sha):
        raise SystemExit("Created draft does not match the reviewed source")
    gh("release","upload",tag,official,notes_path,checksums,"--repo",repo)
    draft=json.loads(gh("api",f"repos/{repo}/releases/{release_id}").stdout)
    if not (draft.get("id")==release_id and draft.get("draft") is True and draft.get("prerelease") is False
        and draft.get("tag_name")==tag and draft.get("target_commitish")==sha):
        raise SystemExit("Draft identity changed; refusing publication")
    if len(draft["assets"])!=3 or {a["name"] for a in draft["assets"]}!={official.name,notes_path.name,checksums.name}:
        raise SystemExit("Unexpected uploaded assets; release remains a draft")
    for path in (official,notes_path,checksums):
        matches=[a for a in draft["assets"] if a["name"]==path.name]
        if len(matches)!=1 or matches[0]["state"]!="uploaded" or matches[0]["size"]!=path.stat().st_size or matches[0].get("digest")!="sha256:"+digest(path):
            raise SystemExit("Uploaded asset verification failed; release remains a draft")
    publish_request=directory/"publish-request.json"
    publish_request.write_text(json.dumps(dict(draft=False,prerelease=False,make_latest="true")))
    gh("api",f"repos/{repo}/releases/{release_id}","--method","PATCH","--input",publish_request)
    release=json.loads(gh("api",f"repos/{repo}/releases/{release_id}").stdout)
    latest=json.loads(gh("api",f"repos/{repo}/releases/latest").stdout)
    if release["draft"] or release["prerelease"] or latest["id"]!=release["id"]:raise SystemExit("Final release state verification failed")
    tag_ref=json.loads(gh("api",f"repos/{repo}/git/ref/tags/{tag}").stdout)
    target=tag_ref["object"]
    if target["type"]=="tag":target=json.loads(gh("api",f"repos/{repo}/git/tags/{target['sha']}").stdout)["object"]
    if target["type"]!="commit" or target["sha"]!=sha:raise SystemExit("Published tag does not match the reviewed source")
    receipt={"status":"verified","sourceSha":sha,"tag":tag,"url":release["html_url"],"draft":False,"prerelease":False,"latest":True,
        "assets":[{"name":a["name"],"size":a["size"],"digest":a["digest"],"url":a["browser_download_url"]} for a in release["assets"]],
        "officialUpgrade":"passed-on-disposable-emulator","physicalPhone":"not_run"}
    (directory/"publication.json").write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps(receipt,ensure_ascii=False))

if __name__=="__main__":main()
