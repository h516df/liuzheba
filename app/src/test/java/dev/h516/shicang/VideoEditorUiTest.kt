package dev.h516.shicang

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.*
import java.io.File

/** Actual Compose controls with synthetic metadata. Not a real decoder/encoder acceptance test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,shadows=[VideoEditStoreTest.Metadata::class],qualifiers="zh-rCN-w412dp-h915dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@SQLiteMode(SQLiteMode.Mode.LEGACY)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VideoEditorUiTest {
    @get:Rule val compose=createComposeRule()
    @Before fun bind(){Dispatchers.setMain(android.os.Handler(android.os.Looper.getMainLooper()).asCoroutineDispatcher().immediate)}
    @After fun reset(){Dispatchers.resetMain()}
    @Test fun cropMaskPreviewAndReturnDoNotMutateOriginal()=render("video-edit-controls") {original,model,closed->
        compose.onNodeWithText("遮挡",useUnmergedTree=true).performClick()
        compose.onNodeWithTag("video-region").performTouchInput{swipe(topLeft+androidx.compose.ui.geometry.Offset(30f,30f),center,300)}
        compose.onNodeWithText("预览效果").performClick()
        compose.onNodeWithTag("video-region").assertDoesNotExist()
        compose.onNodeWithText("框选区域").performClick()
        compose.onNodeWithTag("video-region").assertExists()
        compose.onNodeWithTag("video-save-copy").performScrollTo().assertExists()
        screenshot("video-editor")
        compose.onNodeWithText("返回").performScrollTo().performClick()
        assertTrue(closed());assertEquals(listOf(original),runBlocking{model.store.load()}.active.single().photos)
    }
    @Test @Config(qualifiers="zh-rCN-w320dp-h900dp-xhdpi")
    fun largeTextControlsRemainReachable()=render("video-edit-large",2f) {_,_,_->
        compose.onNodeWithTag("video-save-copy").performScrollTo().assertIsDisplayed()
        screenshot("video-editor-font200")
        compose.onNodeWithText("返回").performScrollTo().assertIsDisplayed()
    }
    private fun screenshot(name:String){val file=File("build/outputs/video-editor/$name.png");file.parentFile!!.mkdirs();compose.onRoot().captureRoboImage(file.absolutePath)}
    private fun render(name:String,font:Float=1f,verify:(Photo,AppModel,()->Boolean)->Unit) {
        RuntimeEnvironment.setFontScale(font)
        val app=RuntimeEnvironment.getApplication();app.getSharedPreferences("device",0).edit().clear().putString("generation",uid()).commit()
        val store=Store(app);val id=uid();val file=File(store.photoDir,"$id.original").apply{writeText("synthetic only $name")}
        val p=Photo(id,file.name,file.length(),sha(file),"video/mp4",1200)
        val archive=Archive(name="合成录屏",photos=listOf(p))
        runBlocking{store.save(archive);store.close()}
        val model=AppModel(app);val owners=androidx.lifecycle.ViewModelStore().apply{put("fixture",model)}
        var closed=false
        val shown=androidx.compose.runtime.mutableStateOf(true)
        try {
            compose.waitUntil(10000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();model.state.value.ready}
            compose.setContent{if(shown.value)MaterialTheme(colorScheme=appearanceScheme(Appearance(theme=if(font>1f)"曜石" else "月白"))){VideoEditorDialog(archive,p,model){closed=true;shown.value=false}}}
            compose.waitUntil(10000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();compose.onAllNodesWithTag("video-region").fetchSemanticsNodes().isNotEmpty()}
            verify(p,model){closed}
        } finally {
            compose.runOnIdle{shown.value=false}
            compose.waitForIdle()
            owners.clear()
            compose.waitUntil(10000){org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();model.resourcesClosed}
        }
    }
}
