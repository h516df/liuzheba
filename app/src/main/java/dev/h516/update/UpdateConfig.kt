package dev.h516.update

import java.net.URI

/** 更新路上出问题时给用户看的话：全中文，不带术语，不出现仓库、token、API 这些词。 */
open class UpdateFailure(val userMessage: String) : Exception(userMessage)

/** Distinguish rate limits/server errors from invalid or untrusted update metadata. */
internal class UpdateHttpStatusFailure(val statusCode:Int,message:String):UpdateFailure(message)

internal fun requireUpdate(value: Boolean, message: String = "更新资料不完整，请稍后重试") {
    if (!value) throw UpdateFailure(message)
}

/** 公开发行入口固定为本项目；fork 必须更换自己的更新仓库和包名。 */
object UpdateConfig {
    const val REPO = "h516df/liuzheba"
    const val PACKAGE = "dev.h516.shicang"
    // Public production certificate, recorded in docs/acceptance/v0.13/build.json.
    const val CERTIFICATE_SHA256 = "67127caf5a760d51f11dc5a2fadcf44ddc30ce836fa14ffb9efd96c4b7e95a26"
    const val APK_PREFIX = "shicang"
    const val API = "https://api.github.com/repos/" + REPO
    const val PUBLIC_RELEASES = "https://github.com/" + REPO + "/releases"
    const val MAX_APK = 120L * 1024 * 1024

    /** 只允许访问这个仓库自己的 API 路径。 */
    fun apiUrl(value: String): Boolean = runCatching {
        val u = URI(value)
        u.scheme == "https" && u.host == "api.github.com" && u.port == -1 &&
            u.userInfo == null && u.fragment == null &&
            (u.rawPath == "/repos/$REPO" || u.rawPath.startsWith("/repos/$REPO/")) &&
            !u.rawPath.contains("%") && !u.rawPath.contains("..")
    }.getOrDefault(false)

    /** 下载安装包时 GitHub 会跳一次，只认它自己的资产域名，且跳转后不再带令牌。 */
    fun assetRedirect(value: String): Boolean = runCatching {
        val u = URI(value)
        u.scheme == "https" && u.port == -1 && u.userInfo == null && u.fragment == null &&
            u.host in setOf(
                "release-assets.githubusercontent.com",
                "objects.githubusercontent.com",
                "github-releases.githubusercontent.com",
            )
    }.getOrDefault(false)

    /** GitHub's official latest-release redirect, not a mirror or an HTML-derived URL. */
    fun latestTagFromLocation(value:String):String? = runCatching {
        val u=URI(value)
        if(u.scheme!="https" || u.host!="github.com" || u.port!=-1 || u.userInfo!=null || u.rawQuery!=null || u.fragment!=null)return@runCatching null
        val prefix="/$REPO/releases/tag/"
        if(!u.rawPath.startsWith(prefix))return@runCatching null
        val tag=u.rawPath.removePrefix(prefix)
        tag.takeIf{it.matches(Regex("v[0-9]{1,6}\\.[0-9]{1,2}"))}
    }.getOrNull()

    fun releaseAssetUrl(version:String,name:String):String {
        requireUpdate(version.matches(Regex("[0-9]{1,6}\\.[0-9]{1,2}")))
        requireUpdate(name in setOf(notesName(version),shaName(version),"$APK_PREFIX-v$version-universal.apk"))
        return "$PUBLIC_RELEASES/download/v$version/$name"
    }

    /** Never follow an untrusted asset URL even if a caller accidentally supplies one. */
    fun publicAssetUrl(value:String,name:String):Boolean = runCatching {
        val u=URI(value)
        if(u.scheme!="https" || u.host!="github.com" || u.port!=-1 || u.userInfo!=null || u.rawQuery!=null || u.fragment!=null)return@runCatching false
        val prefix="/$REPO/releases/download/"
        if(!u.rawPath.startsWith(prefix) || u.rawPath.contains('%'))return@runCatching false
        val segments=u.rawPath.removePrefix(prefix).split('/')
        if(segments.size!=2 || !segments[0].startsWith('v') || segments[1]!=name)return@runCatching false
        releaseAssetUrl(segments[0].removePrefix("v"),name)==value
    }.getOrDefault(false)

    /**
     * 发布时的安装包命名：`<前缀>-v0.3-arm64.apk` 或 `<前缀>-v0.3-universal.apk`。
     * 优先拿对应 CPU 架构的那个，没有再退到通用包。
     */
    fun apkNames(version: String, abis: List<String>): List<String> {
        val preferred = if (abis.firstOrNull() == "arm64-v8a") "arm64" else "universal"
        return listOf("$APK_PREFIX-v$version-$preferred.apk", "$APK_PREFIX-v$version-universal.apk").distinct()
    }

    fun notesName(version: String): String = "$APK_PREFIX-v$version-release-notes.json"

    fun shaName(version: String): String = "$APK_PREFIX-v$version-SHA256.txt"
}
