package dev.h516.shicang

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class BackupStoreTest {
    private val openedStores=mutableListOf<Store>()
    private fun newStore()=Store(app).also{openedStores+=it}
    @org.junit.After fun closeStores(){runBlocking{openedStores.forEach{it.close()}}}
    private lateinit var app: Application
    private lateinit var store: Store
    private lateinit var provider: BackupProvider
    private val destination=Uri.parse("content://backup.fixture/document/backup")

    @Before fun setup() {
        app=RuntimeEnvironment.getApplication()
        app.getSharedPreferences("device",0).edit().clear().putString("generation",uid()).commit()
        store=newStore()
        provider=BackupProvider(File(app.cacheDir,"previous.zip").apply { writeText("existing backup") })
        provider.attachInfo(app,ProviderInfo().apply { authority="backup.fixture" })
        ShadowContentResolver.registerProviderInternal("backup.fixture",provider)
    }
    @Test fun closedModelCannotDownloadOrReopen()=runBlocking {
        val model=LocalTextModel(app);model.close();model.close()
        assertEquals("本机模型已关闭",runCatching{model.download{error("unexpected network progress")}}.exceptionOrNull()?.message)
        assertEquals("本机模型已关闭",runCatching{model.suggest(Library(),Archive())}.exceptionOrNull()?.message)
        val a=Archive(name="关闭后文字仍可用")
        assertEquals(listOf(a.id),model.search(Library(archives=listOf(a)),ArchiveQuery(text="关闭后",semantic=true)).map{it.archive.id})
        assertFalse(File(app.filesDir,"local-text-model-bge-v1").exists())
    }
    @Test fun closeIsIdempotentAndDoesNotDeleteData()=runBlocking {
        val a=Archive(name="资源关闭合成档案");store.save(a)
        store.close();store.close()
        assertTrue(runCatching{store.load()}.isFailure)
        assertEquals(a.name,newStore().load().active.single().name)
    }
    private suspend fun rejectWithoutTouchingDestination(expectedMessage:String) {
        val previous=provider.file.readBytes()
        val failure=runCatching { store.backup(destination) {} }.exceptionOrNull()
        assertTrue(failure.toString(),failure is IllegalArgumentException)
        assertTrue(failure!!.message!!,failure.message!!.contains(expectedMessage))
        assertEquals(0,provider.writeOpens)
        assertEquals(0,provider.deleteCalls)
        assertArrayEquals(previous,provider.file.readBytes())
        assertEquals(0L,store.lastBackup())
        assertTrue(app.cacheDir.listFiles().orEmpty().none { it.name.startsWith("backup-") })
    }
    @Test fun oversizedMetadataFailsBeforeReadingMediaOrOpeningDestination()=runBlocking {
        // No large file is created. Missing files must not mask the known capacity failure.
        val photos=List(20) { val id=uid(); Photo(id,"$id.original",Codec.MAX_MEDIA_BYTES,"0".repeat(64),"video/mp4",1000) }
        store.draft(Archive(name="synthetic large library",photos=photos))
        assertTrue(runCatching{store.validateBackupCapacity()}.isFailure)
        rejectWithoutTouchingDestination("展开容量超过恢复上限")
    }
    @Test fun damagedMediaDoesNotDeleteOrOverwritePreviousBackup()=runBlocking {
        val id=uid()
        val file=File(store.photoDir,"$id.original").apply { writeText("synthetic") }
        store.save(Archive(name="synthetic",photos=listOf(Photo(id,file.name,file.length(),"0".repeat(64),"image/png"))))
        rejectWithoutTouchingDestination("照片校验失败")
    }
    @Test fun cancellationBeforeDestinationWritePreservesPreviousBackup()=runBlocking {
        val id=uid()
        val file=File(store.photoDir,"$id.original").apply { writeText("synthetic media bytes") }
        store.save(Archive(name="synthetic",photos=listOf(Photo(id,file.name,file.length(),sha(file),"image/png"))))
        val previous=provider.file.readBytes()
        val failure=runCatching {
            store.backup(destination) { if(it.startsWith("打包媒体")) throw CancellationException("fixture cancellation") }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertEquals(0,provider.writeOpens)
        assertEquals(0,provider.deleteCalls)
        assertArrayEquals(previous,provider.file.readBytes())
        assertEquals(0L,store.lastBackup())
        assertEquals(1,store.load().active.size)
        assertTrue(app.cacheDir.listFiles().orEmpty().none { it.name.startsWith("backup-") })
    }
    @Test fun importedOriginalSurvivesSourceDeletionReopenAndBackupRestore()=runBlocking {
        val source=File(app.cacheDir,"synthetic.png")
        val bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888)
        source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
        bitmap.recycle()
        val originalHash=sha(source)
        val imported=store.importPhotos(listOf(Uri.fromFile(source)))
        assertTrue(imported.errors.toString(),imported.errors.isEmpty())
        val photo=imported.photos.single()
        assertTrue(source.delete())
        assertEquals(originalHash,sha(File(store.photoDir,photo.file)))
        store.save(Archive(name="synthetic photo",photos=listOf(photo)))
        assertEquals(photo,newStore().load().active.single().photos.single())
        store.backup(destination) {}
        assertEquals(1,provider.writeOpens)
        assertTrue(store.lastBackup()>0)
        store.restore(destination) {}
        assertEquals(originalHash,sha(File(store.photoDir,store.load().active.single().photos.single().file)))
    }

    class BackupProvider(val file:File):ContentProvider() {
        var writeOpens=0
        var deleteCalls=0
        override fun onCreate()=true
        override fun getType(uri:Uri)="application/zip"
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor?=null
        override fun insert(uri:Uri,values:ContentValues?):Uri?=null
        override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
        override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?):Int { deleteCalls++;return 0 }
        override fun call(method:String,arg:String?,extras:Bundle?):Bundle { deleteCalls++;return Bundle() }
        override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
            if(mode.contains('w')) writeOpens++
            return ParcelFileDescriptor.open(file,ParcelFileDescriptor.parseMode(mode))
        }
    }
}
