#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
if [[ -f .cloud/env.sh ]]; then source .cloud/env.sh; fi
python3 -m json.tool .devcontainer/devcontainer.json >/dev/null
git diff --check
export SHICANG_BUILD_PYTHON="$(command -v python3.12)"
gradle_args=(--no-daemon --max-workers=1)
# GitHub Actions preserves Gradle's task cache between runs; keep local builds unchanged.
if [[ "${CI:-}" == "true" ]]; then gradle_args+=(--build-cache); fi

# Local execution keeps the original direct Gradle path and exit behavior.
if [[ -z "${GITHUB_STEP_SUMMARY:-}" ]]; then
  exec bash ./gradlew "${gradle_args[@]}" -I tools/verification.init.gradle testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
fi

# Show a compact result in the Actions run without additional builds or API polling.
gradle_log="$(mktemp)"
trap 'rm -f "$gradle_log"' EXIT
gradle_started="$SECONDS"
gradle_status=0
bash ./gradlew "${gradle_args[@]}" -I tools/verification.init.gradle testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug 2>&1 | tee "$gradle_log" || gradle_status=$?
task_summary="$(grep -E '^[[:space:]]*[0-9]+ actionable tasks?:' "$gradle_log" | tail -n 1 || true)"
{
  printf '### Cloud Gradle checks\n\n'
  printf '| Metric | Result |\n|---|---|\n'
  printf '| Exit status | `%s` |\n' "$gradle_status"
  printf '| Elapsed | `%s s` |\n' "$((SECONDS - gradle_started))"
  printf '| Build cache | `%s` |\n' "$([[ "${CI:-}" == "true" ]] && printf enabled || printf disabled)"
  if [[ -n "$task_summary" ]]; then
    printf '| Gradle tasks | `%s` |\n' "$task_summary"
  else
    printf '| Gradle tasks | `summary unavailable` |\n'
  fi
} >> "$GITHUB_STEP_SUMMARY"
exit "$gradle_status"
