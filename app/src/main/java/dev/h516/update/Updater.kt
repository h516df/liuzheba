package dev.h516.update

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import java.io.File
import java.util.concurrent.Executors

/** 更新界面要显示的状态。 */
sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    class Latest(val notes: ReleaseNotes?) : UpdateState()
    class Available(val candidate: UpdateCandidate) : UpdateState()
    class Downloading(val percent: Int) : UpdateState()
    class ReadyToInstall(val file: File) : UpdateState()
    class Failed(val message: String) : UpdateState()
}

/**
 * 检查更新 → 下载 → 校验 → 交给系统安装。
 *
 * 联网、下载都在后台线程，状态回主线程；任何一步失败都只影响这次更新，旧版本继续用。
 */
class Updater(private val context: Context) {

    private var http = UpdateHttp()
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "shicang-update").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    private var candidate: UpdateCandidate? = null
    @Volatile private var cancelled = false

    /** 当前版本的更新说明，打进 App 里，没网也能看。 */
    fun currentNotes(): ReleaseNotes? = runCatching {
        context.assets.open("release-notes.json").use {
            ReleaseNotes.decode(String(it.readBytes(), Charsets.UTF_8))
        }
    }.getOrNull()

    fun installedVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(UpdateConfig.PACKAGE, 0).versionName.orEmpty()
    }.getOrDefault("")

    fun installedVersionCode(): Int = runCatching { UpdateVerify.installedVersionCode(context) }.getOrDefault(0)

    fun check() {
        if (state is UpdateState.Checking || state is UpdateState.Downloading) return
        cancelled = false
        http = UpdateHttp()
        candidate = null
        set(UpdateState.Checking)
        io.execute {
            try {
                clearLegacyUpdateAuthorization(context)
                val releases = http.json("/releases?per_page=20")
                val published = UpdateCatalog.parse(releases)
                val newest = UpdateCatalog.newest(published)
                    ?: throw UpdateFailure("还没发布过带更新说明的版本，请稍后再试")
                val notes = ReleaseNotes.decode(http.assetText(newest.notesAsset.id))
                val installed = UpdateVerify.installedVersionCode(context)
                when (val result = UpdateCatalog.decide(notes, newest.assets, installed, UpdateVerify.deviceAbis())) {
                    is UpdateCatalog.Result.Latest -> set(UpdateState.Latest(result.notes))
                    is UpdateCatalog.Result.Available -> {
                        candidate = result.candidate
                        set(UpdateState.Available(result.candidate))
                    }
                }
            } catch (e: UpdateFailure) {
                set(UpdateState.Failed(e.userMessage))
            } catch (e: InterruptedException) {
                set(UpdateState.Idle)
            } catch (e: Exception) {
                set(if (cancelled) UpdateState.Idle else UpdateState.Failed("检查更新失败，请稍后重试"))
            }
        }
    }

    fun download() {
        val target = candidate ?: return
        if (state is UpdateState.Downloading) return
        cancelled = false
        http = UpdateHttp()
        set(UpdateState.Downloading(0))
        io.execute {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val dest = File(dir, target.apk.name)
                http.downloadApk(target.apk.id, target.apk.size, dest) { percent ->
                    set(UpdateState.Downloading(percent))
                }
                val expectedSha = target.shaFile?.let { UpdateVerify.parseShaFile(http.assetText(it.id)) }
                    ?: target.apk.apiDigest
                    ?: throw UpdateFailure("发布资料里缺校验值，请稍后再试")
                UpdateVerify.verifyHash(dest, target.apk.size, expectedSha, target.apk.apiDigest)
                UpdateVerify.verifyArchive(context, dest, target.notes.code)
                set(UpdateState.ReadyToInstall(dest))
            } catch (e: UpdateFailure) {
                set(UpdateState.Failed(e.userMessage))
            } catch (e: InterruptedException) {
                set(UpdateState.Idle)
            } catch (e: Exception) {
                set(if (cancelled) UpdateState.Idle else UpdateState.Failed("下载或校验失败，请稍后重试"))
            }
        }
    }

    /** 交给系统安装器；装不装由你在系统界面上确认，App 不会偷偷装。 */
    fun install() {
        val ready = state as? UpdateState.ReadyToInstall ?: return
        try {
            val uri = FileProvider.getUriForFile(context, "${UpdateConfig.PACKAGE}.updates", ready.file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            set(UpdateState.Failed("打不开系统安装界面，请稍后重试"))
        }
    }

    fun cancel() {
        cancelled = true
        http.cancel()
        candidate = null
        if (state !is UpdateState.Checking && state !is UpdateState.Downloading) set(UpdateState.Idle)
    }

    private fun set(value: UpdateState) {
        main.post { state = value }
    }

}
