# Isolated Android device acceptance

`Android device checks` is a separate, single-device workflow. It runs only
`DeviceAcceptanceTest` with AndroidJUnitRunner on a disposable GitHub-hosted
Ubuntu 24.04 runner and one API 35 Google APIs x86_64 emulator. It does not replace
the existing host unit tests, APK assembly, lint, or target-phone acceptance.

## Scope

- Import the repository's synthetic 60-second H.264 fixture through a real
  `content://` FileProvider, streaming it into the library and verifying its hash.
- Start actual ExoPlayer playback through the app UI, wait for position advancement,
  use forward/backward seek controls, and verify paused position across Home/return
  and collapse/reopen.
- Run actual Media3 trim, crop, and fixed-mask export. Decode a resulting frame,
  verify dimensions, duration, and the mask, and append an independent copy while
  preserving the source hash.
- Cancel a running Media3 export and verify temporary output cleanup and unchanged
  source/library.
- Use the native keyboard while editing an archive, cancel changes, exercise failed
  validation, and save while retaining other fields.
- Open actual system SAF DocumentsUI from the backup flow, cancel without changing
  state, then save and read the resulting ZIP through its returned document URI.
  Verify both the manifest and original media hash.

## Reproduce

On a disposable Android test environment, with an API 35 emulator running:

```sh
adb shell settings put secure show_ime_with_hard_keyboard 1
./gradlew --no-daemon --max-workers=1 \
  -I tools/verification.init.gradle connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.h516.shicang.DeviceAcceptanceTest
```

The application ID must be exactly `dev.h516.shicang.verification`. Every test
checks this before changing preferences or creating fixture data. The production
package, signing configuration, and production update route are unchanged.

## Security and evidence

The workflow uses `pull_request`, not `pull_request_target`, plus manual dispatch.
Its token has only `contents: read`, checkout does not persist credentials, all
actions are pinned to full commit SHAs, and it requires no signing secrets, user
computer, long-lived service, or paid device-testing service. KVM access changes
apply only to the disposable GitHub runner.

The emulator runner is pinned to official v2.37.0:
https://github.com/ReactiveCircus/android-emulator-runner/commit/e89f39f1abbbd05b1113a29cf4db69e7540cae5a

Only JUnit XML text results are retained for seven days. This test class neither
takes screenshots nor reads user media. No screenshots, APKs, media fixtures,
database files, or backups are uploaded. The instrumentation selection excludes
existing network and screenshot-producing tests.

## Explicit limits

These small synthetic fixtures prove device execution paths, not performance at
the 6 GB limit. The 6 GB size/space/streaming boundary checks remain logic tests;
sparse files or metadata-only fixtures are not codec playback evidence. Neither
repository video has an audio track, so audio preservation is not verified here.
This is one emulator/API, not an OEM/physical-device, upgrade, full SAF-provider,
low-storage, interruption, HDR/HEVC, or large-file compatibility matrix.

An assembled test APK is not a passed device run. Record the exact commit and
completed workflow result before describing any of these checks as passing.
