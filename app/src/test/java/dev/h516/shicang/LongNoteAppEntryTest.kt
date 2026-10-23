package dev.h516.shicang

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rCN-w360dp-h800dp-xhdpi")
@OptIn(ExperimentalCoroutinesApi::class)
class LongNoteAppEntryTest {
    @get:Rule val compose=createComposeRule()
    @Before fun main(){Dispatchers.setMain(android.os.Handler(android.os.Looper.getMainLooper()).asCoroutineDispatcher().immediate)}
    @After fun reset(){Dispatchers.resetMain()}
    private fun idle(check:()->Boolean){compose.waitUntil(10000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();check()}}
    @Test fun actualArchiveEntryDraftReturnSaveSearchAndReadAgain() {
        val app=RuntimeEnvironment.getApplication();app.getSharedPreferences("device",0).edit().clear().putString("generation",uid()).commit()
        val archive=Archive(name="合成记录",fields=listOf(Field(name="旧备注",value="不能改变")))
        runBlocking{val store=Store(app);store.save(archive);store.close()}
        val model=AppModel(app);val models=androidx.lifecycle.ViewModelStore().apply{put("fixture",model)}
        try {
            compose.setContent{ShicangApp(model,true,{},{},{},{},{},{})};idle{model.state.value.ready}
            compose.onNodeWithText("合成记录").performClick()
            compose.onNodeWithText("写文字").performScrollTo().performClick();idle{model.state.value.editor!=null}
            compose.onNodeWithTag("archive-body").performTextInput("合成雪夜\n第二段观察")
            compose.onNodeWithContentDescription("返回").performClick();idle{model.state.value.editor==null}
            assertEquals("合成雪夜\n第二段观察",runBlocking{model.store.load()}.noteDrafts.single().body)
            compose.onNodeWithText("写文字").performScrollTo().performClick();idle{model.state.value.editor!=null}
            compose.onNodeWithTag("archive-body").assertTextContains("第二段观察",substring=true)
            compose.onNodeWithText("保存").performClick();idle{model.state.value.editor==null && model.state.value.library.notes.isNotEmpty()}
            val saved=runBlocking{model.store.load()};assertTrue(saved.noteDrafts.isEmpty());assertEquals("不能改变",saved.active.single().fields.single().value);assertEquals(archive.id,saved.find("雪夜").single().id)
            compose.onNodeWithText("展开全文").performScrollTo().performClick()
            compose.onNodeWithTag("archive-text").assertTextContains("第二段观察",substring=true)
            assertNull(model.noteEditor.state.value)
            compose.onNodeWithText("编辑文字").performClick();idle{model.state.value.editor!=null}
            compose.onNodeWithTag("archive-body").assertTextContains("第二段观察",substring=true)
        }finally {models.clear();idle{model.resourcesClosed}}
    }
}
