package dev.h516.shicang

import kotlin.random.Random

/** A feed entry is a saved work, not a category/archive. It never owns or copies the originals. */
internal data class DiscoveryWork(val archive: Archive, val media: List<Photo>, val expectedCount: Int = media.size) {
    val key: String get() = "${archive.id}:${media.first().id}"
    val isVideo: Boolean get() = media.first().isVideo
}

/**
 * An imported image post remains a swipeable gallery. Other legacy archive media are
 * individual feed entries: a folder of unrelated clips must not become one feed page.
 * The source archive and its original media order remain untouched.
 */
internal fun discoveryWorks(archives: List<Archive>): List<DiscoveryWork> = archives
    .asSequence().filter { it.deleted == 0L }.flatMap { archive ->
        val media = archive.photos.distinctBy { it.id }
        val importedImagePost = media.isNotEmpty() && media.all { !it.isVideo } &&
            archive.fields.any { field ->
                field.name == "来源链接" && field.type == "链接" &&
                    (SharedLinks.platform(Links.normalize(field.value).orEmpty()) != null ||
                        Links.title(field.value) == "Instagram")
            }
        (if (importedImagePost) listOf(media) else media.map(::listOf))
            .map { DiscoveryWork(archive, it) }.asSequence()
    }.toList()

internal fun filterDiscoveryWorks(works: List<DiscoveryWork>, mediaType: String, category: String): List<DiscoveryWork> =
    works.filter { work ->
        (category.isEmpty() || work.archive.category == category) && when (mediaType) {
            "视频" -> work.isVideo
            "图片" -> !work.isVideo
            else -> true
        }
    }

/** Stable shuffled rounds. Prefer a different archive/author next when alternatives exist. */
internal fun discoveryWorkOrder(works: List<DiscoveryWork>, seed: Long): List<DiscoveryWork> {
    val deck = works.distinctBy { it.key }.sortedBy { it.key }.shuffled(Random(seed)).toMutableList()
    for (index in 1 until deck.size) {
        val previous = deck[index - 1]
        fun related(other: DiscoveryWork): Boolean =
            other.archive.id == previous.archive.id ||
                other.archive.authorIds.any { it in previous.archive.authorIds }
        if (!related(deck[index])) continue
        val alternative = (index + 1 until deck.size).firstOrNull { !related(deck[it]) } ?: continue
        val swap = deck[index]
        deck[index] = deck[alternative]
        deck[alternative] = swap
    }
    return deck
}
