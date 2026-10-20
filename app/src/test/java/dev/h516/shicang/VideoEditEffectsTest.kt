package dev.h516.shicang

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VideoEditEffectsTest {
    @Test fun maskDrawsOnlyInsideTopLeftNormalizedRegion() {
        val bitmap=Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        VideoMaskOverlay(VideoRect(.1f,.2f,.4f,.6f)).onDraw(Canvas(bitmap),0)
        assertEquals(Color.BLACK,bitmap.getPixel(40,30));assertEquals(Color.TRANSPARENT,bitmap.getPixel(10,30));assertEquals(Color.TRANSPARENT,bitmap.getPixel(150,80));bitmap.recycle()
    }
    @Test fun exportAndPreviewBuildIndependentEffectsMaskBeforeCrop() {
        val plan=VideoEditPlan(100,1000,VideoRect(.2f,.1f,.8f,.9f),VideoRect(.3f,.3f,.4f,.4f))
        val a=VideoEditEngine.effects(plan);val b=VideoEditEngine.effects(plan)
        assertTrue(a[0] is OverlayEffect);assertTrue(a[1] is Crop);assertNotSame(a[0],b[0]);assertNotSame(a[1],b[1])
        val size=(a[1] as Crop).configure(1000,2000)
        assertEquals(600,size.width);assertEquals(1600,size.height)
    }
    @Test fun clippingUsesSelectedMillisAndKeepsAudioByDefault() {
        val item=VideoEditEngine.item(File("synthetic.mp4"),VideoEditPlan(1234,5678))
        assertEquals(1234L,item.clippingConfiguration.startPositionMs);assertEquals(5678L,item.clippingConfiguration.endPositionMs)
        assertTrue(VideoEditEngine.effects(VideoEditPlan(0,1000)).single() is Crop)
    }
}
