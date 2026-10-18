package dev.h516.shicang

import java.net.HttpURLConnection
import java.net.URI

data class SharedLink(val url:String,val platform:String,val kind:String,val name:String,val original:String) {
    fun archive()=Archive(name=name,fields=listOf(
        Field(name="平台",value=platform),Field(name="内容类型",value=kind),
        Field(name=if(kind=="个人主页")"个人主页" else if(kind=="直播间")"直播间" else "来源链接",type="链接",value=url),
        Field(name="分享原文",value=original)))
}

/** Local recognition always works. Public redirects only improve short-link classification. */
object SharedLinks {
    fun platform(url:String):String? {
        val host=runCatching{URI(url).host?.lowercase()}.getOrNull() ?: return null
        fun belongs(d:String)=host==d || host.endsWith(".$d")
        return when {
            belongs("douyin.com") || belongs("iesdouyin.com")->"抖音"
            belongs("xiaohongshu.com") || belongs("xhslink.com")->"小红书"
            belongs("kuaishou.com") || belongs("gifshow.com")->"快手"
            else->null
        }
    }
    fun parse(text:String,resolved:String?=null):SharedLink? {
        val originalUrl=Links.normalize(text) ?: return null
        val url=resolved?.takeIf{platform(it)!=null} ?: originalUrl
        val platform=platform(url) ?: return null
        val uri=URI(url);val path=uri.path.orEmpty().lowercase()
        val kind=when {
            uri.host.startsWith("live.") || Regex("/(live|reflow|livestream)(/|$)").containsMatchIn(path) || "直播间" in text -> "直播间"
            Regex("/(user|profile)(/|$)").containsMatchIn(path) || "个人主页" in text || "的主页" in text -> "个人主页"
            Regex("/(video|photo|explore|discovery/item|short-video)(/|$)").containsMatchIn(path)->"作品"
            else->"分享链接"
        }
        val title=text.replace(Regex("https?://[^\\s]+"),"").replace(Regex("[\\r\\n]+")," ").trim()
            .replace(Regex("^[0-9.:/\\s]+"),"").trim().take(100)
        return SharedLink(url,platform,kind,title.ifBlank{"$platform · $kind"},text)
    }
    fun resolve(text:String):SharedLink? {
        val local=parse(text) ?: return null
        // A complete URL already identifies its destination; keep this path offline and immediate.
        if(parse(local.url)?.kind != "分享链接") return local
        return runCatching {
            var url=local.url
            repeat(4) {
                val uri=URI(url)
                require(uri.scheme=="https" && uri.rawUserInfo==null && uri.port in listOf(-1,443) && platform(url)==local.platform)
                val connection=uri.toURL().openConnection() as HttpURLConnection
                connection.instanceFollowRedirects=false;connection.connectTimeout=3000;connection.readTimeout=3000
                connection.setRequestProperty("User-Agent","Mozilla/5.0 (Android) Shicang/0.9")
                try {
                    if(connection.responseCode in listOf(301,302,303,307,308)) {
                        val location=connection.getHeaderField("Location") ?: return local
                        url=uri.resolve(location).toString()
                    } else return parse(text,url) ?: local
                } finally {connection.disconnect()}
            }
            parse(text,url) ?: local
        }.getOrDefault(local)
    }
}
