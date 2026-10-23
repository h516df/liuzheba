package dev.h516.shicang

import androidx.compose.ui.test.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*

/** Synthetic real-app UI flows; host test, not Android IME/device evidence. */
class LabelEditorWorkflowUiTest:UnifiedNavigationFixture() {
    @Test fun deletingLabelInsideDraftDebouncePersistsLatestOtherFields()=fixture {model,a->
        model.label("tag","临时标签",false);idle{!model.state.value.busy && model.state.value.library.labels.isNotEmpty()}
        val tag=model.state.value.library.labels.single()
        compose.runOnIdle {
            model.edit(a.copy(tags=listOf(tag.id)))
            model.change(model.state.value.editor!!.copy(name="防抖内新名称"))
            model.deleteLabel(tag)
        }
        idle{!model.state.value.busy && model.state.value.library.labels.isEmpty()}
        val draft=runBlocking{model.store.load()}.drafts.single()
        assertEquals("防抖内新名称",draft.name)
        assertTrue(draft.tags.isEmpty())
        assertEquals(a.photos,draft.photos)
    }

    @Test fun globalDeleteFromEditorPreservesLatestDraftAndOriginalMedia()=fixture {model,a->
        model.label("tag","旅行",false);idle{!model.state.value.busy && model.state.value.library.labels.any{it.name=="旅行"}}
        val tag=model.state.value.library.labels.single{it.name=="旅行"}
        model.edit(a.copy(tags=listOf(tag.id)));model.saveEditor()
        idle{!model.state.value.busy && model.state.value.editor==null}
        model.edit(model.state.value.library.active.single());idle{model.state.value.editor!=null}
        model.change(model.state.value.editor!!.copy(name="尚未等待防抖的修改"))
        screenshot("editor-regular")
        compose.onNodeWithText("分类与标签").performScrollTo().performClick()
        compose.onNodeWithText("标签 · 1").performClick()
        compose.onNodeWithText("管理标签（改名或全局删除）").performClick()
        compose.onNodeWithText("管理").performClick()
        compose.onNodeWithText("全局删除标签").performClick()
        compose.onNodeWithText("1 份档案（含回收站）和 1 份草稿",substring=true).assertExists()
        screenshot("label-delete-confirmation")
        compose.onNodeWithText("确认").performClick()
        idle{!model.state.value.busy && model.state.value.library.labels.none{it.id==tag.id}}
        assertEquals("尚未等待防抖的修改",model.state.value.editor!!.name)
        assertTrue(model.state.value.editor!!.tags.isEmpty())
        val saved=runBlocking{model.store.load()}
        assertEquals("尚未等待防抖的修改",saved.drafts.single().name)
        assertTrue(saved.drafts.single().tags.isEmpty())
        assertTrue(saved.active.single().tags.isEmpty())
        assertEquals(a.photos,saved.active.single().photos)
        a.photos.forEach{assertEquals(it.sha256,sha(model.photoFile(it)))}
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithContentDescription("编辑选项").assertExists()
    }

    @Test fun localRemovalDoesNotDeleteDefinitionAndBackRetainsDraft()=fixture {model,a->
        model.label("tag","旅行",false);idle{!model.state.value.busy && model.state.value.library.labels.isNotEmpty()}
        val tag=model.state.value.library.labels.single()
        model.edit(a.copy(tags=listOf(tag.id)));idle{model.state.value.editor!=null}
        compose.onNodeWithText("分类与标签").performScrollTo().performClick()
        compose.onNodeWithContentDescription("移除标签 旅行").performClick()
        compose.onNodeWithText("完成").performClick()
        assertTrue(model.state.value.editor!!.tags.isEmpty())
        assertTrue(model.state.value.library.labels.any{it.id==tag.id})
        compose.onNodeWithContentDescription("返回").performClick();idle{model.state.value.editor==null && !model.state.value.busy}
        assertTrue(runBlocking{model.store.load()}.drafts.single().tags.isEmpty())
        assertEquals(a.photos,runBlocking{model.store.load()}.active.single().photos)
    }

    @Test fun narrowLargeTextShowsDraftAndExplicitDiscardWithoutClearingSavedFields()=fixture(font=2f) {model,a->
        model.edit(a);idle{model.state.value.editor!=null}
        compose.onNodeWithContentDescription("编辑选项").assertIsDisplayed()
        compose.onNodeWithText("保存").assertIsDisplayed()
        compose.onNodeWithText("返回保留草稿",substring=true).assertIsDisplayed()
        screenshot("editor-large-text")
        compose.onNodeWithText("收起可选资料").performScrollTo().performClick()
        compose.onNodeWithText("旧备注").assertDoesNotExist()
        compose.onNodeWithText("添加可选资料（作者、来源、字段）").performScrollTo().performClick()
        compose.onNodeWithTag("archive-editor-fields").performScrollToNode(hasText("旧备注"))
        compose.onNodeWithText("旧备注").assertExists()
        compose.onNodeWithContentDescription("编辑选项").performClick();compose.onNodeWithText("放弃修改").performClick();compose.onNodeWithText("确认").performClick()
        idle{model.state.value.editor==null && !model.state.value.busy}
        assertEquals(a.fields,runBlocking{model.store.load()}.active.single().fields)
    }
}
