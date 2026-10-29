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
bash ./gradlew "${gradle_args[@]}" -I tools/verification.init.gradle testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
