package dev.h516.shicang

import android.app.Application
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.*
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rCN-w320dp-h900dp-xhdpi")
@OptIn(ExperimentalCoroutinesApi::class,coil3.annotation.DelicateCoilApi::class)
abstract class DiscoveryFeedUiFixture {
    @get:Rule val compose=createComposeRule()
    @Before fun bind(){Dispatchers.setMain(android.os.Handler(android.os.Looper.getMainLooper()).asCoroutineDispatcher().immediate)}
    @After fun reset(){Dispatchers.resetMain()}
    protected fun stableRandomPreviewReturnsFromDetailsAndSwitchesToCover()=fixture {model,a->
        var inFeed by mutableStateOf(true)
        var displayed by mutableStateOf(a)
        val seen=mutableMapOf<String,String>()
        compose.setContent {
            MaterialTheme {
                val state=rememberSaveableStateHolder()
                if(inFeed) state.SaveableStateProvider("discovery") {
                    ArchiveFeed(listOf(displayed),"",model,Appearance(),emptyMap(),{id,p->seen[id]=p},{}, {},{inFeed=false},{},{},{},{},false)
                } else TextButton(onClick={inFeed=true}){Text("返回发现")}
            }
        }
        compose.waitUntil(10000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();seen[a.id]!=null}
        val first=seen[a.id]!!
        val fixedCover=a.photos.drop(1).first {it.id!=first}.id
        compose.runOnIdle {displayed=a.copy(cover=fixedCover)}
        compose.loadedPhotoWithControls()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("随机预览 · 已开启").assertExists()
        compose.onNodeWithText("查看资料").performClick()
        compose.onNodeWithText("打开完整档案").performClick()
        compose.runOnIdle {seen.clear()}
        compose.onNodeWithText("返回发现").performClick()
        compose.waitUntil(10000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();seen[a.id]!=null}
        assertEquals(first,seen[a.id])
        compose.onNodeWithText("${a.photos.indexOfFirst{it.id==first}+1} / ${a.photos.size}").assertExists()
        compose.loadedPhotoWithControls()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("随机预览 · 已开启").performClick()
        compose.waitUntil(10000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();seen[a.id]==fixedCover}
        compose.onNodeWithText("${a.photos.indexOfFirst{it.id==fixedCover}+1} / ${a.photos.size}").assertExists()
        compose.loadedPhotoWithControls()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("固定封面 · 已开启").assertExists()
        val output=File("build/outputs/discovery-ui/fixed-cover-menu.png");output.parentFile?.mkdirs()
        compose.onRoot().captureRoboImage(output.absolutePath)
    }
    protected fun singleArchiveNextVisitUsesDifferentMedia()=fixture {model,a->
        val seen=mutableMapOf<String,String>()
        compose.setContent {MaterialTheme {ArchiveFeed(listOf(a),"",model,Appearance(),emptyMap(),{id,p->seen[id]=p},{},{},{},{},{},{},{},false)}}
        compose.waitUntil(10000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();seen[a.id]!=null}
        val first=seen[a.id]
        compose.loadedPhotoWithControls()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("下一份").performClick()
        compose.waitUntil(10000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();seen[a.id]!=first}
        assertTrue(seen[a.id] in a.photos.map{it.id})
    }
    protected fun missingFilesAreNotSelected()=fixture {model,a->
        model.photoFile(a.photos[0]).delete()
        model.photoFile(a.photos[1]).delete()
        val seen=mutableMapOf<String,String>()
        compose.setContent {MaterialTheme {ArchiveFeed(listOf(a),"",model,Appearance(),emptyMap(),{id,p->seen[id]=p},{},{},{},{},{},{},{},false)}}
        compose.waitUntil(10000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();seen[a.id]!=null}
        assertEquals(a.photos.last().id,seen[a.id])
    }
    protected fun videoPreviewWaitsForTapAndDoesNotPlayWhenInactive()=fixture {model,a->
        val video=a.copy(photos=listOf(a.photos.first().copy(mime="video/mp4")))
        var enabled by mutableStateOf(true)
        val seen=mutableMapOf<String,String>()
        compose.setContent {MaterialTheme {ArchiveFeed(listOf(video),"",model,Appearance(),emptyMap(),{id,p->seen[id]=p},{},{},{},{},{},{},{},false,enabled)}}
        compose.waitUntil(10000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();seen[a.id]!=null}
        compose.onNodeWithText("播放视频").assertExists()
        compose.runOnIdle{enabled=false}
        compose.onNodeWithText("播放视频").assertDoesNotExist()
        compose.runOnIdle{enabled=true}
        compose.onNodeWithText("播放视频").assertExists()
    }
    private fun fixture(check:(AppModel,Archive)->Unit) {
        val app=RuntimeEnvironment.getApplication()
        // Initialize native bitmap JNI on the test thread before Coil decodes neighbors in parallel.
        android.graphics.Bitmap.createBitmap(1,1,android.graphics.Bitmap.Config.ARGB_8888).apply {
            copyPixelsToBuffer(java.nio.ByteBuffer.allocate(4));recycle()
        }
        app.getSharedPreferences("device",0).edit().clear().putString("generation",uid()).putBoolean("frameHintV2",true).apply()
        val store=Store(app)
        val photos=(1..3).map {n->
            val id=uid();val file=File(store.photoDir,"$id.original")
            val bitmap=java.awt.image.BufferedImage(40,60,java.awt.image.BufferedImage.TYPE_INT_RGB)
            bitmap.createGraphics().apply{color=java.awt.Color(40*n,60,100);fillRect(0,0,40,60);dispose()}
            javax.imageio.ImageIO.write(bitmap,"png",file)
            Photo(id,file.name,file.length(),java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString(""){"%02x".format(it)},"image/png")
        }
        val a=Archive(name="合成发现档案",photos=photos,cover=photos.first().id)
        runBlocking{store.save(a)}
        val imageLoader=coil3.ImageLoader.Builder(app).components{add(coil3.decode.BitmapFactoryDecoder.Factory())}.build()
        coil3.SingletonImageLoader.setUnsafe(imageLoader)
        runBlocking {photos.forEach {photo->
            val result=imageLoader.execute(coil3.request.ImageRequest.Builder(app).data(File(store.photoDir,photo.file)).build())
            check(result is coil3.request.SuccessResult){"Synthetic fixture decode failed: $result"}
        }}
        val model=AppModel(app);val models=androidx.lifecycle.ViewModelStore().apply{put("fixture",model)}
        try {
            compose.waitUntil(10000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();model.state.value.ready}
            check(model,a)
        } finally {models.clear();compose.waitUntil(10000){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();model.resourcesClosed};runBlocking{store.close()};imageLoader.shutdown()}
    }
}

// One native-rendering fixture per class lets Gradle forkEvery=1 isolate Android native state.
class DiscoveryReturnUiTest:DiscoveryFeedUiFixture() {
    @Test fun returnsToSameMediaAndCanUseFixedCover()=stableRandomPreviewReturnsFromDetailsAndSwitchesToCover()
}
class DiscoveryNextUiTest:DiscoveryFeedUiFixture() {
    @Test fun nextArchiveVisitChangesMedia()=singleArchiveNextVisitUsesDifferentMedia()
}
class DiscoveryMissingUiTest:DiscoveryFeedUiFixture() {
    @Test fun missingOriginalsAreExcluded()=missingFilesAreNotSelected()
}
class DiscoveryVideoUiTest:DiscoveryFeedUiFixture() {
    @Test fun videoIsManualAndInactivePreviewDoesNotPlay()=videoPreviewWaitsForTapAndDoesNotPlayWhenInactive()
}
