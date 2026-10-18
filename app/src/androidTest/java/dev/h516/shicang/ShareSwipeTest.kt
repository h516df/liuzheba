package dev.h516.shicang

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ShareSwipeTest {
 @Test fun shareCreatesEditableArchiveAndSwipeCanRecover()=runBlocking<Unit> {
  val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext;check(c.packageName.endsWith(".verification"))
  val prefs=c.getSharedPreferences("device",0);val old=prefs.getString("generation","initial");val lock=prefs.getBoolean("appLock",false)
  prefs.edit().putString("generation",uid()).putBoolean("appLock",false).putBoolean("libraryCovers",false).putBoolean("frameHintV2",true).commit()
  val store=Store(c);store.save(Archive(name="左滑回收测试"))
  val ui=i.uiAutomation;var activity:Activity?=null
  fun nodes():List<AccessibilityNodeInfo>{ui.clearCache();val list=mutableListOf<AccessibilityNodeInfo>();fun scan(n:AccessibilityNodeInfo?){if(n==null)return;if(n.isVisibleToUser)list+=n;repeat(n.childCount){scan(n.getChild(it))}};scan(ui.rootInActiveWindow);return list}
  fun find(text:String)=nodes().firstOrNull{it.text?.toString()==text || it.contentDescription?.toString()==text}
  fun wait(text:String):AccessibilityNodeInfo {val end=SystemClock.uptimeMillis()+18000;while(SystemClock.uptimeMillis()<end){find("Got it")?.performAction(AccessibilityNodeInfo.ACTION_CLICK);find(text)?.let{return it};Thread.sleep(100)};error("Missing $text; "+nodes().mapNotNull{it.text}.take(25))}
  fun click(text:String){var n:AccessibilityNodeInfo?=wait(text);while(n!=null && !n.isClickable)n=n.parent;check(n?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true);Thread.sleep(350)}
  fun shot(name:String){val image=ui.takeScreenshot()!!;File(c.getExternalFilesDir(null),"$name.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()}
  try {
   activity=i.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
   wait("我的");click("我的");wait("左滑回收测试")
   val rect=Rect();wait("左滑回收测试").getBoundsInScreen(rect)
   val screen=Rect();ui.rootInActiveWindow.getBoundsInScreen(screen)
   val start=screen.width()*.85f;val y=rect.centerY().toFloat();var time=SystemClock.uptimeMillis()
   fun event(action:Int,x:Float){val e=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,x,y,0);ui.injectInputEvent(e,true);e.recycle()}
   event(MotionEvent.ACTION_DOWN,start);repeat(20){Thread.sleep(20);event(MotionEvent.ACTION_MOVE,start-(it+1)*screen.width()*.035f)};event(MotionEvent.ACTION_UP,screen.width()*.15f)
   wait("移入回收站？");shot("swipe-confirm");click("取消");assertEquals(1,store.load().active.size)
   Thread.sleep(500);time=SystemClock.uptimeMillis()
   event(MotionEvent.ACTION_DOWN,start);repeat(20){Thread.sleep(20);event(MotionEvent.ACTION_MOVE,start-(it+1)*screen.width()*.035f)};event(MotionEvent.ACTION_UP,screen.width()*.15f)
   click("移入回收站");wait("把值得记住的，留在这里");assertEquals(0,store.load().active.size)
   click("设置")
   if(find("回收站")==null)nodes().filter{it.isScrollable}.forEach{it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)}
   click("回收站");click("恢复档案");Thread.sleep(400);assertEquals(1,store.load().active.size)
   // Receive the same real ACTION_SEND entry point used by the system share sheet.
   val text="示例直播间 https://live.kuaishou.com/u/demo"
   c.startActivity(Intent(c,MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
   click("创建档案草稿");wait("编辑档案");wait("示例直播间");shot("share-prefilled")
   click("保存");wait("档案")
   val saved=store.load().active.first{it.name=="示例直播间"}
   assertEquals("快手",saved.fields.first{it.name=="平台"}.value)
   assertEquals("直播间",saved.fields.first{it.name=="内容类型"}.value)
   assertEquals(text,saved.fields.first{it.name=="分享原文"}.value)
   // Reload from disk: neither the original archive nor the shared record is a UI-only draft.
   assertEquals(2,Store(c).load().active.size)
  } finally {i.runOnMainSync{activity?.finish()};prefs.edit().putString("generation",old).putBoolean("appLock",lock).commit()}
 }
}
