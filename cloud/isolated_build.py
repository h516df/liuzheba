#!/usr/bin/env python3
"""Overlap the disposable CI build with cold emulator boot, preserving its gate."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import time


def build(state):
    code = 1
    try:
        code = subprocess.run([
            'bash', './gradlew', '--no-daemon', '--max-workers=1', '--build-cache',
            '-I', 'tools/verification.init.gradle', 'assembleDebug', 'assembleDebugAndroidTest',
        ], check=False).returncode
        if code == 0:
            code = subprocess.run([
                sys.executable, 'cloud/check_apk_identity.py',
                'app/build/outputs/apk/debug/app-debug.apk', 'dev.h516.shicang.verification',
            ], check=False).returncode
    except Exception:
        code = 1
        raise
    finally:
        receipt = state / 'result.tmp'
        receipt.write_text(json.dumps({'sha': os.environ['GITHUB_SHA'], 'code': code}), encoding='utf-8')
        receipt.replace(state / 'result.json')
    return code


def start(state):
    # Never consume an old receipt, or launch a second build in the same job.
    state.mkdir(parents=True, exist_ok=False)
    with (state / 'build.log').open('wb') as log:
        process = subprocess.Popen(
            [sys.executable, str(Path(__file__).resolve()), '_run'],
            stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT,
            start_new_session=True,
        )
    print(f'Isolated build started as {process.pid}; emulator may cold-boot concurrently.')
    return 0


def wait(state, timeout=300):
    deadline = time.monotonic() + timeout
    while not (state / 'result.json').is_file():
        if time.monotonic() >= deadline:
            raise RuntimeError('Isolated build did not return a receipt; no device tests may run')
        time.sleep(1)
    receipt = json.loads((state / 'result.json').read_text(encoding='utf-8'))
    if receipt['sha'] != os.environ['GITHUB_SHA']:
        raise RuntimeError('Isolated build receipt belongs to a different commit')
    print((state / 'build.log').read_text(encoding='utf-8', errors='replace'))
    return int(receipt['code'])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['start', 'wait', '_run'])
    args = parser.parse_args()
    state = Path(os.environ['RUNNER_TEMP']) / 'shicang-isolated-build'
    raise SystemExit({'start': start, 'wait': wait, '_run': build}[args.action](state))
