package dev.h516.update

import android.content.Context
import android.os.Looper
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
@org.robolectric.annotation.LooperMode(org.robolectric.annotation.LooperMode.Mode.PAUSED)
class UpdaterSessionTest {
    private lateinit var context:Context
    private val sessions=mutableListOf<Updater>()
    private val requests=AtomicInteger()
    private val bytes="synthetic-apk".toByteArray()
    private val sha=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    private val record get()=VerifiedUpdate("shicang-v0.14-universal.apk",bytes.size.toLong(),sha,sha,15,"0.14")
    private class Environment:UpdateEnvironment {
        var beforeVerify:(()->Unit)?=null;var installedCode=14;var official=true;var allowed=false;var installs=0;var settings=0;var verifications=0;var rejected=false
        override fun official()=official
        override fun code()=installedCode
        override fun version()="0.13"
        override fun verify(file:File,record:VerifiedUpdate){beforeVerify?.invoke();verifications++;requireUpdate(!rejected,"测试签名不匹配");requireUpdate(record.code>code());UpdateVerify.verifyHash(file,record.size,record.sha,record.digest)}
        override fun installAllowed()=allowed
        override fun permissionSettings(){settings++}
        override fun install(file:File){installs++}
    }
    @Before fun setup(){context=RuntimeEnvironment.getApplication();context.getSharedPreferences("public_update_session",0).edit().clear().commit();File(context.cacheDir,"updates").deleteRecursively()}
    @After fun cleanup(){sessions.forEach{it.close()}}
    private fun seed(){File(context.cacheDir,"updates").mkdirs();File(context.cacheDir,"updates/${record.name}").writeBytes(bytes);context.getSharedPreferences("public_update_session",0).edit().putString("verified",record.encode()).commit()}
    private fun session(env:Environment)=Updater(context,env){UpdateHttp{url->requests.incrementAndGet();object:HttpURLConnection(URL(url)){
        override fun connect(){};override fun disconnect(){};override fun usingProxy()=false
        override fun getResponseCode()=200
        override fun getInputStream()=ByteArrayInputStream("[]".toByteArray())
    }}}.also{sessions+=it}
    private fun until(condition:()->Boolean){val deadline=System.nanoTime()+5_000_000_000L;while(!condition() && System.nanoTime()<deadline){shadowOf(Looper.getMainLooper()).idle();Thread.yield()};shadowOf(Looper.getMainLooper()).idle();assertTrue(condition())}
    @Test fun explicitDisabledReminderIsPreservedAndForegroundChecksAreThrottled(){
        context.getSharedPreferences("public_update_session",0).edit().putBoolean("auto_check",false).commit()
        val u=session(Environment());u.onForeground();assertEquals(0,requests.get())
        u.onBackground();u.changeAutoCheckEnabled(true);assertEquals(0,requests.get())
        u.onForeground();until{requests.get()==1 && u.state==UpdateState.Idle}
        u.onForeground();assertEquals(1,requests.get());u.check();until{requests.get()==2 && u.state is UpdateState.Failed}
    }
    @Test fun unsetPreferenceChecksByDefaultOnlyInForeground(){
        val u=session(Environment());assertTrue(u.autoCheckEnabled);assertEquals(0,requests.get())
        u.onForeground();until{requests.get()==1 && u.state==UpdateState.Idle}
        u.onForeground();assertEquals(1,requests.get())
    }
    @Test fun installerReturnIsNotSuccessUntilInstalledCodeChanges(){
        seed();val env=Environment().apply{allowed=true};val u=session(env)
        until{u.state is UpdateState.ReadyToInstall};u.onForeground();u.install()
        until{env.installs==1 && u.state is UpdateState.ReadyToInstall}
        u.onBackground();u.onForeground();assertTrue(u.state is UpdateState.ReadyToInstall)
        env.installedCode=15;u.onForeground();assertTrue(u.state is UpdateState.Installed)
        assertFalse(context.getSharedPreferences("public_update_session",0).contains("verified"))
    }
    @Test fun upgradedProcessRecognizesActualInstalledCodeWithoutLaunchingInstaller(){
        seed();val env=Environment().apply{installedCode=15};val u=session(env)
        until{u.state is UpdateState.Installed};u.onForeground();assertEquals(0,env.installs)
    }
    @Test fun installedResultDoesNotDisableFutureAutomaticChecks(){
        seed();val env=Environment().apply{installedCode=15};val u=session(env)
        until{u.state is UpdateState.Installed}
        context.getSharedPreferences("public_update_session",0).edit().putLong("last_attempt",System.currentTimeMillis()-86_400_001L).commit()
        u.onForeground();until{requests.get()==1 && u.state==UpdateState.Idle}
    }
    private fun downloadableSession(env:Environment):Updater {
        val notes="""{"versionName":"0.14","versionCode":15,"改进":["合成测试"]}"""
        val releases="""[{"tag_name":"v0.14","assets":[{"id":1,"size":200,"name":"shicang-v0.14-release-notes.json"},{"id":2,"size":${bytes.size},"name":"${record.name}","digest":"sha256:$sha"}]}]"""
        return Updater(context,env){UpdateHttp{url->object:HttpURLConnection(URL(url)){
            override fun connect(){};override fun disconnect(){};override fun usingProxy()=false
            override fun getResponseCode()=200
            override fun getInputStream()=ByteArrayInputStream(when{url.endsWith("/assets/2")->bytes;url.endsWith("/assets/1")->notes.toByteArray();else->releases.toByteArray()})
        }}}.also{sessions+=it}
    }
    @Test fun foregroundUpdateContinuesToSystemConfirmationWithoutSecondTap(){
        val env=Environment().apply{allowed=true};val u=downloadableSession(env)
        u.onForeground();until{u.state is UpdateState.Available};u.download()
        until{env.installs==1 && u.state is UpdateState.ReadyToInstall};assertTrue(env.verifications>=3)
    }
    @Test fun leavingForegroundRevokesAutomaticInstallEvenIfUserReturnsBeforeCompletion(){
        val env=Environment().apply{allowed=true};val u=downloadableSession(env)
        u.onForeground();until{u.state is UpdateState.Available};u.download();u.onBackground();u.onForeground()
        until{u.state is UpdateState.ReadyToInstall};assertEquals(0,env.installs)
        u.install();until{env.installs==1}
    }
    @Test fun leavingAndReturningDuringInstallVerificationRevokesThatAttempt(){
        seed();val env=Environment().apply{allowed=true};val u=session(env)
        until{u.state is UpdateState.ReadyToInstall};u.onForeground()
        val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        env.beforeVerify={entered.countDown();check(release.await(3,java.util.concurrent.TimeUnit.SECONDS))}
        u.install();assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS))
        u.onBackground();u.onForeground();release.countDown()
        until{u.state is UpdateState.ReadyToInstall};assertEquals(0,env.installs)
        env.beforeVerify=null;u.install();until{env.installs==1}
    }
    @Test fun restoredDownloadDoesNotAutomaticallyOpenSystemInstaller(){
        seed();val env=Environment().apply{allowed=true};val u=session(env)
        until{u.state is UpdateState.ReadyToInstall};u.onForeground();assertEquals(0,env.installs)
    }

    @Test fun nonOfficialBuildNeverRequestsOrInstalls(){
        val env=Environment().apply{official=false};val u=session(env);u.changeAutoCheckEnabled(true);u.onForeground();u.check();u.install()
        assertTrue(u.state is UpdateState.Unsupported);assertEquals(0,requests.get());assertEquals(0,env.installs)
    }
    @Test fun verifiedReceiptRestoresAndPermissionReturnReverifiesBeforeInstalling(){
        seed();val env=Environment();val u=session(env);until{u.state is UpdateState.ReadyToInstall}
        u.onForeground();u.install();until{u.state==UpdateState.NeedsInstallPermission};assertEquals(0,env.installs)
        u.openInstallPermission();assertEquals(1,env.settings);u.onBackground();env.allowed=true;u.onForeground()
        until{env.installs==1 && u.state is UpdateState.ReadyToInstall};assertTrue(env.verifications>=3);assertEquals(0,requests.get())
    }
    @Test fun deniedPermissionKeepsVerifiedDownloadWithoutNewNetwork(){
        seed();val env=Environment();val u=session(env);until{u.state is UpdateState.ReadyToInstall}
        u.onForeground();u.install();until{u.state==UpdateState.NeedsInstallPermission};u.openInstallPermission();u.onForeground()
        assertEquals(UpdateState.NeedsInstallPermission,u.state);assertEquals(0,env.installs);assertEquals(0,requests.get())
    }
    @Test fun corruptRestoredFileIsNotOffered(){
        seed();File(context.cacheDir,"updates/${record.name}").writeText("broken")
        val u=session(Environment());until{u.state==UpdateState.Idle};assertFalse(context.getSharedPreferences("public_update_session",0).contains("verified"))
    }
    @Test fun mutationAfterDownloadIsDetectedAgainAtInstall(){
        seed();val env=Environment().apply{allowed=true};val u=session(env);until{u.state is UpdateState.ReadyToInstall}
        File(context.cacheDir,"updates/${record.name}").writeText("changed")
        u.install();until{u.state is UpdateState.Failed};assertEquals(0,env.installs)
    }
    @Test fun signatureRejectionCannotReachSystemInstaller(){
        seed();val env=Environment().apply{allowed=true};val u=session(env);until{u.state is UpdateState.ReadyToInstall};env.rejected=true
        u.install();until{u.state is UpdateState.Failed};assertEquals(0,env.installs)
    }
    @Test fun closedSessionCannotPublishQueuedRestore(){
        seed();val u=session(Environment());u.close();shadowOf(Looper.getMainLooper()).idle();assertEquals(UpdateState.Verifying,u.state)
    }
    @Test fun leavingForegroundDuringVerificationNeverLaunchesInstaller(){
        seed();val env=Environment().apply{allowed=true};val u=session(env);until{u.state is UpdateState.ReadyToInstall}
        u.onForeground();u.install();u.onBackground();until{u.state is UpdateState.ReadyToInstall};assertEquals(0,env.installs)
    }
    @Test fun cancelThenRetryCannotBorrowTheNewHttpClient(){
        val entered=java.util.concurrent.CountDownLatch(1)
        val release=java.util.concurrent.CountDownLatch(1)
        val clientIds=AtomicInteger()
        val connections=java.util.concurrent.CopyOnWriteArrayList<Pair<Int,String>>()
        val u=Updater(context,Environment()){
            val id=clientIds.incrementAndGet()
            UpdateHttp{url->connections+=id to url;object:HttpURLConnection(URL(url)){
                override fun connect(){};override fun disconnect(){};override fun usingProxy()=false
                override fun getResponseCode()=200
                override fun getInputStream():java.io.InputStream {
                    if(id==2){entered.countDown();check(release.await(3,java.util.concurrent.TimeUnit.SECONDS))}
                    return ByteArrayInputStream("[]".toByteArray())
                }
            }}
        }.also{sessions+=it}
        u.check();assertTrue(entered.await(3,java.util.concurrent.TimeUnit.SECONDS))
        u.cancel();u.check();release.countDown();until{u.state is UpdateState.Failed}
        assertEquals(listOf(2,3),connections.map{it.first});assertTrue(connections.all{it.second.endsWith("/releases?per_page=20")})
    }

    private fun fallbackSession(env:Environment,requests:MutableList<String>,apiCode:Int=429,
                                latestLocation:String="https://github.com/h516df/liuzheba/releases/tag/v0.14",
                                shaText:String="$sha  ${record.name}",shaAvailable:Boolean=true,webOffline:Boolean=false):Updater {
        val notes="""{"versionName":"0.14","versionCode":15,"改进":["合成回归"]}"""
        val official="${UpdateConfig.PUBLIC_RELEASES}/download/v0.14/"
        return Updater(context,env){UpdateHttp { url ->
            requests+=url
            val resource=url.substringAfterLast('/')
            val (code,body,location)=when {
                url.endsWith("/releases?per_page=20") -> Triple(apiCode,ByteArray(0),null)
                url=="${UpdateConfig.PUBLIC_RELEASES}/latest" -> Triple(302,ByteArray(0),latestLocation)
                url.startsWith(official) -> Triple(302,ByteArray(0),"https://release-assets.githubusercontent.com/synthetic/$resource")
                url.contains("/synthetic/shicang-v0.14-release-notes.json") -> Triple(200,notes.toByteArray(),null)
                url.contains("/synthetic/shicang-v0.14-SHA256.txt") -> if(shaAvailable)Triple(200,shaText.toByteArray(),null) else Triple(404,ByteArray(0),null)
                url.contains("/synthetic/${record.name}") -> Triple(200,bytes,null)
                else -> Triple(404,ByteArray(0),null)
            }
            object:HttpURLConnection(URL(url)) {
                override fun connect(){};override fun disconnect(){};override fun usingProxy()=false
                override fun getResponseCode():Int {
                    if(apiCode==-1 && url.endsWith("/releases?per_page=20"))throw java.net.SocketTimeoutException("synthetic offline API")
                    if(webOffline && url=="${UpdateConfig.PUBLIC_RELEASES}/latest")throw java.net.SocketTimeoutException("synthetic offline web")
                    return code
                }
                override fun getHeaderField(name:String)=if(name=="Location")location else null
                override fun getInputStream()=ByteArrayInputStream(body)
            }
        }}.also{sessions+=it}
    }

    @Test fun apiRateLimitUsesOfficialReleaseAndStillVerifiesBeforeInstall() {
        val calls=java.util.concurrent.CopyOnWriteArrayList<String>()
        val env=Environment().apply{allowed=true}
        val u=fallbackSession(env,calls)
        u.onForeground();until{u.state is UpdateState.Available}
        val candidate=(u.state as UpdateState.Available).candidate
        assertEquals(0L,candidate.apk.id)
        assertNull(candidate.apk.apiDigest)
        u.download();until{env.installs==1 && u.state is UpdateState.ReadyToInstall}
        assertTrue(env.verifications>=3)
        assertTrue(calls.any{it=="${UpdateConfig.PUBLIC_RELEASES}/latest"})
        assertTrue(calls.any{it=="${UpdateConfig.PUBLIC_RELEASES}/download/v0.14/${record.name}"})
        assertTrue(calls.all{it.startsWith(UpdateConfig.API) || it.startsWith(UpdateConfig.PUBLIC_RELEASES) || it.startsWith("https://release-assets.githubusercontent.com/synthetic/")})
    }

    @Test fun apiForbiddenServerErrorAndTimeoutUseTheSameOfficialFallback() {
        listOf(403,503,-1).forEach { code ->
            val calls=java.util.concurrent.CopyOnWriteArrayList<String>()
            val u=fallbackSession(Environment(),calls,apiCode=code)
            u.check();until{u.state is UpdateState.Available}
            assertEquals(1,calls.count{it=="${UpdateConfig.PUBLIC_RELEASES}/latest"})
            assertTrue(calls.any{it.endsWith("shicang-v0.14-SHA256.txt")})
            u.close()
        }
    }

    @Test fun missingChecksumStopsBeforeOfferingAnApk() {
        val calls=java.util.concurrent.CopyOnWriteArrayList<String>()
        val u=fallbackSession(Environment(),calls,shaAvailable=false)
        u.check();until{u.state is UpdateState.Failed}
        assertTrue(calls.any{it.endsWith("shicang-v0.14-SHA256.txt")})
        assertTrue(calls.none{it.endsWith(record.name)})
    }

    @Test fun fallbackChecksumMismatchNeverReachesInstaller() {
        val calls=java.util.concurrent.CopyOnWriteArrayList<String>()
        val env=Environment().apply{allowed=true}
        val u=fallbackSession(env,calls,shaText="${"0".repeat(64)}  ${record.name}")
        u.check();until{u.state is UpdateState.Available};u.download()
        until{u.state is UpdateState.Failed}
        assertEquals(0,env.installs)
        assertFalse(context.getSharedPreferences("public_update_session",0).contains("verified"))
    }

    @Test fun foreignLatestRedirectStopsWithoutRequestingAssets() {
        val calls=java.util.concurrent.CopyOnWriteArrayList<String>()
        val u=fallbackSession(Environment(),calls,latestLocation="https://attacker.test/h516df/liuzheba/releases/tag/v0.14")
        u.check();until{u.state is UpdateState.Failed}
        assertEquals(2,calls.size)
        assertTrue(calls.none{it.contains("/download/")})
    }

    @Test fun missingReleaseApiResponseDoesNotDowngradeToWebFallback() {
        val calls=java.util.concurrent.CopyOnWriteArrayList<String>()
        val u=fallbackSession(Environment(),calls,apiCode=404)
        u.check();until{u.state is UpdateState.Failed}
        assertEquals(1,calls.size)
    }

    @Test fun fullOfflineFailureKeepsCurrentAppAndNeverInstalls() {
        val calls=java.util.concurrent.CopyOnWriteArrayList<String>()
        val env=Environment().apply{allowed=true}
        val u=fallbackSession(env,calls,apiCode=-1,webOffline=true)
        u.check();until{u.state is UpdateState.Failed}
        assertEquals(2,calls.size)
        assertEquals(0,env.installs)
        assertFalse(context.getSharedPreferences("public_update_session",0).contains("verified"))
    }

}
