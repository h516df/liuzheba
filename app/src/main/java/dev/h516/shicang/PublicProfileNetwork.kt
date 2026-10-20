package dev.h516.shicang

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit

/** DNS addresses are validated and used by OkHttp itself, not checked then resolved again. */
internal object PublicProfileNetwork {
    data class Page(val url: String, val html: String)
    private const val MAX_BYTES = 512 * 1024
    private val client = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .dns(object: Dns { override fun lookup(hostname:String):List<InetAddress> = Dns.SYSTEM.lookup(hostname).also { addresses ->
            require(addresses.isNotEmpty() && addresses.all(::publicAddress)) { "非公开网络地址" }
        } })
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(3, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build()

    internal fun publicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        val b = address.address.map { it.toInt() and 255 }
        return if (b.size == 4) {
            !(b[0] == 0 || b[0] == 10 || b[0] == 127 || b[0] >= 224 || b[0] == 169 && b[1] == 254 || b[0] == 172 && b[1] in 16..31 || b[0] == 192 && (b[1] == 168 || b[1] == 0 || b[1] == 2) || b[0] == 100 && b[1] in 64..127 || b[0] == 198 && b[1] in 18..19 || b[0] == 198 && b[1] == 51 && b[2] == 100 || b[0] == 203 && b[1] == 0 && b[2] == 113)
        } else b.size == 16 && (b[0] and 0xe0) == 0x20 && !(b[0] == 0x20 && b[1] == 0x01 && (b[2] == 0x0d && b[3] == 0xb8 || b[2] < 2)) && !(b[0] == 0x20 && b[1] == 0x02) && !(b[0] == 0x3f && b[1] == 0xff)
    }

    private fun allowed(url: String, platform: String) = AuthorProfiles.platform(url) == platform || platform == "微博" && AuthorProfiles.weiboShort(url)

    fun fetch(start: String, platform: String): Page {
        var url = start
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(12)
        repeat(4) {
            require(allowed(url, platform)) { "跳转不在平台白名单内" }
            require(System.nanoTime() < deadline) { "读取超时" }
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Android) Shicang/0.15").header("Accept", "text/html").header("Accept-Encoding", "identity").build()
            client.newCall(request).execute().use { response ->
                require(allowed(response.request.url.toString(), platform))
                if (response.code in listOf(301, 302, 303, 307, 308)) {
                    url = URI(url).resolve(response.header("Location") ?: error("缺少跳转地址")).toString()
                    require(allowed(url, platform)) { "跳转不在平台白名单内" }
                } else {
                    require(response.isSuccessful)
                    require(response.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase() in listOf("text/html", "application/xhtml+xml"))
                    val body = response.body ?: error("空页面")
                    require(body.contentLength() <= MAX_BYTES)
                    val output = java.io.ByteArrayOutputStream()
                    body.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            require(System.nanoTime() < deadline) { "读取超时" }
                            val n = input.read(buffer)
                            if (n < 0) break
                            require(output.size() + n <= MAX_BYTES) { "页面过大" }
                            output.write(buffer, 0, n)
                        }
                    }
                    return Page(url, output.toString("UTF-8"))
                }
            }
        }
        error("跳转次数过多")
    }
}
