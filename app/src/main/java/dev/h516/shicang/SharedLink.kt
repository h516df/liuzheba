package dev.h516.shicang

import java.net.URI

data class SharedLink(val url:String,val platform:String,val kind:String,val name:String,val original:String) {
    fun archive()=Archive(name=name,fields=listOf(
        Field(name="平台",value=platform),Field(name="内容类型",value=kind),
        Field(name=if(kind=="个人主页")"个人主页" else if(kind=="直播间")"直播间" else "来源链接",type="链接",value=url),
        Field(name="分享原文",value=original)))
}

/** Local recognition always works. Public redirects only improve short-link classification. */
object SharedLinks {
    fun platform(url:String):String? = AuthorProfiles.platform(url)
    fun parse(text:String,resolved:String?=null):SharedLink? {
        val originalUrl=Links.normalize(text) ?: return null
        val url=resolved?.takeIf{platform(it)==platform(originalUrl)} ?: originalUrl
        val platform=platform(url) ?: return null
        val uri=URI(url);val path=uri.path.orEmpty().lowercase()
        val kind=when {
            platform=="微博" && Regex("^/[0-9]{1,32}/[A-Za-z0-9]+/?$").matches(path) -> "作品"
            Regex("/(video|photo|explore|discovery/item|short-video|detail|status)(/|$)").containsMatchIn(path)->"作品"
            uri.host.lowercase(java.util.Locale.ROOT).startsWith("live.") || Regex("/(live|reflow|livestream)(/|$)").containsMatchIn(path) -> "直播间"
            AuthorProfiles.parse(url)!=null -> "个人主页"
            else->"分享链接"
        }
        // Share prose is preserved as source text, not mistaken for an author's nickname.
        val plain=text.replace(Regex("https?://[^\\s]+"), "").trim()
        val title=plain.takeIf { it.length<=50 && Regex("^[^\\r\\n]+的(?:个人)?主页$").matches(it) }.orEmpty()
        return SharedLink(url,platform,kind,title.ifBlank{"$platform · $kind"},text)
    }

    fun resolve(text:String):SharedLink? {
        val local=parse(text) ?: return null
        // A complete URL already identifies its destination; keep this path offline and immediate.
        if(parse(local.url)?.kind != "分享链接") return local
        return runCatching {
            val page=PublicProfileNetwork.fetch(local.url,local.platform)
            parse(text,page.url) ?: local
        }.getOrDefault(local)
    }
}
