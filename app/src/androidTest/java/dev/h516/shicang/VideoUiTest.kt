package dev.h516.shicang

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityService
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VideoUiTest {
    @Test fun manualMutedVideoStopsWhenBackgroundedAndReturnsToPoster()=runBlocking<Unit> {
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        check(c.packageName.endsWith(".verification"))
        val prefs=c.getSharedPreferences("device",Context.MODE_PRIVATE)
        val generation=prefs.getString("generation","initial");val locked=prefs.getBoolean("appLock",false)
        prefs.edit().putString("generation",uid()).putBoolean("appLock",false).putBoolean("frameHintV2",true).commit()
        val s=Store(c);val input=File(c.cacheDir,"video-ui.mp4")
        i.context.assets.open("sample-video.mp4").use{src->input.outputStream().use{src.copyTo(it)}}
        val p=s.importPhotos(listOf(Uri.fromFile(input))).photos.single()
        s.save(Archive(name="视频测试档案",photos=listOf(p)))
        var activity:Activity?=null
        val ui=i.uiAutomation
        fun node(text:String):AccessibilityNodeInfo? {
            fun visit(n:AccessibilityNodeInfo?):AccessibilityNodeInfo? {if(n==null)return null;if(n.isVisibleToUser && (n.text?.toString()==text || n.contentDescription?.toString()==text))return n;repeat(n.childCount){visit(n.getChild(it))?.let{return it}};return null}
            return visit(ui.rootInActiveWindow)
        }
        fun waitFor(text:String):AccessibilityNodeInfo {val end=SystemClock.uptimeMillis()+12000;while(SystemClock.uptimeMillis()<end){node(text)?.let{return it};Thread.sleep(100)};error("Missing video control $text")}
        fun click(text:String){
            ui.waitForIdle(300,5000)
            val target=waitFor(text)
            var actionable:AccessibilityNodeInfo?=target
            while(actionable!=null && !actionable.isClickable) actionable=actionable.parent
            check(actionable?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true){"Could not activate video control $text"}
            ui.waitForIdle(300,5000)
        }
        try {
            activity=i.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitFor("播放视频");assertNull(node("开启声音"));click("播放视频");waitFor("开启声音")
            Thread.sleep(700);assertNull(node("此视频无法播放 · 返回预览"))
            click("开启声音");waitFor("静音")
            val folder=File(c.filesDir,"v04-ui").apply{mkdirs()}
            val shot=ui.takeScreenshot() ?: error("No video screenshot")
            try{File(folder,"video-playing.png").outputStream().use{shot.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{shot.recycle()}
            assertTrue(ui.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME));Thread.sleep(500)
            i.context.startActivity(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            waitFor("播放视频");assertNull(node("静音"));assertNull(node("开启声音"));click("播放视频");waitFor("开启声音");assertNull(node("静音"))
        } finally {
            i.runOnMainSync{activity?.finish()}
            prefs.edit().putString("generation",generation).putBoolean("appLock",locked).commit();input.delete()
        }
    }
}
