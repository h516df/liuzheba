package dev.h516.shicang

import java.net.URI
import java.util.Locale

/** Accept browser links, including a URL embedded in a platform share message. */
object Links {
    fun normalize(input:String):String? {
        val raw=input.trim()
        val embedded=Regex("https?://[^\\s<>\"，。！？；【】“”‘’「」『』\u200B\uFEFF]+",RegexOption.IGNORE_CASE).find(raw)?.value
        val candidate=(embedded ?: raw).trimEnd('.',',',';',')','）',']','】','!','。')
        val url=if(candidate.startsWith("https://",true) || candidate.startsWith("http://",true)) candidate else "https://$candidate"
        return runCatching {
            val uri=URI(url)
            val host=uri.host?.lowercase(Locale.ROOT) ?: return null
            if(uri.scheme.lowercase(Locale.ROOT) !in listOf("http","https") || uri.rawUserInfo!=null || !host.contains('.') || host.startsWith('.') || host.endsWith('.') || uri.port !in -1..65535 || url.any{it.isWhitespace() || it.isISOControl()}) return null
            url
        }.getOrNull()
    }
    fun host(url:String)=runCatching{URI(url).host.orEmpty().removePrefix("www.")}.getOrDefault("")
    fun title(url:String):String {
        val h=host(url)
        fun belongs(domain:String)=h==domain || h.endsWith(".$domain")
        return when {
            belongs("douyin.com")->"抖音"
            belongs("kuaishou.com") || belongs("gifshow.com")->"快手"
            belongs("bilibili.com") || belongs("b23.tv")->"哔哩哔哩"
            belongs("xiaohongshu.com") || belongs("xhslink.com") || belongs("xhslink.cn")->"小红书"
            belongs("weibo.com") || belongs("weibo.cn")->"微博"
            else->"打开网站"
        }
    }
}
