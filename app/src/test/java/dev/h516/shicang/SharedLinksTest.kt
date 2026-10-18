package dev.h516.shicang
import org.junit.Assert.*
import org.junit.Test
class SharedLinksTest {
 @Test(timeout=1000) fun completeLinksPrefillWithoutWaitingForTheNetwork(){
  listOf("示例直播间 https://live.kuaishou.com/u/demo", "https://www.douyin.com/user/abc", "https://www.xiaohongshu.com/explore/abc").forEach { text ->
   assertEquals(SharedLinks.parse(text),SharedLinks.resolve(text))
  }
 }

 @Test fun recognizesPlatformAndType() {
  listOf("https://www.douyin.com/user/abc" to "个人主页","https://live.douyin.com/123" to "直播间","https://www.xiaohongshu.com/user/profile/abc" to "个人主页","https://www.kuaishou.com/profile/abc" to "个人主页","https://live.kuaishou.com/u/abc" to "直播间","https://www.kuaishou.com/short-video/123" to "作品").forEach{(url,kind)->assertEquals(kind,SharedLinks.parse(url)!!.kind)}
 }
 @Test fun keepsOriginalAndPrefillsEditableFields(){val text="小林的主页 https://v.douyin.com/abc/";val result=SharedLinks.parse(text,"https://www.douyin.com/user/123")!!;assertEquals("个人主页",result.kind);assertEquals("小林的主页",result.name);assertEquals(text,result.archive().fields.last().value);assertEquals("链接",result.archive().fields[2].type)}
 @Test fun shortLinkNeverPretendsToBeVerifiedVideo(){assertEquals("分享链接",SharedLinks.parse("https://xhslink.com/a/abc")!!.kind)}
 @Test fun spoofedHostsAndCredentialsAreRejected(){listOf("https://douyin.com.evil.test/user/a","https://notdouyin.com/a","https://name:secret@douyin.com/a","hello").forEach{assertNull(SharedLinks.parse(it))}}
 @Test fun seekClampsBothEnds(){assertEquals(0L,seekPosition(3000,-10000,60000));assertEquals(60000L,seekPosition(59000,10000,60000));assertEquals(13000L,seekPosition(3000,10000,60000))}
}
