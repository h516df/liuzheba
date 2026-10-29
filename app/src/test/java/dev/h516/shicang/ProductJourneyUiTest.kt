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
        // Card semantics may merge/replace the title; keep targeting its visible
        // bounds and injecting a real touch rather than bypassing the UI action.
        compose.onNodeWithText(a.name,useUnmergedTree=true).assertIsDisplayed().performClick()
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

    @Test fun discoveryFeedReturnsToSameMediaAfterReadingAndEditing()=fixture {model,a->
        model.edit(a);model.changeArchiveText("这是一份照片、视频与文字的档案。");model.saveEditor()
        idle{model.state.value.editor==null && !model.state.value.busy}
        compose.onNodeWithTag("nav-frame").performClick()
        screenshot("product-discovery")
        val before=mediaNumber(3)
        compose.onNodeWithText("阅读文字").performClick()
        idle { compose.onAllNodesWithTag("note-reader").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("note-reader").assertTextContains("照片、视频与文字",substring=true)
        compose.onNodeWithTag("note-reader-back").performClick()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("编辑档案").performClick()
        idle{model.state.value.editor!=null}
        compose.onNodeWithTag("archive-body").assertTextContains("照片、视频与文字",substring=true)
        screenshot("product-editing")
        compose.onNodeWithContentDescription("返回").performClick()
        idle{model.state.value.editor==null}
        assertEquals(before,mediaNumber(3))
        compose.onNodeWithTag("nav-frame").assertIsSelected()
        compose.onNodeWithTag("nav-library").performClick()
        screenshot("product-library")
    }
    @Test fun archiveNoteIndexUsesUnifiedEditorAndKeepsItsMedia()=fixture {model,a->
        model.edit(a);model.changeArchiveText("从笔记索引继续写同一份档案");model.saveEditor()
        idle{model.state.value.editor==null && !model.state.value.busy}
        compose.onNodeWithText("笔记").performClick()
        compose.onNodeWithText("从笔记索引继续写同一份档案").performClick()
        compose.onNodeWithText("编辑档案").performClick()
        idle{model.state.value.editor!=null}
        assertNull(model.noteEditor.state.value)
        assertEquals(a.photos,model.state.value.editor!!.photos)
        compose.onNodeWithTag("archive-body").performTextReplacement("从笔记索引继续写同一份档案，保留全部媒体。")
        compose.onNodeWithText("保存").performClick()
        idle{model.state.value.editor==null && !model.state.value.busy}
        val saved=runBlocking{model.store.load()}
        assertEquals(a.photos,saved.active.single().photos)
        assertTrue(saved.archiveNote(a.id)!!.body.endsWith("保留全部媒体。"))
    }

    @Test fun missingCoverHasAnExplicitStateWithoutChangingTheArchive()=fixture {model,a->
        val before=runBlocking{model.store.load()}
        model.photoFile(a.photos.first()).delete()
        compose.onNodeWithTag("nav-frame").performClick()
        idle{compose.onAllNodesWithText("原件不可用").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText(a.name).performClick()
        compose.onNodeWithText("编辑").assertIsDisplayed()
        assertEquals(before,runBlocking{model.store.load()})
        screenshot("product-missing-original")
    }

    @Test fun discoverSwipeSwitchesArchivesAndKeepsPositionAcrossTabs()=fixture {model,a->
        val other=Archive(name="另一份合成收藏",photos=listOf(a.photos.last()))
        runBlocking{model.store.save(other)}
        model.appearance(model.state.value.appearance)
        idle{!model.state.value.busy && model.state.value.library.active.size==2}
        compose.onNodeWithTag("nav-frame").performClick()
        val first=if(compose.onAllNodesWithContentDescription("打开档案：${a.name}").fetchSemanticsNodes().isNotEmpty())a.name else other.name
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("下一份").performScrollTo().performClick()
        val second=if(first==a.name)other.name else a.name
        idle{compose.onAllNodesWithContentDescription("打开档案：$second").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("nav-library").performClick()
        compose.onNodeWithTag("nav-library").assertIsSelected()
        compose.onNodeWithTag("nav-frame").performClick()
        compose.onNodeWithContentDescription("打开档案：$second").assertExists()
    }

    @Test fun allMediaBackReturnsToTheExactViewerRatherThanLeavingIt()=fixture {_,a->
        compose.onNodeWithText(a.name).performClick()
        compose.onNodeWithTag("archive-media-${a.photos[1].id}").performScrollTo().performClick()
        assertEquals(2,mediaNumber(3))
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("全部媒体").performClick()
        compose.onNodeWithContentDescription("查看第2项照片或视频").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        assertEquals(2,mediaNumber(3))
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithText("档案详情").assertExists()
    }

}
