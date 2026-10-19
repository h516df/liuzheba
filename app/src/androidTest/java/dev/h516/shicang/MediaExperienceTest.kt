package dev.h516.shicang

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real rendered vertical drags, byte-preserving exports and system-managed background downloads. */
class MediaExperienceTest {
    @Test fun verticalPagingExportAndDownloads()=runBlocking<Unit> {
        val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext
        check(c.packageName.endsWith(".verification"))
        val prefs=c.getSharedPreferences("device",0);val old=prefs.all
        prefs.edit().putString("generation",uid()).putBoolean("appLock",false).putBoolean("frameHintV2",true).commit()
        val store=Store(c);store.appearance(Appearance(skin="全幅",fit="铺满"))
        val fixtures=mutableListOf<File>()
        val colors=listOf(Color.rgb(230,180,110),Color.rgb(110,210,190),Color.rgb(180,140,230))
        val archives=colors.mapIndexed {index,color->
            val f=File(c.cacheDir,"vertical-$index.jpg");fixtures+=f
            val bitmap=Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888);bitmap.eraseColor(color)
            f.outputStream().use{bitmap.compress(Bitmap.CompressFormat.JPEG,95,it)};bitmap.recycle()
            val p=store.importPhotos(listOf(Uri.fromFile(f))).photos.single()
            Archive(name="分页验收 $index",photos=listOf(p)).also{store.save(it)}
        }
        val ui=i.uiAutomation;var activity:Activity?=null
        val output=File(c.getExternalFilesDir(null),"v013-proof").apply{mkdirs()}
        fun nodes():List<AccessibilityNodeInfo> {ui.clearCache();val result=mutableListOf<AccessibilityNodeInfo>();fun scan(n:AccessibilityNodeInfo?){if(n==null)return;if(n.isVisibleToUser)result+=n;repeat(n.childCount){scan(n.getChild(it))}};scan(ui.rootInActiveWindow);return result}
        fun find(text:String)=nodes().firstOrNull{it.text?.toString()==text || it.contentDescription?.toString()==text}
        fun wait(text:String,timeout:Long=15000):AccessibilityNodeInfo {val end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){find("Got it")?.performAction(AccessibilityNodeInfo.ACTION_CLICK);find(text)?.let{return it};Thread.sleep(100)};error("Missing $text: "+nodes().mapNotNull{it.text}.take(25))}
        fun click(text:String){var n:AccessibilityNodeInfo?=wait(text);while(n!=null && !n.isClickable)n=n.parent;check(n?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true);Thread.sleep(250)}
        fun screenshot(name:String):Bitmap {val b=checkNotNull(ui.takeScreenshot());File(output,"$name.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};return b}
        try {
            activity=i.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            wait("搜索档案");click("发现");wait("全幅照片，双击或双指缩放");Thread.sleep(1400)
            val before=screenshot("before");val w=before.width.toFloat();val h=before.height.toFloat()
            val beforePixel=before.getPixel((w*.5f).toInt(),(h*.72f).toInt());before.recycle()
            var minimum=1.0;var frames=0
            fun sample() {val b=screenshot("drag-${frames++}");var light=0;var total=0
                for(y in b.height*25/100 until b.height*65/100 step 12)for(x in b.width/10 until b.width*9/10 step 12){val pixel=b.getPixel(x,y);if(Color.red(pixel)+Color.green(pixel)+Color.blue(pixel)>180)light++;total++}
                minimum=minOf(minimum,light.toDouble()/total);b.recycle()
            }
            val down=SystemClock.uptimeMillis()
            fun event(action:Int,y:Float){val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,w*.5f,y,0);ui.injectInputEvent(e,true);e.recycle()}
            event(MotionEvent.ACTION_DOWN,h*.82f)
            repeat(12){step->event(MotionEvent.ACTION_MOVE,h*(.82f-.6f*(step+1)/12));Thread.sleep(20);sample()}
            val mid=screenshot("finger-still-down")
            assertNotEquals("Next archive must enter before finger release",beforePixel,mid.getPixel((w*.5f).toInt(),(h*.72f).toInt()));mid.recycle()
            event(MotionEvent.ACTION_UP,h*.22f);repeat(5){Thread.sleep(60);sample()}
            assertTrue("Black flash in rendered vertical paging: $minimum",minimum>.94)
            // A real viewer click publishes the original into the system gallery.
            if(find("保存到相册")==null){val time=SystemClock.uptimeMillis();for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){val e=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,w*.5f,h*.4f,0);ui.injectInputEvent(e,true);e.recycle()}}
            click("保存到相册");Thread.sleep(1800)
            val exported=c.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,arrayOf(MediaStore.Images.Media._ID,MediaStore.Images.Media.DISPLAY_NAME),"${MediaStore.Images.Media.RELATIVE_PATH}=?",arrayOf("Pictures/留册/"),null)!!.use{cursor->buildList {while(cursor.moveToNext())add(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cursor.getLong(0).toString()))}}
            assertTrue("Viewer save must publish an image",exported.isNotEmpty())
            val hashes=exported.map{uri->c.contentResolver.openInputStream(uri).use{hashStream(checkNotNull(it))}}
            assertTrue(archives.any{it.photos.single().sha256 in hashes})
            exported.forEach{c.contentResolver.delete(it,null,null)}
            // DownloadManager is recreated to prove jobs survive leaving the app/recreating repository.
            val downloads=MediaDownloads(c)
            val base="https://raw.githubusercontent.com/h516df/shicang/feat/smooth-browse-media-downloads/app/src/androidTest/assets/"
            val files=listOf("v03-demo.png" to "image","sample-video.mp4" to "video")
            val ids=mutableListOf<String>()
            for((name,kind) in files) {
                val remote=RemoteMedia(base+name,kind,name.substringAfterLast('.'))
                i.runOnMainSync{downloads.enqueue(MediaPlan("下载验收",base+name,listOf(remote)),listOf(remote))}
            }
            val deadline=SystemClock.uptimeMillis()+120000
            var jobs=MediaDownloads(c).list()
            while(jobs.any{it.state !in listOf("待收录","下载失败")} && SystemClock.uptimeMillis()<deadline){Thread.sleep(700);jobs=MediaDownloads(c).list()}
            assertEquals(2,jobs.size);assertTrue("Download status: $jobs",jobs.all{it.state=="待收录"})
            for(job in jobs) {
                ids+=job.id
                val imported=store.importPhotos(listOf(Uri.fromFile(downloads.file(job))))
                assertTrue(imported.errors.toString(),imported.errors.isEmpty());assertEquals(job.kind=="video",imported.photos.single().isVideo)
                assertEquals(sha(downloads.file(job)),imported.photos.single().sha256)
                downloads.remove(job.id)
            }
            File(output,"result.txt").writeText("verticalFrames=$frames minimumVisible=$minimum; fingerDownTransition=true; originalExport=true; systemImageVideoDownloads=2")
        } finally {
            i.runOnMainSync{activity?.finish()};fixtures.forEach{it.delete()}
            val edit=prefs.edit().clear();old.forEach{(key,v)->when(v){is String->edit.putString(key,v);is Boolean->edit.putBoolean(key,v);is Int->edit.putInt(key,v);is Long->edit.putLong(key,v);is Float->edit.putFloat(key,v)}};edit.commit()
        }
    }
}
