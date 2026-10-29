package dev.h516.update

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.concurrent.Executors

sealed class UpdateState {
    object Idle:UpdateState()
    object Unsupported:UpdateState()
    object Checking:UpdateState()
    object Verifying:UpdateState()
    class Latest(val notes:ReleaseNotes?):UpdateState()
    class Available(val candidate:UpdateCandidate):UpdateState()
    class Downloading(val percent:Int):UpdateState()
    class ReadyToInstall(val file:File):UpdateState()
    object NeedsInstallPermission:UpdateState()
    class Installed(val version:String):UpdateState()
    class Failed(val message:String):UpdateState()
}

/** Application-owned session. Only a verified APK receipt survives process death. */
class Updater internal constructor(context:Context,private val environment:UpdateEnvironment,private val httpFactory:()->UpdateHttp) {
    constructor(context:Context):this(context.applicationContext,AndroidUpdateEnvironment(context.applicationContext),{UpdateHttp()})
    private val context=context.applicationContext
    private val prefs=this.context.getSharedPreferences("public_update_session",Context.MODE_PRIVATE)
    private val directory=File(this.context.cacheDir,"updates")
    private val io=Executors.newSingleThreadExecutor{r->Thread(r,"shicang-update").apply{isDaemon=true}}
    private val main=Handler(Looper.getMainLooper())
    private var http=httpFactory()
    @Volatile private var generation=0L
    @Volatile private var closed=false
    private var candidate:UpdateCandidate?=null
    private var verified:VerifiedUpdate?=null
    private var foreground=false
    private var foregroundEpoch=0L
    private var installAfterDownload=false
    private var quietChecking=false
    private var returningFromPermission=false
    var state by mutableStateOf<UpdateState>(UpdateState.Idle);private set
    var promptDismissed by mutableStateOf(false);private set
    var autoCheckEnabled by mutableStateOf(prefs.getBoolean("auto_check",true));private set
    var reminderAsked by mutableStateOf(prefs.getBoolean("reminder_asked",false));private set
    fun changeAutoCheckEnabled(enabled:Boolean){if(!enabled && quietChecking)cancel();autoCheckEnabled=enabled;reminderAsked=true;prefs.edit().putBoolean("auto_check",enabled).putBoolean("reminder_asked",true).apply();if(enabled && foreground)checkIfDue()}
    fun dismissReminder(){reminderAsked=true;prefs.edit().putBoolean("reminder_asked",true).apply()}
    fun canOfferReminder()=state!=UpdateState.Unsupported && !autoCheckEnabled && !reminderAsked

    init {
        if(!environment.official()) state=UpdateState.Unsupported
        else if(prefs.contains("verified")) {
            state=UpdateState.Verifying
            val token=generation
            io.execute {
                val record=runCatching{VerifiedUpdate.decode(prefs.getString("verified","").orEmpty())}.getOrNull()
                try {
                    requireUpdate(record!=null,"之前的安装包已失效，请重新检查")
                    if(environment.code()>=record!!.code) {
                        emit(token){markInstalled(record)}
                        return@execute
                    }
                    environment.verify(file(record),record)
                    emit(token){verified=record;state=UpdateState.ReadyToInstall(file(record))}
                } catch(_:Exception) {
                    emit(token){prefs.edit().remove("verified").apply();state=UpdateState.Idle;if(foreground)checkIfDue()}
                }
            }
        }
    }
    fun currentNotes():ReleaseNotes?=runCatching{context.assets.open("release-notes.json").use{ReleaseNotes.decode(String(it.readBytes(),Charsets.UTF_8))}}.getOrNull()
    fun installedVersionName():String=runCatching{environment.version()}.getOrDefault("")
    fun installedVersionCode():Int=runCatching{environment.code()}.getOrDefault(0)
    fun hint():String?=when(val s=state) {
        is UpdateState.Available->"发现新版本 ${s.candidate.notes.version}"
        is UpdateState.Downloading->if(s.percent<0)"更新下载中" else "更新下载中 ${s.percent}%"
        is UpdateState.ReadyToInstall,UpdateState.NeedsInstallPermission->"更新已准备好，继续安装"
        else->null
    }
    fun dismissPrompt(){promptDismissed=true}
    fun onForeground() {
        foreground=true
        verified?.let { if(environment.code()>=it.code){markInstalled(it);return} }
        if(returningFromPermission) {
            returningFromPermission=false
            if(environment.installAllowed())install()
            return
        }
        checkIfDue()
    }
    fun onBackground(){foreground=false;foregroundEpoch++;installAfterDownload=false;if(quietChecking){quietChecking=false;cancel()}}
    private fun checkIfDue() {
        if(state!=UpdateState.Idle && state !is UpdateState.Latest && state !is UpdateState.Installed && state !is UpdateState.Failed)return
        val now=System.currentTimeMillis()
        if(autoCheckEnabled && environment.official() && updateCheckDue(prefs.getLong("last_attempt",0),now))check(quiet=true)
    }
    fun check(quiet:Boolean=false) {
        if(closed || busy())return
        if(!environment.official()){state=UpdateState.Unsupported;return}
        prefs.edit().putLong("last_attempt",System.currentTimeMillis()).apply()
        val token=++generation;val client=httpFactory();http=client;candidate=null;quietChecking=quiet;state=UpdateState.Checking
        io.execute {
            try {
                ensureCurrent(token)
                clearLegacyUpdateAuthorization(context)
                val newest=try {
                    UpdateCatalog.newest(UpdateCatalog.parse(client.json("/releases?per_page=20")))
                } catch(e:Exception) {
                    if(!canUsePublicReleaseFallback(e))throw e
                    UpdateCatalog.fromOfficialLatestTag(client.officialLatestTag())
                }
                    ?: throw UpdateFailure("还没发布过带更新说明的版本，请稍后再试")
                ensureCurrent(token)
                val notes=ReleaseNotes.decode(client.assetText(newest.notesAsset))
                requireUpdate(notes.version==newest.version,"新版说明与发行版本不一致，已停止更新")
                val result=UpdateCatalog.decide(notes,newest.assets,environment.code(),UpdateVerify.deviceAbis())
                // The web fallback has no API asset inventory. Validate the small checksum
                // manifest before offering a potentially large APK download.
                if(result is UpdateCatalog.Result.Available && result.candidate.apk.publicUrl!=null) {
                    val shaAsset=result.candidate.shaFile ?: throw UpdateFailure("官方发布缺少校验文件，已停止更新")
                    UpdateVerify.parseShaFile(client.assetText(shaAsset),result.candidate.apk.name)
                }
                emit(token){quietChecking=false;when(result){
                    is UpdateCatalog.Result.Latest->state=UpdateState.Latest(result.notes)
                    is UpdateCatalog.Result.Available->{candidate=result.candidate;promptDismissed=false;state=UpdateState.Available(result.candidate)}
                }}
            }catch(e:Exception){emit(token){quietChecking=false;state=if(quiet)UpdateState.Idle else UpdateState.Failed(message(e,"检查更新失败，请稍后重试"))}}
        }
    }
    fun download() {
        val target=candidate ?: return
        if(closed || busy() || !environment.official())return
        installAfterDownload=foreground
        val token=++generation;val client=httpFactory();http=client
        state=UpdateState.Downloading(if(target.apk.size>0)0 else -1)
        io.execute {
            directory.mkdirs()
            val partial=File(directory,"download-$token.part")
            try {
                ensureCurrent(token)
                client.downloadApk(target.apk,target.apk.size,partial){percent->emit(token){state=UpdateState.Downloading(percent)}}
                ensureCurrent(token)
                val sha=target.shaFile?.let{UpdateVerify.parseShaFile(client.assetText(it),target.apk.name)}
                    ?:target.apk.apiDigest ?:throw UpdateFailure("发布资料里缺校验值，请稍后再试")
                val size=target.apk.size.takeIf{it>0} ?: partial.length()
                val record=VerifiedUpdate.decode(VerifiedUpdate(target.apk.name,size,sha,target.apk.apiDigest,target.notes.code,target.notes.version).encode())
                environment.verify(partial,record)
                if(token!=generation || closed)return@execute
                val dest=file(record)
                requireUpdate(partial.renameTo(dest),"无法保存安装包，请重新下载")
                // Check again immediately before publishing a durable receipt.
                environment.verify(dest,record)
                emit(token){
                    verified=record;prefs.edit().putString("verified",record.encode()).apply()
                    state=UpdateState.ReadyToInstall(dest)
                    val continueInstall=installAfterDownload && foreground
                    installAfterDownload=false
                    if(continueInstall)install()
                }
            }catch(e:Exception){emit(token){state=UpdateState.Failed(message(e,"下载或校验失败，请重试"))}}
            finally{partial.delete()}
        }
    }
    fun install() {
        val record=verified ?: return
        if(closed || busy())return
        val token=++generation;val installEpoch=foregroundEpoch;state=UpdateState.Verifying
        io.execute {
            try {
                environment.verify(file(record),record)
                emit(token){
                    if(!foreground || installEpoch!=foregroundEpoch)state=UpdateState.ReadyToInstall(file(record))
                    else if(!environment.installAllowed())state=UpdateState.NeedsInstallPermission
                    else try{environment.install(file(record));state=UpdateState.ReadyToInstall(file(record))}
                    catch(_:Exception){state=UpdateState.Failed("未能打开系统安装，请重试安装；原资料不受影响")}
                }
            }catch(e:Exception){emit(token){verified=null;prefs.edit().remove("verified").apply();state=UpdateState.Failed(message(e,"安装包已失效，请重新检查"))}}
        }
    }
    fun openInstallPermission() {
        if(state!=UpdateState.NeedsInstallPermission)return
        try{returningFromPermission=true;environment.permissionSettings()}
        catch(_:Exception){returningFromPermission=false;state=UpdateState.Failed("打不开安装来源设置，请稍后重试安装")}
    }
    fun retry(){if(verified!=null)install() else if(candidate!=null)download() else check()}
    fun cancel() {
        generation++;quietChecking=false;installAfterDownload=false;http.cancel();candidate=null;returningFromPermission=false
        state=verified?.let{UpdateState.ReadyToInstall(file(it))} ?: UpdateState.Idle
        promptDismissed=true
    }
    private fun markInstalled(record:VerifiedUpdate) {
        // An installer intent returning is not success. Only the installed code proves it.
        if(environment.code()<record.code)return
        generation++;verified=null;candidate=null;returningFromPermission=false;installAfterDownload=false
        prefs.edit().remove("verified").apply()
        state=UpdateState.Installed(environment.version())
    }
    fun close(){closed=true;generation++;http.cancel();io.shutdownNow()}
    private fun ensureCurrent(token:Long){if(closed || token!=generation)throw InterruptedException()}
    private fun busy()=state==UpdateState.Checking || state==UpdateState.Verifying || state is UpdateState.Downloading
    private fun file(record:VerifiedUpdate):File {
        // Decode enforces the exact public asset basename; canonical containment is defense in depth.
        VerifiedUpdate.decode(record.encode())
        val f=File(directory,record.name)
        requireUpdate(f.canonicalFile.parentFile==directory.canonicalFile)
        return f
    }
    private fun emit(token:Long,change:()->Unit){main.post{if(!closed && token==generation)change()}}
    private fun message(e:Exception,fallback:String)=(e as? UpdateFailure)?.userMessage ?: fallback
}

/** Do not bypass invalid metadata, untrusted redirects, TLS failures or a missing release. */
internal fun canUsePublicReleaseFallback(e:Exception):Boolean = when(e) {
    is UpdateHttpStatusFailure -> e.statusCode in setOf(401,403,429) || e.statusCode in 500..599
    is java.net.SocketTimeoutException,is java.net.ConnectException,is java.net.UnknownHostException -> true
    else -> false
}
