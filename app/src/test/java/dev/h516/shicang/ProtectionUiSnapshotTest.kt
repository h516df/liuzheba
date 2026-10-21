package dev.h516.shicang

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import java.io.File

/** Actual Compose host rendering, not physical-device or system picker acceptance. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk=[35],application=Application::class,qualifiers="zh-rCN-w412dp-h915dp-xhdpi")
class ProtectionUiSnapshotTest {
    @get:Rule val compose=createComposeRule()
    @Test fun setupNotice()=render("protection-setup",ProtectionState())
    @Test fun protectedWithLocalPath()=render("protection-verified",ProtectionState("已保护","当前资料与 12 项原件已在外部保存并校验","primary:Documents/留册自动保护",1792464000000,12))
    @Test fun importWarningIsNeverHidden()=render("protection-warning",ProtectionState("待核对","部分原件未收录，请保留原文件","primary:Documents/留册自动保护",1792464000000,12))
    @Test fun failedKeepsLastGoodVisible()=render("protection-failed",ProtectionState("保护失败","文件夹权限已失效。旧恢复版本仍保留，请先保留原文件","primary:Documents/留册自动保护",1792464000000,12))
    @Test @Config(qualifiers="zh-rCN-w320dp-h900dp-xhdpi")
    fun narrowLargeDark() {RuntimeEnvironment.setFontScale(2f);render("protection-320-font200-dark",ProtectionState("待保护","最新修改已保存在 App 内，正在等待外部保护","primary:Documents/留册自动保护"),true)}
    private fun render(name:String,state:ProtectionState,dark:Boolean=false) {
        var requested=0
        compose.setContent {MaterialTheme(colorScheme=if(dark)darkColorScheme() else lightColorScheme()) {
            Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text("设置",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.padding(vertical=20.dp))
                ProtectionPanel(state,false,{requested++},{requested++})
            }}
        }}
        compose.onNodeWithText("自动保护 · ${state.status}").assertExists()
        if(state.location.isNotEmpty()) {
            compose.onNodeWithText("立即校验保护").assertDoesNotExist()
            compose.onNodeWithText("更换保存位置").assertDoesNotExist()
            if(state.needsAttention) compose.onNodeWithText(state.detail).assertExists()
            if(state.status=="保护失败") compose.onNodeWithText("重试保护").assertExists()
        }
        val output=File("build/outputs/protection-ui/$name.png");output.parentFile.mkdirs();compose.onRoot().captureRoboImage(output.absolutePath)
        if(state.location.isNotEmpty()) compose.onNodeWithText("保护范围与注意事项").performScrollTo().performClick()
        compose.onNodeWithText(if(state.location.isEmpty())"开启自动保护" else "更换保存位置").performScrollTo().performClick()
        org.junit.Assert.assertEquals(1,requested)
    }
}
