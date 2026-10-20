package dev.h516.shicang

import android.app.Application
import android.content.pm.ProviderInfo
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

/** Format 6 through the real Store, with synthetic media metadata; not device SAF/codec acceptance. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,shadows=[VideoEditStoreTest.Metadata::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class UnifiedBackupStoreTest {
    @Test fun editedVideoNotesAuthorsAndOriginalBytesSurviveBackupRestoreUndo()=runBlocking {
        val app=RuntimeEnvironment.getApplication();app.getSharedPreferences("device",0).edit().clear().putString("generation",uid()).commit()
        val store=Store(app)
        try {
            org.junit.Assert.assertFalse(store.canUndoRestore())
            val source=File(store.photoDir,"${uid()}.original").apply{writeText("synthetic unmodified video")}
            val p=Photo(source.name.removeSuffix(".original"),source.name,source.length(),sha(source),"video/mp4",1200)
            val author=Author(name="合成作者");store.saveAuthor(author)
            val a=Archive(name="合成备份档案",photos=listOf(p),cover=p.id,authorIds=listOf(author.id));store.save(a)
            val n=LongNote("media",p.id,body="原片的笔记");val generation=store.noteSession(n).generation
            store.saveNote(n,null,generation);store.noteDraft(n.copy(body="未提交原片笔记"),generation)
            val before=store.load()
            val exported=File(app.cacheDir,"synthetic-copy.mp4").apply{writeText("synthetic edited video")}
            val copy=store.appendEditedVideo(a.id,p,source,exported)
            val after=store.load()
            val zip=File(app.cacheDir,"integrated-format6.zip")
            val provider=BackupStoreTest.BackupProvider(zip).apply{attachInfo(app,ProviderInfo().apply{authority="integration.backup"})}
            ShadowContentResolver.registerProviderInternal("integration.backup",provider)
            val uri=Uri.parse("content://integration.backup/document/backup")
            store.backup(uri){}
            store.save(after.active.single().copy(name="恢复前新名称"));val beforeRestore=store.load()
            store.restore(uri){}
            org.junit.Assert.assertTrue(store.canUndoRestore())
            assertEquals(after,store.load())
            assertEquals(p.sha256,sha(File(store.photoDir,p.file)))
            assertEquals(copy.sha256,sha(File(store.photoDir,copy.file)))
            assertEquals(before.notes,store.load().notes);assertEquals(before.noteDrafts,store.load().noteDrafts)
            assertEquals(listOf(author.id),store.load().active.single().authorIds)
            assertEquals(p.id,store.load().active.single().cover)
            store.undoRestore();assertEquals(beforeRestore,store.load())
            assertEquals(p.sha256,sha(File(store.photoDir,p.file)))
            exported.delete();Unit
        } finally {store.close()}
    }
}
