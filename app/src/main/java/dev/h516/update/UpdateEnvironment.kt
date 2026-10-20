package dev.h516.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dev.h516.shicang.BuildConfig
import java.io.File

internal interface UpdateEnvironment {
    fun official():Boolean
    fun code():Int
    fun version():String
    fun verify(file:File,record:VerifiedUpdate)
    fun installAllowed():Boolean
    fun permissionSettings()
    fun install(file:File)
}
internal class AndroidUpdateEnvironment(private val context:Context):UpdateEnvironment {
    override fun official()=context.packageName==UpdateConfig.PACKAGE && !BuildConfig.DEBUG &&
        runCatching{UpdateVerify.installedCertificateHashes(context)==setOf(UpdateConfig.CERTIFICATE_SHA256)}.getOrDefault(false)
    override fun code()=UpdateVerify.installedVersionCode(context)
    override fun version()=context.packageManager.getPackageInfo(context.packageName,0).versionName.orEmpty()
    override fun verify(file:File,record:VerifiedUpdate) {
        requireUpdate(official(),"仅正式版留着吧可以使用公开更新")
        UpdateVerify.verifyHash(file,record.size,record.sha,record.digest)
        UpdateVerify.verifyArchive(context,file,record.code,record.version)
    }
    override fun installAllowed()=Build.VERSION.SDK_INT<26 || context.packageManager.canRequestPackageInstalls()
    override fun permissionSettings() {
        if(Build.VERSION.SDK_INT>=26)context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    override fun install(file:File) {
        val uri=FileProvider.getUriForFile(context,"${context.packageName}.updates",file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
