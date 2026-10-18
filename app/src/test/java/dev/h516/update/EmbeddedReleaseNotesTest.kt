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
}
