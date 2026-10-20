package dev.h516.shicang

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.content.ContentValues
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

data class MediaExportRequest(val archive:Archive,val photos:List<Photo>)
object MediaExport {
    fun extension(mime:String)=android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        ?: if(mime.startsWith("video/")) "mp4" else "jpg"
    private fun name(a:Archive,p:Photo)=a.name.replace(Regex("[^\\p{L}\\p{N} _-]"),"_").take(50).ifBlank{"留着吧"}+"-${p.id.take(8)}.${extension(p.mime)}"
    suspend fun save(context:Context,request:MediaExportRequest,file:(Photo)->File,progress:(String)->Unit):Int=withContext(Dispatchers.IO) {
        val operation=coroutineContext
        val resolver=context.contentResolver
        val history=context.getSharedPreferences("media-exports",Context.MODE_PRIVATE)
        var count=0
        for((index,p) in request.photos.withIndex()) {
            operation.ensureActive()
            progress("保存 ${index+1} / ${request.photos.size}")
            val source=file(p)
            require(source.isFile && source.length()==p.bytes && sha(source){operation.ensureActive()}==p.sha256) {"原件读取或校验失败"}
            val remembered=history.getString(p.sha256,null)
            if(remembered!=null && runCatching{resolver.openFileDescriptor(Uri.parse(remembered),"r")?.use{it.statSize==p.bytes}==true}.getOrDefault(false)) {count++;continue}
            if(Build.VERSION.SDK_INT>=29) {
                val collection=if(p.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val values=ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME,name(request.archive,p));put(MediaStore.MediaColumns.MIME_TYPE,p.mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH,if(p.isVideo) "Movies/留册" else "Pictures/留册")
                    put(MediaStore.MediaColumns.IS_PENDING,1)
                }
                val uri=resolver.insert(collection,values) ?: error("无法创建相册文件")
                try {
                    resolver.openOutputStream(uri,"w").use {out->requireNotNull(out);source.inputStream().use{input->LargeMediaIo.copy(input,out,p.bytes,p.bytes,check={operation.ensureActive()})}}
                    val actual=resolver.openInputStream(uri).use{requireNotNull(it);hashStream(it){operation.ensureActive()}}
                    require(actual==p.sha256) {"相册文件校验失败"}
                    values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0);check(resolver.update(uri,values,null,null)==1)
                    history.edit().putString(p.sha256,uri.toString()).apply()
                } catch(e:Exception) {resolver.delete(uri,null,null);throw e}
            } else {
                @Suppress("DEPRECATION") val folder=File(Environment.getExternalStoragePublicDirectory(if(p.isVideo)Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES),"留册").apply{mkdirs()}
                val dest=File(folder,name(request.archive,p));val temp=File(folder,".${p.id}.pending")
                try {
                    source.inputStream().use{input->temp.outputStream().use{out->LargeMediaIo.copy(input,out,p.bytes,p.bytes,{folder.usableSpace},{operation.ensureActive()})}}
                    require(sha(temp){operation.ensureActive()}==p.sha256);check(temp.renameTo(dest)) {"相册保存失败"}
                    MediaScannerConnection.scanFile(context,arrayOf(dest.path),arrayOf(p.mime),null)
                } finally {temp.delete()}
            }
            count++
        }
        count
    }
    fun share(context:Context,request:MediaExportRequest,file:(Photo)->File) {
        require(request.photos.isNotEmpty())
        val uris=ArrayList(request.photos.map{FileProvider.getUriForFile(context,"${context.packageName}.files",file(it))})
        val mime=request.photos.map{it.mime}.distinct().singleOrNull() ?: if(request.photos.all{it.isVideo})"video/*" else if(request.photos.none{it.isVideo})"image/*" else "*/*"
        val intent=Intent(if(uris.size==1)Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).apply {
            type=mime
            if(uris.size==1) putExtra(Intent.EXTRA_STREAM,uris.first()) else putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris)
            clipData=ClipData.newUri(context.contentResolver,"媒体",uris.first()).apply{uris.drop(1).forEach{addItem(ClipData.Item(it))}}
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent,"分享原件").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
