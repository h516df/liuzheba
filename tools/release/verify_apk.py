#!/usr/bin/env python3
"""Check official APK identity before an upgrade. Does not install or publish."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys


def run(command):
    result = subprocess.run(command, capture_output=True, text=True, timeout=120)
    if result.returncode:
        raise ValueError(f"{Path(command[0]).name} verification failed")
    return result.stdout


def inspect(apk, sdk, expected):
    if not apk.is_file():
        raise ValueError("APK file is missing")
    tools = sdk / "build-tools" / "36.0.0"
    signature = run([str(tools / "apksigner"), "verify", "--print-certs", str(apk)])
    signers = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", signature)
    if len(signers) != 1 or signers[0].lower() != expected:
        raise ValueError("APK is not signed by the original production certificate")
    badging = run([str(tools / "aapt"), "dump", "badging", str(apk)])
    identity = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not identity or identity[1] != "dev.h516.shicang":
        raise ValueError("APK package does not match the installed official application")
    if "application-debuggable" in badging:
        raise ValueError("A debug APK cannot be an official update")
    size = apk.stat().st_size
    if not 0 < size <= 120 * 1024 * 1024:
        raise ValueError("APK exceeds the updater's size limit")
    digest = hashlib.sha256()
    with apk.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return dict(package=identity[1], code=int(identity[2]), version=identity[3],
                certificate_sha256=signers[0].lower(), bytes=size, sha256=digest.hexdigest())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate", type=Path, required=True)
    parser.add_argument("--previous", type=Path, required=True)
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, default=Path(os.environ.get("ANDROID_HOME", "")))
    args = parser.parse_args()
    receipt = dict(status="blocked", installation_test="not_run", data_preservation_test="not_run")
    try:
        root = Path(__file__).resolve().parents[2]
        config = (root / "app/src/main/java/dev/h516/update/UpdateConfig.kt").read_text()
        match = re.search(r'CERTIFICATE_SHA256 = "([0-9a-f]{64})"', config)
        if not match:
            raise ValueError("Production certificate pin is missing")
        candidate = inspect(args.candidate, args.sdk, match[1])
        previous = inspect(args.previous, args.sdk, match[1])
        if candidate["code"] <= previous["code"]:
            raise ValueError("Upgrade versionCode must increase")
        notes = json.loads((root / "app/src/main/assets/release-notes.json").read_text())
        if (candidate["version"], candidate["code"]) != (notes["versionName"], notes["versionCode"]):
            raise ValueError("Candidate does not match this source's release notes")
        receipt.update(status="metadata_passed", candidate=candidate, previous=previous)
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        receipt["reason"] = str(error)
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps(receipt))
    return 0 if receipt["status"] == "metadata_passed" else 1


if __name__ == "__main__":
    sys.exit(main())
