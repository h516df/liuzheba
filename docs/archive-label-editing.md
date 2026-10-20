# Archive editing and label management

Categories and tags remain separate, backwards-compatible fields: one optional category
for filing, multiple optional tags for description. No migration or relationship merging.

- Editor shows the distinction near the primary name/media controls, before optional data.
- Empty optional author/source/custom-field data starts folded; existing data starts open.
  Folding never deletes values. Templates still add fields without replacing values.
- Back retains the draft; the separate **放弃修改** action confirms discard. Draft status
  stays near the header rather than requiring scrolling to the end.
- Label picker checkbox/chip removal changes only this selection. Explicit management
  is reachable from the picker and from My → 分类与标签. Picker selection is retained
  in the draft before opening management.
- Global deletion shows affected archives (including recycle bin) and draft counts,
  warns relationships cannot be automatically restored, and explicitly preserves archives,
  photos and videos. The existing transactional Store deletion is reused. Pending editor
  changes are persisted before global deletion cancels its debounce, preventing draft loss.
- Author preview distinguishes **保存并关联此档案** for a new card with a target from
  **保存作者资料** for an existing card. The existing confirmation/association logic remains.

Reference patterns, not copied implementation:
- https://docs.immich.app/features/tags/
- https://github.com/photoprism/photoprism-docs/blob/develop/docs/user-guide/organize/labels.md

Verification is recorded after execution. Host UI renders do not prove Android IME or codec
behavior; the separate API35 device acceptance suite covers the explicitly listed device flows.

## Candidate checks (2026-10-20)

- 34 existing navigation/archive/author/backup host checks passed, plus four focused
  label/editor host checks: actual global-delete UI with archive/draft relationships,
  local selection removal, immediate deletion inside draft debounce, and 320dp/200%
  editor controls/folded-field preservation/discard.
- The initial large-font test incorrectly looked for a non-composed lazy-list row;
  it now scrolls the list to the field before asserting. No business workaround.
- Android instrumentation APK compiled; device execution is a separate CI gate.
- Synthetic host screenshots are local review artifacts only and are not committed.
