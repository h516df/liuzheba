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
        targetDraft?.let {model.importPhotos(it,uris)}
    }
    private val camera=registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if(ok) targetDraft?.let {id -> cameraUri?.let{model.importPhotos(id,listOf(it))}} else model.message("已取消拍照")
    }
    private val backup=registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) {uri -> if(uri!=null) model.backup(uri)}
    private val restore=registerForActivityResult(ActivityResultContracts.OpenDocument()) {uri -> if(uri!=null) model.restoreBackup(uri)}
    override fun onResume(){super.onResume();GrowthSession.resume(this)}
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
            {backup.launch("留册备份-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss",java.util.Locale.ROOT).format(java.util.Date())}.zip")},
            {restore.launch(arrayOf("application/zip","application/octet-stream"))})}
    }
    override fun onNewIntent(intent:Intent) {super.onNewIntent(intent);setIntent(intent);receiveShare(intent)}
    private fun receiveShare(intent:Intent) {if(intent.action==Intent.ACTION_SEND && intent.type?.startsWith("text/")==true) model.receiveShare(intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty())}
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
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("解锁留册").setSubtitle("使用手机系统身份验证").setAllowedAuthenticators(allowed).build())
        } else {
            val keyguard=getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val intent=keyguard.createConfirmDeviceCredentialIntent("解锁留册","使用手机锁屏密码验证")
            if(intent==null) model.message("请先在手机系统设置中启用锁屏密码") else credential.launch(intent)
        }
    }
    private fun choosePhotos(id:String) {model.preparePhotos(id){targetDraft=id;picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))}}
    private fun takePhoto(id:String) {
        model.preparePhotos(id) {
        targetDraft=id
        val dir=File(cacheDir,"camera").apply{mkdirs()};val f=File(dir,"${uid()}.jpg")
        cameraUri=FileProvider.getUriForFile(this,"$packageName.files",f)
        try {camera.launch(cameraUri!!)} catch(e:Exception){model.message("无法打开系统相机：${e.message}")}
        }
    }
}
