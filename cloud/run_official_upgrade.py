#!/usr/bin/env python3
"""Exercise a real 0.16 -> candidate update exclusively on the workflow's disposable emulator."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import sys

def run(*args,stdin=None):
    result=subprocess.run(list(map(str,args)),stdin=stdin,capture_output=True,text=stdin is None,timeout=180)
    if result.returncode:raise RuntimeError(f"{args[0]} failed: "+(result.stderr if isinstance(result.stderr,str) else "binary transfer failed"))
    return result.stdout

def phase(name):
    output=run("adb","shell","am","instrument","-w","-e","class","dev.h516.shicang.OfficialUpgradeUiTest","-e","disposable","true","-e","phase",name,
        "dev.h516.shicang.verification.test/androidx.test.runner.AndroidJUnitRunner")
    print(output,flush=True)
    if not re.search(r"OK \(1 test\)",output):raise RuntimeError(f"Official upgrade UI phase failed: {name}")

def pull_backup(name,output):
    listing=run("adb","shell","find","/sdcard/","-name",name)
    paths=[line.strip() for line in listing.splitlines() if line.strip().endswith("/"+name)]
    if len(paths)!=1:raise RuntimeError("Expected exactly one real UI-exported backup")
    run("adb","pull",paths[0],output)

def main():
    p=argparse.ArgumentParser()
    for name in ("previous","candidate","verification","tests","output"):p.add_argument("--"+name,type=Path,required=True)
    p.add_argument("--source-sha",required=True);args=p.parse_args()
    args.output.mkdir(parents=True,exist_ok=True)
    hardware=run("adb","shell","getprop","ro.hardware").strip()
    if hardware not in {"ranchu","goldfish"}:raise SystemExit("Refusing to run official upgrade on a physical device")
    run("adb","install",args.verification);run("adb","install",args.tests);run("adb","install",args.previous)
    phase("seed");pull_backup("upgrade-before.zip",args.output/"before.zip")
    run("adb","shell","run-as","dev.h516.shicang.verification","mkdir","-p","cache/camera")
    with args.candidate.open("rb") as source:
        run("adb","shell","-T","run-as dev.h516.shicang.verification sh -c 'cat > cache/camera/upgrade.apk'",stdin=source)
    phase("install");phase("verify");pull_backup("upgrade-after.zip",args.output/"after.zip")
    run(sys.executable,Path(__file__).with_name("verify_upgrade.py"),"--before",args.output/"before.zip","--after",args.output/"after.zip",
        "--source-sha",args.source_sha,"--candidate",args.candidate,"--receipt",args.output/"official-upgrade.json")
    print((args.output/"official-upgrade.json").read_text())

if __name__=="__main__":main()
