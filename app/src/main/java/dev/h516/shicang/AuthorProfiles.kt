package dev.h516.shicang

import java.net.URI
import java.util.Locale
import org.json.JSONObject
import org.json.JSONArray

/** Local IDs survive a nickname, account handle, or homepage change. */
data class Author(val id: String = uid(), val name: String = "", val identities: List<PlatformIdentity> = emptyList(), val updated: Long = System.currentTimeMillis())
data class PlatformIdentity(
    val id: String = uid(), val platform: String = "", val nickname: String = "",
    val publicAccount: String = "", val platformId: String = "", val idType: String = "", val profileUrl: String = ""
)
data class AuthorImportResult(val identity: PlatformIdentity, val message: String, val metadataFetched: Boolean = false)

object AuthorProfiles {
    val platforms = listOf("小红书", "抖音", "快手", "微博")
    private val hosts = mapOf(
        "小红书" to setOf("xiaohongshu.com", "www.xiaohongshu.com", "xhslink.com", "www.xhslink.com"),
        "抖音" to setOf("douyin.com", "www.douyin.com", "v.douyin.com", "www.iesdouyin.com", "iesdouyin.com", "live.douyin.com"),
        "快手" to setOf("kuaishou.com", "www.kuaishou.com", "v.kuaishou.com", "live.kuaishou.com", "gifshow.com", "www.gifshow.com"),
        "微博" to setOf("weibo.com", "www.weibo.com", "m.weibo.cn", "weibo.cn", "www.weibo.cn")
    )
    fun platform(url: String): String? = runCatching {
        val u = URI(url)
        if (u.scheme != "https" || u.rawUserInfo != null || u.port !in listOf(-1, 443) || u.rawFragment?.any { it.isISOControl() } == true) return null
        hosts.entries.firstOrNull { u.host?.lowercase(Locale.ROOT) in it.value }?.key
    }.getOrNull()

    /** Only a platform's explicit profile route yields an identity, never a work ID. */
    fun parse(text: String): PlatformIdentity? {
        val url = Links.normalize(text) ?: return null
        val platform = platform(url) ?: return null
        val uri = URI(url)
        if (uri.host.lowercase(Locale.ROOT) in setOf("xhslink.com", "www.xhslink.com", "v.douyin.com", "v.kuaishou.com")) return null
        if (Regex("%2f|%5c", RegexOption.IGNORE_CASE).containsMatchIn(uri.rawPath.orEmpty())) return null
        val path = uri.path.orEmpty().trimEnd('/')
        if (uri.host.lowercase(Locale.ROOT).startsWith("live.")) return null
        val route: Triple<String, String, String> = when (platform) {
            "小红书" -> {
                val id = Regex("^/user/profile/([A-Za-z0-9_-]{1,160})$").matchEntire(path)?.groupValues?.get(1) ?: return null
                Triple(id, "user_id", "https://www.xiaohongshu.com/user/profile/$id")
            }
            "抖音" -> {
                val sec = Regex("^/user/([A-Za-z0-9_-]{1,200})$").matchEntire(path)?.groupValues?.get(1)
                val uid = Regex("^/share/user/([0-9]{1,32})$").matchEntire(path)?.groupValues?.get(1)
                if (sec != null) Triple(sec, "sec_uid", "https://www.douyin.com/user/$sec")
                else if (uid != null) Triple(uid, "uid", "https://www.iesdouyin.com/share/user/$uid") else return null
            }
            "快手" -> {
                val match = Regex("^/(profile|user)/([A-Za-z0-9_-]{1,160})$").matchEntire(path) ?: return null
                val kind=match.groupValues[1]; val id=match.groupValues[2]
                Triple(id, if(kind=="profile") "profile_id" else "user_id", "https://www.kuaishou.com/$kind/$id")
            }
            else -> {
                val numeric = Regex("^/(?:u/|profile/)?([0-9]{1,32})$").matchEntire(path)?.groupValues?.get(1)
                val screenName = Regex("^/n/([^/]{1,160})$").matchEntire(path)?.groupValues?.get(1)
                val domain = Regex("^/([A-Za-z][A-Za-z0-9_]{1,79})$").matchEntire(path)?.groupValues?.get(1)
                val reserved = setOf("login", "signup", "register", "status", "detail", "home", "search", "discover", "hot", "tv", "video", "live", "about", "privacy", "service", "help", "newlogin", "p", "u", "n", "profile", "ajax", "a", "s", "overseas")
                when {
                    numeric != null -> Triple(numeric, "uid", "https://weibo.com/u/$numeric")
                    screenName != null && !screenName.contains('%') && screenName.none { it.isWhitespace() || it.isISOControl() || it in "<>\\" } -> Triple(screenName, "screen_name", URI("https", uri.host, path, null).toASCIIString())
                    domain != null && domain.lowercase(Locale.ROOT) !in reserved -> Triple(domain, "domain", "https://${uri.host}/$domain")
                    else -> return null
                }
            }
        }
        return PlatformIdentity(platform = platform, platformId = route.first, idType = route.second, profileUrl = route.third)
    }

    fun weiboShort(url: String): Boolean = runCatching {
        val u=URI(url)
        u.scheme=="https" && u.host=="t.cn" && u.rawUserInfo==null && u.port in listOf(-1,443)
    }.getOrDefault(false)

    /** No cookies, account state, APIs, or script execution. Failure never invents metadata. */
    fun resolve(text: String): AuthorImportResult? = resolveWithFetcher(text,PublicProfileNetwork::fetch)
    internal fun resolveWithFetcher(text:String,fetch:(String,String)->PublicProfileNetwork.Page):AuthorImportResult? {
        val url = Links.normalize(text) ?: return null
        val platform = platform(url) ?: if(weiboShort(url)) "微博" else return null
        val local = parse(url)
        return runCatching {
            val page = fetch(url, platform)
            val identity = parse(page.url) ?: error("公开页面不是作者主页")
            val structured = metadataDetails(page.html, identity)
            val nickname = if(structured.nameConflict) "" else structured.nickname.ifBlank { publicNickname(page.html, identity.platform) }
            val fetched = nickname.isNotBlank() || structured.account.isNotBlank()
            val message = if(structured.nameConflict || structured.accountConflict) "公开页面资料不完整或有冲突，相关字段已留空，请手动核对补充。" else if (!fetched) "已识别作者主页；公开页面未提供可确认的资料，请手动补充。" else "已从公开主页读取可用资料，请核对；未读取的字段保留空白。"
            AuthorImportResult(identity.copy(nickname = nickname, publicAccount = structured.account), message, fetched)
        }.getOrElse {
            local?.let { AuthorImportResult(it, "已识别作者主页；公开页面暂不可读取，请手动补充昵称和公开账号。") }
        }
    }

    /** Read only JSON script data belonging to the exact current identity; never evaluate JavaScript. */
    internal data class Metadata(val nickname:String, val account:String, val nameConflict:Boolean, val accountConflict:Boolean)
    internal fun publicMetadata(html: String, identity: PlatformIdentity): Pair<String, String> = metadataDetails(html,identity).let { it.nickname to it.account }
    internal fun metadataDetails(html: String, identity: PlatformIdentity): Metadata {
        val idKeys = when (identity.platform) {
            "小红书" -> listOf("userId", "user_id")
            "抖音" -> if(identity.idType=="sec_uid") listOf("secUid", "sec_uid") else listOf("uid", "user_id")
            "快手" -> if(identity.idType=="profile_id") listOf("eid", "userEid", "user_id") else listOf("userId", "user_id")
            else -> if(identity.idType=="uid") listOf("idstr", "id", "uid") else emptyList()
        }
        val accountKeys=when(identity.platform) {
            "小红书" -> listOf("redId", "red_id", "xiaohongshuId")
            "抖音" -> listOf("uniqueId", "unique_id")
            "快手" -> listOf("kwaiId", "kwai_id")
            else -> emptyList()
        }
        fun field(o:JSONObject, keys:List<String>):String = keys.firstNotNullOfOrNull { key ->
            val value=o.opt(key)
            val text=if(value is String || value is Number) value.toString().trim() else ""
            text.takeIf { it.isNotEmpty() && it.length<=100 && it.none { c -> c.isISOControl() || c in "<>" } }
        }.orEmpty()
        val candidates=mutableListOf<Pair<String,String>>()
        var visited=0
        var exhausted=false
        fun visit(value:Any?, depth:Int) {
            if(exhausted)return
            if(depth>20 || ++visited>5000) {exhausted=true;return}
            when(value) {
                is JSONObject -> {
                    if(idKeys.any { value.opt(it)?.toString()==identity.platformId }) {
                        val nickname=field(value, if(identity.platform=="微博") listOf("screen_name") else listOf("nickname", "nickName", "userName", "user_name"))
                        val account=field(value,accountKeys).takeUnless { it=="0" }.orEmpty()
                        if(nickname.isNotEmpty() || account.isNotEmpty()) candidates.add(nickname to account)
                    }
                    value.keys().forEach { visit(value.opt(it),depth+1) }
                }
                is JSONArray -> for(i in 0 until value.length()) visit(value.opt(i),depth+1)
            }
        }
        Regex("<script\\b([^>]{0,4096})>(.*?)</script\\s*>", setOf(RegexOption.IGNORE_CASE,RegexOption.DOT_MATCHES_ALL)).findAll(html).take(20).forEach { script ->
            if(!Regex("type\\s*=\\s*['\"]application/(?:ld\\+)?json['\"]",RegexOption.IGNORE_CASE).containsMatchIn(script.groupValues[1])) return@forEach
            val json=script.groupValues[2].trim()
            if(json.length>256*1024 || !boundedJson(json)) return@forEach
            runCatching { visit(if(json.startsWith("{")) JSONObject(json) else JSONArray(json),0) }
        }
        // Conflicting page data is not an invitation to guess.
        if(exhausted)return Metadata("","",true,true)
        val names=candidates.map{it.first}.filter{it.isNotEmpty()}.distinct()
        val accounts=candidates.map{it.second}.filter{it.isNotEmpty()}.distinct()
        return Metadata(names.singleOrNull().orEmpty(), accounts.singleOrNull().orEmpty(), names.size>1, accounts.size>1)
    }

    private fun boundedJson(json:String):Boolean {
        var depth=0;var quoted=false;var escaped=false
        for(c in json) {
            if(quoted) { if(escaped) escaped=false else if(c=='\\') escaped=true else if(c=='"') quoted=false }
            else when(c) { '"' -> quoted=true; '{','[' -> { depth++;if(depth>20) return false }; '}',']' -> { depth--;if(depth<0)return false } }
        }
        return depth==0 && !quoted
    }

    internal fun publicNickname(html: String, platform: String): String {
        // Restrict extraction to the page's own social title; never search arbitrary nested author objects.
        val tags = Regex("<meta\\b[^>]{0,4096}>", RegexOption.IGNORE_CASE).findAll(html)
        val attrs = Regex("([A-Za-z:_-]+)\\s*=\\s*([\"'])(.*?)\\2", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val title = tags.map { tag -> attrs.findAll(tag.value).associate { it.groupValues[1].lowercase(Locale.ROOT) to it.groupValues[3] } }
            .firstOrNull { it["property"]?.lowercase(Locale.ROOT) == "og:title" }?.get("content").orEmpty()
        val decoded = title.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
        val cleaned = decoded.replace(Regex("\\s*[-_|｜]\\s*(?:$platform)(?:个人主页|主页)?\\s*$"), "").removeSuffix("的个人主页").removeSuffix("的主页").trim()
        if (cleaned.isBlank() || cleaned.length > 100 || cleaned.any { it.isISOControl() } || cleaned.contains('<') || cleaned.contains('>') || cleaned.contains("https://") || cleaned in platforms || listOf("登录", "安全验证", "验证码", "访问受限", "页面不存在", "找不到", "随时随地发现新鲜事", "记录美好生活", "标记我的生活", "拥抱每一种生活", "登录后", "Login", "Sign in", "Access Denied").any { it in cleaned }) return ""
        return cleaned
    }

    fun valid(identity: PlatformIdentity): Boolean {
        if (!Codec.validId(identity.id) || identity.platform !in platforms || identity.nickname.length > 200 || identity.publicAccount.length > 200 || identity.platformId.length > 200 || identity.idType.length > 40 || identity.profileUrl.length > 2048) return false
        if (listOf(identity.nickname, identity.publicAccount, identity.platformId, identity.idType, identity.profileUrl).any { value -> value.any { it.isISOControl() } }) return false
        if (identity.profileUrl.isEmpty()) return true
        if(platform(identity.profileUrl)!=identity.platform)return false
        val parsed = parse(identity.profileUrl) ?: return false
        return parsed.platform == identity.platform && (identity.platformId.isEmpty() || identity.platformId == parsed.platformId) && (identity.idType.isEmpty() || identity.idType == parsed.idType)
    }
}
