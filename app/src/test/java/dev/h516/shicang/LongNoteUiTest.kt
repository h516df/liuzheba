package dev.h516.shicang

import android.app.Application
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rCN-w320dp-h640dp-xhdpi")
class LongNoteUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun multilineInputAndExplicitSave() {
        var s by mutableStateOf(NoteEditing(LongNote("archive",uid()),null,"合成媒体笔记"));var saved=""
        compose.setContent{MaterialTheme(colorScheme=appearanceScheme(Appearance())){NoteEditorScreen(s,{t,b->s=s.copy(note=s.note.copy(title=t,body=b))},{},{saved=s.note.body},{})}}
        compose.onNodeWithTag("note-body").performTextInput("第一段\n第二段")
        compose.onNodeWithText("保存笔记").performClick();assertEquals("第一段\n第二段",saved)
        compose.onRoot().captureRoboImage(File("build/outputs/note-ui/note-light.png").apply{parentFile.mkdirs()})
    }
    @Test fun darkLargeTextHasReachableEditorAndDiscardConfirmation() {
        var discard=false;val s=NoteEditing(LongNote("archive",uid(),body="原稿\n第二段"),null,"合成长名称的作者与视频笔记",status="草稿已存本机")
        compose.setContent{CompositionLocalProvider(LocalDensity provides Density(2f,2f)){MaterialTheme(colorScheme=appearanceScheme(Appearance(theme="曜石"))){NoteEditorScreen(s,{_,_->},{},{},{discard=true})}}}
        compose.onNodeWithText("保存笔记").assertIsDisplayed()
        compose.onNodeWithTag("note-body").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage(File("build/outputs/note-ui/note-dark-large.png").apply{parentFile.mkdirs()})
        compose.onNodeWithText("放弃此草稿，保留原笔记").performScrollTo().performClick()
        assertFalse(discard);compose.onNodeWithText("继续编辑").performClick();assertFalse(discard)
    }
}
