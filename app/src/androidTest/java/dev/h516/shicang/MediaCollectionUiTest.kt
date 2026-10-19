package dev.h516.shicang

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MediaCollectionUiTest {
    @Test fun receivesPhotoAndVideoAndExportsOriginalsInDarkTheme()=runBlocking<Unit> {
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext;check(c.packageName.endsWith(".verification"))
        val prefs=c.getSharedPreferences("device",0);val oldGeneration=prefs.getString("generation","initial");val oldLock=prefs.getBoolean("appLock",false)
        prefs.edit().putString("generation",uid()).putBoolean("appLock",false).commit()
        val store=Store(c);store.appearance(Appearance(theme="曜石"))
        val ui=i.uiAutomation;var activity:Activity?=null
        fun nodes():List<AccessibilityNodeInfo>{ui.clearCache();val result=mutableListOf<AccessibilityNodeInfo>();fun scan(n:AccessibilityNodeInfo?){if(n==null)return;if(n.isVisibleToUser)result+=n;repeat(n.childCount){scan(n.getChild(it))}};scan(ui.rootInActiveWindow);return result}
        fun find(text:String)=nodes().firstOrNull{it.text?.toString()==text || it.contentDescription?.toString()==text}
        fun wait(text:String):AccessibilityNodeInfo{val end=SystemClock.uptimeMillis()+18000;while(SystemClock.uptimeMillis()<end){find(text)?.let{return it};Thread.sleep(100)};error("Missing $text: "+nodes().mapNotNull{it.text}.take(30))}
        fun click(text:String){var n:AccessibilityNodeInfo?=wait(text);while(n!=null && !n.isClickable)n=n.parent;check(n?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true);Thread.sleep(300)}
        fun shot(name:String){val b=checkNotNull(ui.takeScreenshot());val folder=File(c.getExternalFilesDir(null),"v013-collection").apply{mkdirs()};File(folder,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()}
        val exported=mutableListOf<Uri>()
        try {
            activity=i.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            wait("把值得记住的，留在这里");shot("empty-dark")
            val uris=ArrayList(listOf("v03-demo.png","sample-video.mp4").map{name ->
                val target=File(c.cacheDir,"camera/$name");target.parentFile!!.mkdirs()
                i.context.assets.open(name).use{input->target.outputStream().use{input.copyTo(it)}}
                FileProvider.getUriForFile(c,"${c.packageName}.files",target)
            })
            c.startActivity(Intent(c,MainActivity::class.java).setAction(Intent.ACTION_SEND_MULTIPLE).setType("image/*")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
                .apply{clipData=ClipData.newUri(c.contentResolver,"测试媒体",uris[0]).apply{addItem(ClipData.Item(uris[1]))}}
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION))
            wait("收录 2 项媒体");click("新建档案");wait("编辑档案");shot("shared-editor-dark")
            click("保存");wait("档案")
            val persisted=Store(c).load().active.single();assertEquals(2,persisted.photos.size);assertEquals(1,persisted.photos.count{it.isVideo})
            // The real multi-item gallery pipeline retains the bytes for both kinds and suppresses duplicates.
            assertEquals(2,MediaExport.save(c,MediaExportRequest(persisted,persisted.photos),{File(store.photoDir,it.file)}){})
            assertEquals(2,MediaExport.save(c,MediaExportRequest(persisted,persisted.photos),{File(store.photoDir,it.file)}){})
            for(p in persisted.photos) {
                val collection=if(p.isVideo)MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val path=if(p.isVideo)"Movies/留册/" else "Pictures/留册/"
                val found=c.contentResolver.query(collection,arrayOf(MediaStore.MediaColumns._ID),"${MediaStore.MediaColumns.RELATIVE_PATH}=? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",arrayOf(path,"%-${p.id.take(8)}.%"),null)!!.use{cursor->buildList{while(cursor.moveToNext())add(Uri.withAppendedPath(collection,cursor.getLong(0).toString()))}}
                assertEquals("Repeated save should not duplicate media",1,found.size);exported+=found
                assertEquals(p.sha256,c.contentResolver.openInputStream(found.single()).use{hashStream(checkNotNull(it))})
            }
            click("返回");wait("搜索档案");click("链接下载");wait("粘贴分享链接或媒体地址");shot("downloads-dark-large")
        } finally {
            exported.forEach{c.contentResolver.delete(it,null,null)}
            i.runOnMainSync{activity?.finish()};prefs.edit().putString("generation",oldGeneration).putBoolean("appLock",oldLock).commit()
        }
    }
}
