package dev.h516.shicang

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.mutableStateOf
import java.io.File
import coil3.SingletonImageLoader
import coil3.ImageLoader
import coil3.memory.MemoryCache

class MainActivity: FragmentActivity() {
    val model: AppModel by viewModels()
    private val unlocked=mutableStateOf(false)
    private var verifiedAction: (() -> Unit)?=null
    private var targetDraft: String?=null
    private var cameraUri: Uri?=null
    private val credential=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if(it.resultCode==RESULT_OK) {unlocked.value=true;verifiedAction?.invoke()} else model.message("未解锁，资料保持锁定")
        verifiedAction=null
    }
    private val picker=registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        targetDraft?.let {if(it.isEmpty())model.receiveMedia(uris) else model.importPhotos(it,uris)}
    }
    private val camera=registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if(ok) targetDraft?.let {id -> cameraUri?.let{if(id.isEmpty())model.receiveMedia(listOf(it)) else model.importPhotos(id,listOf(it))}} else model.message("已取消拍照")
    }
    private val backup=registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {uri -> if(uri!=null) model.backup(uri)}
    private val restore=registerForActivityResult(ActivityResultContracts.OpenDocument()) {uri -> if(uri!=null) model.restoreBackup(uri)}
    private val protectionFolder=registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {result ->
        if(result.resultCode==RESULT_OK) result.data?.data?.let {uri ->
            try {
                RecoveryFolder.validateLocalTree(uri)
                contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                model.chooseProtectionFolder(uri)
            } catch(e:Exception) {model.cancelProtectionSelectionRequest();model.message("无法使用所选目录：${e.message}，请保留原文件")}
        } else model.cancelProtectionSelectionRequest()
    }
    private fun chooseProtectionLocation() {
        protectionFolder.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            putExtra(Intent.EXTRA_LOCAL_ONLY,true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        })
    }
    override fun onResume(){super.onResume();GrowthSession.resume(this);model.retryProtection()}
    override fun onPause(){GrowthSession.pause();super.onPause()}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge()
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context).memoryCache {MemoryCache.Builder().maxSizeBytes(32L*1024*1024).build()}.build()
        }
        targetDraft=savedInstanceState?.getString("targetDraft")
        cameraUri=savedInstanceState?.getString("cameraUri")?.let(Uri::parse)
        unlocked.value=!getSharedPreferences("device",MODE_PRIVATE).getBoolean("appLock",false)
        securePreview()
        if(savedInstanceState==null) receiveShare(intent)
        setContent {ShicangApp(model,unlocked.value,{authenticate()},::setLock,::choosePhotos,::takePhoto,
            {model.prepareBackup{backup.launch("留着吧备份-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss",java.util.Locale.ROOT).format(java.util.Date())}.zip")}},
            {restore.launch(arrayOf("application/zip","application/octet-stream"))},::chooseProtectionLocation)}
    }
    override fun onNewIntent(intent:Intent) {super.onNewIntent(intent);setIntent(intent);receiveShare(intent)}
    @Suppress("DEPRECATION")
    private fun receiveShare(intent:Intent) {
        if(intent.action !in listOf(Intent.ACTION_SEND,Intent.ACTION_SEND_MULTIPLE))return
        if(intent.type?.startsWith("text/")==true) {model.receiveShare(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty());return}
        if(intent.type?.startsWith("image/")!=true && intent.type?.startsWith("video/")!=true)return
        val uris=buildList<Uri> {
            if(intent.action==Intent.ACTION_SEND) intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let{add(it)}
            else addAll(intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty())
            intent.clipData?.let{clip->repeat(clip.itemCount){clip.getItemAt(it).uri?.let{uri->add(uri)}}}
        }.distinct().filter{it.scheme=="content"}.take(100)
        model.receiveMedia(uris)
    }
    override fun onSaveInstanceState(out: Bundle) {out.putString("targetDraft",targetDraft);out.putString("cameraUri",cameraUri?.toString());super.onSaveInstanceState(out)}
    override fun onStop() {super.onStop();if(getSharedPreferences("device",MODE_PRIVATE).getBoolean("appLock",false)) unlocked.value=false}
    private fun securePreview() {if(getSharedPreferences("device",MODE_PRIVATE).getBoolean("appLock",false)) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)}
    private fun setLock(enable: Boolean) {authenticate {model.store.setLock(enable);unlocked.value=true;securePreview();model.message(if(enable) "应用锁已开启" else "应用锁已关闭")}}
    private fun authenticate(action: ()->Unit = {}) {
        verifiedAction=action
        if(Build.VERSION.SDK_INT>=30) {
            val allowed=BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            if(BiometricManager.from(this).canAuthenticate(allowed)!=BiometricManager.BIOMETRIC_SUCCESS) {model.message("请先在手机系统设置中启用锁屏密码或指纹");return}
            val prompt=BiometricPrompt(this,ContextCompat.getMainExecutor(this),object:BiometricPrompt.AuthenticationCallback(){
                override fun onAuthenticationSucceeded(result:BiometricPrompt.AuthenticationResult){unlocked.value=true;verifiedAction?.invoke();verifiedAction=null}
                override fun onAuthenticationError(code:Int,text:CharSequence){model.message("验证未完成：$text");verifiedAction=null}
            })
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("解锁留着吧").setSubtitle("使用手机系统身份验证").setAllowedAuthenticators(allowed).build())
        } else {
            val keyguard=getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val intent=keyguard.createConfirmDeviceCredentialIntent("解锁留着吧","使用手机锁屏密码验证")
            if(intent==null) model.message("请先在手机系统设置中启用锁屏密码") else credential.launch(intent)
        }
    }
    private fun choosePhotos(id:String) {
        val launch={targetDraft=id;picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))}
        if(id.isEmpty())launch() else model.preparePhotos(id,launch)
    }
    private fun takePhoto(id:String) {
        val launch={
        targetDraft=id
        val dir=File(cacheDir,"camera").apply{mkdirs()};val f=File(dir,"${uid()}.jpg")
        cameraUri=FileProvider.getUriForFile(this,"$packageName.files",f)
        try {camera.launch(cameraUri!!)} catch(e:Exception){model.message("无法打开系统相机：${e.message}")}
        }
        if(id.isEmpty())launch() else model.preparePhotos(id,launch)
    }
}
