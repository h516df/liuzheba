package dev.h516.shicang

import java.net.InetAddress
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class AuthorProfilesTest {
    @Test fun parsesFourPlatformsWithoutInventingNicknameOrPublicHandle() {
        val links=listOf("https://www.xiaohongshu.com/user/profile/abc123?xsec_token=public" to "小红书", "https://www.douyin.com/user/MS4wLjABabc" to "抖音", "https://www.kuaishou.com/profile/abc123" to "快手", "https://m.weibo.cn/profile/123456" to "微博")
        links.forEach { (url,platform) ->
            val i=AuthorProfiles.parse("复制打开App 某人的主页 $url")!!
            assertEquals(platform,i.platform);assertEquals("",i.nickname);assertEquals("",i.publicAccount)
            assertFalse(i.profileUrl.contains('?'));assertTrue(AuthorProfiles.valid(i))
        }
    }
    @Test fun keepsAliasAndIdKindsSeparate() {
        listOf(
            "https://weibo.com/n/小林" to "screen_name",
            "https://weibo.com/exampleCreator" to "domain",
            "https://www.iesdouyin.com/share/user/123456" to "uid",
            "https://www.kuaishou.com/user/123456" to "user_id"
        ).forEach { (url,type) ->
            val i=AuthorProfiles.parse(url)!!
            assertEquals(type,i.idType);assertTrue(AuthorProfiles.valid(i));assertEquals("",i.publicAccount)
            if(type=="screen_name" || type=="domain") assertFalse(i.profileUrl.contains("/u/"))
        }
        assertNull(AuthorProfiles.platform("https://t.cn/example"))
        assertNull(AuthorProfiles.parse("https://t.cn/example"))
        assertTrue(AuthorProfiles.weiboShort("https://t.cn/example"))
        assertFalse(AuthorProfiles.weiboShort("https://t.cn.evil.test/example"))
        listOf("https://weibo.com/login", "https://weibo.com/status", "https://weibo.com/123/abcdef").forEach { assertNull(AuthorProfiles.parse(it)) }
    }
    @Test fun rejectsWorksSpoofsCredentialsPortsAndUnverifiedPaths() {
        listOf("https://www.douyin.com/video/123", "https://www.xiaohongshu.com/explore/123", "https://www.kuaishou.com/short-video/123", "https://weibo.com/detail/123", "https://douyin.com.evil.test/user/abc", "https://evil.douyin.com/user/abc", "https://user@www.douyin.com/user/abc", "http://www.douyin.com/user/abc", "https://www.douyin.com:8080/user/abc", "https://www.douyin.com/user/%2e%2e", "https://live.kuaishou.com/profile/abc", "https://LIVE.KUAISHOU.COM/profile/abc", "https://xhslink.com/user/profile/abc", "https://v.douyin.com/user/abc", "https://v.kuaishou.com/profile/abc").forEach { assertNull(it, AuthorProfiles.parse(it)) }
    }
    @Test fun rejectsInternalAndReservedDnsAddresses() {
        listOf("127.0.0.1", "0.0.0.0", "10.0.0.1", "172.16.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1", "198.18.0.1", "192.0.2.1", "224.0.0.1", "::1", "fc00::1", "fe80::1", "2001:db8::1", "2002:7f00:1::").forEach { assertFalse(it,PublicProfileNetwork.publicAddress(InetAddress.getByName(it))) }
        assertTrue(PublicProfileNetwork.publicAddress(InetAddress.getByName("8.8.8.8")))
        assertTrue(PublicProfileNetwork.publicAddress(InetAddress.getByName("2606:4700:4700::1111")))
    }
    @Test fun extractsOnlyBoundedPageMetadataNotShareOrLoginText() {
        assertEquals("小林",AuthorProfiles.publicNickname("<meta content='小林的个人主页 - 抖音' property='og:title'>","抖音"))
        assertEquals("A & B",AuthorProfiles.publicNickname("<meta property=\"og:title\" content=\"A &amp; B - 微博\">","微博"))
        listOf("登录 - 微博","微博 - 随时随地发现新鲜事","安全验证","<script>").forEach { assertEquals("",AuthorProfiles.publicNickname("<meta property='og:title' content='$it'>","微博")) }
        assertEquals("",AuthorProfiles.publicNickname("<title>随机分享标题</title>","抖音"))
    }
    @Test fun structuredMetadataRequiresExactCurrentIdentityAndBoundedJson() {
        val identity=AuthorProfiles.parse("https://www.douyin.com/user/sec123")!!
        fun script(json:String)="<script type='application/json'>$json</script>"
        val own=script("""{"user":{"sec_uid":"sec123","nickname":"小林","unique_id":"lin.public"},"other":{"sec_uid":"other","nickname":"不能混入","unique_id":"wrong"}}""")
        assertEquals("小林" to "lin.public",AuthorProfiles.publicMetadata(own,identity))
        assertEquals("" to "",AuthorProfiles.publicMetadata(script("""{"sec_uid":"different","nickname":"别人"}"""),identity))
        val deep="[".repeat(30)+"""{"sec_uid":"sec123","nickname":"深层"}"""+"]".repeat(30)
        assertEquals("" to "",AuthorProfiles.publicMetadata(script(deep),identity))
        val conflict=script("""[{"sec_uid":"sec123","nickname":"甲"},{"sec_uid":"sec123","nickname":"乙"}]""")
        assertEquals("" to "",AuthorProfiles.publicMetadata(conflict,identity))
        assertTrue(AuthorProfiles.metadataDetails(conflict+"<meta property='og:title' content='不应覆盖冲突'>",identity).nameConflict)
        assertEquals("" to "",AuthorProfiles.publicMetadata("<script>window.state={\"sec_uid\":\"sec123\",\"nickname\":\"脚本\"}</script>",identity))
    }
    @Test fun refusesPartialMetadataWhenNodeBudgetHidesConflicts() {
        val identity=AuthorProfiles.parse("https://www.douyin.com/user/sec123")!!
        val json="""[{"sec_uid":"sec123","nickname":"甲"},"""+"0,".repeat(5000)+"""{"sec_uid":"sec123","nickname":"乙"}]"""
        val result=AuthorProfiles.metadataDetails("<script type='application/json'>$json</script>",identity)
        assertEquals("",result.nickname);assertTrue(result.nameConflict)
    }
    @Test fun storedProfileMustBeLiteralHttpsNotEmbeddedInAnotherScheme() {
        val identity=AuthorProfiles.parse("https://weibo.com/u/123")!!
        listOf("otherapp://action?url=https://weibo.com/u/123", "share https://weibo.com/u/123", "https://evil.test/?url=https://weibo.com/u/123").forEach {
            assertFalse(it,AuthorProfiles.valid(identity.copy(profileUrl=it)))
        }
    }
    @Test fun directProfileRedirectedToLoginKeepsVerifiedLinkAndEmptyMetadata() {
        val result=AuthorProfiles.resolveWithFetcher("https://weibo.com/u/123") { _,_->PublicProfileNetwork.Page("https://weibo.com/login","<meta property='og:title' content='登录'>") }
        assertNotNull(result);assertEquals("https://weibo.com/u/123",result!!.identity.profileUrl);assertEquals("",result.identity.nickname);assertFalse(result.metadataFetched)
        assertNull(AuthorProfiles.resolveWithFetcher("https://t.cn/example") { _,_->PublicProfileNetwork.Page("https://weibo.com/login","") })
    }
    @Test fun formatFiveRoundTripPreservesStableRelationsAndSeparateFields() {
        val i=AuthorProfiles.parse("https://www.douyin.com/user/sec123")!!.copy(nickname="昵称",publicAccount="公开号")
        val author=Author(name="本地称呼",identities=listOf(i))
        val a=Archive(name="作品",authorIds=listOf(author.id))
        val l=Library(archives=listOf(a),drafts=listOf(a.copy(id=uid())),authors=listOf(author))
        assertEquals(5,Codec.library(l).getInt("format"));assertEquals(l,Codec.library(Codec.library(l)))
        val renamed=author.copy(name="改名",identities=listOf(i.copy(nickname="新昵称")))
        assertEquals(author.id,renamed.id);assertEquals(i.id,renamed.identities.single().id)
        assertEquals(author.copy(name=""),Codec.author(Codec.author(author.copy(name=""))))
    }
    @Test fun oldFormatsDefaultToNoAuthorsOrRelations() {
        (1..4).forEach { format ->
            val json=Codec.library(Library(archives=listOf(Archive(name="旧档案")))).put("format",format)
            json.remove("authors");json.getJSONArray("archives").getJSONObject(0).remove("authorIds")
            val restored=Codec.library(json)
            assertTrue(restored.authors.isEmpty());assertTrue(restored.archives.single().authorIds.isEmpty())
        }
    }
    @Test fun rejectsDanglingDuplicateAndUnsafeIdentities() {
        val i=AuthorProfiles.parse("https://weibo.com/u/123")!!
        val author=Author(name="作者",identities=listOf(i))
        fun rejected(l:Library) { assertTrue(runCatching{Codec.library(Codec.library(l))}.isFailure) }
        rejected(Library(archives=listOf(Archive(name="作品",authorIds=listOf(uid())))))
        rejected(Library(authors=listOf(author,author)))
        rejected(Library(authors=listOf(author,author.copy(id=uid()))))
        rejected(Library(authors=listOf(author.copy(identities=listOf(i,i.copy(id=uid()))))))
        rejected(Library(authors=listOf(author.copy(identities=listOf(i.copy(profileUrl="https://evil.test/u/123"))))))
        rejected(Library(authors=listOf(author.copy(identities=listOf(i.copy(platformId="456"))))))
        val json=Codec.library(Library(authors=listOf(author),archives=listOf(Archive(name="作品",authorIds=listOf(author.id)))))
        json.getJSONArray("archives").getJSONObject(0).put("authorIds",JSONArray(listOf(author.id,author.id)))
        assertTrue(runCatching{Codec.library(json)}.isFailure)
    }
}
