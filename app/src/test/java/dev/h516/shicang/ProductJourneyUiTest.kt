package dev.h516.shicang

import androidx.compose.ui.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.robolectric.annotation.Config

/** Product paths: real Compose actions and persistent store, synthetic files only. */
@Config(qualifiers="zh-rCN-w412dp-h915dp-xhdpi")
class ProductJourneyUiTest:UnifiedNavigationFixture() {
    @Test fun undoRemovingAllMediaRestoresOrderCoverAndPreservesText()=fixture {model,a->
        compose.onNodeWithText(a.name).performClick()
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithTag("archive-body").performTextInput("撤销移除期间写下的文字")
        compose.onNodeWithTag("editor-media-grid").performScrollTo()
        compose.onNodeWithText("多选").performClick()
        compose.onNodeWithText("全选").performClick()
        compose.onNodeWithText("移除 3 项").performScrollTo().performClick()
        assertTrue(model.state.value.editor!!.photos.isEmpty())
        compose.onNodeWithText("撤销移除").performScrollTo().performClick()
        assertEquals(a.photos,model.state.value.editor!!.photos)
        assertEquals(a.cover,model.state.value.editor!!.cover)
        compose.onNodeWithText("保存").performClick()
        idle{model.state.value.editor==null && !model.state.value.busy}
        val saved=runBlocking{model.store.load()}
        assertEquals(a.photos,saved.active.single().photos)
        assertEquals("撤销移除期间写下的文字",saved.archiveNote(a.id)!!.body)
        assertTrue(saved.photoTrash.isEmpty())
        screenshot("product-reading")
    }

    @Test fun discoveryViewerReturnsToSameStoryAfterReadingAndEditing()=fixture {model,a->
        model.edit(a);model.changeArchiveText("这是一份照片、视频与文字的档案。");model.saveEditor()
        idle{model.state.value.editor==null && !model.state.value.busy}
        compose.onNodeWithTag("nav-frame").performClick()
        screenshot("product-discovery")
        val before=compose.onNodeWithText(a.name).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("浏览${a.name}的照片与视频").performClick()
        assertEquals(1,mediaNumber(3))
        compose.onNodeWithText("阅读文字").performClick()
        compose.onNodeWithTag("note-reader").assertTextContains("照片、视频与文字",substring=true)
        compose.onNodeWithTag("note-reader-back").performClick()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("编辑档案").performClick()
        idle{model.state.value.editor!=null}
        compose.onNodeWithTag("archive-body").assertTextContains("照片、视频与文字",substring=true)
        screenshot("product-editing")
        compose.onNodeWithContentDescription("返回").performClick()
        idle{model.state.value.editor==null}
        assertEquals(1,mediaNumber(3))
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithTag("nav-frame").assertIsSelected()
        assertEquals(before,compose.onNodeWithText(a.name).fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithTag("nav-library").performClick()
        screenshot("product-library")
    }
}
