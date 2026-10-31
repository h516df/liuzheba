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
    @Test fun extractedPlatformAuthorIsOptionalAndBoundToItsOwnSource() {
        fun payload(source:String,profile:String)="""{"source":"$source","author":{"profile_url":"$profile","name":"同名创作者"},"media":[{"url":"https://cdn.example.com/a.jpg","kind":"image","ext":"jpg"}]}"""
        val xhs="https://www.xiaohongshu.com/user/profile/syntheticUser123"
        val verified=MediaLinks.read(payload("https://www.xiaohongshu.com/explore/0123456789abcdef01234567",xhs))
        assertEquals(xhs,verified.authorProfile)
        assertEquals("同名创作者",verified.authorName)
        assertEquals("",MediaLinks.read(payload("https://www.douyin.com/video/123456789",xhs)).authorProfile)
        assertEquals("",MediaLinks.read(payload("https://www.xiaohongshu.com/explore/0123456789abcdef01234567","https://www.xiaohongshu.com/explore/abcdef")).authorProfile)
        assertEquals("",MediaLinks.read("""{"media":[{"url":"https://cdn.example.com/a.jpg","kind":"image"}]}""").authorProfile)
    }
    @Test fun mediaSizeLimitIsCheckedBeforeEnqueue() {
        assertThrows(IllegalArgumentException::class.java){MediaLinks.read("""{"media":[{"url":"https://example.com/large.jpg","kind":"image","bytes":1073741824}]}""")}
    }
    @Test fun commonSocialShareTextsRetainThePostAndQueryTokens() {
        val social=listOf(
            "图片分享 https://xhslink.cn/o/AbCd123 复制后打开小红书" to "https://xhslink.cn/o/AbCd123",
            "复制口令 https://xhslink.com/a/Example2?xsec_token=synthetic" to "https://xhslink.com/a/Example2?xsec_token=synthetic",
            "抖音 https://v.douyin.com/Synthetic01/ 分享" to "https://v.douyin.com/Synthetic01/",
            "快手 https://v.kuaishou.com/Synthetic02 分享" to "https://v.kuaishou.com/Synthetic02",
            "微博 https://weibo.com/12345/AbCd" to "https://weibo.com/12345/AbCd",
            "哔哩哔哩 https://www.bilibili.com/video/BVsynthetic" to "https://www.bilibili.com/video/BVsynthetic",
            "Instagram https://www.instagram.com/reel/De6y3nBMmnZ/?dlrf=synthetic" to "https://www.instagram.com/reel/De6y3nBMmnZ/?dlrf=synthetic")
        social.forEach{(input,expected)->assertEquals(expected,MediaLinks.validUrl(input))}
        assertEquals("Instagram",Links.title("https://www.instagram.com/reel/De6y3nBMmnZ/"))
        assertEquals("打开网站",Links.title("https://malicious-instagram.com/reel/De6y3nBMmnZ/"))
        assertTrue(Links.canResolveFromBrowser("https://www.instagram.com/reel/De6y3nBMmnZ/?dlrf=synthetic"))
        assertFalse(Links.canResolveFromBrowser("https://www.instagram.com/accounts/login/?next=/reel/De6y3nBMmnZ/"))
        assertFalse(Links.canResolveFromBrowser("https://www.instagram.com/"))
        assertFalse(Links.canResolveFromBrowser("https://malicious-instagram.com/reel/De6y3nBMmnZ/"))
    }
    @Test fun failedRemoteDownloadExplainsRetryWithoutMarkingItComplete() {
        assertTrue(mediaDownloadFailure(403).contains("验证"))
        assertTrue(mediaDownloadFailure(404).contains("过期"))
        assertTrue(mediaDownloadFailure(429).contains("稍后"))
        assertTrue(mediaDownloadFailure(android.app.DownloadManager.ERROR_INSUFFICIENT_SPACE).contains("存储"))
    }
}
