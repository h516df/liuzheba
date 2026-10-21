package dev.h516.shicang

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/** External image work uses wall time; the viewer's hide timer uses the Compose test clock. */
internal fun ComposeContentTestRule.loadedPhotoWithControls(): SemanticsNodeInteraction {
    val photos=onAllNodes(
        hasContentDescription("全幅照片，双击或双指缩放") or
            hasContentDescription("完整照片，双击或双指缩放")
    )
    var visible:SemanticsNodeInteraction?=null
    waitUntil(10000) {
        visible=photos.fetchSemanticsNodes().indices.map{photos[it]}.firstOrNull {
            it.isDisplayed() && it.fetchSemanticsNode().config.getOrElse(SemanticsProperties.StateDescription){""}=="照片已显示"
        }
        visible!=null
    }
    val photo=checkNotNull(visible)
    val previous=mainClock.autoAdvance
    mainClock.autoAdvance=false
    try {
        // Do not assume decoding finishes inside the 4-second hide window. Deliberately verify
        // auto-hide, then perform the real user tap to show controls on a known clock frame.
        mainClock.advanceTimeBy(4500)
        onNodeWithContentDescription("更多").assertDoesNotExist()
        photo.performTouchInput{click(center)}
        mainClock.advanceTimeBy(600)
        onNodeWithContentDescription("更多").assertExists()
    } finally {mainClock.autoAdvance=previous}
    return photo
}
