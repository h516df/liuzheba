package dev.h516.shicang

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Tests the archive wall's three distinct actions against persisted state. */
class ContentFirstUiTest {
    @Test fun coverOpensDetailsMediaOpensViewerAndSwipeRestores()=runBlocking<Unit> {
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        check(c.packageName.endsWith(".verification"))
        val prefs=c.getSharedPreferences("device",0);val old=prefs.all
        prefs.edit().putString("generation",uid()).putBoolean("appLock",false).putBoolean("libraryCovers",true).putBoolean("frameHintV2",true).commit()
        val store=Store(c);val picture=File(c.cacheDir,"content-first-demo.png")
        i.context.assets.open("v03-demo.png").use{input->picture.outputStream().use{input.copyTo(it)}}
        val photos=store.importPhotos(listOf(Uri.fromFile(picture))).photos
        val category=store.label("category","生活片段")
        val archive=Archive(name="周末的城市散步",category=category,photos=photos,fields=listOf(Field(name="地点",value="老街与河岸")))
        store.save(archive)
        store.save(Archive(name="外婆的手写食谱与节日记忆",photos=photos))
        store.save(Archive(name="一封旧信",fields=listOf(Field(name="备注",value="合成验收资料"))))
        store.save(Archive(name="旅行途中",photos=photos))
        store.appearance(Appearance(theme=InstrumentationRegistry.getArguments().getString("visualTheme","月白"),skin="全幅",fit="完整"))
        val ui=i.uiAutomation;val activity=i.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun nodes():List<AccessibilityNodeInfo>{ui.clearCache();val out=mutableListOf<AccessibilityNodeInfo>();fun visit(n:AccessibilityNodeInfo?){if(n==null)return;if(n.isVisibleToUser)out+=n;repeat(n.childCount){visit(n.getChild(it))}};visit(ui.rootInActiveWindow);return out}
        fun find(text:String)=nodes().firstOrNull{it.text?.toString()==text || it.contentDescription?.toString()==text}
        fun wait(text:String):AccessibilityNodeInfo{val end=SystemClock.uptimeMillis()+10000;while(SystemClock.uptimeMillis()<end){find("Got it")?.performAction(AccessibilityNodeInfo.ACTION_CLICK);find(text)?.let{return it};Thread.sleep(100)};error("Missing $text: "+nodes().mapNotNull{it.text}.take(30))}
        fun click(text:String){var n:AccessibilityNodeInfo?=wait(text);while(n!=null && !n.isClickable)n=n.parent;check(n?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true);Thread.sleep(350)}
        val out=File(c.getExternalFilesDir(null),"content-first").apply{mkdirs()}
        fun shot(name:String){Thread.sleep(300);val b=ui.takeScreenshot()!!;File(out,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
        fun scrollTo(text:String){repeat(8){if(find(text)!=null)return;nodes().lastOrNull{it.isScrollable}?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);Thread.sleep(250)};wait(text)}
        try {
            wait("搜索档案");wait("4 份档案");assertNull(find("收藏成长"));shot("wall")
            scrollTo(archive.name);click(archive.name);wait("档案");wait("编辑");shot("detail")
            assertNull(find("完整照片，双击或双指缩放"))
            click("浏览${archive.name}的照片与视频");wait("完整照片，双击或双指缩放");shot("viewer")
            click("返回档案");wait("档案");click("返回");wait("搜索档案")
            scrollTo(archive.name)
            var target:AccessibilityNodeInfo?=wait(archive.name);while(target!=null && !target.isClickable)target=target.parent
            val bounds=Rect();checkNotNull(target).getBoundsInScreen(bounds)
            val y=bounds.centerY().toFloat();val down=SystemClock.uptimeMillis()
            fun event(action:Int,x:Float){val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);ui.injectInputEvent(e,true);e.recycle()}
            val start=bounds.right-bounds.width()*.08f;val end=bounds.left+bounds.width()*.08f
            event(MotionEvent.ACTION_DOWN,start);repeat(20){Thread.sleep(20);event(MotionEvent.ACTION_MOVE,start+(end-start)*(it+1)/20)};event(MotionEvent.ACTION_UP,end)
            wait("移入回收站？");shot("swipe");click("移入回收站")
            val deadline=SystemClock.uptimeMillis()+5000;while(store.load().active.any{it.id==archive.id} && SystemClock.uptimeMillis()<deadline)Thread.sleep(100)
            assertFalse(store.load().active.any{it.id==archive.id})
            assertEquals(photos,store.load().archives.first{it.id==archive.id}.photos)
            click("设置");shot("settings");click("回收站");click("恢复档案")
            val restored=SystemClock.uptimeMillis()+5000;while(store.load().active.none{it.id==archive.id} && SystemClock.uptimeMillis()<restored)Thread.sleep(100)
            assertEquals(archive,store.load().active.first{it.id==archive.id}.copy(updated=archive.updated))
            click("返回");scrollTo("收藏成长");click("收藏成长");wait("慢慢收集，慢慢记得");shot("keepsakes")
        } catch(e:Throwable){shot("failure");File(out,"failure.txt").writeText(nodes().joinToString("\n"){"${it.text} | ${it.contentDescription}"});throw e}
        finally {i.runOnMainSync{activity.finish()};val edit=prefs.edit().clear();old.forEach{(key,value)->when(value){is String->edit.putString(key,value);is Boolean->edit.putBoolean(key,value);is Int->edit.putInt(key,value);is Long->edit.putLong(key,value);is Float->edit.putFloat(key,value);is Set<*>->edit.putStringSet(key,value.filterIsInstance<String>().toSet())}};edit.commit();picture.delete()}
    }
}
