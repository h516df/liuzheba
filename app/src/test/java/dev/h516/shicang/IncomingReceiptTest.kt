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
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,shadows=[VideoEditStoreTest.Metadata::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class IncomingReceiptTest {
    private lateinit var app:Application;private lateinit var store:Store
    @Before fun start(){app=RuntimeEnvironment.getApplication();app.getSharedPreferences("device",0).edit().clear().putString("generation",uid()).commit();store=Store(app)}
    @After fun close()=runBlocking{store.close()}
    @Test fun selectedSourcesSurviveReopenButNeverCrossAChangedLibrary() {
        val a=Uri.parse("content://synthetic/a");val b=Uri.parse("content://synthetic/b")
        val queue=IncomingMediaQueue(app);val first=queue.write(store.generationToken,listOf(a,b))
        assertEquals(first,IncomingMediaQueue(app).read(store.generationToken))
        val next=queue.write(store.generationToken,listOf(b));assertEquals(first[1],next.single())
        assertTrue(queue.read(uid()).isEmpty())
        assertEquals(first[1],queue.read(store.generationToken).single())
    }
    @Test fun restartAfterLinkBeforeQueueAcknowledgementCannotDuplicateMedia()=runBlocking {
        val source=File(app.cacheDir,"incoming-video.mp4").apply{writeText("synthetic original video")}
        val provider=LargeMediaStoreTest.Source(source,source.length()).apply{attachInfo(app,ProviderInfo().apply{authority="incoming.receipt"})}
        ShadowContentResolver.registerProviderInternal("incoming.receipt",provider)
        val uri=Uri.parse("content://incoming.receipt/video");val queue=IncomingMediaQueue(app)
        val key=queue.write(store.generationToken,listOf(uri)).single().id
        val a=Archive(name="已保存档案");store.save(a)
        assertTrue(runCatching{store.importIntoArchive(a.id,listOf(uri),sourceKeys=listOf(key),onLinked={error("synthetic crash after durable link")})}.isFailure)
        store.close();store=Store(app)
        assertEquals(setOf(key),store.completedIncoming(listOf(key)))
        store.importIntoArchive(a.id,listOf(uri),sourceKeys=listOf(key))
        assertEquals(1,store.load().active.single().photos.size)
        assertEquals("synthetic original video",source.readText())
    }
    @Test fun failedLinkLeavesSourceQueuedAndDoesNotCreateAReceipt()=runBlocking {
        val source=File(app.cacheDir,"incoming-failure.mp4").apply{writeText("synthetic source untouched")}
        val provider=LargeMediaStoreTest.Source(source,source.length()).apply{attachInfo(app,ProviderInfo().apply{authority="incoming.failure"})}
        ShadowContentResolver.registerProviderInternal("incoming.failure",provider)
        val uri=Uri.parse("content://incoming.failure/video");val key=IncomingMediaQueue(app).write(store.generationToken,listOf(uri)).single().id
        val a=Archive(name="原档案");store.save(a)
        val db=android.database.sqlite.SQLiteDatabase.openDatabase(File(app.filesDir,"libraries/${store.generationToken}/library.db").path,null,0)
        try {
            db.execSQL("CREATE TRIGGER reject_link BEFORE INSERT ON objects WHEN NEW.kind='archive' BEGIN SELECT RAISE(ABORT,'synthetic SQL failure'); END")
            assertTrue(runCatching{store.importIntoArchive(a.id,listOf(uri),sourceKeys=listOf(key))}.isFailure)
            assertTrue(store.completedIncoming(listOf(key)).isEmpty());assertTrue(store.load().active.single().photos.isEmpty())
            assertEquals(key,IncomingMediaQueue(app).read(store.generationToken).single().id)
            assertEquals("synthetic source untouched",source.readText())
        }finally{db.close()}
    }
}
