package dev.h516.shicang

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupPrivacyTest {
    @Test fun backupRequiresPrivacyConfirmationBeforeTheSystemPicker() {
        val i=InstrumentationRegistry.getInstrumentation()
        val c=i.targetContext
        check(c.packageName.endsWith(".verification")) { "Only the isolated verification app is allowed" }
        val prefs=c.getSharedPreferences("device",Context.MODE_PRIVATE)
        val oldGeneration=prefs.getString("generation","initial")!!
        val oldLock=prefs.getBoolean("appLock",false)
        val generation=uid()
        val previousBackup=prefs.getLong("lastBackup",0)
        prefs.edit().putString("generation",generation).putBoolean("appLock",false).commit()
        kotlinx.coroutines.runBlocking {
            Store(c).appearance(Appearance(theme=InstrumentationRegistry.getArguments().getString("visualTheme","月白")))
        }
        val ui=i.uiAutomation
        var activity:Activity?=null
        fun find(text:String):AccessibilityNodeInfo? {
            fun visit(n:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
                if(n==null) return null
                val value=n.text?.toString()
                if(n.isVisibleToUser && (value?.lineSequence()?.firstOrNull()==text || (text=="备份文件未加密" && value?.startsWith(text)==true) || n.contentDescription?.toString()==text)) return n
                repeat(n.childCount) {visit(n.getChild(it))?.let { found -> return found }}
                return null
            }
            ui.clearCache();return visit(ui.rootInActiveWindow)
        }
        fun waitFor(text:String):AccessibilityNodeInfo {
            val end=SystemClock.uptimeMillis()+10000
            while(SystemClock.uptimeMillis()<end) {find(text)?.let{return it};Thread.sleep(80)}
            error("Missing native UI: $text")
        }
        fun reveal(text:String) {
            repeat(15) {
                if(find(text)!=null) return
                val display=c.resources.displayMetrics
                val x=display.widthPixels/2
                val start=(display.heightPixels*.79).toInt()
                val end=(display.heightPixels*.55).toInt()
                ui.executeShellCommand("input swipe $x $start $x $end 350").use { descriptor ->
                    java.io.FileInputStream(descriptor.fileDescriptor).use {it.readBytes()}
                }
                Thread.sleep(250)
            }
            waitFor(text)
        }
        fun tap(x:Int,y:Int) {
            val now=SystemClock.uptimeMillis()
            val down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x.toFloat(),y.toFloat(),0)
            val up=MotionEvent.obtain(now,now+70,MotionEvent.ACTION_UP,x.toFloat(),y.toFloat(),0)
            try {ui.injectInputEvent(down,true);ui.injectInputEvent(up,true)} finally {down.recycle();up.recycle()}
        }
        fun click(text:String) {
            val target=waitFor(text)
            var clickable:AccessibilityNodeInfo?=target
            while(clickable!=null) {
                if(clickable.isClickable && clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    i.waitForIdleSync();Thread.sleep(180);return
                }
                clickable=clickable.parent
            }
            val r=Rect();target.getBoundsInScreen(r);tap(r.centerX(),r.centerY())
        }
        fun screenshot(name:String) {
            Thread.sleep(400) // Wait for the static confirmation dialog's entrance transition.
            val image=ui.takeScreenshot() ?: error("No native screenshot")
            try {File(c.getExternalFilesDir(null),name).outputStream().use {assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it))}} finally {image.recycle()}
        }
        try {
            activity=i.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            click("设置与恢复备份")
            reveal("完整备份")
            click("完整备份")
            val warning=waitFor("备份文件未加密").text.toString()
            assertTrue(warning.contains("应用锁不会保护导出的备份"))
            assertTrue(warning.contains("原始媒体可能保留拍摄位置"))
            assertEquals(c.packageName,ui.rootInActiveWindow.packageName.toString())
            screenshot("backup-privacy-confirmation.png")
            click("取消")
            waitFor("完整备份")
            assertNull(find("备份文件未加密"))
            assertEquals(c.packageName,ui.rootInActiveWindow.packageName.toString())
            assertEquals(previousBackup,prefs.getLong("lastBackup",0))
            click("完整备份");waitFor("备份文件未加密");click("确认")
            val deadline=SystemClock.uptimeMillis()+10000
            while(ui.rootInActiveWindow?.packageName?.toString()?.contains("documentsui")!=true && SystemClock.uptimeMillis()<deadline) Thread.sleep(80)
            assertTrue("Confirmation must open the system document picker",ui.rootInActiveWindow?.packageName?.toString()?.contains("documentsui")==true)
            ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            waitFor("完整备份")
            assertEquals(generation,prefs.getString("generation",null))
            assertEquals(previousBackup,prefs.getLong("lastBackup",0))
        } catch(error:Throwable) {
            screenshot("backup-privacy-failure.png")
            throw error
        } finally {
            i.runOnMainSync {activity?.finish()}
            prefs.edit().putString("generation",oldGeneration).putBoolean("appLock",oldLock).commit()
            File(c.filesDir,"libraries/$generation").deleteRecursively()
        }
    }
}
