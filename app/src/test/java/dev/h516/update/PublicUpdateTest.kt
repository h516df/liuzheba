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
}
