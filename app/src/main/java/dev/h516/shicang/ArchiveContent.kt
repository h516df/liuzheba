package dev.h516.shicang

/** Editing text and media share a generation and one explicit commit. Existing note IDs stay stable. */
data class ArchiveTextDraft(
    val note: LongNote,
    val expected: LongNote?,
    val original: Archive?,
    val generation: String
)

internal fun Library.archiveNote(id: String): LongNote? = notes.firstOrNull { it.type == "archive" && it.target == id }

internal fun Library.archiveTextDraft(a: Archive, generation: String): ArchiveTextDraft {
    val saved = archiveNote(a.id)
    val draft = noteDrafts.firstOrNull { it.type == "archive" && it.target == a.id }
    return ArchiveTextDraft(draft ?: saved ?: LongNote("archive", a.id), saved,
        archives.firstOrNull { it.id == a.id }, generation)
}

internal fun Library.archiveSummary(a: Archive): String = archiveNote(a.id)?.body
    ?.lineSequence()?.map(String::trim)?.filter(String::isNotEmpty)?.joinToString(" ")
    ?.takeIf(String::isNotBlank) ?: archiveCaption(a, this)

internal fun Archive.mediaCountLabel(): String {
    val videos = photos.count { it.isVideo }
    val images = photos.size - videos
    return when {
        photos.isEmpty() -> "文字档案"
        videos == 0 -> "$images 张图片"
        images == 0 -> "$videos 个视频"
        else -> "$images 张图片 · $videos 个视频"
    }
}

internal enum class MediaFilter(val label: String) { ALL("全部"), IMAGES("图片"), VIDEOS("视频") }
internal fun Archive.media(filter: MediaFilter): List<Photo> = photos.filter {
    filter == MediaFilter.ALL || (filter == MediaFilter.VIDEOS) == it.isVideo
}
