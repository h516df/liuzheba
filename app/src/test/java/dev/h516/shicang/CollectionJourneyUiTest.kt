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
            compose.onNodeWithContentDescription("添加内容").performClick()
            screenshot("refactor-collection")
            compose.onNodeWithText("从相册添加").performClick()
            assertEquals("",picked)
            assertNull(model.state.value.editor)
            assertTrue(runBlocking{model.store.load()}.drafts.isEmpty())
        }
    }

    @Test fun savingLinkStartsAnEditorDirectlyWithoutAnotherMenu()=fixture {model,_->
        compose.onNodeWithContentDescription("添加内容").performClick()
        compose.onNodeWithText("保存链接").performClick()
        idle{model.state.value.editor!=null}
        val draft=model.state.value.editor!!
        assertTrue(draft.fields.any{it.name=="来源" && it.type=="链接"})
        compose.onNodeWithText("新建档案").assertExists()
        compose.onNodeWithText("来源链接").assertExists()
    }

    @Test fun librarySortAndAdvancedSearchAreSeparateActions()=fixture {_,_->
        compose.onNodeWithText("按最近更新").performClick()
        compose.onNodeWithText("按名称排序").performClick()
        compose.onNodeWithText("按名称排序").assertExists()
        compose.onNodeWithText("高级搜索").performClick()
        compose.onNodeWithText("定制规则").assertExists()
        compose.onNodeWithText("收起高级搜索").performClick()
        compose.onNodeWithText("定制规则").assertDoesNotExist()
        compose.onNodeWithContentDescription("切换到列表视图").assertExists()
    }

    @Test fun detailAppendFinishesWithoutOpeningWholeArchiveEditor() {
        var picked:String?=null
        fixture(choose={picked=it}) {model,a->
            compose.onNodeWithText(a.name).performClick()
            screenshot("refactor-detail")
            compose.onNodeWithContentDescription("档案选项").performClick();compose.onNodeWithText("添加照片或视频").performClick()
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
        compose.onNodeWithContentDescription("添加内容").assertIsDisplayed().performClick()
        compose.onNodeWithText("新建档案").performScrollTo().assertIsDisplayed()
        screenshot("refactor-collection-font200")
        compose.onNodeWithText("取消").performScrollTo().performClick()
        compose.onNodeWithContentDescription("搜索档案").assertIsDisplayed()
    }
}
