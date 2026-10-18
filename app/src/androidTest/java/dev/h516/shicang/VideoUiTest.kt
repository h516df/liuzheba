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
    @Test fun videoRetainsPositionAndSoundAcrossBackgroundAndCollapse()=runBlocking<Unit> {
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
            ui.clearCache();return visit(ui.rootInActiveWindow)
        }
        fun waitFor(text:String):AccessibilityNodeInfo {val end=SystemClock.uptimeMillis()+12000;while(SystemClock.uptimeMillis()<end){node("Got it")?.performAction(AccessibilityNodeInfo.ACTION_CLICK);node(text)?.let{return it};Thread.sleep(100)};val shot=ui.takeScreenshot();if(shot!=null){File(c.getExternalFilesDir(null),"video-failure.png").outputStream().use{shot.compress(Bitmap.CompressFormat.PNG,100,it)};shot.recycle()};error("Missing video control $text")}
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
            Thread.sleep(700);assertNull(node("视频暂时无法播放，原文件仍保留"))
            click("开启声音");waitFor("静音")
            fun playerIn(view:android.view.View):androidx.media3.common.Player? {
                if(view is androidx.media3.ui.PlayerView)return view.player
                if(view is android.view.ViewGroup) repeat(view.childCount){playerIn(view.getChildAt(it))?.let{return it}}
                return null
            }
            i.runOnMainSync { val player=playerIn(activity!!.window.decorView)!!;player.pause();player.seekTo(1000L) }
            val folder=File(c.filesDir,"v04-ui").apply{mkdirs()}
            val shot=ui.takeScreenshot() ?: error("No video screenshot")
            try{File(folder,"video-playing.png").outputStream().use{shot.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{shot.recycle()}
            assertTrue(ui.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME));Thread.sleep(500)
            i.context.startActivity(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            waitFor("静音")
            i.runOnMainSync { val player=playerIn(activity!!.window.decorView)!!;assertTrue(kotlin.math.abs(player.currentPosition-1000L)<150L);assertFalse(player.playWhenReady) }
            assertNull(node("播放视频"));assertNull(node("视频暂时无法播放，原文件仍保留"))
            click("1.0×");waitFor("1.5×")
            click("收起播放")
            val until=SystemClock.uptimeMillis()+5000
            fun resumeNode(n:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
                if(n==null)return null
                if(n.text?.toString()?.startsWith("继续播放 · ")==true)return n
                repeat(n.childCount){resumeNode(n.getChild(it))?.let{return it}}
                return null
            }
            var resume=resumeNode(ui.rootInActiveWindow)
            while(resume==null && SystemClock.uptimeMillis()<until){Thread.sleep(100);resume=resumeNode(ui.rootInActiveWindow)}
            check(resume!=null){"Playback position was lost when collapsed"}
            click(resume!!.text.toString());waitFor("静音");waitFor("1.5×")
        } finally {
            i.runOnMainSync{activity?.finish()}
            prefs.edit().putString("generation",generation).putBoolean("appLock",locked).commit();input.delete()
        }
    }
}
