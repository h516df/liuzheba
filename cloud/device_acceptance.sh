#!/usr/bin/env bash
set -euo pipefail
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell wm size 720x1280
adb shell wm density 240
# PR/main run native acceptance; optional manual runs exercise only the upgrade harness.
if [ -z "${REFERENCE_SHA:-}" ]; then
  status=0
  ./gradlew --no-daemon --max-workers=1 -I tools/verification.init.gradle connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.h516.shicang.DeviceAcceptanceTest || status=$?
  adb shell run-as dev.h516.shicang.verification cat files/device-acceptance-media3.txt || true
  exit "$status"
fi
if [ -n "${REFERENCE_SHA:-}" ]; then
  # The candidate is from a prior reviewed main SHA. This validates only the current harness.
  python3 cloud/run_official_upgrade.py \
    --previous "$RUNNER_TEMP/reference/shicang-v0.16-universal.apk" \
    --candidate "$RUNNER_TEMP/reference/official.apk" \
    --verification app/build/outputs/apk/debug/app-debug.apk \
    --tests app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
    --source-sha "$REFERENCE_SHA" --output "$RUNNER_TEMP/reference-upgrade"
  python3 -c 'import json,os,pathlib; pathlib.Path(os.environ["RUNNER_TEMP"],"reference-upgrade","harness.json").write_text(json.dumps(dict(harnessSourceSha=os.environ["GITHUB_SHA"],candidateSourceSha=os.environ["REFERENCE_SHA"],purpose="harness validation only; final release must repeat acceptance on exact new main source")))'
fi
