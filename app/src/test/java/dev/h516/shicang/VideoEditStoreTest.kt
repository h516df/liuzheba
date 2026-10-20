package dev.h516.shicang

import android.app.Application
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.SQLiteMode
import java.io.File

/** Storage/rollback tests only. Metadata is a synthetic fixture, not codec export proof. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,shadows=[VideoEditStoreTest.Metadata::class])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class VideoEditStoreTest {
    @Implements(MediaMetadataRetriever::class)
    class Metadata {
        @Implementation fun setDataSource(path:String) {require(File(path).isFile)}
        @Implementation fun extractMetadata(key:Int):String?=when(key){
            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH -> "1080"
            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT -> "1920"
            MediaMetadataRetriever.METADATA_KEY_DURATION -> "1200"
            MediaMetadataRetriever.METADATA_KEY_MIMETYPE -> "video/mp4"
            else -> null
        }
        @Implementation fun release(){}
    }
    private lateinit var app:Application
    private lateinit var store:Store
    private lateinit var archive:Archive
    private lateinit var source:File
    private lateinit var original:Photo
    private lateinit var edited:File
    @Before fun setup()=runBlocking {
        app=RuntimeEnvironment.getApplication();app.getSharedPreferences("device",0).edit().clear().putString("generation",uid()).commit();store=Store(app)
        val id=uid();source=File(store.photoDir,"$id.original").apply{writeText("synthetic source bytes")}
        original=Photo(id,source.name,source.length(),sha(source),"video/mp4",2000)
        val author=Author(name="合成作者");store.saveAuthor(author)
        archive=Archive(name="录屏档案",photos=listOf(original),cover=original.id,authorIds=listOf(author.id),fields=listOf(Field(name="备注",value="保留")))
        store.save(archive)
        edited=File(app.cacheDir,"${uid()}.mp4").apply{writeText("synthetic edited bytes")}
    }
    @After fun close()=runBlocking{store.close();edited.delete();Unit}
    @Test fun appendKeepsOriginalMetadataAndAuthorRelation()=runBlocking {
        val before=source.readBytes();val result=store.appendEditedVideo(archive.id,original,source,edited)
        val saved=store.load().active.single()
        assertEquals(listOf(original,result),saved.photos);assertEquals(archive.authorIds,saved.authorIds);assertEquals(archive.fields,saved.fields);assertEquals(original.id,saved.cover)
        assertArrayEquals(before,source.readBytes());assertArrayEquals(edited.readBytes(),File(store.photoDir,result.file).readBytes());assertNotEquals(original.id,result.id)
    }
    @Test fun changesMadeSinceEditorOpenedAreNotOverwritten()=runBlocking {
        store.save(archive.copy(name="新名称",fields=listOf(Field(name="新备注",value="新值"))))
        store.appendEditedVideo(archive.id,original,source,edited)
        assertEquals("新名称",store.load().active.single().name);assertEquals("新值",store.load().active.single().fields.single().value)
    }
    @Test fun unsavedDraftBlocksWithoutTouchingIt()=runBlocking {
        val draft=archive.copy(name="未保存");store.draft(draft)
        assertTrue(runCatching{store.appendEditedVideo(archive.id,original,source,edited)}.isFailure)
        assertEquals(draft,store.load().drafts.single());assertEquals(1,store.load().active.single().photos.size);assertEquals(1,store.photoDir.listFiles()!!.size)
    }
    @Test fun deletedArchiveRejectedAndOriginalKept()=runBlocking {
        store.trash(archive.id);assertTrue(runCatching{store.appendEditedVideo(archive.id,original,source,edited)}.isFailure);assertTrue(source.exists());assertEquals(1,store.photoDir.listFiles()!!.size)
    }
    @Test fun foreignGenerationPathRejected()=runBlocking {
        val foreign=File(app.cacheDir,source.name).apply{writeBytes(source.readBytes())}
        try {assertTrue(runCatching{store.appendEditedVideo(archive.id,original,foreign,edited)}.isFailure);assertEquals(1,store.load().active.single().photos.size)}finally{foreign.delete()}
    }
    @Test fun tamperedSourceRejectedWithoutAddingCopy()=runBlocking {
        source.appendText(" changed")
        assertTrue(runCatching{store.appendEditedVideo(archive.id,original,source,edited)}.isFailure)
        assertEquals(listOf(original),store.load().active.single().photos);assertEquals(1,store.photoDir.listFiles()!!.size)
    }
    @Test fun emptyOutputRejected()=runBlocking {
        edited.writeBytes(byteArrayOf());assertTrue(runCatching{store.appendEditedVideo(archive.id,original,source,edited)}.isFailure);assertEquals(1,store.photoDir.listFiles()!!.size)
    }
    @Test fun databaseFailureRollsBackAndDeletesOnlyOwnNewFile()=runBlocking {
        val gen=app.getSharedPreferences("device",0).getString("generation",null)
        val db=android.database.sqlite.SQLiteDatabase.openDatabase(File(app.filesDir,"libraries/$gen/library.db").path,null,android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)
        try {
            db.execSQL("CREATE TRIGGER reject_edit BEFORE INSERT ON objects WHEN NEW.kind='archive' BEGIN SELECT RAISE(ABORT,'synthetic rejection'); END")
            assertTrue(runCatching{store.appendEditedVideo(archive.id,original,source,edited)}.isFailure)
            assertEquals(listOf(original),store.load().active.single().photos);assertEquals(listOf(source.name),store.photoDir.listFiles()!!.map{it.name});assertTrue(edited.exists())
        }finally{db.close()}
    }
}
