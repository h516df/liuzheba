package dev.h516.shicang

import android.net.Uri
import androidx.compose.ui.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Product entry points on the isolated host fixture; no system picker or device claim. */
class CollectionJourneyUiTest:UnifiedNavigationFixture() {
    @Test fun contentDestinationsAreVisibleAndKeepArchiveSearch()=fixture {model,_->
        compose.onNodeWithContentDescription("搜索档案").performTextInput("合成")
        compose.onNodeWithText("笔记").performClick()
        compose.onNodeWithText("还没有笔记").assertExists()
        compose.onNodeWithText("作者").performClick()
        compose.onNodeWithText("合成作者").assertExists()
        compose.onNodeWithTag("index-archives").performClick()
        compose.onNodeWithContentDescription("搜索档案").assertTextContains("合成")
        assertNull(model.state.value.editor)
        screenshot("refactor-library")
    }

    @Test fun collectingPhotosStartsPickerWithoutCreatingBlankDraft() {
        var picked:String?=null
        fixture(choose={picked=it}) {model,_->
            compose.onNodeWithContentDescription("收录").performClick()
            screenshot("refactor-collection")
            compose.onNodeWithText("照片或视频").performClick()
            assertEquals("",picked)
            assertNull(model.state.value.editor)
            assertTrue(runBlocking{model.store.load()}.drafts.isEmpty())
        }
    }

    @Test fun detailAppendFinishesWithoutOpeningWholeArchiveEditor() {
        var picked:String?=null
        fixture(choose={picked=it}) {model,a->
            compose.onNodeWithText(a.name).performClick()
            screenshot("refactor-detail")
            compose.onNodeWithText("添加").performClick()
            assertEquals(a.id,picked)
            assertNull(model.state.value.editor)
            compose.runOnIdle{model.importPhotos(a.id,listOf(Uri.fromFile(model.photoFile(a.photos.first()))))}
            idle{!model.state.value.busy && model.state.value.library.active.single().photos.size==4}
            assertNull(model.state.value.editor)
            val saved=runBlocking{model.store.load()}
            assertEquals(4,saved.active.single().photos.size)
            assertTrue(saved.drafts.isEmpty())
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithContentDescription("搜索档案").assertExists()
        }
    }

    @Test fun collectionRemainsReachableAtTwoHundredPercent()=fixture(font=2f) {_,_->
        compose.onNodeWithContentDescription("收录").assertIsDisplayed().performClick()
        compose.onNodeWithText("文字档案").performScrollTo().assertIsDisplayed()
        screenshot("refactor-collection-font200")
        compose.onNodeWithText("取消").performScrollTo().performClick()
        compose.onNodeWithContentDescription("搜索档案").assertIsDisplayed()
    }
}
