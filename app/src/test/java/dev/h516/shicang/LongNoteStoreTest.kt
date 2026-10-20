package dev.h516.shicang

import android.app.Application
import android.content.pm.ProviderInfo
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class LongNoteStoreTest {
    private lateinit var app:Application;private lateinit var store:Store
    @Before fun start(){app=RuntimeEnvironment.getApplication();app.getSharedPreferences("device",0).edit().clear().putString("generation",uid()).commit();store=Store(app)}
    @After fun close()=runBlocking{store.close()}
    private suspend fun save(n:LongNote,expected:LongNote?)=store.saveNote(n,expected,store.noteSession(n).generation)
    private suspend fun draft(n:LongNote)=store.noteDraft(n,store.noteSession(n).generation)
    private suspend fun discard(n:LongNote)=store.discardNoteDraft(n,store.noteSession(n).generation)
    @Test fun draftReopenThenSaveAndDiscardPreserveOriginal()=runBlocking {
        val n=LongNote("archive",uid(),body="原稿");save(n,null)
        val saved=store.load().notes.single();draft(saved.copy(body="未保存\n长草稿"))
        store.close();store=Store(app);assertEquals("未保存\n长草稿",store.load().noteDrafts.single().body)
        assertEquals("原稿",store.load().notes.single().body)
        discard(n);assertTrue(store.load().noteDrafts.isEmpty());assertEquals("原稿",store.load().notes.single().body)
        draft(saved.copy(body="正式新稿"));save(saved.copy(body="正式新稿"),saved)
        assertTrue(store.load().noteDrafts.isEmpty());assertEquals("正式新稿",store.load().notes.single().body)
    }
    @Test fun staleEditorCannotOverwriteAndRetainsDraft()=runBlocking {
        val n=LongNote("author",uid(),body="原稿");save(n,null);val old=store.load().notes.single()
        save(old.copy(body="外部更新"),old);draft(old.copy(body="过期编辑"))
        assertTrue(runCatching{save(old.copy(body="过期编辑"),old)}.isFailure)
        assertEquals("外部更新",store.load().notes.single().body);assertEquals("过期编辑",store.load().noteDrafts.single().body)
    }
    @Test fun backupRestoreUndoKeepNotesDraftsAndFields()=runBlocking {
        val provider=BackupStoreTest.BackupProvider(File(app.cacheDir,"notes.zip"))
        provider.attachInfo(app,ProviderInfo().apply{authority="notes.fixture"});ShadowContentResolver.registerProviderInternal("notes.fixture",provider)
        val uri=Uri.parse("content://notes.fixture/document/backup")
        val a=Archive(name="合成档案",fields=listOf(Field(name="旧字段",value="保留")));store.save(a)
        val n=LongNote("archive",a.id,body="正文\n"+"甲".repeat(6000));save(n,null);draft(n.copy(body="草稿"));val before=store.load()
        store.backup(uri){};discard(n);val changed=store.load();store.restore(uri){};assertEquals(before,store.load());store.undoRestore();assertEquals(changed,store.load())
    }
    @Test fun failureDuringSaveRollsBackAndKeepsDraft()=runBlocking {
        val n=LongNote("media",uid(),body="旧");save(n,null);val old=store.load().notes.single();draft(old.copy(body="新"))
        val generation=app.getSharedPreferences("device",0).getString("generation",null)
        val db=android.database.sqlite.SQLiteDatabase.openDatabase(File(app.filesDir,"libraries/$generation/library.db").path,null,android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
        try {db.execSQL("CREATE TRIGGER fail_note_delete BEFORE DELETE ON objects WHEN OLD.kind='longNoteDraft' BEGIN SELECT RAISE(ABORT,'fixture failure'); END")
            assertTrue(runCatching{save(old.copy(body="新"),old)}.isFailure)
            assertEquals("旧",store.load().notes.single().body);assertEquals("新",store.load().noteDrafts.single().body)
        }finally{db.close()}
    }    @Test fun restorationRejectsOldEditorEvenWhenSavedNoteMatches()=runBlocking {
        val provider=BackupStoreTest.BackupProvider(File(app.cacheDir,"generation-notes.zip"))
        provider.attachInfo(app,ProviderInfo().apply{authority="note-generation.fixture"});ShadowContentResolver.registerProviderInternal("note-generation.fixture",provider)
        val uri=Uri.parse("content://note-generation.fixture/document/backup")
        val n=LongNote("archive",uid(),body="已保存");save(n,null);draft(n.copy(body="应保留草稿"));val snapshot=store.noteSession(n)
        store.backup(uri){};store.restore(uri){}
        assertTrue(runCatching{store.noteDraft(n.copy(body="过期"),snapshot.generation)}.isFailure)
        assertTrue(runCatching{store.discardNoteDraft(n,snapshot.generation)}.isFailure)
        assertTrue(runCatching{store.saveNote(n,snapshot.saved,snapshot.generation)}.isFailure)
        assertEquals("应保留草稿",store.load().noteDrafts.single().body)
    }

}
