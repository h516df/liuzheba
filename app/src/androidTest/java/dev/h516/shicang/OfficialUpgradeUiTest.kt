package dev.h516.shicang

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** External UI acceptance on a disposable emulator. Never instruments, clears or uninstalls production. */
@androidx.test.filters.SdkSuppress(minSdkVersion=28)
@RunWith(AndroidJUnit4::class)
class OfficialUpgradeUiTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private val ui=instrumentation.uiAutomation
    private val production="dev.h516.shicang"
    private val archiveName="升级验收：文字与混合媒体"
    private val body="从正式 0.16 保留的合成文字。照片、横屏视频和竖屏视频属于同一档案。"

    @Test fun officialUpgradeThroughSharedMediaSystemInstallerAndExport() {
        val args=InstrumentationRegistry.getArguments()
        check(context.packageName=="$production.verification" && args.getString("disposable")=="true" && Build.HARDWARE in setOf("ranchu","goldfish")) {
            "Official upgrade acceptance is restricted to an explicitly disposable emulator and isolated test package"
        }
        ui.serviceInfo=ui.serviceInfo.apply{flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS}
        when(args.getString("phase")) {
            "seed"->seed()
            "install"->install()
            "verify"->verify()
            else->error("Explicit seed/install/verify phase is required")
        }
    }
    @Suppress("DEPRECATION") private fun installedCode()=context.packageManager.getPackageInfo(production,0).longVersionCode
    private fun launchOfficial() {
        context.startActivity(checkNotNull(context.packageManager.getLaunchIntentForPackage(production)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("official navigation ready") {
            val current=nodes()
            current.none{it.text?.toString()?.startsWith("正在打开资料")==true} && current.any {
                it.packageName?.toString()==production &&
                    (it.text?.toString() in listOf("档案详情","备份与恢复","设置") || it.contentDescription?.toString()=="搜索档案")
            }
        }
        returnToLibrary()
    }

    private fun seed() {
        check(installedCode()==17L){"Seed requires the published 0.16 installation"}
        val sources=listOf("sample-long-video.mp4","sample-portrait-video.mp4").map {asset->
            val file=File(context.cacheDir,"camera/upgrade-$asset");file.parentFile!!.mkdirs()
            instrumentation.context.assets.open(asset).use{input->file.outputStream().use{input.copyTo(it)}}
            FileProvider.getUriForFile(context,"${context.packageName}.files",file)
        }
        val image=File(context.cacheDir,"camera/upgrade-image.png")
        android.graphics.Bitmap.createBitmap(160,160,android.graphics.Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.rgb(30,110,150));image.outputStream().use{compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};recycle()
        }
        val media=listOf(FileProvider.getUriForFile(context,"${context.packageName}.files",image))+sources
        val clips=ClipData.newUri(context.contentResolver,"synthetic mixed media",media.first()).apply{media.drop(1).forEach{addItem(ClipData.Item(it))}}
        // 0.16 only routes image/* or video/*; each URI still has its real provider MIME type.
        context.startActivity(Intent(Intent.ACTION_SEND_MULTIPLE).setClassName(production,"$production.MainActivity").setType("image/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM,ArrayList(media)).apply{clipData=clips}
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
        click("新建档案");waitText("新建档案")
        setText(waitNode("archive name"){it.isEditable},archiveName)
        click("保存");waitText(archiveName)
        clickScrolling("写长笔记")
        waitText("笔记正文")
        val text=nodes().filter{it.isEditable}.last()
        setText(text,body);click("保存笔记");waitText("档案详情")
        returnToLibrary()
        export("upgrade-before.zip")
    }
    private fun install() {
        check(installedCode()==17L)
        val apk=File(context.cacheDir,"camera/upgrade.apk");check(apk.isFile && apk.length()>1000000)
        if(Build.VERSION.SDK_INT>=26 && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            waitNode("unknown-source settings label"){it.text?.toString() in listOf("Allow from this source","允许来自此来源的应用","允许此来源的应用")}
            val toggle=waitNode("system unknown-source switch"){
                it.packageName?.toString()=="com.android.settings" && it.isCheckable && it.isEnabled
            }
            if(!toggle.isChecked)click(toggle)
            await("allow this installation source"){context.packageManager.canRequestPackageInstalls()}
            ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);SystemClock.sleep(300)
        }
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
            .apply{clipData=ClipData.newRawUri("verified official upgrade",uri)}
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
        clickAny("Update","更新")
        await("system installation",60000){installedCode()>17L}
        File(context.filesDir,"official-upgrade-install.json").writeText("{\"systemConfirmation\":true,\"previousCode\":17,\"installedCode\":${installedCode()}}")
    }
    private fun verify() {
        check(installedCode()>17L)
        launchOfficial();click(archiveName);waitText(body);waitText("1 张图片 · 2 个视频")
        // Verify through the actual new reader before exporting all data for byte/hash comparison.
        returnToLibrary();export("upgrade-after.zip")
    }
    private fun returnToLibrary() {
        repeat(4){if(nodes().any{it.contentDescription?.toString()=="搜索档案"})return;ui.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);SystemClock.sleep(350)}
        waitText("搜索档案")
    }
    private fun export(filename:String) {
        // The immutable 0.16 baseline has a bottom "我的" tab, while current versions
        // expose "设置" from the library header. Exercise each published UI as shipped.
        if (installedCode()==17L) {
            click("切换到我的")
            waitText("我的")
        } else {
            click("设置")
        }
        clickScrolling("备份与恢复");clickScrolling("导出备份文件（换机或留存）");click("确认")
        val name=waitNode("system backup filename"){it.isEditable && it.packageName?.toString()?.contains("documentsui")==true}
        setText(name,filename)
        click(waitNode("system save action"){it.isEnabled && (it.viewIdResourceName?.endsWith(":id/action_menu_save")==true || it.text?.toString().equals("Save",ignoreCase=true) || it.text?.toString()=="保存")})
        waitText("完整备份已保存并校验，可在所选文件位置找到")
    }
    private fun nodes():List<AccessibilityNodeInfo> {
        ui.clearCache()
        val result=mutableListOf<AccessibilityNodeInfo>()
        fun visit(node:AccessibilityNodeInfo?){if(node==null)return;if(node.isVisibleToUser)result+=node;repeat(node.childCount){visit(node.getChild(it))}}
        visit(ui.rootInActiveWindow);return result
    }
    private fun waitNode(label:String,predicate:(AccessibilityNodeInfo)->Boolean):AccessibilityNodeInfo {
        var found:AccessibilityNodeInfo?=null
        await(label){found=nodes().firstOrNull(predicate);found!=null};return checkNotNull(found)
    }
    private fun waitText(text:String)=waitNode(text){it.text?.toString()==text || it.contentDescription?.toString()==text}
    private fun click(text:String)=click(waitText(text))
    private fun clickAny(vararg text:String)=click(waitNode(text.joinToString("/")){node->text.any{it.equals(node.text?.toString(),ignoreCase=true) || it.equals(node.contentDescription?.toString(),ignoreCase=true)}})
    private fun click(node:AccessibilityNodeInfo) {
        var target:AccessibilityNodeInfo?=node
        while(target!=null && !target.isClickable)target=target.parent
        if(target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)!=true) {
            // Some system controls expose a visible switch but reject accessibility ACTION_CLICK.
            // Touch its actual reported bounds, exactly as a person would; never grant permissions via shell.
            val visible=target ?: node
            val bounds=android.graphics.Rect();visible.getBoundsInScreen(bounds)
            check(visible.isVisibleToUser && visible.isEnabled && !bounds.isEmpty){"Control is not touchable: ${visible.text}"}
            val down=SystemClock.uptimeMillis()
            for(action in listOf(android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP)) {
                val event=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
                event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                try{check(ui.injectInputEvent(event,true))}finally{event.recycle()}
            }
        }
        SystemClock.sleep(250)
    }
    private fun clickScrolling(text:String) {
        repeat(14){
            nodes().firstOrNull{it.text?.toString()==text || it.contentDescription?.toString()==text}?.let{click(it);return}
            check(nodes().filter{it.isScrollable}.any{it.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)}){"Cannot scroll to required control"};SystemClock.sleep(250)
        }
        error("Required control was not found")
    }
    private fun setText(node:AccessibilityNodeInfo,text:String){check(node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply{putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text)}));SystemClock.sleep(200)}
    private fun await(label:String,timeout:Long=25000,condition:()->Boolean){val end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){if(condition())return;SystemClock.sleep(150)};error("Timed out: $label; UI: "+nodes().mapNotNull{it.text?.toString() ?: it.contentDescription?.toString()}.joinToString(" | ").take(2500))}
}
