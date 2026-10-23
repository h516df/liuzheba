package dev.h516.shicang

import android.app.Application
import android.content.pm.ProviderInfo
import android.database.sqlite.SQLiteDatabase
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
class ArchiveContentStoreTest {
    private lateinit var app:Application
    private lateinit var store:Store
    @Before fun start(){app=RuntimeEnvironment.getApplication();app.getSharedPreferences("device",0).edit().clear().putString("generation",uid()).commit();store=Store(app)}
    @After fun close()=runBlocking{store.close()}
    private fun media(video:Boolean):Photo {
        val id=uid();val file=File(store.photoDir,"$id.original").apply{writeText("synthetic ${if(video)"video" else "image"} $id")}
        return Photo(id,file.name,file.length(),sha(file),if(video)"video/mp4" else "image/png",if(video)12000 else 0)
    }
    private suspend fun original():Archive {
        val a=Archive(name="0.16 合成档案",photos=listOf(media(false),media(true),media(false)),fields=listOf(Field(name="旧备注",value="原字段保留")))
        store.save(a)
        val n=LongNote("archive",a.id,title="原笔记标题",body="0.16 原正文")
        store.saveNote(n,null,store.generationToken)
        return store.load().active.single()
    }
    @Test fun textOrderCoverAndArchiveCommitTogetherThenSurviveReopen()=runBlocking {
        val a=original();val session=store.load().archiveTextDraft(a,store.generationToken)
        val next=a.copy(name="新档案",photos=a.photos.reversed(),cover=a.photos.last().id)
        val text=session.copy(note=session.note.copy(body="新正文\n照片和横竖视频同档案"))
        store.draft(next,text);store.save(next,text);store.close();store=Store(app)
        val saved=store.load();assertEquals(next.photos,saved.active.single().photos)
        assertEquals(next.cover,saved.active.single().cover);assertEquals(a.fields,saved.active.single().fields)
        assertEquals("原笔记标题",saved.notes.single().title);assertEquals(text.note.body,saved.notes.single().body)
        assertTrue(saved.drafts.isEmpty());assertTrue(saved.noteDrafts.isEmpty())
        a.photos.forEach{assertEquals(it.sha256,sha(File(store.photoDir,it.file)))}
    }
    @Test fun unsavedTextAndMediaDraftRestoreWithoutChangingFormalContent()=runBlocking {
        val a=original();val session=store.load().archiveTextDraft(a,store.generationToken)
        val next=a.copy(name="草稿标题",photos=a.photos.reversed())
        store.draft(next,session.copy(note=session.note.copy(body="尚未提交")));store.close();store=Store(app)
        val saved=store.load();assertEquals(a,saved.active.single());assertEquals("0.16 原正文",saved.notes.single().body)
        assertEquals(next,saved.drafts.single());assertEquals("尚未提交",saved.archiveTextDraft(next,store.generationToken).note.body)
        store.discard(a.id,saved.archiveTextDraft(next,store.generationToken));assertTrue(store.load().drafts.isEmpty());assertTrue(store.load().noteDrafts.isEmpty())
    }
    @Test fun failureInTextCommitRollsBackArchiveAndKeepsBothDrafts()=runBlocking {
        val a=original();val session=store.load().archiveTextDraft(a,store.generationToken)
        val next=a.copy(name="不可部分提交",photos=a.photos.reversed())
        val text=session.copy(note=session.note.copy(body="不可部分提交的文字"));store.draft(next,text)
        val db=SQLiteDatabase.openDatabase(File(app.filesDir,"libraries/${store.generationToken}/library.db").path,null,SQLiteDatabase.OPEN_READWRITE)
        try {
            db.execSQL("CREATE TRIGGER reject_new_note BEFORE INSERT ON objects WHEN NEW.kind='longNote' BEGIN SELECT RAISE(ABORT,'synthetic write failure'); END")
            assertTrue(runCatching{store.save(next,text)}.isFailure)
            val saved=store.load();assertEquals(a,saved.active.single());assertEquals("0.16 原正文",saved.notes.single().body)
            assertEquals(next,saved.drafts.single());assertEquals(text.note.body,saved.noteDrafts.single().body)
        } finally {db.close()}
    }
    @Test fun externalTextChangeCannotBeOverwrittenByUnifiedEditor()=runBlocking {
        val a=original();val session=store.load().archiveTextDraft(a,store.generationToken)
        store.saveNote(session.note.copy(body="外部新正文"),session.expected,store.generationToken)
        val edit=session.copy(note=session.note.copy(body="过期内容"));store.draft(a,edit)
        assertTrue(runCatching{store.save(a.copy(name="过期标题"),edit)}.isFailure)
        assertEquals(a,store.load().active.single());assertEquals("外部新正文",store.load().notes.single().body)
        assertEquals("过期内容",store.load().noteDrafts.single().body)
    }
    @Test fun backupRestorePreservesUnifiedDraftAndRejectsOldGeneration()=runBlocking {
        val a=original();val session=store.load().archiveTextDraft(a,store.generationToken)
        store.draft(a,session.copy(note=session.note.copy(body="待继续草稿")))
        val provider=BackupStoreTest.BackupProvider(File(app.cacheDir,"archive-rebuild.zip"))
        provider.attachInfo(app,ProviderInfo().apply{authority="rebuild.fixture"});ShadowContentResolver.registerProviderInternal("rebuild.fixture",provider)
        val uri=Uri.parse("content://rebuild.fixture/document/backup")
        val before=store.load();store.backup(uri){};store.restore(uri){}
        assertEquals(before,store.load())
        assertTrue(runCatching{store.save(a,session)}.exceptionOrNull() is StaleNoteSession)
        assertTrue(runCatching{store.draft(a,session)}.exceptionOrNull() is StaleNoteSession)
        assertTrue(runCatching{store.discard(a.id,session)}.exceptionOrNull() is StaleNoteSession)
        assertEquals(before,store.load())
    }
    @Test fun mediaFilterNeverReordersTheUnderlyingMixedSequence()=runBlocking {
        val a=original();assertEquals(listOf(a.photos[1]),a.media(MediaFilter.VIDEOS))
        assertEquals(listOf(a.photos[0],a.photos[2]),a.media(MediaFilter.IMAGES))
        assertEquals(a.photos,a.media(MediaFilter.ALL));assertEquals("2 张图片 · 1 个视频",a.mediaCountLabel())
        assertEquals(listOf(a.photos[2],a.photos[0],a.photos[1]),moveMediaBefore(a,a.photos[2].id,a.photos[0].id).photos)
    }
}
