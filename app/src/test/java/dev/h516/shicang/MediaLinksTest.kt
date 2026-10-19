package dev.h516.shicang

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MediaLinksTest {
    @Test fun preparedRandomPagesHaveNoImmediateRepeatsAndCoverEveryArchive() {
        val ids=listOf("a","b","c","d")
        val queue=ArchiveQueue(ids,"c",Random(2))
        assertEquals("c",queue.pages.first())
        assertEquals(ids.toSet(),queue.pages.take(4).toSet())
        val before=queue.pages.toList()
        assertEquals(before,queue.pages) // Observing a canceled drag consumes nothing.
        repeat(10){queue.extend()}
        assertTrue(queue.pages.zipWithNext().all{it.first!=it.second})
        assertEquals(ids.toSet(),queue.pages.takeLast(4).toSet())
    }
    @Test fun directMediaUsesUrlPathRatherThanQueryOrCase() {
        assertEquals("image",MediaLinks.direct("https://example.com/A.JPG?token=x")?.kind)
        assertEquals("video",MediaLinks.direct("https://example.com/a.mp4?x=.jpg")?.kind)
        assertNull(MediaLinks.direct("https://example.com/explore/abc"))
        assertThrows(IllegalArgumentException::class.java){MediaLinks.validUrl("http://example.com/a.mp4")}
        assertThrows(IllegalArgumentException::class.java){MediaLinks.read("""{"media":[{"url":"file:///secret","kind":"image"}]}""")}
    }
    @Test fun extractionOnlyKeepsDownloadHeadersAndRealAlbumItems() {
        val plan=MediaLinks.read("""{"title":"图集","source":"https://example.com/post","media":[{"url":"https://cdn.example.com/1.jpg","kind":"image","ext":"jpg","headers":{"Referer":"https://example.com/post","Cookie":"private","Authorization":"private"}}]}""")
        assertEquals("图集",plan.title)
        assertEquals(mapOf("Referer" to "https://example.com/post"),plan.items.single().headers)
    }
    @Test fun mediaSizeLimitIsCheckedBeforeEnqueue() {
        assertThrows(IllegalArgumentException::class.java){MediaLinks.read("""{"media":[{"url":"https://example.com/large.jpg","kind":"image","bytes":1073741824}]}""")}
    }
}
