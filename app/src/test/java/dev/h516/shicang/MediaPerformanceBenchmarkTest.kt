package dev.h516.shicang

import android.app.Application
import android.content.pm.ProviderInfo
import android.database.sqlite.SQLiteDatabase
import android.provider.DocumentsContract
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.SQLiteMode
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime

/** Host SQLite/JSON/locking diagnostics, NOT Android device or video-codec benchmarks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class MediaPerformanceBenchmarkTest {
    @Test fun labelWorkAndProtectionContention()=runBlocking {
        val app=RuntimeEnvironment.getApplication()
        val generation=uid()
        app.getSharedPreferences("device",0).edit().clear().putString("generation",generation).commit()
        val store=Store(app)
        val db=SQLiteDatabase.openDatabase(File(app.filesDir,"libraries/$generation/library.db").path,null,SQLiteDatabase.OPEN_READWRITE)
        try {
            val label=store.label("tag","目标标签")
            db.beginTransaction()
            try {
                repeat(1000){i->
                    val a=Archive(name="合成档案 $i",tags=if(i<20)listOf(label)else emptyList(),fields=List(6){Field(name="字段 $it",value="合成文本 ".repeat(24))},photos=List(4){val id=uid();Photo(id,"$id.original",1024,"0".repeat(64),"video/mp4",1000)})
                    db.execSQL("INSERT INTO objects(kind,id,json) VALUES(?,?,?)",arrayOf("archive",a.id,Codec.archive(a).toString()))
                }
                db.setTransactionSuccessful()
            }finally{db.endTransaction()}
            val add=mutableListOf<Double>();val rename=mutableListOf<Double>()
            repeat(9){i->
                add+=measureNanoTime{store.label("tag","新增 $i")}/1e6
                rename+=measureNanoTime{store.renameLabel(label,"目标标签 $i")}/1e6
            }
            db.execSQL("CREATE TABLE perf_writes(kind TEXT)")
            db.execSQL("CREATE TRIGGER perf_count AFTER INSERT ON objects BEGIN INSERT INTO perf_writes VALUES(NEW.kind); END")
            val delete=measureNanoTime{store.deleteLabel(label)}/1e6
            val writes=db.rawQuery("SELECT COUNT(*) FROM perf_writes WHERE kind='archive'",null).use{it.moveToFirst();it.getInt(0)}
            println("PERF labels archives=1000 photos=4000 add_ms=${add.drop(2)} rename_ms=${rename.drop(2)} delete_ms=$delete archive_writes=$writes")
            // Replace synthetic metadata with one real immutable local file for protection checks.
            db.execSQL("DELETE FROM objects")
            val file=File(store.photoDir,"${uid()}.original").apply{writeText("synthetic original")}
            val p=Photo(file.name.removeSuffix(".original"),file.name,file.length(),sha(file),"video/mp4",1000)
            store.save(Archive(name="保护快照",photos=listOf(p)))
            val provider=ProtectionStoreTest.LocalDocuments(File(app.cacheDir,"perf-external-${uid()}").apply{mkdirs()})
            provider.attachInfo(app,ProviderInfo().apply{authority="com.android.externalstorage.documents";exported=true;grantUriPermissions=true;readPermission="android.permission.MANAGE_DOCUMENTS";writePermission="android.permission.MANAGE_DOCUMENTS"})
            ShadowContentResolver.registerProviderInternal("com.android.externalstorage.documents",provider)
            store.selectProtectionFolder(DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents","primary:Documents"))
            store.protectNow()
            val entered=CountDownLatch(1);val release=CountDownLatch(1)
            provider.readBarrier=entered to release
            val copying=async(Dispatchers.Default){store.protectNow()}
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val unblock=launch(Dispatchers.Default){delay(750);release.countDown()}
            val load=measureNanoTime{store.load()}/1e6
            copying.await();unblock.join()
            println("PERF protection provider_delay_ms=750 load_ms=$load source_sha=${sha(file)}")
        }finally{db.close();store.close();File(app.filesDir,"libraries/$generation").deleteRecursively()}
    }
    @Test fun streamedBackupRetainsFullReadback()=runBlocking {
        val app=RuntimeEnvironment.getApplication();val generation=uid()
        app.getSharedPreferences("device",0).edit().clear().putString("generation",generation).commit()
        val store=Store(app);val source=File(store.photoDir,"${uid()}.original")
        java.io.RandomAccessFile(source,"rw").use{it.setLength(128L*1024*1024);it.seek(source.length()-1);it.write(42)}
        val digest=sha(source);val p=Photo(source.name.removeSuffix(".original"),source.name,source.length(),digest,"video/mp4",1000)
        val zip=File(app.cacheDir,"${uid()}.zip")
        val provider=BackupStoreTest.BackupProvider(zip).apply{attachInfo(app,ProviderInfo().apply{authority="performance.backup"})}
        ShadowContentResolver.registerProviderInternal("performance.backup",provider)
        try {
            store.save(Archive(name="128MiB streamed backup",photos=listOf(p)))
            val times=List(4){measureNanoTime{store.backup(android.net.Uri.parse("content://performance.backup/file")) {}}/1e6}
            assertEquals(digest,sha(source))
            println("PERF backup bytes=${source.length()} backup_ms=${times.drop(1)} zip_bytes=${zip.length()}")
        }finally{store.close();zip.delete();File(app.filesDir,"libraries/$generation").deleteRecursively()}
    }

}
