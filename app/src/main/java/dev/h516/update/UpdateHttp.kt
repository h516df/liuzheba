package dev.h516.update

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI

/** 公开发行资料匿名读取，地址固定，资产跳转只认 GitHub；不发送账号凭据。 */
internal class UpdateHttp(
    private val connect: (String) -> HttpURLConnection = { URI(it).toURL().openConnection() as HttpURLConnection },
) {

    @Volatile private var connection: HttpURLConnection? = null
    @Volatile private var cancelled = false

    fun cancel() {
        cancelled = true
        connection?.disconnect()
    }

    private fun ensureAlive() {
        if (cancelled || Thread.currentThread().isInterrupted) throw InterruptedException()
    }

    private fun open(url: String, binary: Boolean): HttpURLConnection {
        ensureAlive()
        val c = connect(url)
        connection = c
        c.instanceFollowRedirects = false
        c.connectTimeout = 15_000
        c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", UpdateConfig.APK_PREFIX + "-updater")
        c.setRequestProperty(
            "Accept",
            if (binary) "application/octet-stream" else "application/vnd.github+json",
        )
        c.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
        return c
    }

    fun json(path: String): String {
        val url = UpdateConfig.API + path
        requireUpdate(UpdateConfig.apiUrl(url), "更新地址不可信，已停止")
        val c = open(url, binary = false)
        try {
            status(c.responseCode)
            return c.inputStream.use { String(readBounded(it, 2 * 1024 * 1024), Charsets.UTF_8) }
        } finally {
            c.disconnect()
            connection = null
        }
    }

    /** 下载资产（安装包或校验文件）。全程匿名，且只认 GitHub 的资产域名。 */
    fun <T> asset(id: Long, read: (InputStream) -> T): T {
        requireUpdate(id > 0, "更新资料不完整，请稍后重试")
        var url = "${UpdateConfig.API}/releases/assets/$id"
        repeat(4) {
            val c = open(url, binary = true)
            try {
                val code = c.responseCode
                if (code in setOf(301, 302, 303, 307, 308)) {
                    val next = c.getHeaderField("Location").orEmpty()
                    requireUpdate(UpdateConfig.assetRedirect(next), "更新下载地址不可信，已停止下载")
                    url = next
                } else {
                    status(code)
                    return c.inputStream.use(read)
                }
            } finally {
                c.disconnect()
                connection = null
            }
        }
        throw UpdateFailure("更新下载地址异常，请稍后重试")
    }

    /** 读一个小的文本资产（更新说明、SHA256 文件）。 */
    fun assetText(id: Long): String = asset(id) { input ->
        String(readBounded(input, 512 * 1024), Charsets.UTF_8)
    }

    /** 下载安装包到指定文件，边下边报进度（0-100）。大小必须和发布资料一致。 */
    fun downloadApk(id: Long, expectedSize: Long, dest: File, onProgress: (Int) -> Unit) {
        asset(id) { input ->
            dest.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var written = 0L
                var lastPercent = -1
                while (true) {
                    ensureAlive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    written += n
                    requireUpdate(written <= UpdateConfig.MAX_APK, "安装包异常偏大，已停止下载")
                    if (expectedSize > 0) {
                        val percent = (written * 100 / expectedSize).toInt().coerceIn(0, 100)
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                }
                requireUpdate(written == expectedSize, "安装包下载不完整，请重试")
            }
        }
    }

    private fun status(code: Int) {
        if (code in 200..299) return
        throw UpdateFailure(
            when (code) {
                401, 403, 429 -> "公开更新暂时不可用或请求过多，请稍后重试"
                404 -> "没找到更新资料，可能这个版本还没发布"
                in 500..599 -> "GitHub 暂时连不上，请稍后重试"
                else -> "检查更新失败，请稍后重试"
            }
        )
    }

    private fun readBounded(input: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            ensureAlive()
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            requireUpdate(total <= max, "更新资料异常偏大，已停止")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}
