package dev.h516.shicang

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Isolated real-device UI flows. All records and images are synthetic. */
class ArchiveBookshelfTest {
    @Test fun searchFiltersListDetailsAndRecovery()=runBlocking<Unit> {
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        check(c.packageName.endsWith(".verification"))
        val prefs=c.getSharedPreferences("device",0);val old=prefs.all
        prefs.edit().putString("generation",uid()).putBoolean("appLock",false).putBoolean("libraryCovers",true).putBoolean("frameHintV2",true).commit()
        val store=Store(c);val picture=File(c.cacheDir,"bookshelf-fixture.png")
        i.context.assets.open("v03-demo.png").use{input->picture.outputStream().use{input.copyTo(it)}}
        val photos=store.importPhotos(listOf(Uri.fromFile(picture))).photos
        val category=store.label("category","日常收藏");val tag=store.label("tag","值得记住")
        val archive=Archive(name="山间的一个下午",category=category,tags=listOf(tag),photos=photos,fields=listOf(Field(name="记忆",value="沿着山路慢慢走。")))
        store.save(archive);store.save(Archive(name="一封旧信",category=category));store.save(Archive(name="周末散步",photos=photos));store.save(Archive(name="手写食谱"))
        store.appearance(Appearance(theme=InstrumentationRegistry.getArguments().getString("visualTheme","月白")))
        val ui=i.uiAutomation;val activity=i.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun nodes():List<AccessibilityNodeInfo>{if(android.os.Build.VERSION.SDK_INT>=34)ui.clearCache();val out=mutableListOf<AccessibilityNodeInfo>();fun visit(n:AccessibilityNodeInfo?){if(n==null)return;if(n.isVisibleToUser)out+=n;repeat(n.childCount){visit(n.getChild(it))}};visit(ui.rootInActiveWindow);return out}
        fun find(text:String)=nodes().firstOrNull{it.text?.toString()==text || it.contentDescription?.toString()==text}
        fun wait(text:String):AccessibilityNodeInfo{val end=SystemClock.uptimeMillis()+20000;while(SystemClock.uptimeMillis()<end){find("Got it")?.performAction(AccessibilityNodeInfo.ACTION_CLICK);find(text)?.let{return it};Thread.sleep(150)};error("Missing $text: "+nodes().mapNotNull{it.text}.take(35))}
        fun click(text:String){var n:AccessibilityNodeInfo?=wait(text);while(n!=null && !n.isClickable)n=n.parent;check(n?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true);Thread.sleep(600)}
        fun search(text:String){val n=nodes().first{it.isEditable};assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text)}));Thread.sleep(700)}
        val out=File(c.getExternalFilesDir(null),"archive-bookshelf").apply{mkdirs()}
        fun shot(name:String){Thread.sleep(600);val bitmap=ui.takeScreenshot()!!;File(out,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()}
        fun back(){ui.injectInputEvent(KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK),true);ui.injectInputEvent(KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK),true);Thread.sleep(500)}
        try {
            wait("4 份档案");shot("wall")
            search("不存在");wait("没有匹配的档案");shot("no-results");click("清除搜索与筛选");wait("4 份档案")
            click("筛选");wait("筛选档案");click("日常收藏");click("完成");wait("2 份档案");shot("filtered")
            click("移除分类筛选");wait("4 份档案")
            click("筛选");wait("筛选档案");click("日常收藏");back();wait("4 份档案")
            click("列表");wait("封面");shot("list")
            search("一封");wait("1 份档案")
            click("一封旧信");wait("档案");wait("补充资料");shot("name-detail");click("返回");wait("1 份档案");wait("封面")
            click("清除搜索");wait("4 份档案")
            click("山间的一个下午");wait("编辑");shot("detail")
            // Delete remains available without relying on a swipe gesture.
            repeat(5){if(find("移入回收站")==null){nodes().lastOrNull{it.isScrollable}?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);Thread.sleep(300)}}
            click("移入回收站");wait("确认");click("取消");assertTrue(store.load().active.any{it.id==archive.id})
            click("移入回收站");click("确认");wait("3 份档案")
            click("设置");wait("整理与外观");shot("settings");click("回收站");click("恢复档案")
            val deadline=SystemClock.uptimeMillis()+10000
            while(store.load().active.none{it.id==archive.id} && SystemClock.uptimeMillis()<deadline) Thread.sleep(100)
            assertEquals(4,store.load().active.size)
            assertEquals(archive.fields,store.load().active.first{it.id==archive.id}.fields)
            assertEquals(photos,store.load().active.first{it.id==archive.id}.photos)
        } catch(e:Throwable){shot("failure");File(out,"failure.txt").writeText(nodes().joinToString("\n"){"${it.text} | ${it.contentDescription}"});throw e}
        finally {i.runOnMainSync{activity.finish()};prefs.edit().clear().apply();val edit=prefs.edit();old.forEach{(k,v)->when(v){is String->edit.putString(k,v);is Boolean->edit.putBoolean(k,v);is Int->edit.putInt(k,v);is Long->edit.putLong(k,v);is Float->edit.putFloat(k,v)}};edit.commit()}
    }
}
