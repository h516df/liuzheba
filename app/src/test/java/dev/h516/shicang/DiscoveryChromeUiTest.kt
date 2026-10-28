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
    @Test fun discoveryAndLibraryShareReaderAndRetainScroll()=fixture {_,a->
        compose.onNodeWithTag("nav-frame").performClick()
        val media=mediaNumber(a.photos.size)
        screenshot("product-discovery-light")
        compose.onNodeWithContentDescription("打开档案：${a.name}").performClick()
        compose.onNodeWithText("档案详情").assertExists()
        compose.onNodeWithText("编辑").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithTag("nav-frame").assertIsSelected()
        assertEquals(media,mediaNumber(a.photos.size))
        compose.onNodeWithTag("nav-library").performClick()
        compose.onNodeWithText(a.name).performClick()
        compose.onNodeWithText("档案详情").assertExists()
    }

    @Test fun largeTextKeepsBothDestinationsAndSharedActionsReachable()=fixture(font=2f) {model,a->
        compose.runOnIdle {model.appearance(model.state.value.appearance.copy(theme="曜石"))}
        idle{!model.state.value.busy && model.state.value.appearance.theme=="曜石"}
        compose.onNodeWithTag("nav-frame").performClick()
        compose.onNodeWithTag("nav-library").assertIsDisplayed()
        compose.onNodeWithContentDescription("更多").assertIsDisplayed()
        val left=compose.onNodeWithTag("nav-frame").fetchSemanticsNode().boundsInRoot
        val right=compose.onNodeWithTag("nav-library").fetchSemanticsNode().boundsInRoot
        assertTrue(left.right<=right.left)
        screenshot("product-discovery-dark-font200")
        compose.onNodeWithContentDescription("打开档案：${a.name}").performClick()
        screenshot("product-detail-dark-font200");compose.onNodeWithText("编辑").assertIsDisplayed().performClick()
        compose.onNodeWithText("保存").assertIsDisplayed()
    }

    @Test fun emptyDiscoveryHasAnActualCreationAction()=fixture {model,a->
        compose.runOnIdle {model.trash(a)}
        idle{!model.state.value.busy && model.state.value.library.active.isEmpty()}
        compose.onNodeWithTag("nav-frame").performClick()
        compose.onNodeWithText("值得留下的，都在这里").assertIsDisplayed()
        screenshot("product-discovery-empty")
        compose.onNodeWithText("新建第一份档案").performClick()
        idle{model.state.value.editor!=null}
        compose.onNodeWithText("新建档案").assertExists()
    }

    @Test fun pureTextIsDiscoverableAndOpeningItNeverCreatesDrafts()=fixture {model,a->
        model.edit(a);model.change(a.copy(photos=emptyList()));model.changeArchiveText("只有文字，也是一份完整收藏。")
        model.saveEditor();idle{model.state.value.editor==null && !model.state.value.busy}
        compose.onNodeWithTag("nav-frame").performClick()
        compose.onNodeWithContentDescription("打开档案：${a.name}").assertExists()
        compose.onNodeWithContentDescription("打开档案：${a.name}").performClick()
        compose.onNodeWithTag("archive-text").assertTextContains("完整收藏",substring=true)
        assertTrue(model.state.value.library.drafts.isEmpty())
        assertTrue(model.state.value.library.noteDrafts.isEmpty())
    }

    @Test fun aSingleArchiveOffersMediaShuffleButNoCardList()=fixture {_,_->
        compose.onNodeWithTag("nav-frame").performClick()
        compose.onNodeWithTag("discovery-stories").assertDoesNotExist()
        compose.onNodeWithText("换个顺序").assertDoesNotExist()
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("换一轮").assertExists()
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
