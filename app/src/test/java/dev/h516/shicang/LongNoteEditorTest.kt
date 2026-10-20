package dev.h516.shicang

import android.app.Application
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class LongNoteEditorTest {
    private lateinit var store:Store;private lateinit var scope:CoroutineScope
    @Before fun start(){val app=RuntimeEnvironment.getApplication();app.getSharedPreferences("device",0).edit().clear().putString("generation",uid()).commit();store=Store(app);scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)}
    @After fun stop()=runBlocking{scope.cancel();scope.coroutineContext[Job]?.join();store.close()}
    private suspend fun until(check:()->Boolean){withTimeout(5000){while(!check())delay(10)}}
    @Test fun exitFlushesDraftAndOpenRestoresIt()=runBlocking {
        val id=uid();val c=LongNoteEditor(scope,{store},{})
        c.open("archive",id,"合成");until{c.state.value?.loaded==true};c.change("标题","多段正文\n第二段");c.leave();until{c.state.value==null}
        assertEquals("多段正文\n第二段",store.load().noteDrafts.single().body)
        val next=LongNoteEditor(scope,{store},{})
        next.open("archive",id,"合成");until{next.state.value?.loaded==true};assertEquals("多段正文\n第二段",next.state.value?.note?.body);assertTrue(store.load().notes.isEmpty())
    }
    @Test fun saveSuccessRefreshFailureDoesNotResurrectEditor()=runBlocking {
        val id=uid();var notice="";val c=LongNoteEditor(scope,{store},{error("fixture refresh failure")},{notice=it})
        c.open("archive",id,"合成");until{c.state.value?.loaded==true};c.change("","已提交正文");c.save();until{c.state.value==null};until{notice.isNotEmpty()}
        assertEquals("已提交正文",store.load().notes.single().body);assertTrue(store.load().noteDrafts.isEmpty())
    }
    @Test fun discardSuccessRefreshFailureDoesNotRecreateDraft()=runBlocking {
        val id=uid();val c=LongNoteEditor(scope,{store},{error("fixture refresh failure")})
        c.open("archive",id,"合成");until{c.state.value?.loaded==true};c.change("","准备丢弃");c.discard();until{c.state.value==null};assertTrue(store.load().noteDrafts.isEmpty());assertTrue(store.load().notes.isEmpty())
    }
    @Test fun oversizedInputRejectedWithoutTruncatingPreviousText()=runBlocking {
        val c=LongNoteEditor(scope,{store},{})
        c.open("archive",uid(),"合成");until{c.state.value?.loaded==true};c.change("","原内容");c.change("","甲".repeat(100001))
        assertEquals("原内容",c.state.value?.note?.body);assertTrue(c.state.value!!.error.contains("超长"));c.leave();until{c.state.value==null};assertEquals("原内容",store.load().noteDrafts.single().body)
    }
    @Test fun invalidatedSessionCanCloseWithoutTouchingRestoredDraft()=runBlocking {
        val provider=BackupStoreTest.BackupProvider(java.io.File(RuntimeEnvironment.getApplication().cacheDir,"editor-generation.zip"))
        provider.attachInfo(RuntimeEnvironment.getApplication(),android.content.pm.ProviderInfo().apply{authority="editor-generation.fixture"})
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("editor-generation.fixture",provider)
        val uri=android.net.Uri.parse("content://editor-generation.fixture/document/backup")
        val n=LongNote("archive",uid(),body="备份草稿");store.noteDraft(n,store.noteSession(n).generation);store.backup(uri){}
        val c=LongNoteEditor(scope,{store},{})
        c.open("archive",n.target,"合成");until{c.state.value?.loaded==true};c.change("","旧会话文字")
        store.restore(uri){};c.save();until{c.state.value?.invalidated==true};c.discard();until{c.state.value==null}
        assertEquals("备份草稿",store.load().noteDrafts.single().body);assertTrue(store.load().notes.isEmpty())
    }

}
