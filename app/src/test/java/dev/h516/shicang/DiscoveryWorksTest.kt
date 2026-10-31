package dev.h516.shicang

import org.junit.Assert.*
import org.junit.Test

class DiscoveryWorksTest {
    private fun media(id: String, video: Boolean = false) =
        Photo(id, "$id.original", 1, "a".repeat(64), if (video) "video/mp4" else "image/png")

    @Test fun unrelatedMediaWithinOneArchiveAreIndependentFeedEntries() {
        val archive = Archive(id = "a", photos = listOf(media("one", true), media("two", true), media("three")))
        assertEquals(listOf("a:one", "a:two", "a:three"), discoveryWorks(listOf(archive)).map { it.key })
    }

    @Test fun oneImportedImagePostStaysTogetherAndPreservesPhotoOrder() {
        val source = Field(name = "来源链接", type = "链接", value = "https://www.xiaohongshu.com/explore/example")
        val archive = Archive(id = "b", fields = listOf(source), photos = listOf(media("first"), media("second")))
        val works = discoveryWorks(listOf(archive))
        assertEquals(1, works.size)
        assertEquals(listOf("first", "second"), works.single().media.map { it.id })
        assertEquals(2, archive.photos.size)
    }

    @Test fun aSocialShortLinkAlsoKeepsTheImagePostTogether() {
        val source = Field(name = "来源链接", type = "链接", value = "https://xhslink.cn/o/sample")
        val archive = Archive(id = "b", fields = listOf(source), photos = listOf(media("one"), media("two")))
        assertEquals(1, discoveryWorks(listOf(archive)).size)
    }

    @Test fun draftsAndDeletedArchivesNeverEnterTheFeedAndDuplicateMediaIsOneEntry() {
        val a = Archive(id = "a", photos = listOf(media("one"), media("one")))
        val deleted = Archive(id = "deleted", deleted = 42, photos = listOf(media("other")))
        assertEquals(listOf("a:one"), discoveryWorks(listOf(a, deleted, Archive())).map { it.key })
    }

    @Test fun shuffledRoundIsStableAndDoesNotRepeatOneMediaBeforeExhaustingPool() {
        val a = Archive(id = "a", authorIds = listOf("author"), photos = listOf(media("1"), media("2")))
        val b = Archive(id = "b", photos = listOf(media("3")))
        val works = discoveryWorks(listOf(a, b))
        val first = discoveryWorkOrder(works, 123).map { it.key }
        assertEquals(first, discoveryWorkOrder(works.reversed(), 123).map { it.key })
        assertEquals(3, first.toSet().size)
        assertEquals(setOf("a:1", "a:2", "b:3"), first.toSet())
        assertFalse(discoveryWorkOrder(works, 123)[0].media.isEmpty())
    }

    @Test fun filtersDoNotChangeOrReclassifyOriginalArchives() {
        val travel = Archive(id = "travel", category = "cat-1", photos = listOf(media("clip", true), media("cover")))
        val daily = Archive(id = "daily", category = "cat-2", photos = listOf(media("snap")))
        val works = discoveryWorks(listOf(travel, daily))
        assertEquals(3, filterDiscoveryWorks(works, "全部", "").size)
        assertEquals(listOf("travel:clip"), filterDiscoveryWorks(works, "视频", "cat-1").map { it.key })
        assertEquals(listOf("travel:cover"), filterDiscoveryWorks(works, "图片", "cat-1").map { it.key })
        assertTrue(filterDiscoveryWorks(works, "视频", "cat-2").isEmpty())
        assertEquals(2, travel.photos.size)
    }
}
