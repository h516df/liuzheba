#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
if [[ -f .cloud/env.sh ]]; then source .cloud/env.sh; fi
python3 -m json.tool .devcontainer/devcontainer.json >/dev/null
git diff --check
export SHICANG_BUILD_PYTHON="$(command -v python3.12)"
gradle_workers=1
# Only GitHub-hosted CI has two workers; local checks retain their current limit.
if [[ "${GITHUB_ACTIONS:-}" == "true" ]]; then gradle_workers=2; fi
gradle_args=(--no-daemon "--max-workers=${gradle_workers}")
# GitHub Actions preserves Gradle's task cache between runs; keep local builds unchanged.
if [[ "${CI:-}" == "true" ]]; then gradle_args+=(--build-cache); fi

# The default remains the original full local check. Split modes are used only
# by the Cloud workflow, whose final check requires BOTH build and test shards.
mode="${1:-full}"
if (( $# > 1 )); then echo "usage: cloud/check.sh [full|package|tests]" >&2; exit 2; fi
case "$mode" in
  full) gradle_tasks=(testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug) ;;
  package) gradle_tasks=(assembleDebug assembleDebugAndroidTest lintDebug) ;;
  tests)
    gradle_tasks=(testDebugUnitTest)
    if [[ "${GITHUB_ACTIONS:-}" == "true" ]]; then
      case "${SHICANG_CI_TEST_SHARD:-}" in
        ui|other) gradle_args+=("-PciTestShard=${SHICANG_CI_TEST_SHARD}") ;;
        *) echo "CI tests mode requires ui or other shard" >&2; exit 2 ;;
      esac
    fi
    ;;
  *) echo "unknown check mode: $mode" >&2; exit 2 ;;
esac

# Local execution keeps the original direct Gradle path and exit behavior.
if [[ -z "${GITHUB_STEP_SUMMARY:-}" ]]; then
  exec bash ./gradlew "${gradle_args[@]}" -I tools/verification.init.gradle "${gradle_tasks[@]}"
fi

# Show a compact result in the Actions run without additional builds or API polling.
gradle_log="$(mktemp)"
trap 'rm -f "$gradle_log"' EXIT
gradle_started="$SECONDS"
gradle_status=0
bash ./gradlew "${gradle_args[@]}" -I tools/verification.init.gradle "${gradle_tasks[@]}" 2>&1 | tee "$gradle_log" || gradle_status=$?
task_summary="$(grep -E '^[[:space:]]*[0-9]+ actionable tasks?:' "$gradle_log" | tail -n 1 || true)"
if ! {
  printf '### Cloud Gradle checks\n\n'
  printf '| Metric | Result |\n|---|---|\n'
  printf '| Mode | `%s` |\n' "$mode"
  printf '| Exit status | `%s` |\n' "$gradle_status"
  printf '| Elapsed | `%s s` |\n' "$((SECONDS - gradle_started))"
  printf '| Build cache | `%s` |\n' "$([[ "${CI:-}" == "true" ]] && printf enabled || printf disabled)"
  if [[ -n "$task_summary" ]]; then
    printf '| Gradle tasks | `%s` |\n' "$task_summary"
  else
    printf '| Gradle tasks | `summary unavailable` |\n'
  fi
} >> "$GITHUB_STEP_SUMMARY"; then
  printf 'warning: could not write GitHub Actions summary\n' >&2
fi
exit "$gradle_status"
