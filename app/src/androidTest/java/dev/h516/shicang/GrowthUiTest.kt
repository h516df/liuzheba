package dev.h516.shicang
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import kotlinx.coroutines.runBlocking
class GrowthUiTest {
 @Test fun progressUsesRealControlsAndSurvivesReopen()=runBlocking<Unit>{
  val i=InstrumentationRegistry.getInstrumentation();val c=i.targetContext;check(c.packageName.endsWith(".verification"))
  val prefs=c.getSharedPreferences("device",0);val old=prefs.getString("generation","initial");val lock=prefs.getBoolean("appLock",false)
  prefs.edit().putString("generation",uid()).putBoolean("appLock",false).putBoolean("frameHintV2",true).commit()
  val store=Store(c);store.save(Archive(name="成长合成档案",fields=listOf(Field(name="备注",value="原生验收"))))
  val activity=i.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  try {check(GrowthJourney.run(i,activity,"收藏成长","我的","3 份整理").startsWith("GROWTH_PASS"))}
  finally{i.runOnMainSync{activity.finish()};prefs.edit().putString("generation",old).putBoolean("appLock",lock).commit()}
 }
}
