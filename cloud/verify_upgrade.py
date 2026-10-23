#!/usr/bin/env python3
"""Compare real UI-exported backups across an official APK update. No private app access."""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def read_backup(path):
    with zipfile.ZipFile(path) as archive:
        names=archive.namelist()
        if len(names)!=len(set(names)) or "manifest.json" not in names:
            raise ValueError("Duplicate or missing backup entry")
        manifest=json.loads(archive.read("manifest.json"))
        originals={}
        for name in names:
            if name=="manifest.json":continue
            if not name.startswith("photos/") or ".." in name.split("/"):
                raise ValueError("Unexpected backup path")
            digest=hashlib.sha256()
            with archive.open(name) as stream:
                for data in iter(lambda:stream.read(1024*1024),b""):digest.update(data)
            originals[name]=digest.hexdigest()
    return manifest,originals


def verify(before,after,source_sha,candidate_sha256):
    left,left_files=read_backup(before);right,right_files=read_backup(after)
    fields=("format","archives","drafts","labels","photoTrash","templates","searchPreferences","authors","notes","noteDrafts")
    if any(left.get(key)!=right.get(key) for key in fields):
        raise ValueError("Archive data changed during the upgrade")
    if left_files!=right_files:raise ValueError("Original media bytes changed during the upgrade")
    archives=left.get("archives",[])
    if len(archives)!=1 or archives[0]["name"]!="升级验收：文字与混合媒体":
        raise ValueError("The expected UI-created archive is missing")
    media=archives[0]["photos"]
    if len(media)!=3 or sum(item["mime"].startswith("video/") for item in media)!=2:
        raise ValueError("Expected one image and two videos")
    notes=left.get("notes",[])
    if len(notes)!=1 or "从正式 0.16 保留的合成文字" not in notes[0]["body"]:
        raise ValueError("The expected original note is missing")
    for item in media:
        if left_files.get("photos/"+item["file"])!=item["sha256"]:
            raise ValueError("Original media does not match its stored digest")
    return {"status":"passed","sourceSha":source_sha,"candidateSha256":candidate_sha256,
        "environment":"disposable API35 x86_64 emulator","physicalPhone":"not_run",
        "route":"official 0.16 UI seed -> system installer confirmation -> new UI export",
        "archives":1,"images":1,"videos":2,"notes":1,"dataAndOrderUnchanged":True,"originalHashesUnchanged":True}


def main():
    parser=argparse.ArgumentParser()
    for name in ("before","after","receipt"):parser.add_argument("--"+name,type=Path,required=True)
    parser.add_argument("--source-sha",required=True);parser.add_argument("--candidate",type=Path,required=True)
    args=parser.parse_args()
    receipt={"status":"failed","sourceSha":args.source_sha}
    try:
        receipt=verify(args.before,args.after,args.source_sha,hashlib.sha256(args.candidate.read_bytes()).hexdigest())
    except (ValueError,KeyError,OSError,zipfile.BadZipFile) as error:
        receipt["reason"]=str(error)
    args.receipt.parent.mkdir(parents=True,exist_ok=True)
    args.receipt.write_text(json.dumps(receipt,ensure_ascii=False,indent=2)+"\n")
    print(json.dumps(receipt,ensure_ascii=False))
    return 0 if receipt["status"]=="passed" else 1


if __name__=="__main__":raise SystemExit(main())
