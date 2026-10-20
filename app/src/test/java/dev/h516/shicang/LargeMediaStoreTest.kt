package dev.h516.shicang

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.RandomAccessFile

/** Real >1GiB streamed file I/O and storage; synthetic metadata, never a codec claim. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,shadows=[VideoEditStoreTest.Metadata::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class LargeMediaStoreTest {
    class Source(private val file:File,private val size:Long?):ContentProvider() {
        override fun onCreate()=true
        override fun getType(uri:Uri)="video/mp4"
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):Cursor=MatrixCursor(arrayOf(OpenableColumns.SIZE)).apply{addRow(arrayOf<Any?>(size))}
        override fun openFile(uri:Uri,mode:String)=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
        override fun insert(uri:Uri,values:ContentValues?):Uri?=null
        override fun delete(uri:Uri,selection:String?,selectionArgs:Array<out String>?)=0
        override fun update(uri:Uri,values:ContentValues?,selection:String?,selectionArgs:Array<out String>?)=0
    }
    @Test fun declaredSixGBPlusOneRejectsWithoutTouchingSourceAndLongMetadataRoundTrips()=runBlocking {
        val app=RuntimeEnvironment.getApplication();app.getSharedPreferences("device",0).edit().clear().putString("generation",uid()).commit()
        val store=Store(app);val source=File(app.cacheDir,"${uid()}.mp4").apply{writeText("source preserved")}
        try {
            val provider=Source(source,6_000_000_001L).apply{attachInfo(app,ProviderInfo().apply{authority="large.declared"})}
            ShadowContentResolver.registerProviderInternal("large.declared",provider)
            val result=store.importPhotos(listOf(Uri.parse("content://large.declared/video")))
            assertTrue(result.photos.isEmpty());assertTrue(result.errors.single().contains("6GB"))
            assertEquals("source preserved",source.readText());assertTrue(store.photoDir.listFiles().orEmpty().isEmpty())
            listOf(1_073_741_825L,2_147_483_648L,4_294_967_296L,6_000_000_000L).forEach { size ->
                val id=uid();val p=Photo(id,"$id.original",size,"0".repeat(64),"video/mp4",1000)
                assertEquals(size,Codec.photo(Codec.photo(p)).bytes)
                assertTrue(runCatching{Codec.photo(Codec.photo(p.copy(bytes=6_000_000_001L)))}.isFailure)
            }
        } finally {store.close();source.delete();File(app.filesDir,"libraries").deleteRecursively();Unit}
    }
    @Test fun interruptedImportDeletesOnlyItsPartialCopyAndKeepsExistingOriginal()=runBlocking {
        val app=RuntimeEnvironment.getApplication();app.getSharedPreferences("device",0).edit().clear().putString("generation",uid()).commit()
        val store=Store(app);val source=File(app.cacheDir,"${uid()}.mp4").apply{writeText("source preserved")}
        val existing=File(store.photoDir,"${uid()}.original").apply{writeText("existing original")}
        try {
            val provider=Source(source,null).apply{attachInfo(app,ProviderInfo().apply{authority="large.cancel"})}
            ShadowContentResolver.registerProviderInternal("large.cancel",provider)
            val uri=Uri.parse("content://large.cancel/video")
            org.robolectric.Shadows.shadowOf(app.contentResolver).registerInputStream(uri,object:java.io.InputStream(){
                var calls=0
                override fun read():Int=throw UnsupportedOperationException()
                override fun read(b:ByteArray,off:Int,len:Int):Int {if(calls++>0)throw kotlinx.coroutines.CancellationException("fixture interruption");return 1024}
            })
            assertTrue(runCatching{store.importPhotos(listOf(uri))}.exceptionOrNull() is kotlinx.coroutines.CancellationException)
            assertEquals("source preserved",source.readText());assertEquals("existing original",existing.readText())
            assertEquals(listOf(existing.name),store.photoDir.listFiles().orEmpty().map{it.name})
        } finally {store.close();source.delete();File(app.filesDir,"libraries").deleteRecursively();Unit}
    }
    @Test fun unknownSizeVideoAboveOneGiBImportsAndFormatSixRestoresWithoutChangingSource()=runBlocking {
        val app=RuntimeEnvironment.getApplication();val generation=uid()
        app.getSharedPreferences("device",0).edit().clear().putString("generation",generation).commit()
        val store=Store(app);val root=File(app.filesDir,"libraries")
        val source=File(app.cacheDir,"${uid()}.mp4");val zip=File(app.cacheDir,"${uid()}.zip")
        val size=1024L*1024*1024+65537
        RandomAccessFile(source,"rw").use{it.setLength(size);it.seek(size-1);it.write(42)}
        val sourceHash=sha(source)
        try {
            val provider=Source(source,null).apply{attachInfo(app,ProviderInfo().apply{authority="large.source"})}
            ShadowContentResolver.registerProviderInternal("large.source",provider)
            val result=store.importPhotos(listOf(Uri.parse("content://large.source/video")))
            assertTrue(result.errors.toString(),result.errors.isEmpty());val p=result.photos.single()
            assertEquals(size,p.bytes);assertEquals(sourceHash,p.sha256)
            val a=Archive(name="合成大视频",photos=listOf(p));store.save(a);val saved=store.load()
            assertEquals(size,Codec.photo(Codec.photo(p)).bytes)
            val backup=BackupStoreTest.BackupProvider(zip).apply{attachInfo(app,ProviderInfo().apply{authority="large.backup"})}
            ShadowContentResolver.registerProviderInternal("large.backup",backup)
            val uri=Uri.parse("content://large.backup/backup")
            store.backup(uri){};store.restore(uri){}
            assertEquals(size,store.load().active.single().photos.single().bytes)
            assertEquals(sourceHash,sha(File(store.photoDir,p.file)))
            store.undoRestore();assertEquals(saved,store.load())
            assertEquals(size,source.length());assertEquals(sourceHash,sha(source))
        } finally {store.close();source.delete();zip.delete();root.deleteRecursively();Unit}
    }
}
