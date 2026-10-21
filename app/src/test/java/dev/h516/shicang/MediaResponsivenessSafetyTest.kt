package dev.h516.shicang

import android.app.Application
import android.content.pm.ProviderInfo
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
class MediaResponsivenessSafetyTest {
    private val app get()=RuntimeEnvironment.getApplication()
    private val tree=DocumentsContract.buildTreeDocumentUri("com.android.externalstorage.documents","primary:Documents")
    private suspend fun fixture(block:suspend (Store,ProtectionStoreTest.LocalDocuments,Archive)->Unit) {
        app.getSharedPreferences("device",0).edit().clear().putString("generation",uid()).commit()
        val store=Store(app)
        val provider=ProtectionStoreTest.LocalDocuments(File(app.cacheDir,"responsive-${uid()}").apply{mkdirs()})
        provider.attachInfo(app,ProviderInfo().apply{authority="com.android.externalstorage.documents";exported=true;grantUriPermissions=true;readPermission="android.permission.MANAGE_DOCUMENTS";writePermission="android.permission.MANAGE_DOCUMENTS"})
        ShadowContentResolver.registerProviderInternal("com.android.externalstorage.documents",provider)
        val file=File(store.photoDir,"${uid()}.original").apply{writeText("immutable original")}
        val p=Photo(file.name.removeSuffix(".original"),file.name,file.length(),sha(file),"video/mp4",1000)
        val a=Archive(name="当前档案",photos=listOf(p))
        try {store.save(a);store.selectProtectionFolder(tree);store.protectNow();block(store,provider,a)}finally{store.close()}
    }
    @Test fun unchangedForegroundReturnDoesNotRestartVerifiedProtectionButDirtyDataDoes()=runBlocking {
        fixture {store,provider,a->
            val entered=CountDownLatch(1);val release=CountDownLatch(1)
            provider.readBarrier=entered to release
            try {
                withTimeout(1000){store.resumeProtection()}
                assertEquals(1L,entered.count)
                store.save(a.copy(name="需要自动续上"))
                val resumed=async(Dispatchers.Default){store.resumeProtection()}
                assertTrue(entered.await(10,TimeUnit.SECONDS));release.countDown();resumed.await()
                assertTrue(store.protection.value.protected)
            }finally{release.countDown()}
        }
    }
    @Test fun slowExternalReadDoesNotBlockMetadataAndNeverMarksNewerRevisionProtected()=runBlocking {
        fixture {store,provider,a->
            val entered=CountDownLatch(1);val release=CountDownLatch(1);provider.readBarrier=entered to release
            val copying=async(Dispatchers.Default){store.protectNow()}
            try {
                assertTrue(entered.await(10,TimeUnit.SECONDS))
                withTimeout(2000){store.label("tag","保护期间新增");store.save(a.copy(name="未被旧快照覆盖"));assertEquals("未被旧快照覆盖",store.load().active.single().name)}
                assertFalse(copying.isCompleted)
            }finally{release.countDown();copying.await()}
            assertEquals("待保护",store.protection.value.status)
            assertTrue(app.getSharedPreferences("device",0).getBoolean("protectionDirty",false))
            store.protectNow();assertTrue(store.protection.value.protected)
            val choice=store.inspectProtectionFolder(tree).first();store.restoreProtectionFolder(tree,choice.name){}
            assertEquals("未被旧快照覆盖",store.load().active.single().name)
        }
    }
    @Test fun purgePinsSnapshotOriginalsAndCancelledPurgeDoesNotDeleteThem()=runBlocking {
        fixture {store,provider,a->
            store.trash(a.id)
            val original=File(store.photoDir,a.photos.single().file)
            val entered=CountDownLatch(1);val release=CountDownLatch(1);provider.readBarrier=entered to release
            val copying=async(Dispatchers.Default){store.protectNow()}
            try {
                assertTrue(entered.await(10,TimeUnit.SECONDS))
                val purge=launch(Dispatchers.Default){store.purge()}
                delay(100);assertFalse(purge.isCompleted);assertTrue(original.isFile)
                purge.cancelAndJoin();assertTrue(original.isFile)
                withTimeout(2000){assertEquals(1,store.load().archives.size)}
            }finally{release.countDown();copying.await()}
            assertTrue(store.protection.value.protected)
            store.purge();assertFalse(original.exists());assertEquals("待保护",store.protection.value.status)
        }
    }
    @Test fun restoreAndUndoWaitForPinnedSnapshotThenRemainConsistent()=runBlocking {
        fixture {store,provider,a->
            val originalDirectory=store.photoDir
            val choice=store.inspectProtectionFolder(tree).first()
            val entered=CountDownLatch(1);val release=CountDownLatch(1);provider.readBarrier=entered to release
            val copying=async(Dispatchers.Default){store.protectNow()}
            assertTrue(entered.await(10,TimeUnit.SECONDS))
            val restore=async(Dispatchers.Default){store.restoreProtectionFolder(tree,choice.name){}}
            try {delay(100);assertFalse(restore.isCompleted);assertEquals(originalDirectory,store.photoDir)}finally{release.countDown();copying.await();restore.await()}
            assertNotEquals(originalDirectory,store.photoDir);assertEquals(a.id,store.load().active.single().id)
            assertTrue(File(originalDirectory,a.photos.single().file).isFile)
            val entered2=CountDownLatch(1);val release2=CountDownLatch(1);provider.readBarrier=entered2 to release2
            val copying2=async(Dispatchers.Default){store.protectNow()}
            assertTrue(entered2.await(10,TimeUnit.SECONDS))
            val undo=async(Dispatchers.Default){store.undoRestore()}
            try {delay(100);assertFalse(undo.isCompleted)}finally{release2.countDown();copying2.await();undo.await()}
            assertEquals(originalDirectory,store.photoDir);assertEquals(a.id,store.load().active.single().id)
            assertEquals("待保护",store.protection.value.status)
        }
    }
    @Test fun cancellingProtectionKeepsPendingAndReleasesSnapshotPin()=runBlocking {
        fixture {store,provider,a->
            val entered=CountDownLatch(1);val release=CountDownLatch(1);provider.readBarrier=entered to release
            val copying=launch(Dispatchers.Default){store.protectNow()}
            assertTrue(entered.await(10,TimeUnit.SECONDS));copying.cancel();release.countDown();copying.join()
            assertEquals("待保护",store.protection.value.status)
            withTimeout(2000){store.trash(a.id);store.purge()}
            assertTrue(store.load().archives.isEmpty())
            store.protectNow();assertTrue(store.protection.value.protected)
        }
    }
}
