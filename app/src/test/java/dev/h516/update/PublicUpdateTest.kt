package dev.h516.update

import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayInputStream
import java.io.File

class PublicUpdateTest {
    private class Response(val code: Int, val body: String = "", val location: String? = null) : HttpURLConnection(URL("https://api.github.com")) {
        override fun connect() {}
        override fun disconnect() {}
        override fun usingProxy() = false
        override fun getResponseCode() = code
        override fun getHeaderField(name: String) = if (name == "Location") location else null
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray())
    }

    @Test fun publicJsonAndRedirectedAssetNeverSendCredentials() {
        val api = Response(200, "[]")
        assertEquals("[]", UpdateHttp { api }.json("/releases"))
        assertNull(api.getRequestProperty("Authorization"))
        val redirect = Response(302, location = "https://release-assets.githubusercontent.com/asset")
        val asset = Response(200, "public notes")
        val requests = mutableListOf<String>()
        val http = UpdateHttp { url -> requests.add(url); if (requests.size == 1) redirect else asset }
        assertEquals("public notes", http.assetText(123))
        assertEquals(2, requests.size)
        assertNull(redirect.getRequestProperty("Authorization"))
        assertNull(asset.getRequestProperty("Authorization"))
    }

    @Test fun foreignRedirectIsRejectedBeforeConnecting() {
        var calls = 0
        val http = UpdateHttp { calls++; Response(302, location = "https://example.org/package.apk") }
        assertThrows(UpdateFailure::class.java) { http.assetText(123) }
        assertEquals(1, calls)
    }

    @Test fun truncatedDownloadCannotBecomeInstallCandidate() {
        val file = File.createTempFile("shicang-update-", ".apk")
        try {
            val http = UpdateHttp { Response(200, "short") }
            assertThrows(UpdateFailure::class.java) { http.downloadApk(123, 100, file) {} }
        } finally { file.delete() }
    }

    @Test fun onlyPublishedStableVersionsAreSelected() {
        fun release(tag: String, flags: String) = """{"tag_name":"$tag",$flags"assets":[{"id":1,"size":12,"name":"shicang-$tag-release-notes.json"}]}"""
        val releases = "[" + listOf(release("v0.5", ""), release("v0.6", "\"draft\":true,"), release("v0.7", "\"prerelease\":true,")).joinToString(",") + "]"
        assertEquals("0.5", UpdateCatalog.newest(UpdateCatalog.parse(releases))?.version)
    }
    @Test fun renamedRepositoryUsesOnlyNewOfficialApiWithoutChangingIdentity(){
        assertEquals("h516df/liuzheba",UpdateConfig.REPO)
        assertEquals("dev.h516.shicang",UpdateConfig.PACKAGE)
        assertEquals("shicang",UpdateConfig.APK_PREFIX)
        assertTrue(UpdateConfig.apiUrl("https://api.github.com/repos/h516df/liuzheba/releases"))
        assertFalse(UpdateConfig.apiUrl("https://api.github.com/repos/h516df/shicang/releases"))
        assertFalse(UpdateConfig.apiUrl("https://api.github.com/repos/h516df/liuzheba-other/releases"))
        assertFalse(UpdateConfig.apiUrl("https://api.github.com/repos/attacker/liuzheba/releases"))
        assertFalse(UpdateConfig.apiUrl("https://api.github.com/repos/h516df/liuzheba/%2e%2e/releases"))
    }
    @Test fun officialWebFallbackOnlyTrustsExactReleaseAndAssetPaths() {
        assertEquals("v0.20",UpdateConfig.latestTagFromLocation("https://github.com/h516df/liuzheba/releases/tag/v0.20"))
        listOf("https://github.com/attacker/liuzheba/releases/tag/v0.20",
            "https://github.com/h516df/liuzheba/releases/tag/v0.20?from=other",
            "https://github.com/h516df/liuzheba/releases/tag/v0.20/extra",
            "http://github.com/h516df/liuzheba/releases/tag/v0.20",
            "https://github.com@evil.test/h516df/liuzheba/releases/tag/v0.20").forEach {
            assertNull(it,UpdateConfig.latestTagFromLocation(it))
        }
        val asset=UpdateCatalog.fromOfficialLatestTag("v0.20").assets.single{it.name.endsWith("universal.apk")}
        assertEquals(0L,asset.size)
        assertTrue(UpdateConfig.publicAssetUrl(asset.publicUrl!!,asset.name))
        assertFalse(UpdateConfig.publicAssetUrl(asset.publicUrl+"?token=foreign",asset.name))
        assertFalse(UpdateConfig.publicAssetUrl(asset.publicUrl.replace("github.com","example.org"),asset.name))
        assertFalse(UpdateConfig.publicAssetUrl(asset.publicUrl.replace("/v0.20/","/v0.19/"),asset.name))
    }

    @Test fun officialLatestWebRequestDoesNotRequireCredentialsOrApiHeaders() {
        val redirect=Response(302,location="https://github.com/h516df/liuzheba/releases/tag/v0.20")
        val requests=mutableListOf<String>()
        assertEquals("v0.20",UpdateHttp { url -> requests+=url;redirect }.officialLatestTag())
        assertEquals(listOf("${UpdateConfig.PUBLIC_RELEASES}/latest"),requests)
        assertEquals("text/html",redirect.getRequestProperty("Accept"))
        assertNull(redirect.getRequestProperty("Authorization"))
        assertNull(redirect.getRequestProperty("X-GitHub-Api-Version"))
    }

    @Test fun retryableApiFailuresAreExplicitAndBadMetadataNeverFallsBack() {
        listOf(401,403,429,500,503).forEach { code ->
            assertTrue(canUsePublicReleaseFallback(UpdateHttpStatusFailure(code,"synthetic")))
        }
        assertFalse(canUsePublicReleaseFallback(UpdateHttpStatusFailure(404,"synthetic")))
        assertFalse(canUsePublicReleaseFallback(UpdateFailure("malformed metadata")))
        assertFalse(canUsePublicReleaseFallback(javax.net.ssl.SSLHandshakeException("untrusted certificate")))
        assertTrue(canUsePublicReleaseFallback(java.net.SocketTimeoutException("synthetic timeout")))
    }

    @Test fun officialFallbackChecksRedirectAndBoundsUnspecifiedApkSize() {
        val url=UpdateConfig.releaseAssetUrl("0.20","shicang-v0.20-universal.apk")
        val asset=UpdateCatalog.fromOfficialLatestTag("v0.20").assets.single{it.publicUrl==url}
        val requests=mutableListOf<String>()
        val http=UpdateHttp { input ->
            requests+=input
            if(input==url)Response(302,location="https://release-assets.githubusercontent.com/public-test")
            else Response(200,"synthetic-apk")
        }
        val file=File.createTempFile("official-fallback-",".apk")
        try {
            http.downloadApk(asset,asset.size,file){}
            assertEquals("synthetic-apk",file.readText())
            assertEquals(listOf(url,"https://release-assets.githubusercontent.com/public-test"),requests)
        }finally{file.delete()}
    }
}
