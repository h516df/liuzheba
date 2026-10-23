package dev.h516.shicang

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.SQLiteMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EditorPersistenceTest {
    private val scheduler=TestCoroutineScheduler()
    private val owners=ViewModelStore()
    private lateinit var app:Application
    private lateinit var model:AppModel
    @Before fun start(){
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device",0).edit().putString("generation",uid()).commit()
        model=AppModel(app);owners.put("editor",model)
        await{model.state.value.ready}
    }
    @After fun stop(){owners.clear();await{model.resourcesClosed};Dispatchers.resetMain()}
    private suspend fun reopenedLibrary():Library {val store=Store(app);return try{store.load()}finally{store.close()}}
    private fun await(condition:()->Boolean){
        val end=System.nanoTime()+10_000_000_000L
        while(System.nanoTime()<end){scheduler.runCurrent();if(condition())return;Thread.sleep(5)}
        fail("Timed out: ${model.state.value}")
    }
    private fun initial():Archive{
        val a=Archive(name="旧标题",fields=listOf(Field(name="备注",value="旧正文")))
        runBlocking{model.store.save(a)}
        model.edit(a);scheduler.runCurrent();scheduler.advanceTimeBy(200)
        await{model.state.value.library.drafts.any{it.id==a.id}}
        return a
    }
    @Test fun immediateReturnAndReopenKeepsLatestInput(){
        val a=initial();val latest=a.copy(name="刚输入的新标题")
        model.change(latest);model.leaveEditor();model.edit(a)
        await{!model.state.value.busy}
        // A return may finish asynchronously; reopening must never replace the pending edit.
        if(model.state.value.editor==null)model.edit(a)
        assertEquals(latest.name,model.state.value.editor!!.name)
        scheduler.advanceTimeBy(200);await{model.state.value.draftStatus=="草稿已保存"}
        assertEquals(latest.name,runBlocking{reopenedLibrary()}.drafts.single().name)
    }
    @Test fun explicitSaveCommitsLatestAndRemovesDraft(){
        val a=initial();model.change(a.copy(name="已提交"));model.saveEditor()
        await{!model.state.value.busy && model.state.value.editor==null}
        val reopened=runBlocking{reopenedLibrary()}
        assertEquals("已提交",reopened.active.single().name);assertTrue(reopened.drafts.isEmpty())
    }
    @Test fun explicitDiscardNeverSavesPendingChanges(){
        val a=initial();model.change(a.copy(name="放弃这段"));model.discardEditor()
        await{!model.state.value.busy && model.state.value.editor==null}
        scheduler.advanceTimeBy(500);scheduler.runCurrent()
        val reopened=runBlocking{reopenedLibrary()}
        assertEquals("旧标题",reopened.active.single().name);assertTrue(reopened.drafts.isEmpty())
    }
    @Test fun immediateLabelCreationFlushesPendingBodyAndDeletionAllowsSave(){
        val a=initial();model.changeArchiveText("尚未到自动保存窗口的正文")
        model.label("tag","合成标签");await{!model.state.value.busy}
        val draft=runBlocking{reopenedLibrary()}
        assertEquals("尚未到自动保存窗口的正文",draft.noteDrafts.single().body)
        model.saveEditor();await{!model.state.value.busy && model.state.value.editor==null}
        val saved=model.state.value.library.active.single();model.edit(saved)
        model.deleteLabel(model.state.value.library.labels.single());await{!model.state.value.busy}
        model.saveEditor();await{!model.state.value.busy && model.state.value.editor==null}
        val reopened=runBlocking{reopenedLibrary()}
        assertEquals("尚未到自动保存窗口的正文",reopened.notes.single().body)
        assertTrue(reopened.active.single().tags.isEmpty())
    }
    @Test fun failedReturnKeepsEditorAndCanRetry(){
        val a=initial();model.change(a.copy(name="失败后仍保留"))
        val generation=app.getSharedPreferences("device",0).getString("generation",null)
        val db=SQLiteDatabase.openDatabase(File(app.filesDir,"libraries/$generation/library.db").path,null,SQLiteDatabase.OPEN_READWRITE)
        try{
            db.execSQL("CREATE TRIGGER fail_draft BEFORE INSERT ON objects WHEN NEW.kind='draft' BEGIN SELECT RAISE(ABORT,'fixture write failure'); END")
            model.leaveEditor();await{!model.state.value.busy}
            assertEquals("失败后仍保留",model.state.value.editor?.name)
            assertEquals("旧标题",runBlocking{reopenedLibrary()}.drafts.single().name)
            db.execSQL("DROP TRIGGER fail_draft")
            model.leaveEditor();await{!model.state.value.busy && model.state.value.editor==null}
            assertEquals("失败后仍保留",runBlocking{reopenedLibrary()}.drafts.single().name)
        }finally{db.close()}
    }
}
