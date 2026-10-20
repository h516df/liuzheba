# Tag-first, direct-action collection workflow

Research date: 2026-10-20. Starting point: merged `079b904` (0.16/code 17).
This change does not change a published asset, release identifier, media format, or backup format.

## Observed friction, not a feature wishlist

- `LabelPicker` always began on categories, even when the user's workflow is tags. A normal tag edit required opening the sheet, switching tab, selecting, then confirming. The library filter had the same overhead.
- `EditorV19`'s add-media button opened a second dialog whose main purpose was asking the user to choose the photo library. Detail-page add also opened the whole editor and required a later save.
- `AllPhotos` required tapping multi-select before selecting the first item, unlike familiar gallery behavior. System Back left the album rather than first ending selection.
- Routine automatic-protection progress inserted a notice into the library header. The backup panel prominently offered a manual re-verification on every visit, making an automatic process look like regular manual work.
- Detail pages suppress the global top bar, including its busy indicator. A direct import needed an in-place progress/cancel surface rather than appearing unresponsive.

## Implemented interaction choices

1. Tags first everywhere: editor, filter sheet, and label management. Existing categories remain optional and unchanged. Frequent tags are directly tappable in the editor and library. Clearing the tag selection does not erase the category. New labels still use the existing search-and-create field.
2. Direct media actions: photo-library and camera entry points are separate, with no intermediate choice dialog. System-picker detail import stays in detail, displays progress, and appends media without committing other unfinished draft fields. The store implementation separately protects the formal archive and existing draft. Shared-intent/download collection still uses its existing two-stage durable-draft flow and incomplete-import warning; this is not claimed as a rewrite of every import source.
3. Long-press media starts selection with that item already selected. Ordinary tap still opens the viewer. System Back first clears selection and stays in the album. Text buttons remain for users who do not discover long press; actions wrap at large text sizes.
4. Automatic protection is quiet during ordinary work: configured pending/success states do not insert a library banner. The backup screen still displays the real state; manual verification and location changes are in details. Errors and incomplete-import warnings remain visible, and new-directory permission, restoration, destructive operations, and privacy notices are not bypassed.

No additional settings, framework, cloud sync, face recognition, or schema migration are introduced.

## Acceptance criteria

- With an existing tag visible in the editor, changing its selection takes one tap instead of opening the sheet, switching from category to tag, selecting, and confirming. Saving the archive remains explicit; autosaved drafts are not silently promoted to formal archives.
- With an existing tag visible on the library shelf, filtering takes one tap instead of the same four-step sheet path. Tapping it again clears that tag filter. Multiple selected tags retain the existing AND semantics.
- Editor → photo library takes one tap instead of a choice dialog followed by another tap. Detail → photo library no longer opens the whole editor or requires a later archive save. Camera capture retains its own explicit action.
- A media long press enters selection with that item selected. System Back leaves selection before leaving the album. Share/export remain explicit; no source file is deleted.
- A configured, healthy automatic-protection process adds no recurring library notice or manual-confirmation step. Setup, failure, incomplete-import warnings, manual retry, and restore confirmations remain available and testable.

## Primary-source references and boundaries

- [Immich tags](https://docs.immich.app/features/tags/): the tag prompt combines finding/selecting an existing tag and creating a new one. Tag-first defaults here follow the user's preference; they are not claimed to be Immich's default.
- [Immich v3 release](https://immich.app/blog/v3.0.0-release): direct album upload removes the upload-then-organize split. Our local analogue is adding media directly from its destination archive.
- [Ente album/organization FAQ](https://ente.com/help/photos/faq/albums-and-organization): mobile selection begins by long-pressing the first item.
- [Fossify Gallery MediaAdapter](https://github.com/FossifyOrg/Gallery/blob/main/app/src/main/kotlin/org/fossify/gallery/adapters/MediaAdapter.kt): long-press preparation and contextual selection actions support the same familiar native pattern.
- [Immich repository](https://github.com/immich-app/immich): automatic backup and separate album organization are established gallery responsibilities, not repeated user prompts.

Immich and Ente are AGPL-3.0; Fossify Gallery is GPL-3.0. This implementation independently uses the project's existing Compose APIs. No source code or assets from those projects are copied into this Apache-2.0 repository.

## Verification boundaries

The host test suite uses real native Compose rendering through Robolectric/Roborazzi with synthetic media. It is not a physical-phone benchmark, system picker, codec, SAF-provider, or signed-upgrade acceptance result. No ADB device is connected and no emulator/KVM is available in this executor. Generated screenshots stay outside tracked source under build outputs. Final test totals and performance evidence are recorded in the task handoff, not inferred from appearance.

Automatic protection continues in the running app and resumes when reopened if Android interrupts it. It is not a promise of unrestricted background execution, recovery after loss of both local copies, or protection for files that never finished importing.

Explicit media imports still use the existing busy-operation model and hold the store gate while copying/linking. This batch removes the long gate hold from automatic external protection, not from every file operation. It does not claim unrestricted editing during a 6 GB import or faster physical-provider throughput; source/provider speed, codec parsing, UI frame timing, and signed upgrades remain phone acceptance work.

### Host validation record

- The integrated full suite passed all 277 tests across 55 suites, with no failures, errors, or skips. After the final success-state wording refinement, all 7 protection-state/component tests passed again.
- `lintDebug` passed with no errors. It still reports 75 warnings and one hint; this is not a zero-warning project. The regular debug application and instrumentation APKs compiled successfully.
- The final wording was also built with `-I tools/verification.init.gradle`; the APK package is `dev.h516.shicang.verification`, and its test APK targets that isolated package. Version 0.16/code 17 and release configuration remain unchanged. This is a debug verification build, not a published update.
- The new native Compose journey tests cover tag defaults, inline selection, category preservation, first-tag creation, one-tap filtering and navigation return, direct picker callbacks, long-press selection, System Back, and 200% text.
- `app/build/outputs/unified-navigation/` contains full app journey renders, including `tag-first-picker.png`, `tag-one-tap-filter.png`, `direct-media-actions.png`, `direct-media-font200.png`, and `selection-font200.png`.
- `app/build/outputs/protection-ui/` contains **isolated ProtectionPanel component fixtures**, not a full backup navigation page. The success, failure, import warning, and narrow/200% dark renders distinguish routine state from required attention.
- The full XML results were preserved before the final targeted rerun under the ignored local `evidence/core-journey-validation/full-tests/` directory. Build screenshots and APKs are not committed to the source repository.
