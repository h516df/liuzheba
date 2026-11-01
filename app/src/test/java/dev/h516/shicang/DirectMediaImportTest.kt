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
    @Test fun partialIncomingAppendRetainsFailedSourceAndRetryDoesNotDuplicateSuccess() {
        val retry=File(app.cacheDir,"retry-${uid()}.mp4")
        val missing=Uri.fromFile(retry)
        try {
            model.receiveMedia(listOf(uri,missing));model.importIncoming(formal.id);await{!model.state.value.busy}
            assertEquals(listOf(missing),model.state.value.incomingMedia)
            val first=runBlocking{model.store.load()}.active.single().photos.single()
            assertEquals(sha(source),sha(model.photoFile(first)))
            source.copyTo(retry)
            model.importIncoming(formal.id);await{!model.state.value.busy}
            val saved=runBlocking{model.store.load()}.active.single()
            assertEquals(2,saved.photos.size);assertEquals(first,saved.photos.first())
            assertTrue(model.state.value.incomingMedia.isEmpty())
            assertEquals("synthetic video source untouched",source.readText())
            assertEquals(sha(source),sha(retry))
        }finally{retry.delete()}
    }
    @Test fun partialNewCollectionRetainsFailedSourceAndItsSuccessfulDraft() {
        val missing=Uri.fromFile(File(app.cacheDir,"missing-${uid()}.mp4"))
        model.receiveMedia(listOf(missing,uri));model.importIncoming();await{!model.state.value.busy}
        val saved=runBlocking{model.store.load()}
        assertEquals(listOf(missing),model.state.value.incomingMedia)
        assertEquals(formal.id,saved.active.single().id)
        assertEquals(formal.name,saved.active.single().name)
        assertTrue(saved.active.single().photos.isEmpty())
        assertEquals(1,saved.drafts.single().photos.size)
        assertEquals(saved.drafts.single(),model.state.value.editor)
        assertEquals(sha(source),sha(model.photoFile(saved.drafts.single().photos.single())))
    }
    @Test fun allIncomingFailuresPreserveExistingMediaAndRetrySelection() {
        model.importPhotos(formal.id,listOf(uri));await{!model.state.value.busy}
        val before=runBlocking{model.store.load()}
        val original=before.active.single().photos.single();val digest=sha(model.photoFile(original))
        val missing=Uri.fromFile(File(app.cacheDir,"missing-${uid()}.mp4"))
        model.receiveMedia(listOf(missing));model.importIncoming(formal.id);await{!model.state.value.busy}
        assertEquals(before,runBlocking{model.store.load()})
        assertEquals(digest,sha(model.photoFile(original)))
        assertEquals(listOf(missing),model.state.value.incomingMedia)
        assertEquals("synthetic video source untouched",source.readText())
    }
    @Test fun mediaReceivedDuringImportIsNotClearedWithTheCompletedBatch() {
        val entered=java.util.concurrent.CountDownLatch(1)
        val release=java.util.concurrent.CountDownLatch(1)
        val stream=object:java.io.FilterInputStream(source.inputStream()) {
            override fun read(b:ByteArray,off:Int,len:Int):Int {
                entered.countDown();check(release.await(10,java.util.concurrent.TimeUnit.SECONDS))
                return super.read(b,off,len)
            }
        }
        org.robolectric.Shadows.shadowOf(app.contentResolver).registerInputStream(uri,stream)
        val later=Uri.fromFile(File(app.cacheDir,"later-${uid()}.mp4"))
        try {
            model.receiveMedia(listOf(uri));model.importIncoming(formal.id);scheduler.runCurrent()
            assertTrue(entered.await(10,java.util.concurrent.TimeUnit.SECONDS))
            model.receiveMedia(listOf(later));release.countDown();await{!model.state.value.busy}
            assertEquals(listOf(later),model.state.value.incomingMedia)
            assertEquals(1,runBlocking{model.store.load()}.active.single().photos.size)
        }finally{release.countDown()}
    }
    @Test fun failedLinkPreservesExistingImageVideoDraftAndAllSourceBytes() {
        val image=File(app.cacheDir,"${uid()}.png")
        android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.BLUE)
            image.outputStream().use{assertTrue(compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))};recycle()
        }
        try {
            model.importPhotos(formal.id,listOf(Uri.fromFile(image),uri));await{!model.state.value.busy}
            val existing=runBlocking{model.store.load()}.active.single()
            assertEquals(setOf("image/png","video/mp4"),existing.photos.map{it.mime}.toSet())
            runBlocking{model.store.draft(existing.copy(name="待保存标题"))}
            val before=runBlocking{model.store.load()}
            val bytes=existing.photos.associate{it.file to sha(model.photoFile(it))}
            val generation=app.getSharedPreferences("device",0).getString("generation",null)
            val db=SQLiteDatabase.openDatabase(File(app.filesDir,"libraries/$generation/library.db").path,null,SQLiteDatabase.OPEN_READWRITE)
            try {
                db.execSQL("CREATE TRIGGER reject_media_link BEFORE INSERT ON objects WHEN NEW.kind='draft' BEGIN SELECT RAISE(ABORT,'fixture link failure'); END")
                model.receiveMedia(listOf(Uri.fromFile(image),uri));model.importIncoming(formal.id);await{!model.state.value.busy}
                assertEquals(before,runBlocking{model.store.load()})
                assertEquals(listOf(Uri.fromFile(image),uri),model.state.value.incomingMedia)
                assertEquals(bytes,existing.photos.associate{it.file to sha(model.photoFile(it))})
                assertEquals(bytes.keys,model.store.photoDir.listFiles().orEmpty().map{it.name}.toSet())
                assertEquals(bytes[existing.photos.first().file],sha(image))
                assertEquals("synthetic video source untouched",source.readText())
            }finally{db.close()}
            owners.clear();await{model.resourcesClosed}
            runBlocking {
                val reopened=Store(app)
                try {
                    assertEquals(before,reopened.load())
                    assertEquals(bytes,existing.photos.associate{it.file to sha(File(reopened.photoDir,it.file))})
                }finally{reopened.close()}
            }
        }finally{image.delete()}
    }
    private fun downloadedBatch(authorProfile:String="",authorName:String="",workId:String="",caption:String=""):Pair<MediaDownloads,List<MediaDownload>> {
        val downloads=MediaDownloads(app)
        val media=(1..2).map{RemoteMedia("https://example.com/$it.mp4","video","mp4")}
        downloads.enqueue(MediaPlan("合成图集","https://example.com/post",media,authorProfile,authorName,workId,caption),media)
        val manager=app.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        downloads.list().filter{it.archiveId.isEmpty()}.forEach{job->
            source.copyTo(downloads.file(job),overwrite=true)
            org.robolectric.Shadows.shadowOf(org.robolectric.Shadows.shadowOf(manager).getRequest(job.managerId))
                .setStatus(android.app.DownloadManager.STATUS_SUCCESSFUL)
        }
        return downloads to downloads.list().filter{it.archiveId.isEmpty()}
    }
    @Test fun downloadedWorksAutoLinkVerifiedCreatorAndKeepOriginalMedia() {
        val profile="https://www.xiaohongshu.com/user/profile/syntheticMediaAuthor"
        val (downloads,jobs)=downloadedBatch(profile,"作品作者")
        assertTrue(jobs.all{it.authorProfile==profile && it.authorName=="作品作者"})
        model.importDownloads(jobs,formal.id);await{!model.state.value.busy}
        val result=runBlocking{model.store.load()}
        assertEquals(1,result.authors.size)
        assertEquals("作品作者",result.authors.single().name)
        assertEquals(listOf(result.authors.single().id),result.active.single().authorIds)
        assertEquals(2,result.active.single().photos.size)
        assertTrue(downloads.list().all{it.state=="已收录"})
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
    @Test fun clearingHistoryKeepsFormalArchiveWithUnsubmittedDraft() {
        runBlocking{model.store.draft(formal.copy(name="还没保存的标题"))}
        val (downloads,jobs)=downloadedBatch()
        model.importDownloads(jobs,formal.id);await{!model.state.value.busy}
        model.clearCompletedDownloads();await{!model.state.value.busy}
        assertEquals(jobs.map{it.id},downloads.list().map{it.id})
        val stored=runBlocking{model.store.load()}
        assertEquals("还没保存的标题",stored.drafts.single().name)
        assertEquals(2,stored.active.single().photos.size)
    }
    @Test fun downloadedPostIsSavedWithoutAnExtraArchiveEditorAndPendingDownloadsStayAvailable() {
        val (downloads,jobs)=downloadedBatch()
        val originalSource=sha(source)
        // A link download is a post. The first successful item becomes a formal post;
        // another unfinished download is kept until it is saved or retried.
        model.importDownloads(listOf(jobs.first()));await{!model.state.value.busy}
        val newPost=runBlocking{model.store.load()}.active.first{it.id!=formal.id}
        assertEquals("已收录",downloads.list().first{it.id==jobs.first().id}.state)
        assertTrue(runBlocking{model.store.load()}.drafts.isEmpty())
        assertNull(model.state.value.editor)
        assertEquals(1,newPost.photos.size)
        model.clearCompletedDownloads();await{!model.state.value.busy}
        assertEquals(listOf(jobs.last().id),downloads.list().map{it.id})
        assertEquals("待收录",downloads.list().single().state)
        assertEquals(originalSource,sha(source))
        assertEquals(originalSource,sha(File(model.store.photoDir,newPost.photos.single().file)))
        assertTrue(runBlocking{model.store.load()}.drafts.isEmpty())
    }
    @Test fun remoteWorkMetadataSurvivesQueueAndBackupRoundtrip() {
        val (downloads,jobs)=downloadedBatch(workId="postStable123",caption="原作者的完整正文\n第二行 #标签")
        assertEquals(1,jobs.map{it.batchId}.distinct().size)
        assertEquals(listOf(0,1),jobs.map{it.mediaOrder})
        assertEquals("postStable123",jobs.first().work?.workId)
        model.importDownloads(jobs);await{!model.state.value.busy}
        val library=runBlocking{model.store.load()}
        val post=library.active.first{it.id!=formal.id}
        assertEquals("postStable123",post.work?.workId)
        assertEquals("原作者的完整正文\n第二行 #标签",library.archiveNote(post.id)?.body)
        assertEquals(post.work,Codec.library(Codec.library(library)).active.first{it.id==post.id}.work)
        assertTrue(downloads.list().all{it.state=="已收录"})
    }
    @Test fun invalidOptionalWorkIndexCannotHideOutstandingDownloadJobs() {
        val (downloads,jobs)=downloadedBatch(workId="syntheticPost123",caption="正文")
        app.getSharedPreferences("media-downloads",0).edit().putString("works","not-json").commit()
        val recovered=downloads.list()
        assertEquals(jobs.map{it.id},recovered.map{it.id})
        assertTrue(recovered.all{it.state=="待收录" && it.work==null})
    }
    @Test fun repeatDownloadAppendsToTheSamePostWithoutDuplicatingOriginalFiles() {
        val (first,jobs)=downloadedBatch(workId="samePost123",caption="初次保存")
        model.importDownloads(jobs);await{!model.state.value.busy}
        val before=runBlocking{model.store.load()}
        val post=before.active.first{it.id!=formal.id}
        val files=post.photos.map{it.file to sha(File(model.store.photoDir,it.file))}
        val (second,again)=downloadedBatch(workId="samePost123",caption="初次保存")
        model.importDownloads(again);await{!model.state.value.busy}
        val after=runBlocking{model.store.load()}
        assertEquals(2,after.active.size)
        assertEquals(files,after.active.first{it.id==post.id}.photos.map{it.file to sha(File(model.store.photoDir,it.file))})
        assertEquals(files.size,model.store.photoDir.listFiles().orEmpty().size)
        assertTrue(first.list().all{it.state=="已收录"})
        assertTrue(second.list().all{it.state=="已收录"})
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
