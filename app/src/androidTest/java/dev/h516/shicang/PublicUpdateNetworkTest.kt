package dev.h516.shicang

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.h516.update.UpdateState
import dev.h516.update.Updater
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 手动启用的发行验收：正式 v0.4 已安装，公开 v0.5 已发布。普通测试不联网。 */
@RunWith(AndroidJUnit4::class)
class PublicUpdateNetworkTest {
    @Test fun anonymousDownloadValidatesAgainstInstalledOfficialVersion() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("publicUpdate") == "true")
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".verification"))
        lateinit var updater: Updater
        instrumentation.runOnMainSync { updater = Updater(context) }
        assertEquals(5, updater.installedVersionCode())
        fun waitFor(done: (UpdateState) -> Boolean): UpdateState {
            // Direct release-asset downloads can be slow; retain a bounded ten-minute acceptance window.
            val deadline = SystemClock.uptimeMillis() + 600_000
            var observed: UpdateState = UpdateState.Idle
            while (SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync { observed = updater.state }
                if (observed is UpdateState.Failed) fail((observed as UpdateState.Failed).message)
                if (done(observed)) return observed
                SystemClock.sleep(100)
            }
            error("Public update timed out: ${observed.javaClass.simpleName}")
        }
        instrumentation.runOnMainSync { updater.check() }
        val available = waitFor { it is UpdateState.Available } as UpdateState.Available
        assertEquals(6, available.candidate.notes.code)
        instrumentation.runOnMainSync { updater.download() }
        val ready = waitFor { it is UpdateState.ReadyToInstall } as UpdateState.ReadyToInstall
        assertEquals(available.candidate.apk.size, ready.file.length())
        // Updater has checked size, SHA256, package, greater version, official signature and ABI.
        // Installation remains a separate explicit system operation.
        ready.file.delete()
    }
}
