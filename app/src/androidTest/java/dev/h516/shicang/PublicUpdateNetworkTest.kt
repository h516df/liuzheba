package dev.h516.shicang

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.h516.update.UpdateState
import dev.h516.update.Updater
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** The isolated test build must never download or install an official update.
 * Real signed old-to-new upgrade acceptance is performed through the official app UI.
 */
@RunWith(AndroidJUnit4::class)
class PublicUpdateNetworkTest {
    @Test fun verificationBuildCannotRequestOfficialUpdates() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        check(context.packageName.endsWith(".verification"))
        instrumentation.runOnMainSync {
            val updater=Updater(context)
            try {
                updater.changeAutoCheckEnabled(true);updater.onForeground();updater.check();updater.download();updater.install()
                assertEquals(UpdateState.Unsupported,updater.state)
                assertNull(updater.hint())
            } finally {updater.close()}
        }
    }
}
