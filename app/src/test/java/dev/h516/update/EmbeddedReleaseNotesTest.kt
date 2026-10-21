package dev.h516.update

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedReleaseNotesTest {
    @Test fun shippedNotesCanBeReadByTheUpdater() {
        val notes = ReleaseNotes.decode(File("src/main/assets/release-notes.json").readText())
        val build = File("build.gradle.kts").readText()
        assertEquals(Regex("versionName = \"([^\"]+)\"").find(build)!!.groupValues[1], notes.version)
        assertEquals(Regex("versionCode = (\\d+)").find(build)!!.groupValues[1].toInt(), notes.code)
        assertTrue(notes.sections.isNotEmpty())
    }

    @Test fun nextReleaseKeepsUpgradeIdentityAndCompatibleAssetNames() {
        val notes = ReleaseNotes.decode(File("src/main/assets/release-notes.json").readText())
        assertEquals("0.17", notes.version)
        assertEquals(18, notes.code)
        assertTrue("The candidate must upgrade the published 0.16/code 17", notes.code > 17)
        assertEquals("dev.h516.shicang", UpdateConfig.PACKAGE)
        assertTrue(UpdateConfig.apkNames(notes.version, listOf("arm64-v8a")).contains("shicang-v0.17-universal.apk"))
        assertEquals("shicang-v0.17-release-notes.json", UpdateConfig.notesName(notes.version))
        assertEquals("shicang-v0.17-SHA256.txt", UpdateConfig.shaName(notes.version))
    }
}
