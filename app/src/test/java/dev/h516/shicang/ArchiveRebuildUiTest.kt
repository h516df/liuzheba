package dev.h516.shicang

import androidx.compose.ui.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

/** Real Compose entry points; synthetic images and metadata, no codec/phone claim. */
class ArchiveRebuildUiTest:UnifiedNavigationFixture() {
    @Test fun unifiedEditorSavesTextMixedMediaOrderAndCover()=fixture {model,a->
        compose.onNodeWithText(a.name).performClick()
        compose.onNodeWithText("写文字").assertExists()
        compose.onNodeWithText("编辑",useUnmergedTree=true).performClick()
        idle{model.state.value.editor!=null}
        compose.onNodeWithTag("archive-body").performTextInput("这是档案的正文。\n图片与视频一起留存。")
        val mixed=a.copy(photos=a.photos.mapIndexed{i,p->if(i==1)p.copy(mime="video/mp4",duration=123000)else p})
        model.change(mixed)
        compose.onNodeWithTag("editor-media-grid").performScrollTo()
        compose.onNodeWithTag("editor-media-${a.photos[2].id}").performClick()
        compose.onNodeWithText("设为封面").performClick()
        compose.onNodeWithText("前移").performClick()
        assertEquals(listOf(a.photos[0].id,a.photos[2].id,a.photos[1].id),model.state.value.editor!!.photos.map{it.id})
        screenshot("rebuild-editor-mixed")
        compose.onNodeWithText("保存",useUnmergedTree=true).performClick()
        idle{!model.state.value.busy && model.state.value.editor==null}
        val saved=runBlocking{model.store.load()}
        assertEquals("这是档案的正文。\n图片与视频一起留存。",saved.archiveNote(a.id)!!.body)
        assertEquals(a.photos[2].id,saved.active.single().cover)
        assertTrue(saved.noteDrafts.isEmpty());assertTrue(saved.drafts.isEmpty())
        compose.onNodeWithTag("archive-text").assertTextContains("图片与视频一起留存",substring=true)
        screenshot("rebuild-archive-reading")
        compose.onNodeWithTag("archive-filter-VIDEOS").performScrollTo().performClick()
        compose.onNodeWithTag("archive-media-${a.photos[1].id}").assertExists()
        compose.onNodeWithTag("archive-media-${a.photos[0].id}").assertDoesNotExist()
    }

    @Test fun immersiveTextReaderDoesNotEditAndReturnsToSameMedia()=fixture {model,a->
        model.edit(a);model.changeArchiveText("沉浸浏览中的完整文字\n"+"这是合成记录。".repeat(80));model.saveEditor()
        idle{!model.state.value.busy && model.state.value.editor==null}
        compose.onNodeWithText(a.name).performClick()
        compose.onNodeWithTag("archive-media-${a.photos[1].id}").performScrollTo().performClick()
        assertEquals(2,mediaNumber(3))
        compose.onNodeWithText("阅读文字").performClick()
        assertNull(model.noteEditor.state.value)
        compose.onNodeWithTag("note-reader").assertTextContains("沉浸浏览中的完整文字",substring=true)
        screenshot("rebuild-immersive-reader")
        compose.onNodeWithTag("note-reader-back").performClick()
        assertEquals(2,mediaNumber(3))
        assertTrue(runBlocking{model.store.load()}.noteDrafts.isEmpty())
        screenshot("rebuild-immersive-return")
    }

    @Test fun plainTextArchiveRemainsReadableAtLargeFont()=fixture(font=2f) {model,a->
        model.edit(a);model.change(a.copy(photos=emptyList()));model.changeArchiveText("一份没有照片和视频的纯文字档案。")
        model.saveEditor();idle{!model.state.value.busy && model.state.value.editor==null}
        compose.onNodeWithText(a.name).performClick()
        compose.onNodeWithTag("archive-text").assertTextContains("纯文字档案",substring=true)
        compose.onNodeWithText("编辑文字").performScrollTo().assertIsDisplayed()
        screenshot("rebuild-text-font200")
        val library=runBlocking{model.store.load()};assertEquals(3,library.photoTrash.size)
        assertTrue(a.photos.all{model.photoFile(it).isFile})
    }
}
