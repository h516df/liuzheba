package dev.h516.shicang

import android.app.Application
import android.content.pm.ProviderInfo
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
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
@LooperMode(LooperMode.Mode.PAUSED)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class DirectMediaImportTest {
    private val scheduler=TestCoroutineScheduler()
    private val owners=ViewModelStore()
    private lateinit var model:AppModel
    private lateinit var app:Application
    private lateinit var formal:Archive
    private lateinit var source:File
    private val uri=Uri.parse("content://direct.import/source")
    @Before fun start() {
        Dispatchers.setMain(StandardTestDispatcher(scheduler));app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device",0).edit().clear().putString("generation",uid()).commit()
        app.getSharedPreferences("media-downloads",0).edit().clear().commit()
        formal=Archive(name="正式标题")
        runBlocking{val store=Store(app);try{store.save(formal)}finally{store.close()}}
        source=File(app.cacheDir,"${uid()}.mp4").apply{writeText("synthetic video source untouched")}
        val provider=LargeMediaStoreTest.Source(source,source.length()).apply{attachInfo(app,ProviderInfo().apply{authority="direct.import"})}
        ShadowContentResolver.registerProviderInternal("direct.import",provider)
        model=AppModel(app);owners.put("model",model);await{model.state.value.ready}
    }
    @After fun finish(){owners.clear();await{model.resourcesClosed};Dispatchers.resetMain();source.delete()}
    private fun await(condition:()->Boolean){val end=System.nanoTime()+15_000_000_000L;while(System.nanoTime()<end){scheduler.runCurrent();if(condition())return;Thread.sleep(5)};fail("Timed out: ${model.state.value}")}
    @Test fun cancellingPickerCreatesNoDraftAndDoesNotEnterEditor() {
        var launches=0;model.preparePhotos(formal.id){launches++};model.importPhotos(formal.id,emptyList())
        scheduler.advanceTimeBy(500);scheduler.runCurrent()
        assertEquals(1,launches);assertNull(model.state.value.editor)
        assertTrue(runBlocking{model.store.load()}.drafts.isEmpty())
        assertEquals(formal.name,model.state.value.library.active.single().name)
    }
    @Test fun detailAppendPersistsFormalAndDraftWithoutCommittingUnrelatedEdits() {
        runBlocking{model.store.draft(formal.copy(name="尚未提交的新标题"))}
        model.importPhotos(formal.id,listOf(uri));await{!model.state.value.busy}
        val saved=runBlocking{model.store.load()}
        assertEquals("正式标题",saved.active.single().name);assertEquals("尚未提交的新标题",saved.drafts.single().name)
        assertEquals(saved.active.single().photos,saved.drafts.single().photos);assertEquals(1,saved.active.single().photos.size)
        assertNull(model.state.value.editor)
        val photo=saved.active.single().photos.single();assertEquals(sha(source),sha(File(model.store.photoDir,photo.file)))
        assertEquals("synthetic video source untouched",source.readText())
        assertTrue(app.getSharedPreferences("device",0).getString("protectionImportWarning","").isNullOrEmpty())
    }
    @Test fun editorImportRemainsDraftOnly() {
        model.edit(formal);model.importPhotos(formal.id,listOf(uri));await{!model.state.value.busy}
        val saved=runBlocking{model.store.load()};assertTrue(saved.active.single().photos.isEmpty())
        assertEquals(1,saved.drafts.single().photos.size);assertEquals(saved.drafts.single(),model.state.value.editor)
    }
    @Test fun incomingMediaAppendsWithoutSubmittingExistingDraftTitle() {
        runBlocking{model.store.draft(formal.copy(name="未提交的标题"))}
        model.receiveMedia(listOf(uri));model.importIncoming(formal.id);await{!model.state.value.busy}
        val saved=runBlocking{model.store.load()}
        assertEquals("正式标题",saved.active.single().name)
        assertEquals("未提交的标题",saved.drafts.single().name)
        assertEquals(saved.active.single().photos,saved.drafts.single().photos)
        assertEquals(1,saved.active.single().photos.size)
        assertNull(model.state.value.editor)
        assertTrue(model.state.value.incomingMedia.isEmpty())
    }
    @Test fun newCollectionIsExplicitDraftUntilSaved() {
        model.receiveMedia(listOf(uri));model.importIncoming();await{!model.state.value.busy}
        val saved=runBlocking{model.store.load()}
        assertEquals(1,saved.active.size)
        assertEquals(1,saved.drafts.single().photos.size)
        assertEquals(saved.drafts.single(),model.state.value.editor)
        model.saveEditor();await{!model.state.value.busy}
        assertEquals(2,runBlocking{model.store.load()}.active.size)
        assertTrue(runBlocking{model.store.load()}.drafts.isEmpty())
    }
    @Test fun partialImportReportsOnlySuccessfulSourceIndices()=runBlocking {
        val missing=Uri.fromFile(File(app.cacheDir,"missing-${uid()}.mp4"))
        val result=model.store.importIntoArchive(formal.id,listOf(missing,uri)).first
        assertEquals(listOf(1),result.sourceIndices)
        assertEquals(1,result.errors.size)
        assertEquals(1,model.store.load().active.single().photos.size)
    }
    private fun downloadedBatch():Pair<MediaDownloads,List<MediaDownload>> {
        val downloads=MediaDownloads(app)
        val media=(1..2).map{RemoteMedia("https://example.com/$it.mp4","video","mp4")}
        downloads.enqueue(MediaPlan("合成图集","https://example.com/post",media),media)
        val manager=app.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        downloads.list().forEach{job->
            source.copyTo(downloads.file(job),overwrite=true)
            org.robolectric.Shadows.shadowOf(org.robolectric.Shadows.shadowOf(manager).getRequest(job.managerId))
                .setStatus(android.app.DownloadManager.STATUS_SUCCESSFUL)
        }
        return downloads to downloads.list()
    }
    @Test fun downloadedBatchAppendsOnceAndKeepsUnsubmittedTitle() {
        runBlocking{model.store.draft(formal.copy(name="未提交的下载标题"))}
        val (downloads,jobs)=downloadedBatch()
        assertTrue(jobs.all{it.state=="待收录"})
        model.importDownloads(jobs,formal.id);await{!model.state.value.busy}
        val saved=runBlocking{model.store.load()}
        assertEquals(2,saved.active.single().photos.size)
        assertEquals("正式标题",saved.active.single().name)
        assertEquals("未提交的下载标题",saved.drafts.single().name)
        assertEquals(saved.active.single().photos,saved.drafts.single().photos)
        assertTrue(downloads.list().all{it.state=="已收录"})
        assertNull(model.state.value.editor)
    }
    @Test fun failedDownloadInBatchRemainsAvailableWhileSuccessfulItemIsLinked() {
        val (downloads,jobs)=downloadedBatch()
        org.robolectric.Shadows.shadowOf(app.contentResolver).registerInputStream(Uri.fromFile(downloads.file(jobs.first())),object:java.io.InputStream(){
            override fun read():Int=throw java.io.IOException("synthetic source failure")
        })
        model.importDownloads(jobs,formal.id);await{!model.state.value.busy}
        assertEquals(1,runBlocking{model.store.load()}.active.single().photos.size)
        assertEquals("待收录",downloads.list().first{it.id==jobs.first().id}.state)
        assertEquals("已收录",downloads.list().first{it.id==jobs.last().id}.state)
        assertEquals("synthetic video source untouched",downloads.file(jobs.first()).readText())
    }
    @Test fun cancellationAfterCommitBeforeMainResumeCannotOverwriteImportedMedia() {
        val release=java.util.concurrent.CountDownLatch(1)
        val stream=object:java.io.FilterInputStream(source.inputStream()) {
            override fun read(b:ByteArray,off:Int,len:Int):Int {check(release.await(10,java.util.concurrent.TimeUnit.SECONDS));return super.read(b,off,len)}
        }
        org.robolectric.Shadows.shadowOf(app.contentResolver).registerInputStream(uri,stream)
        model.edit(formal);model.importPhotos(formal.id,listOf(uri));scheduler.runCurrent();release.countDown()
        // Do not pump Main: wait for the IO transaction, then cancel its queued Main continuation.
        val end=System.nanoTime()+10_000_000_000L
        var committed=false
        while(System.nanoTime()<end) {
            committed=runBlocking{model.store.load()}.drafts.any{it.id==formal.id && it.photos.size==1}
            if(committed)break
            Thread.sleep(5)
        }
        assertTrue("import did not reach its committed boundary",committed)
        model.cancelOperation();await{!model.state.value.busy}
        assertEquals(1,model.state.value.editor!!.photos.size)
        model.leaveEditor();await{!model.state.value.busy}
        val saved=runBlocking{model.store.load()}.drafts.single()
        assertEquals(1,saved.photos.size)
        assertEquals(sha(source),sha(File(model.store.photoDir,saved.photos.single().file)))
    }
    @Test fun failedAtomicLinkRemovesOnlyNewCopiesAndPreservesFormalDraftAndSource() {
        runBlocking{model.store.draft(formal.copy(name="未提交草稿"))}
        val before=runBlocking{model.store.load()}
        val generation=app.getSharedPreferences("device",0).getString("generation",null)
        val db=SQLiteDatabase.openDatabase(File(app.filesDir,"libraries/$generation/library.db").path,null,SQLiteDatabase.OPEN_READWRITE)
        try {
            db.execSQL("CREATE TRIGGER fail_link BEFORE INSERT ON objects WHEN NEW.kind='draft' BEGIN SELECT RAISE(ABORT,'fixture link failure'); END")
            model.importPhotos(formal.id,listOf(uri));await{!model.state.value.busy}
            assertEquals(before,runBlocking{model.store.load()})
            assertTrue(model.store.photoDir.listFiles().orEmpty().isEmpty())
            assertEquals("synthetic video source untouched",source.readText())
            assertFalse(model.state.value.message.isEmpty())
        }finally{db.close()}
    }
}
