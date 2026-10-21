#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
if [[ -f .cloud/env.sh ]]; then source .cloud/env.sh; fi
python3 -m json.tool .devcontainer/devcontainer.json >/dev/null
git diff --check
export SHICANG_BUILD_PYTHON="$(command -v python3.12)"
bash ./gradlew --no-daemon --max-workers=1 -I tools/verification.init.gradle testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
