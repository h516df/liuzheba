package dev.h516.shicang

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
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
class DesignV19Test {
    @Test fun nativeV19LibraryDetailsEditorTemplateAndReopen()=runBlocking<Unit> {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val c=instrumentation.targetContext
        check(c.packageName.endsWith(".verification"))
        val prefs=c.getSharedPreferences("device",Context.MODE_PRIVATE)
        val previous=prefs.getString("generation","initial")!!
        val previousLock=prefs.getBoolean("appLock",false)
        val previousCovers=prefs.getBoolean("libraryCovers",false)
        val temporary=uid()
        prefs.edit().putString("generation",temporary).putBoolean("appLock",false).putBoolean("libraryCovers",false).putBoolean("frameHintV2",true).commit()
        val store=Store(c)
        val picture=File(c.cacheDir,"v03-demo.png")
        instrumentation.context.assets.open("v03-demo.png").use{input->picture.outputStream().use{input.copyTo(it)}}
        val photos=store.importPhotos(List(3){Uri.fromFile(picture)}).photos
        val category=store.label("category","朋友")
        val archive=Archive(name="林雨（示例）",category=category,photos=photos,fields=listOf(Field(name="居住地",value="示例城市"),Field(name="抖音主页",type="链接",value="https://www.douyin.com/")))
        store.save(archive)
        store.appearance(Appearance(theme=InstrumentationRegistry.getArguments().getString("visualTheme","月白"),skin="全幅",fit="完整"))
        val automation=instrumentation.uiAutomation
        var activity:Activity?=null
        fun nodes():List<AccessibilityNodeInfo> {
            fun visit(n:AccessibilityNodeInfo?,out:MutableList<AccessibilityNodeInfo>){if(n==null)return;if(n.isVisibleToUser)out.add(n);repeat(n.childCount){visit(n.getChild(it),out)}}
            automation.clearCache();return mutableListOf<AccessibilityNodeInfo>().also{visit(automation.rootInActiveWindow,it)}
        }
        fun node(text:String)=nodes().firstOrNull{it.text?.toString()?.lineSequence()?.firstOrNull()==text || it.contentDescription?.toString()==text}
        fun waitNode(text:String):AccessibilityNodeInfo {
            val end=SystemClock.uptimeMillis()+10000
            while(SystemClock.uptimeMillis()<end){node("Got it")?.performAction(AccessibilityNodeInfo.ACTION_CLICK);node(text)?.let{return it};Thread.sleep(80)}
            error("Missing v19 UI: $text")
        }
        fun tap(x:Int,y:Int){val now=SystemClock.uptimeMillis();val down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,x.toFloat(),y.toFloat(),0);val up=MotionEvent.obtain(now,now+70,MotionEvent.ACTION_UP,x.toFloat(),y.toFloat(),0);automation.injectInputEvent(down,true);automation.injectInputEvent(up,true);down.recycle();up.recycle();Thread.sleep(130)}
        fun click(text:String){
            automation.waitForIdle(300,5000)
            val end=SystemClock.uptimeMillis()+5000
            var previousBounds:Rect?=null
            var stable:Rect?=null
            while(SystemClock.uptimeMillis()<end && stable==null) {
                val target=waitNode(text)
                if(target.refresh()) {
                    val bounds=Rect();target.getBoundsInScreen(bounds)
                    if(!bounds.isEmpty && bounds==previousBounds) stable=bounds else previousBounds=bounds
                } else previousBounds=null
                if(stable==null) automation.waitForIdle(300,5000)
            }
            val bounds=checkNotNull(stable){"No stable touch bounds for $text"}
            tap(bounds.centerX(),bounds.centerY())
            automation.waitForIdle(300,5000)
        }
        fun controls(){
            automation.waitForIdle(500,5000)
            if(nodes().any{it.contentDescription?.toString()?.contains("双指缩放")==true}) {
                val until=SystemClock.uptimeMillis()+10000
                while(nodes().none{it.stateDescription?.toString()=="照片已显示"} && SystemClock.uptimeMillis()<until) Thread.sleep(80)
                check(nodes().any{it.stateDescription?.toString()=="照片已显示"}) {"Photo did not finish loading"}
            }
            if(node("更多")==null){val size=c.resources.displayMetrics;tap(size.widthPixels/2,size.heightPixels/2)};waitNode("更多")
        }
        fun reveal(text:String) {
            repeat(5) {
                val settled=SystemClock.uptimeMillis()+1000
                while(SystemClock.uptimeMillis()<settled) {
                    if(node(text)!=null) return
                    Thread.sleep(80)
                }
                // The archive list/grid follows the horizontal draft strip.
                if(nodes().lastOrNull{it.isScrollable}?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)!=true) {
                    waitNode(text);return
                }
                automation.waitForIdle(300,5000)
            }
            waitNode(text)
        }
        fun editorFields()=nodes().filter{it.className?.toString()=="android.widget.EditText"}
        fun waitEditorFields(count:Int) {
            val end=SystemClock.uptimeMillis()+5000
            while(editorFields().size!=count && SystemClock.uptimeMillis()<end) Thread.sleep(80)
            assertEquals("Visible editor fields after navigation/update",count,editorFields().size)
        }
        fun fill(index:Int,value:String){val edit=editorFields().getOrNull(index) ?: error("Missing field $index");check(edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)}));Thread.sleep(200)}
        val out=File(c.filesDir,"v04-ui").apply{mkdirs()}
        fun screenshot(name:String){Thread.sleep(250);val image=automation.takeScreenshot() ?: error("No screenshot");try{File(out,"$name.png").outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{image.recycle()}}
        fun start():Activity=instrumentation.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        try {
            activity=start();waitNode("搜索档案");screenshot("home");click("发现");waitNode("完整照片，双击或双指缩放");controls()
            click("切换到档案");waitNode("搜索档案");Thread.sleep(500);screenshot("mine")
            click("筛选");waitNode("完成");click("朋友");click("完成");waitNode("资料")
            click("筛选 · 1");click("清除");click("完成");waitNode("筛选")
            click("封面");waitNode("列表");assertTrue(prefs.getBoolean("libraryCovers",false));screenshot("covers");click("列表");waitNode("封面")
            assertNull(node("全部标签"));assertNull(node("全部"))
            click("资料");waitNode("档案详情");screenshot("details");reveal("抖音");waitNode("居住地");waitNode("抖音")
            nodes().firstOrNull{it.isScrollable}?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);Thread.sleep(300)
            click("查看全部");waitNode("查看第2项照片或视频");screenshot("album")
            click("查看第2项照片或视频");waitNode("完整照片，双击或双指缩放");controls();screenshot("viewer")
            click("返回");waitNode("查看第2项照片或视频");click("返回");waitNode("档案详情")
            click("编辑");waitNode("编辑档案");screenshot("editor")
            fill(0,"未保存的改名");click("返回");waitNode("林雨（示例）")
            assertEquals("林雨（示例）",store.load().active.single().name)
            click("返回");waitNode("搜索档案");click("收录");click("文字档案");waitNode("新建档案")
            waitEditorFields(3);assertTrue(editorFields().all{it.text.isNullOrBlank()});screenshot("blank")
            click("删除");waitNode("撤销");waitEditorFields(1);click("撤销");waitEditorFields(3)
            fill(1,"居住地");fill(2,"不会复制的城市")
            click("存为模板");waitNode("保存模板");fill(0,"亲友资料");click("保存模板")
            val timeout=SystemClock.uptimeMillis()+5000
            while(store.load().templates.isEmpty() && SystemClock.uptimeMillis()<timeout) Thread.sleep(100)
            assertEquals("居住地",store.load().templates.single().fields.single().name)
            click("返回");waitNode("搜索档案");click("收录");click("文字档案");waitNode("新建档案")
            waitEditorFields(3)
            assertTrue(editorFields().all{it.text.isNullOrBlank()})
            click("选择模板");waitNode("亲友资料");click("亲友资料");waitNode("新建档案")
            waitEditorFields(3)
            assertEquals("居住地",editorFields()[1].text.toString());assertTrue(editorFields()[2].text.isNullOrBlank())
            fill(0,"妈妈");fill(2,"杭州")
            reveal("分类与标签");click("分类与标签");waitNode("完成");click("标签 · 0")
            fill(0,"常联系");click("新建标签「常联系」");waitNode("移除标签 常联系")
            click("移除标签 常联系");waitNode("标签 · 0");assertNull(node("移除标签 常联系"))
            click("常联系");waitNode("移除标签 常联系")
            screenshot("tag-selected");click("完成");waitNode("新建档案")
            click("保存");waitNode("档案详情");waitNode("杭州");screenshot("no-photo")
            val saved=Store(c).load().active.single{it.name=="妈妈"};assertTrue(saved.photos.isEmpty());assertEquals("杭州",saved.fields.single().value)
            assertEquals("常联系",store.load().labels.single{it.id==saved.tags.single()}.name)
            instrumentation.runOnMainSync{activity?.finish()};Thread.sleep(200);activity=start()
            // The saved record survives an Activity/model reopen. Find it through the library.
            if(node("切换到档案")==null) controls()
            click("切换到档案");waitNode("搜索档案");fill(0,"杭州");waitNode("妈妈");screenshot("search")
            val searchDeadline=SystemClock.uptimeMillis()+5000
            while(node("林雨（示例）")!=null && SystemClock.uptimeMillis()<searchDeadline) Thread.sleep(80)
            assertNull(node("林雨（示例）"));click("妈妈");waitNode("档案详情");waitNode("杭州")
            // Empty-media archives retain the same media section and direct add action.
            nodes().firstOrNull{it.isScrollable}?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);Thread.sleep(300)
            waitNode("照片与视频");waitNode("尚未添加照片与视频");waitNode("添加");screenshot("reopened-no-photo")
            if(InstrumentationRegistry.getArguments().getString("visualDensity")=="true") {
                store.save(Archive(name="周末的城市散步",category=category,photos=photos.take(1),fields=listOf(Field(name="地点",value="老街与河岸"))))
                store.save(Archive(name="外婆的手写食谱与节日记忆",photos=photos.take(2)))
                instrumentation.runOnMainSync{activity?.finish()};Thread.sleep(250);activity=start()
                if(node("切换到档案")==null) controls()
                click("切换到档案");waitNode("搜索档案");waitNode("4 份档案")
                screenshot("populated-list")
                click("封面");waitNode("列表");screenshot("populated-covers")
                reveal("妈妈");click("妈妈");waitNode("档案详情");waitNode("杭州")
                click("返回");waitNode("列表")
                click("列表");waitNode("封面");reveal("妈妈");click("妈妈");waitNode("杭州")
            }
            // Copy last: Android's transient clipboard overlay otherwise covers viewer navigation.
            click("返回");waitNode("搜索档案");fill(0,"林雨");waitNode("林雨（示例）")
            click("资料");reveal("抖音");click("复制")
            val clipboard=c.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val copyEnd=SystemClock.uptimeMillis()+3000
            while(clipboard.primaryClip?.getItemAt(0)?.text?.toString()!="https://www.douyin.com/" && SystemClock.uptimeMillis()<copyEnd) Thread.sleep(80)
            assertEquals("https://www.douyin.com/",clipboard.primaryClip?.getItemAt(0)?.text?.toString())

                } catch(e:Throwable) {
            screenshot("failure")
            File(out,"failure.txt").writeText(nodes().joinToString("\n"){"${it.className}: ${it.text} | ${it.contentDescription} | ${it.stateDescription}"})
            throw e
        } finally {
            instrumentation.runOnMainSync{activity?.finish()}
            prefs.edit().putString("generation",previous).putBoolean("appLock",previousLock).putBoolean("libraryCovers",previousCovers).commit()
            picture.delete()
        }
    }
}
