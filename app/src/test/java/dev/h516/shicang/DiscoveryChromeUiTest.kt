package dev.h516.shicang

import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real navigation with synthetic media; covers the new one-tap archive action. */
class DiscoveryChromeUiTest:UnifiedNavigationFixture() {
    @Test fun titleOpensArchiveDirectlyAndReturnKeepsMedia()=fixture {_,a->
        compose.onNodeWithTag("nav-frame").performClick()
        val first=mediaNumber(3)
        compose.onNodeWithContentDescription("资料").assertIsDisplayed()
        screenshot("discovery-apple-light")
        compose.onNodeWithContentDescription("打开档案：${a.name}").assertIsDisplayed().performClick()
        compose.onNodeWithText("档案详情").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        assertEquals(first,mediaNumber(3))
        compose.onNodeWithTag("nav-frame").assertIsSelected()
    }

    @Test fun darkLargeTextKeepsInformationAndNavigationReachable()=fixture(font=2f) {model,_->
        compose.runOnIdle {model.appearance(model.state.value.appearance.copy(mode="深色"))}
        idle{!model.state.value.busy && model.state.value.appearance.mode=="深色"}
        compose.onNodeWithTag("nav-frame").performClick()
        mediaNumber(3)
        compose.onNodeWithContentDescription("资料").assertIsDisplayed()
        compose.onNodeWithContentDescription("更多").assertIsDisplayed()
        compose.onNodeWithText("查看档案").assertIsDisplayed()
        compose.onNodeWithTag("nav-library").assertIsDisplayed()
        compose.onNodeWithTag("nav-settings").assertIsDisplayed()
        screenshot("discovery-apple-dark-font200")
        compose.onNodeWithContentDescription("资料").performClick()
        compose.onNodeWithText("打开完整档案").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("档案详情").assertExists()
    }

    @Test fun emptyDiscoveryKeepsAnExitToTheLibrary()=fixture {model,a->
        compose.runOnIdle {model.trash(a)}
        idle{!model.state.value.busy && model.state.value.library.active.isEmpty()}
        compose.onNodeWithTag("nav-frame").performClick()
        compose.onNodeWithText("让照片成为主角").assertIsDisplayed()
        screenshot("discovery-apple-empty")
        compose.onNodeWithText("去档案添加媒体").performClick()
        compose.onNodeWithTag("nav-library").assertIsSelected()
    }
}

/** A fake player verifies control geometry only, without claiming codec playback. */
class DiscoveryPlayerChromeUiTest:UnifiedNavigationFixture() {
    @Test fun progressStaysAboveFloatingNavigation() {
        val player=java.lang.reflect.Proxy.newProxyInstance(Player::class.java.classLoader,arrayOf(Player::class.java)) {proxy,method,args->
            when(method.name) {
                "equals"->proxy===args?.firstOrNull()
                "hashCode"->System.identityHashCode(proxy)
                "toString"->"Synthetic layout player"
                "getDuration"->60000L
                else->when(method.returnType) {
                    java.lang.Boolean.TYPE->false
                    java.lang.Long.TYPE->0L
                    java.lang.Integer.TYPE->0
                    java.lang.Float.TYPE->1f
                    else->null
                }
            }
        } as Player
        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize()) {
                    PlayerControls(player,1f,true,{},{},{},bottomInset=90.dp)
                    Box(Modifier.align(Alignment.BottomCenter)){GalleryTabBar("frame") {}}
                }
            }
        }
        val progress=compose.onNodeWithContentDescription("播放进度").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val navigation=compose.onNodeWithTag("nav-frame").fetchSemanticsNode().boundsInRoot
        assertTrue("Progress must remain above navigation",progress.bottom<=navigation.top)
        screenshot("discovery-apple-player-layout")
    }
}
