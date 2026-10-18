package dev.h516.shicang

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityService
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ViewerLockTest {
    @Test fun backgroundingLockedViewerHidesPhotoAndPrivateName() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        check(context.packageName.endsWith(".verification")) {"Only the isolated verification app is allowed"}
        val automation=instrumentation.uiAutomation
        val preferences=context.getSharedPreferences("device",Context.MODE_PRIVATE)
        val originalLock=preferences.getBoolean("appLock",false)
        preferences.edit().putBoolean("appLock",false).commit()
        val store=Store(context)
        val originalAppearance=store.appearance()
        store.appearance(Appearance(skin="暗房",fit="完整"))
        val original=File(context.cacheDir,"lock-fixture-${uid()}.jpg")
        val bitmap=Bitmap.createBitmap(400,200,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawRect(0f,0f,200f,200f,Paint().apply {color=Color.RED})
            drawRect(200f,0f,400f,200f,Paint().apply {color=Color.BLUE})
        }
        original.outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,95,it)};bitmap.recycle()
        ExifInterface(original.path).apply {setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString());saveAttributes()}
        val photo=store.importPhotos(listOf(Uri.fromFile(original))).photos.single()
        val archive=Archive(name="锁屏验证档案",photos=listOf(photo),cover=photo.id)
        store.save(archive)
        var activity:Activity?=null
        fun node(text:String):AccessibilityNodeInfo? {
            fun search(n:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
                if(n==null) return null
                if(n.isVisibleToUser && (n.text?.toString()==text || n.contentDescription?.toString()==text)) return n
                repeat(n.childCount) {search(n.getChild(it))?.let {found->return found}}
                return null
            }
            return search(automation.rootInActiveWindow)
        }
        fun waitNode(text:String):AccessibilityNodeInfo {
            val end=SystemClock.uptimeMillis()+10000
            while(SystemClock.uptimeMillis()<end) {node(text)?.let{return it};Thread.sleep(80)}
            error("Missing native node: $text")
        }
        fun tap(x:Int,y:Int) {
            val now=SystemClock.uptimeMillis()
            val down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x.toFloat(),y.toFloat(),0)
            val up=MotionEvent.obtain(now,now+80,MotionEvent.ACTION_UP,x.toFloat(),y.toFloat(),0)
            automation.injectInputEvent(down,true);automation.injectInputEvent(up,true);down.recycle();up.recycle()
        }
        fun click(text:String) {val r=Rect();waitNode(text).getBoundsInScreen(r);tap(r.centerX(),r.centerY())}
        try {
            activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitNode("完整照片，双击或双指缩放")
            Thread.sleep(500)
            val rendered=automation.takeScreenshot() ?: error("No native screenshot")
            try {
                val top=rendered.getPixel(rendered.width/2,rendered.height/3)
                val bottom=rendered.getPixel(rendered.width/2,rendered.height*2/3)
                assertTrue("JPEG EXIF rotation must show the red half on top",Color.red(top)>Color.blue(top)+100)
                assertTrue("JPEG EXIF rotation must show the blue half below",Color.blue(bottom)>Color.red(bottom)+100)
            } finally {rendered.recycle()}
            val metrics=context.resources.displayMetrics
            if(node("资料")==null) tap(metrics.widthPixels/2,metrics.heightPixels/2)
            click("资料");waitNode("档案");click("查看照片")
            waitNode("完整照片，双击或双指缩放")
            preferences.edit().putBoolean("appLock",true).commit()
            assertTrue(automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME))
            // Wait for the actual background transition; launcher animations can exceed 700 ms.
            var stopped=false
            val stopDeadline=SystemClock.uptimeMillis()+10000
            while(!stopped && SystemClock.uptimeMillis()<stopDeadline) {
                instrumentation.runOnMainSync {
                    stopped=(activity as MainActivity).lifecycle.currentState==androidx.lifecycle.Lifecycle.State.CREATED
                }
                if(!stopped) Thread.sleep(80)
            }
            assertTrue("Viewer must reach onStop before testing its locked return",stopped)
            instrumentation.context.startActivity(Intent(context,MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            waitNode("解锁留册")
            assertNull("A locked viewer must not render its previous photo",node("完整照片，双击或双指缩放"))
            assertNull("A locked viewer must not expose its private title",node(archive.name))
        } finally {
            preferences.edit().putBoolean("appLock",originalLock).commit()
            instrumentation.runOnMainSync {activity?.finish()}
            store.appearance(originalAppearance)
            store.trash(archive.id)
            original.delete()
        }
    }
}
