package dev.h516.shicang

import android.app.Application
import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class SharedMediaIntentTest {
    @Test fun mixedWildcardShareKeepsImageAndVideoInOriginalOrderWithoutDuplicates() {
        val image=Uri.parse("content://synthetic/image");val video=Uri.parse("content://synthetic/video")
        val intent=Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM,arrayListOf(image,video))
        intent.clipData=ClipData(ClipDescription("mixed",arrayOf("image/png","video/mp4")),ClipData.Item(image)).apply{addItem(ClipData.Item(video))}
        assertEquals(listOf(image,video),sharedMediaUris(intent))
    }
    @Test fun wildcardSelectionCannotExposeFilePathsAndStaysWithinImportLimit() {
        val uris=(0..105).map{Uri.parse("content://synthetic/$it")}
        val intent=Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM,
            ArrayList(listOf(Uri.parse("file:///synthetic/private"))+uris))
        assertEquals(uris.take(100),sharedMediaUris(intent))
    }
    @Test fun textAndUnsupportedDocumentsDoNotBecomeMediaImports() {
        val uri=Uri.parse("content://synthetic/document")
        for(type in listOf("text/plain","application/pdf"))assertTrue(sharedMediaUris(Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM,uri)).isEmpty())
        assertTrue(sharedMediaUris(Intent(Intent.ACTION_VIEW).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri)).isEmpty())
        assertEquals(listOf(uri),sharedMediaUris(Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM,uri)))
    }
}
