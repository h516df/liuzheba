package dev.h516.shicang

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File

/** Samples rendered pixels during both directions, including the current-page handoff. */
class PhotoSwipeTest {
 @Test fun photosStayVisibleWhilePaging()=runBlocking<Unit>{
  val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext;check(c.packageName.endsWith(".verification"))
  val prefs=c.getSharedPreferences("device",0);val old=prefs.all
  prefs.edit().putString("generation",uid()).putBoolean("appLock",false).putBoolean("frameHintV2",true).commit()
  val store=Store(c)
  val files=listOf(Color.rgb(230,180,110),Color.rgb(110,210,190),Color.rgb(180,140,230)).mapIndexed{index,color->
   File(c.cacheDir,"swipe-$index.jpg").also{file->val b=Bitmap.createBitmap(1080,1920,Bitmap.Config.ARGB_8888);b.eraseColor(color);file.outputStream().use{b.compress(Bitmap.CompressFormat.JPEG,95,it)};b.recycle()}
  }
  val photos=store.importPhotos(files.map{Uri.fromFile(it)}).photos
  store.save(Archive(name="连续切图验收",photos=photos))
  val activity=i.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  val ui=i.uiAutomation;val folder=File(c.getExternalFilesDir(null),"photo-swipe").apply{mkdirs()}
  try{
   Thread.sleep(2500)
   ui.rootInActiveWindow?.findAccessibilityNodeInfosByText("Got it")?.firstOrNull()?.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
   Thread.sleep(300)
   var minimum=1.0;var frames=0
   fun sample(){val b=ui.takeScreenshot()?:error("No screenshot");var light=0;var total=0
    for(y in b.height*42/100 until b.height*56/100 step 8)for(x in b.width/10 until b.width*9/10 step 8){val p=b.getPixel(x,y);if(Color.red(p)+Color.green(p)+Color.blue(p)>180)light++;total++}
    val fraction=light.toDouble()/total;minimum=minOf(minimum,fraction)
    File(folder,"frame-${frames++}.png").outputStream().use{b.compress(Bitmap.CompressFormat.PNG,100,it)};b.recycle()
   }
   val first=ui.takeScreenshot()!!;val w=first.width.toFloat();val h=first.height.toFloat();first.recycle()
   fun event(action:Int,x:Float,down:Long){val e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,h*.48f,0);ui.injectInputEvent(e,true);e.recycle()}
   sample()
   for(left in listOf(true,true,false,false)){
    val down=SystemClock.uptimeMillis();val start=if(left)w*.86f else w*.14f;val end=if(left)w*.14f else w*.86f
    event(MotionEvent.ACTION_DOWN,start,down)
    repeat(14){step->event(MotionEvent.ACTION_MOVE,start+(end-start)*(step+1)/14,down);sample()}
    event(MotionEvent.ACTION_UP,end,down);repeat(3){Thread.sleep(45);sample()};Thread.sleep(180)
   }
   File(folder,"result.txt").writeText("frames=$frames minimumVisible=$minimum")
   check(minimum>.94){"Photo flashed during page handoff: minimumVisible=$minimum frames=$frames"}
  }finally{i.runOnMainSync{activity.finish()};val e=prefs.edit().clear();old.forEach{(k,v)->when(v){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);is Int->e.putInt(k,v);is Long->e.putLong(k,v);is Float->e.putFloat(k,v);is Set<*>->e.putStringSet(k,v.filterIsInstance<String>().toSet())}};e.commit();files.forEach{it.delete()}}
 }
}
