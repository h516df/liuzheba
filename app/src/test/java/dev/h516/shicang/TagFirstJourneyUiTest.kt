package dev.h516.shicang

import androidx.compose.ui.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Exercise the real native Compose surfaces with synthetic archives only. Not device picker evidence. */
class TagFirstJourneyUiTest:UnifiedNavigationFixture() {
    private fun label(model:AppModel,kind:String,name:String):Label {
        model.label(kind,name,false)
        idle{!model.state.value.busy && model.state.value.library.labels.any{it.name==name}}
        return model.state.value.library.labels.single{it.name==name}
    }

    @Test fun tagsAreOneTapInEditorAndPickerKeepsOptionalCategory()=fixture {model,a->
        val category=label(model,"category","旧分类")
        val tag=label(model,"tag","旅行")
        val second=label(model,"tag","风景")
        model.edit(a.copy(category=category.id,tags=listOf(tag.id)))
        idle{model.state.value.editor!=null}
        compose.onNodeWithContentDescription("档案标签 风景").performScrollTo().performClick()
        assertEquals(listOf(tag.id,second.id),model.state.value.editor!!.tags)
        assertEquals(category.id,model.state.value.editor!!.category)
        compose.onNodeWithContentDescription("档案标签 旅行").performClick()
        assertEquals(listOf(second.id),model.state.value.editor!!.tags)
        compose.onNodeWithText("全部标签").performClick()
        compose.onNodeWithText("标签 · 1").assertIsSelected()
        compose.onNodeWithText("搜索标签").assertIsDisplayed()
        screenshot("tag-first-picker")
        compose.onNodeWithContentDescription("清除标签选择").performClick()
        compose.onNodeWithText("标签 · 0").assertIsSelected()
        // The underlying editor also has a same-name quick chip; select the sheet's row.
        compose.onNode(hasText("风景") and !hasContentDescription("档案标签 风景")).performClick()
        compose.onNodeWithText("完成").performClick()
        assertEquals(category.id,model.state.value.editor!!.category)
        compose.onNodeWithText("分类（可选）：旧分类").performScrollTo().performClick()
        compose.onNodeWithContentDescription("分类（可选）").assertIsSelected()
        compose.onNodeWithText("旧分类").assertExists()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("保存").performClick()
        idle{!model.state.value.busy && model.state.value.editor==null}
        val saved=runBlocking{model.store.load()}.active.single()
        assertEquals(category.id,saved.category)
        assertEquals(listOf(second.id),saved.tags)
        assertEquals(a.photos,saved.photos)
    }

    @Test fun tagFilterWorksInOneTapAndSurvivesDetailReturn()=fixture {model,a->
        val tag=label(model,"tag","旅行")
        model.edit(a.copy(tags=listOf(tag.id)));model.saveEditor()
        idle{!model.state.value.busy && model.state.value.editor==null}
        model.edit(Archive(name="其他资料"));model.saveEditor()
        idle{!model.state.value.busy && model.state.value.library.active.size==2}
        // A created archive opens its detail. Return to the shelf before filtering.
        compose.onNodeWithContentDescription("返回").performClick()
        idle{compose.onAllNodesWithText("2 份档案").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("筛选标签 旅行").performClick()
        idle{compose.onAllNodesWithText("1 份档案").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("筛选标签 旅行").assertIsSelected()
        compose.onNodeWithText(a.name).performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("筛选标签 旅行").assertIsSelected()
        screenshot("tag-one-tap-filter")
        compose.onNodeWithContentDescription("筛选标签 旅行").performClick()
        idle{compose.onAllNodesWithText("2 份档案").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("筛选").performClick()
        compose.onNodeWithText("标签 · 0").assertIsSelected()
    }

    @Test fun mediaButtonsCallPickerDirectlyAndDoNotShowIntermediateDialog()=fixture {model,a->
        compose.onNodeWithText(a.name).performClick()
        compose.onNodeWithText("添加",useUnmergedTree=true).performClick()
        assertEquals(listOf(a.id),chooseRequests)
        assertNull("Adding from detail must not open the entire editor",model.state.value.editor)
        compose.onNodeWithText("档案详情").assertExists()
        compose.onNodeWithText("编辑").performClick()
        compose.onNodeWithText("相册").performScrollTo().performClick()
        assertEquals(listOf(a.id,a.id),chooseRequests)
        compose.onNodeWithText("添加照片与视频").assertDoesNotExist()
        compose.onNodeWithText("从相册选择").assertDoesNotExist()
        compose.onNodeWithText("拍照").performClick()
        assertEquals(listOf(a.id),cameraRequests)
        assertEquals(a.photos,model.state.value.editor!!.photos)
        screenshot("direct-media-actions")
    }

    @Test fun longPressSelectsFirstMediaAndBackStaysInAlbum()=fixture {_,a->
        compose.onNodeWithText(a.name).performClick()
        compose.onNodeWithText("查看全部").performClick()
        val first=compose.onNodeWithContentDescription("查看第1项照片或视频")
        first.performTouchInput{longClick()}
        compose.onNodeWithText("已选 1").assertExists()
        compose.onNodeWithText("保存 1 项").assertIsEnabled()
        screenshot("long-press-selection")
        first.performClick()
        compose.onNodeWithText("已选 0").assertExists()
        compose.onNodeWithText("保存 0 项").assertIsNotEnabled()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("多选").assertExists()
        first.assertExists().performClick()
        compose.onNodeWithText("1 / 3").assertExists()
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithText("多选").assertExists()
    }

    @Test fun tagAndMediaActionsRemainReachableAtLargeText()=fixture(font=2f) {model,a->
        label(model,"tag","常用标签")
        model.edit(a);idle{model.state.value.editor!=null}
        compose.onNodeWithText("相册").performScrollTo().assertIsDisplayed().performClick()
        screenshot("direct-media-font200")
        assertEquals(listOf(a.id),chooseRequests)
        compose.onNodeWithContentDescription("档案标签 常用标签").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("全部标签").performScrollTo().performClick()
        compose.onNodeWithText("标签 · 1").assertIsSelected()
        compose.onNodeWithText("完成").assertIsDisplayed()
        screenshot("tags-font200")
    }
    @Test fun firstTagCanBeCreatedWithoutSwitchingFromCategory()=fixture {model,a->
        model.edit(a);idle{model.state.value.editor!=null}
        compose.onNodeWithText("添加标签").performScrollTo().performClick()
        compose.onNodeWithText("搜索标签").performTextInput("新旅程")
        compose.onNodeWithText("新建标签「新旅程」").performClick()
        idle{!model.state.value.busy && model.state.value.library.labels.any{it.name=="新旅程"}}
        compose.onNodeWithText("标签 · 1").assertIsSelected()
        compose.onNodeWithText("完成").performClick()
        assertEquals(listOf(model.state.value.library.labels.single().id),model.state.value.editor!!.tags)
        assertEquals("",model.state.value.editor!!.category)
    }

    @Test fun selectionActionsWrapAtLargeTextWithoutLeavingAlbum()=fixture(font=2f) {_,a->
        compose.onNodeWithText(a.name).performClick()
        compose.onNodeWithText("查看全部").performScrollTo().performClick()
        compose.onNodeWithContentDescription("查看第1项照片或视频").performTouchInput{longClick()}
        compose.onNodeWithText("已选 1").assertIsDisplayed()
        compose.onNodeWithText("分享").assertIsDisplayed()
        compose.onNodeWithText("保存 1 项").assertIsDisplayed()
        screenshot("selection-font200")
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("多选").assertIsDisplayed()
    }

    @Test fun settingsLabelManagerAlsoDefaultsToTags()=fixture {_,_->
        compose.onNodeWithTag("nav-settings").performClick()
        compose.onNodeWithText("标签与分类").performScrollTo().performClick()
        compose.onNodeWithText("标签").assertIsSelected()
        compose.onNodeWithText("搜索标签").assertExists()
        compose.onNodeWithText("还没有标签").assertExists()
    }

}
