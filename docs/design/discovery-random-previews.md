# Discovery: a different first glimpse

Discovery remains a full-screen vertical archive feed. Each archive visit starts at a shuffled photo or video from that archive, rather than always using its cover. Horizontal browsing, archive details and the existing viewer remain unchanged.

## Interaction

- Random previews are on by default; More offers a persistent fixed-cover alternative and “换一轮”.
- One session seed determines archive order and a separate deterministic media deck for each archive. A deck exhausts before repeating. A single archive can also advance to its next media visit.
- Recomposition does not shuffle. Returning from details preserves the seed, feed position and recent per-visit media IDs. Only small IDs, not media objects, are saved. A new round resets the session.
- The media pool excludes deleted archives and missing local files. The global app lock remains the outer access gate. There is no new access path or background service. An empty pool uses the existing archive name-card fallback.
- Video previews keep the existing tap-to-play, initially muted, active-page/lifecycle player behavior. This feature does not enable autoplay or allocate players for random selection.
- No library format, cover, author, backup or persistent browsing-history changes. The preference is device-local.

## Research and implementation choices

Ente's search/discovery experience illustrates the value of resurfacing existing collections rather than presenting only a static library: https://ente.com/help/photos/features/search-and-discovery/ . This is inspiration, not a claim that Ente implements this exact shuffle behavior. Its code is AGPL; no code or assets were copied: https://github.com/ente/ente/blob/main/LICENSE .

Android recommends saving the smallest UI state, such as IDs and scroll position, and reconstructing complex data: https://developer.android.com/develop/ui/compose/state-saving . The implementation uses Compose saveable state and the existing viewer/thumbnail stack; it adds no dependency.

## Verification boundary

Targeted tests cover shuffled pools, stable selection, same-archive bounds, duplicate/filtered IDs, cover fallback, empty/deleted archives, real Compose return navigation, a single-archive next visit, missing files and inactive/manual video previews. Native Robolectric screenshots contain generated fixtures only. They do not replace Android device acceptance for actual codecs, low-memory/process recreation, app lock unlock, external file deletion while already visible, or cross-feature integration with long notes/video editing.

For integration, preserve the long-note editor's active-page gate at both ArchiveFeed and FullPhotoFrame call sites. The only root Ui change here is a saveable-state scope around discovery. FullPhotoFrame adds optional discovery actions and re-emits position when its active state changes, so a preloaded neighbor is recorded only when settled and active.

## Cloud checks (2026-10-20)

- `testDebugUnitTest --tests 'dev.h516.shicang.Discovery*Test'`: 11 passed, zero skipped/failures (7 pure tests and 4 real Compose tests).
- `assembleDebug`: passed with a 1 GiB Gradle heap. The first 768 MiB packaging attempt failed; no source changes were required.
- Native test fixtures wait for AppModel readiness and initialize bitmap buffer JNI on the test thread before concurrent image loading. Early attempts without these fixture safeguards failed and are not counted as acceptance.
- Inspected the native-rendered 320 dp synthetic discovery screenshot: current media, fixed-cover mode and next-round controls fit. No user media or screenshot is committed/uploaded.
- Still required in the integrated batch: actual-phone gestures/return/lock, real video codecs, process-death/low-memory behavior, and long-note/video-edit overlay pause gates. This PR neither releases nor changes the app version.
- `lintDebug`: passed independently with a 768 MiB Gradle heap.

### CI return-test diagnosis

The first push CI run reported 183/184 passing; its return/cover test timed out, while the sibling PR run passed. The fixture used PAUSED Looper mode but did not drain the main queue when awaiting asynchronous media callbacks. A longer sleep is not the remedy: the wait predicates now explicitly idle the main queue, matching the existing AppModel readiness/cleanup waits.

A controlled re-run of the **unchanged production source** passed with a forced non-cover initial selection. Temporary production changes considered during diagnosis (mode-generation keys/loading placeholders) were withdrawn. The final regression fixture clears the old callback before returning, waits for a fresh callback, forces the cover to be neither the initial media nor item zero, and checks the actual displayed page number as well as the callback. The final 11-test group passes with these stronger assertions.
