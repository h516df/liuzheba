#!/usr/bin/env bash
set -euo pipefail

# Diagnostics are limited to the disposable synthetic-test emulator. In
# particular, an Android launcher ANR should not be reported as an app bug or
# silently retried until it happens to pass. Keep the real test exit status.
device_diagnostics_on_failure() {
  local result=$?
  if (( result != 0 )) && command -v adb >/dev/null 2>&1; then
    echo '::group::Android emulator failure diagnostics (synthetic device)'
    echo 'Device state:'
    timeout 10s adb devices -l || true
    echo 'Android boot completed:'
    timeout 10s adb shell getprop sys.boot_completed || true
    echo 'Recent system ANR/crash events:'
    timeout 15s adb logcat -b events -d -t 800 -v brief 2>/dev/null \
      | grep -Ei 'am_anr|am_crash|am_proc_died|wm_activity_launch_time' \
      | tail -n 60 || true
    echo '::endgroup::'
  fi
}
trap device_diagnostics_on_failure EXIT

if [[ "$(timeout 15s adb shell getprop sys.boot_completed | tr -d '\r')" != "1" ]]; then
  echo '::error::Android emulator has not completed boot; UI tests were not started.'
  exit 1
fi
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
