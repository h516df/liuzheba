package dev.h516.shicang

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Native, isolated coverage for compact cards and the shared read/edit archive surface. */
class ArchiveWorkflowUiTest {
    @Test fun compactCardsSearchOptionsCancelAndFailedSaveKeepContent()=runBlocking<Unit> {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        check(context.packageName.endsWith(".verification"))
        val prefs=context.getSharedPreferences("device",0)
        val previous=prefs.all
        prefs.edit().putString("generation",uid()).putBoolean("appLock",false)
            .putBoolean("libraryCovers",true).putBoolean("frameHintV2",true).commit()
        val store=Store(context)
        val fixture=File(context.cacheDir,"archive-workflow.png")
        instrumentation.context.assets.open("v03-demo.png").use{input->fixture.outputStream().use{input.copyTo(it)}}
        val media=store.importPhotos(listOf(Uri.fromFile(fixture))).photos
        val text=Archive(name="一封旧信",fields=listOf(Field(name="备注",value="合成的纪念资料")))
        val photo=Archive(name="河岸散步",photos=media)
        store.save(photo);store.save(text)
        store.appearance(Appearance(theme=InstrumentationRegistry.getArguments().getString("visualTheme","月白")))
        val ui=instrumentation.uiAutomation
        val activity=instrumentation.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        fun nodes():List<AccessibilityNodeInfo> {
            ui.clearCache()
            val result=mutableListOf<AccessibilityNodeInfo>()
            fun visit(node:AccessibilityNodeInfo?){if(node==null)return;if(node.isVisibleToUser)result+=node;repeat(node.childCount){visit(node.getChild(it))}}
            visit(ui.rootInActiveWindow);return result
        }
        fun find(value:String)=nodes().firstOrNull{it.text?.toString()==value || it.contentDescription?.toString()==value}
        fun waitFor(value:String):AccessibilityNodeInfo {
            val deadline=SystemClock.uptimeMillis()+10000
            while(SystemClock.uptimeMillis()<deadline){find("Got it")?.performAction(AccessibilityNodeInfo.ACTION_CLICK);find(value)?.let{return it};Thread.sleep(100)}
            error("Missing $value: "+nodes().mapNotNull{it.text}.take(40))
        }
        fun clickable(value:String):AccessibilityNodeInfo {var node:AccessibilityNodeInfo?=waitFor(value);while(node!=null && !node.isClickable)node=node.parent;return checkNotNull(node)}
        fun click(value:String){check(clickable(value).performAction(AccessibilityNodeInfo.ACTION_CLICK));Thread.sleep(400)}
        fun bounds(value:String)=Rect().also{clickable(value).getBoundsInScreen(it)}
        fun setName(value:String){val node=nodes().first{it.isEditable};assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,value)}));Thread.sleep(250)}
        val output=File(context.getExternalFilesDir(null),"archive-workflow").apply{mkdirs()}
        fun screenshot(name:String){Thread.sleep(250);val bitmap=checkNotNull(ui.takeScreenshot());try{File(output,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{bitmap.recycle()}}
        try {
            waitFor("2 份档案")
            assertNull(find("定制规则"));assertNull(find("按意思找 · AI"));assertNull(find("我的别名"))
            assertTrue("Text-only card must not reserve a cover-sized placeholder",bounds(text.name).height()<bounds(photo.name).height())
            screenshot("compact-wall")
            click("${photo.name}的封面");waitFor("全幅照片，双击或双指缩放");waitFor("返回")
            screenshot("media-tap-viewer")
            click("返回");waitFor("2 份档案")
            click("显示");click("搜索选项");waitFor("定制规则");click("显示");click("收起选项");assertNull(find("定制规则"))
            click(text.name);waitFor("编辑");waitFor("尚未添加照片与视频");waitFor("添加");screenshot("text-detail")
            click("编辑");waitFor("编辑档案");setName("取消的修改")
            click("取消");waitFor("确认");click("确认");waitFor("档案")
            assertEquals(text.name,store.load().active.first{it.id==text.id}.name)
            click("编辑");waitFor("编辑档案");setName("");click("保存")
            waitFor("请填写名称或称呼");waitFor("编辑档案")
            assertEquals(text.name,store.load().active.first{it.id==text.id}.name)
            screenshot("validation-keeps-editor")
            setName("保留的旧信");click("保存");waitFor("档案");waitFor("保留的旧信")
            val saved=store.load().active.first{it.id==text.id}
            assertEquals("保留的旧信",saved.name);assertEquals(text.fields,saved.fields)
            screenshot("saved-detail")
        } catch(error:Throwable) {
            screenshot("failure")
            File(output,"failure.txt").writeText(nodes().joinToString("\n"){"${it.text} | ${it.contentDescription}"})
            throw error
        } finally {
            instrumentation.runOnMainSync{activity.finish()}
            val edit=prefs.edit().clear()
            previous.forEach{(key,value)->when(value){is String->edit.putString(key,value);is Boolean->edit.putBoolean(key,value);is Int->edit.putInt(key,value);is Long->edit.putLong(key,value);is Float->edit.putFloat(key,value);is Set<*>->edit.putStringSet(key,value.filterIsInstance<String>().toSet())}}
            edit.commit();fixture.delete();store.close()
        }
    }
}
