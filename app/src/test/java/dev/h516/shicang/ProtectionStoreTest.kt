package dev.h516.shicang

import android.app.Application
import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract as Documents
import android.provider.DocumentsProvider
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class ProtectionStoreTest {
    private lateinit var app:Application
    private lateinit var store:Store
    private lateinit var provider:LocalDocuments
    private val tree=Documents.buildTreeDocumentUri("com.android.externalstorage.documents","primary:Documents")
    @Before fun setup() {
        app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device",0).edit().clear().putString("generation",uid()).commit()
        store=Store(app)
        provider=LocalDocuments(File(app.cacheDir,"external-${uid()}").apply{mkdirs()})
        provider.attachInfo(app,ProviderInfo().apply{authority="com.android.externalstorage.documents";exported=true;grantUriPermissions=true;readPermission="android.permission.MANAGE_DOCUMENTS";writePermission="android.permission.MANAGE_DOCUMENTS"})
        ShadowContentResolver.registerProviderInternal("com.android.externalstorage.documents",provider)
    }
    private fun original(bytes:String="synthetic original video bytes"):Photo {
        val id=uid();val f=File(store.photoDir,"$id.original").apply{writeText(bytes)}
        return Photo(id,f.name,f.length(),sha(f),"video/mp4",1234)
    }
    private suspend fun protect(a:Archive=Archive(name="合成档案",photos=listOf(original()))):Archive {
        store.save(a);store.selectProtectionFolder(tree);store.protectNow();assertTrue(store.protection.value.toString(),store.protection.value.protected);return store.load().active.single()
    }
    private fun externalFiles()=provider.base.walkTopDown().filter{it.isFile}.associate{it.relativeTo(provider.base).path to it.readBytes()}
    private fun snapshots()=provider.base.walkTopDown().filter{it.name.startsWith("liuce-v1-")}.toList()
    @Test fun searchRulesAreProtectedRestoredAndUndoable()=runBlocking {
        val a=protect()
        val preferences=SearchPreferences(aliases=listOf(SearchAlias("旧称呼","合成档案")))
        store.searchPreferences(preferences);assertEquals("待保护",store.protection.value.status)
        store.protectNow();assertTrue(store.protection.value.protected)
        val version=store.inspectProtectionFolder(tree).first()
        store.searchPreferences(SearchPreferences());store.restoreProtectionFolder(tree,version.name){}
        assertEquals(preferences,store.load().searchPreferences)
        assertEquals(a.id,store.load().active.single().id)
        store.undoRestore();assertEquals(SearchPreferences(),store.load().searchPreferences)
    }
    @Test fun createsOwnFolderAndVerifiesAllOriginalBytes()=runBlocking {
        val a=protect()
        assertTrue(File(provider.base,RecoveryFolder.DIRECTORY).isDirectory)
        assertEquals(1,store.protection.value.mediaCount)
        val blob=provider.base.walkTopDown().single{it.name.endsWith(".original")}
        assertEquals(a.photos.single().sha256,sha(blob))
        assertEquals(1,store.inspectProtectionFolder(tree).size)
    }
    @Test fun textOnlyChangesReuseOriginalAndKeepPreviousVersion()=runBlocking {
        val a=protect();val before=externalFiles();val originalWrites=provider.blobWrites
        store.save(a.copy(name="修改后"));assertEquals("待保护",store.protection.value.status)
        store.protectNow();assertEquals(originalWrites,provider.blobWrites);assertEquals(2,snapshots().size)
        before.forEach{(name,bytes)->assertArrayEquals(bytes,File(provider.base,name).readBytes())}
        store.protectNow();assertEquals(2,snapshots().size)
    }
    @Test fun revokedGrantNeverReportsProtectedAndLastGoodIsUntouched()=runBlocking {
        val a=protect();val before=externalFiles();val time=store.protection.value.lastSuccess
        store.save(a.copy(name="等待保护"));provider.revoked=true;store.protectNow()
        assertEquals("保护失败",store.protection.value.status);assertEquals(time,store.protection.value.lastSuccess)
        provider.revoked=false;before.forEach{(name,bytes)->assertArrayEquals(bytes,File(provider.base,name).readBytes())}
        store.protectNow(forceVerify=true);assertTrue(store.protection.value.protected)
    }
    @Test fun interruptedWritePreservesEveryPreviousVersionAndRetries()=runBlocking {
        val a=protect();val before=externalFiles()
        store.save(a.copy(photos=a.photos+original("second synthetic video")))
        provider.failWrite=true;store.protectNow();assertFalse(store.protection.value.protected)
        before.forEach{(name,bytes)->assertArrayEquals(bytes,File(provider.base,name).readBytes())}
        provider.failWrite=false;store.protectNow();assertTrue(store.protection.value.protected);assertEquals(2,store.protection.value.mediaCount)
        assertEquals(2,store.inspectProtectionFolder(tree).size)
    }
    @Test fun corruptReadbackNeverBecomesRecoverySuccess()=runBlocking {
        store.save(Archive(name="合成资料",photos=listOf(original())));store.selectProtectionFolder(tree)
        provider.corruptReadback=true;store.protectNow();assertFalse(store.protection.value.protected);assertEquals(0L,store.protection.value.lastSuccess)
        assertTrue(store.inspectProtectionFolder(tree).isEmpty())
    }
    @Test fun newAppStateCanReselectRestorePortablePathsAndUndoWithoutDataLoss()=runBlocking {
        val a=protect();val choices=store.inspectProtectionFolder(tree);val bytes=File(store.photoDir,a.photos.single().file).readBytes()
        app.getSharedPreferences("device",0).edit().clear().putString("generation",uid()).commit()
        val fresh=Store(app);assertEquals("未开启",fresh.protection.value.status)
        fresh.save(Archive(name="恢复前新资料"))
        fresh.restoreProtectionFolder(tree,choices.single().name){}
        assertEquals(a,fresh.load().active.single());assertArrayEquals(bytes,File(fresh.photoDir,a.photos.single().file).readBytes())
        fresh.undoRestore();assertEquals("恢复前新资料",fresh.load().active.single().name)
    }
    @Test fun missingExternalOriginalCannotReplaceCurrentLibrary()=runBlocking {
        val a=protect();val choice=store.inspectProtectionFolder(tree).single()
        provider.base.walkTopDown().single{it.name.endsWith(".original")}.delete()
        assertTrue(runCatching{store.restoreProtectionFolder(tree,choice.name){}}.isFailure)
        assertEquals(a,store.load().active.single())
    }
    @Test fun metadataCapacityFailsBeforeCreatingExternalFiles()=runBlocking {
        val ps=List(20){val id=uid();Photo(id,"$id.original",Codec.MAX_MEDIA_BYTES,"0".repeat(64),"video/mp4",1)}
        store.draft(Archive(name="large metadata only",photos=ps));store.selectProtectionFolder(tree);store.protectNow()
        assertEquals("保护失败",store.protection.value.status);assertTrue(provider.base.listFiles().orEmpty().isEmpty())
    }
    @Test fun reopeningIsPendingUntilVerifiedAgain()=runBlocking {
        protect();val reopened=Store(app);assertEquals("待保护",reopened.protection.value.status)
        provider.revoked=true;reopened.protectNow(forceVerify=true);assertEquals("保护失败",reopened.protection.value.status)
    }
    @Test fun concurrentSaveCannotBeDroppedOrMistakenForProtectedSnapshot()=runBlocking {
        val a=protect();store.save(a.copy(photos=a.photos+original("next")))
        val entered=CountDownLatch(1);val release=CountDownLatch(1);provider.readBarrier=entered to release
        val copying=async(Dispatchers.Default){store.protectNow(forceVerify=true)}
        assertTrue(entered.await(10,TimeUnit.SECONDS))
        val saving=async(Dispatchers.Default){store.save(a.copy(name="最后一次修改"))}
        release.countDown();copying.await();saving.await()
        assertEquals("待保护",store.protection.value.status)
        store.protectNow();assertTrue(store.protection.value.protected)
        val last=store.inspectProtectionFolder(tree).first();store.restoreProtectionFolder(tree,last.name){}
        assertEquals("最后一次修改",store.load().active.single().name)
    }
    @Test fun rapidDraftLoopCoalescesAndSurvivesSchedulerRestart()=runBlocking {
        store.selectProtectionFolder(tree)
        val a=Archive(name="连续编辑")
        repeat(40){store.draft(a.copy(name="连续编辑 $it"))}
        val worker=launch{store.runAutomaticProtection()}
        withTimeout(10000){while(!store.protection.value.protected)delay(50)}
        worker.cancelAndJoin();assertEquals(1,snapshots().size)
        val next=Store(app);val freshWorker=launch{next.runAutomaticProtection()}
        withTimeout(10000){while(!next.protection.value.protected)delay(50)}
        freshWorker.cancelAndJoin();assertEquals(1,snapshots().size)
        val choice=next.inspectProtectionFolder(tree).single();next.restoreProtectionFolder(tree,choice.name){}
        assertEquals("连续编辑 39",next.load().drafts.single().name)
    }
    @Test fun interruptedManifestCanRetryUnchangedLibrary()=runBlocking {
        val a=protect();val before=externalFiles()
        store.save(a.copy(name="new version"));provider.failManifestWrite=true;store.protectNow()
        assertFalse(store.protection.value.protected)
        provider.failManifestWrite=false;store.protectNow();assertTrue(store.protection.value.toString(),store.protection.value.protected)
        assertEquals(2,store.inspectProtectionFolder(tree).size)
        val manifestCount=snapshots().size;store.protectNow();store.protectNow();assertEquals(manifestCount,snapshots().size)
        before.forEach{(name,bytes)->assertArrayEquals(bytes,File(provider.base,name).readBytes())}
    }
    @Test fun sameLengthAndTimestampCorruptionIsRepairedWithoutOverwritingOldBlob()=runBlocking {
        val a=protect();val blob=provider.base.walkTopDown().single{it.name.endsWith(".original")};val time=blob.lastModified()
        blob.writeBytes(ByteArray(blob.length().toInt()){42});blob.setLastModified(time)
        store.save(a.copy(name="new metadata"));store.protectNow();assertTrue(store.protection.value.protected)
        assertEquals(2,provider.base.walkTopDown().count{it.name.endsWith(".original")})
        val choice=store.inspectProtectionFolder(tree).first();store.restoreProtectionFolder(tree,choice.name){}
        assertEquals(a.photos.single().sha256,sha(File(store.photoDir,a.photos.single().file)))
        assertTrue(blob.readBytes().all{it==42.toByte()})
    }
    @Test fun selectingActualRecoverySubfolderFindsExistingSnapshots()=runBlocking {
        protect()
        val direct=Documents.buildTreeDocumentUri("com.android.externalstorage.documents","primary:Documents/"+RecoveryFolder.DIRECTORY)
        assertEquals(1,store.inspectProtectionFolder(direct).size)
        store.selectProtectionFolder(direct);store.protectNow();assertTrue(store.protection.value.protected)
        assertFalse(File(provider.base,RecoveryFolder.DIRECTORY+"/"+RecoveryFolder.DIRECTORY).exists())
    }
    @Test fun successfulImportIsNotProtectedUntilDurablyLinkedToDraft()=runBlocking {
        store.selectProtectionFolder(tree)
        val file=File(app.cacheDir,"synthetic.png")
        val bitmap=android.graphics.Bitmap.createBitmap(8,8,android.graphics.Bitmap.Config.ARGB_8888)
        file.outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        val result=store.importPhotos(listOf(Uri.fromFile(file)));assertTrue(result.errors.isEmpty())
        val reopened=Store(app);reopened.protectNow();assertEquals("待核对",reopened.protection.value.status)
        reopened.draft(Archive(name="durable draft",photos=result.photos));reopened.protectNow();assertTrue(reopened.protection.value.protected)
        assertEquals(1,reopened.protection.value.mediaCount)
    }
    @Test fun partialImportFailureRemainsVisibleAfterProtectingSuccessfulMedia()=runBlocking {
        store.selectProtectionFolder(tree)
        val result=store.importPhotos(listOf(Uri.fromFile(File(app.cacheDir,"missing.png"))))
        assertEquals(1,result.errors.size);store.draft(Archive(name="empty draft",photos=result.photos));store.protectNow()
        assertEquals("待核对",store.protection.value.status)
        store.acknowledgeImportWarning();store.protectNow();assertTrue(store.protection.value.protected)
    }
    @Test fun incompleteImportWarningSurvivesClearDataAndRecovery()=runBlocking {
        store.selectProtectionFolder(tree)
        store.importPhotos(listOf(Uri.fromFile(File(app.cacheDir,"missing-original.png"))))
        store.draft(Archive(name="partial import"));store.protectNow();assertEquals("待核对",store.protection.value.status)
        val choice=store.inspectProtectionFolder(tree).single()
        app.getSharedPreferences("device",0).edit().clear().putString("generation",uid()).commit()
        val fresh=Store(app);fresh.restoreProtectionFolder(tree,choice.name){};fresh.selectProtectionFolder(tree);fresh.protectNow()
        assertEquals("待核对",fresh.protection.value.status)
        fresh.acknowledgeImportWarning();fresh.protectNow();assertTrue(fresh.protection.value.protected)
    }
    @Test fun recoveryChooserKeepsOnlyRecentSummariesWithoutDeletingHistory()=runBlocking {
        protect();val original=snapshots().single();val hash=original.name.substringAfterLast('-')
        repeat(105){index->original.copyTo(File(original.parentFile,"liuce-v1-${1900000000000L+index}-$hash"))}
        val choices=store.inspectProtectionFolder(tree)
        assertEquals(100,choices.size);assertEquals(1900000000104L,choices.first().time)
        assertEquals(106,snapshots().size)
    }
    @Test fun rejectsCloudAndPrivateExternalDirectories() {
        assertTrue(runCatching{RecoveryFolder.validateLocalTree(Documents.buildTreeDocumentUri("cloud.provider","cloud:folder"))}.isFailure)
        assertTrue(runCatching{RecoveryFolder.validateLocalTree(Documents.buildTreeDocumentUri("com.android.externalstorage.documents","primary:Android/data/dev.h516.shicang"))}.isFailure)
    }

    class LocalDocuments(val base:File):DocumentsProvider() {
        var revoked=false;var failWrite=false;var failManifestWrite=false;var corruptReadback=false;var blobWrites=0
        var readBarrier:Pair<CountDownLatch,CountDownLatch>?=null
        private fun checkAccess(){if(revoked)throw SecurityException("fixture access revoked")}
        private fun file(id:String)=if(id=="primary:Documents")base else File(base,id.removePrefix("primary:Documents/"))
        private fun id(file:File)="primary:Documents"+(if(file==base)"" else "/"+file.relativeTo(base).path)
        private val columns=arrayOf(Documents.Document.COLUMN_DOCUMENT_ID,Documents.Document.COLUMN_DISPLAY_NAME,Documents.Document.COLUMN_SIZE,Documents.Document.COLUMN_LAST_MODIFIED,Documents.Document.COLUMN_MIME_TYPE)
        private fun cursor(files:List<File>,projection:Array<out String>?):MatrixCursor {
            val cols=projection ?: columns
            return MatrixCursor(cols).apply{files.forEach{f->addRow(cols.map{column->when(column){Documents.Document.COLUMN_DOCUMENT_ID->id(f);Documents.Document.COLUMN_DISPLAY_NAME->f.name;Documents.Document.COLUMN_SIZE->f.length();Documents.Document.COLUMN_LAST_MODIFIED->f.lastModified();Documents.Document.COLUMN_MIME_TYPE->if(f.isDirectory)Documents.Document.MIME_TYPE_DIR else "application/octet-stream";else->0}}.toTypedArray())}}
        }
        override fun onCreate()=true
        override fun queryRoots(projection:Array<out String>?)=MatrixCursor(arrayOf(Documents.Root.COLUMN_ROOT_ID))
        override fun queryDocument(documentId:String,projection:Array<out String>?)=cursor(listOf(file(documentId)),projection).also{checkAccess()}
        override fun queryChildDocuments(parentDocumentId:String,projection:Array<out String>?,sortOrder:String?)=cursor(file(parentDocumentId).listFiles().orEmpty().toList(),projection).also{checkAccess()}
        override fun createDocument(parentDocumentId:String,mimeType:String,displayName:String):String {
            checkAccess();val f=File(file(parentDocumentId),displayName);check(!f.exists()){"must never overwrite"}
            if(mimeType==Documents.Document.MIME_TYPE_DIR)check(f.mkdir()) else check(f.createNewFile());return id(f)
        }
        override fun openDocument(documentId:String,mode:String,signal:CancellationSignal?):ParcelFileDescriptor {
            checkAccess();val f=file(documentId)
            if(mode.contains('w')) {
                if(f.name.endsWith(".original"))blobWrites++
                if(failWrite || (failManifestWrite && f.name.endsWith(".json"))){f.writeText("interrupted partial");throw FileNotFoundException("fixture insufficient storage")}
            } else {
                readBarrier?.let{readBarrier=null;it.first.countDown();check(it.second.await(10,TimeUnit.SECONDS))}
                if(corruptReadback)f.writeText("corrupted readback")
            }
            return ParcelFileDescriptor.open(f,ParcelFileDescriptor.parseMode(mode))
        }
        override fun isChildDocument(parentDocumentId:String,documentId:String)=documentId.startsWith(parentDocumentId+"/")
    }
}
