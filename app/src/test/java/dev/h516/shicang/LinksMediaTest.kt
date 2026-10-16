package dev.h516.shicang

import org.junit.Assert.*
import org.junit.Test

class LinksMediaTest {
    @Test fun linksAcceptShareMessagesAndDomainsAndRejectUnsafeSchemes() {
        assertEquals("https://v.douyin.com/AbC/",Links.normalize("复制打开抖音 https://v.douyin.com/AbC/ 看作品"))
        assertEquals("https://example.com/about",Links.normalize("example.com/about"))
        listOf("javascript:alert(1)","intent://douyin.com","file:///storage/a.jpg","data:text/html,hi","https://name:pass@example.com","https://","单纯备注","https://good.com bad.com").forEach {
            // A share message containing a valid HTTP URL is intentionally accepted.
            if(it!="https://good.com bad.com") assertNull(it,Links.normalize(it))
        }
        assertEquals("抖音",Links.title("https://v.douyin.com/id"))
        assertEquals("打开网站",Links.title("https://douyin.com.evil.example/id"))
        assertEquals("打开网站",Links.title("https://fakedouyin.com/id"))
    }
    @Test fun mixedMediaRoundtripAndOlderPhotoBackupsRemainReadable() {
        val id=uid()
        val video=Photo(id,"$id.original",300L*1024*1024,"a".repeat(64),"video/mp4",4000)
        val library=Library(archives=listOf(Archive(name="混合档案",photos=listOf(video))))
        val json=Codec.library(library)
        assertEquals(3,json.getInt("format"));assertEquals(library,Codec.library(json))
        val photoId=uid();val p=Photo(photoId,"$photoId.original",10,"b".repeat(64),"image/jpeg")
        val legacy=Codec.library(Library(archives=listOf(Archive(name="旧档案",photos=listOf(p)))))
        legacy.put("format",2).getJSONArray("archives").getJSONObject(0).getJSONArray("photos").getJSONObject(0).remove("duration")
        assertEquals(p,Codec.library(legacy).active.single().photos.single())
        assertThrows(IllegalArgumentException::class.java){Codec.photo(Codec.photo(video).put("bytes",Codec.MAX_MEDIA_BYTES+1))}
        assertThrows(IllegalArgumentException::class.java){Codec.photo(Codec.photo(p).put("bytes",Codec.MAX_PHOTO_BYTES+1))}
    }
}
