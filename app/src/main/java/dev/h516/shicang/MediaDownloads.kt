package dev.h516.shicang

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URI

/** Extraction is local and lazy: the Python runtime never starts just for browsing. */
data class RemoteMedia(val url:String,val kind:String,val ext:String,val bytes:Long=0,val headers:Map<String,String> = emptyMap())
data class MediaPlan(val title:String,val source:String,val items:List<RemoteMedia>)
object MediaLinks {
    private val images=setOf("jpg","jpeg","png","gif","webp","heic","avif")
    private val videos=setOf("mp4","mov","m4v","webm","mkv")
    fun direct(url:String):RemoteMedia? {
        val ext=runCatching{URI(url).path.substringAfterLast('.').lowercase()}.getOrDefault("")
        return when(ext){in images->RemoteMedia(url,"image",ext);in videos->RemoteMedia(url,"video",ext);else->null}
    }
    fun validUrl(input:String):String {
        val url=requireNotNull(Links.normalize(input)) {"请填写图片、视频或作品分享链接"}
        require(URI(url).scheme=="https") {"请使用 HTTPS 链接"}
        return url
    }
    fun read(json:String):MediaPlan {
        val value=JSONObject(json)
        val items=value.getJSONArray("media").objects().take(100).map { item ->
            val url=validUrl(item.getString("url"))
            val headers=item.optJSONObject("headers")
            RemoteMedia(url,item.getString("kind"),item.optString("ext","mp4"),item.optLong("bytes"),
                headers?.keys()?.asSequence()?.filter{it.lowercase() in setOf("user-agent","referer","origin")}?.associateWith{headers.getString(it)} ?: emptyMap())
        }
        require(items.isNotEmpty() && items.all{it.kind in listOf("image","video")}) {"没有可下载的图片或视频"}
        require(items.all{it.bytes<=if(it.kind=="image")Codec.MAX_PHOTO_BYTES else Codec.MAX_MEDIA_BYTES}) {"媒体超过单项大小限制"}
        return MediaPlan(value.optString("title","链接收录").take(160),value.optString("source"),items)
    }
    suspend fun resolve(context:Context,input:String):MediaPlan=withContext(Dispatchers.IO) {
        val url=validUrl(input)
        direct(url)?.let{return@withContext MediaPlan(Uri.parse(url).lastPathSegment?.take(100) ?: "链接收录",url,listOf(it))}
        synchronized(MediaLinks) {if(!Python.isStarted()) Python.start(AndroidPlatform(context.applicationContext))}
        val cookies=withContext(Dispatchers.Main) {
            val cookieUrl=when(SharedLinks.platform(url)){"抖音"->"https://www.douyin.com/";"小红书"->"https://www.xiaohongshu.com/";else->url}
            CookieManager.getInstance().getCookie(cookieUrl).orEmpty()
        }
        try {read(Python.getInstance().getModule("liuce_media").callAttr("resolve",url,cookies).toString())}
        catch(e:Exception) {throw IllegalStateException("暂时无法解析。可先打开网页完成验证后重试，或从其他 App 分享图片、视频到留册。",e)}
    }
}

data class MediaDownload(val id:String,val title:String,val source:String,val kind:String,val ext:String,val managerId:Long,
    val generation:String,val state:String="等待下载",val percent:Int=0,val bytes:Long=0,val archiveId:String="")
/** DownloadManager owns background transport and recovery across process death/reboots. */
class MediaDownloads(private val context:Context) {
    private val prefs=context.getSharedPreferences("media-downloads",Context.MODE_PRIVATE)
    private val device=context.getSharedPreferences("device",Context.MODE_PRIVATE)
    private val manager=context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val folder=context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: error("下载目录暂不可用")
    private fun generation()=device.getString("generation","initial").orEmpty()
    private fun read()=runCatching {JSONArray(prefs.getString("jobs","[]")).objects().map {o ->
        MediaDownload(o.getString("id"),o.getString("title"),o.getString("source"),o.getString("kind"),o.getString("ext"),o.getLong("managerId"),o.getString("generation"),archiveId=o.optString("archiveId"))
    }}.getOrDefault(emptyList())
    private fun write(jobs:List<MediaDownload>) {check(prefs.edit().putString("jobs",JSONArray(jobs.map {j ->JSONObject()
        .put("id",j.id).put("title",j.title).put("source",j.source).put("kind",j.kind).put("ext",j.ext)
        .put("managerId",j.managerId).put("generation",j.generation).put("archiveId",j.archiveId)}).toString()).commit())}
    fun file(job:MediaDownload)=File(folder,"${job.id}.download")
    @Synchronized fun enqueue(plan:MediaPlan,items:List<RemoteMedia>) {
        val jobs=read().toMutableList()
        require(items.isNotEmpty()) {"请至少选择一项媒体"}
        require(jobs.count{it.generation==generation()}+items.size<=200) {"下载记录已达上限，请先清理已完成记录"}
        val newJobs=mutableListOf<MediaDownload>()
        try {
            items.forEach {item ->
                val id=uid()
                val request=DownloadManager.Request(Uri.parse(MediaLinks.validUrl(item.url)))
                    .setTitle("留册媒体下载").setDescription(if(item.kind=="video") "视频" else "图片")
                    .setAllowedOverRoaming(false).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(context,Environment.DIRECTORY_DOWNLOADS,"$id.download")
                item.headers.forEach{(key,value)->request.addRequestHeader(key,value)}
                val cookie=CookieManager.getInstance().getCookie(item.url)
                if(!cookie.isNullOrBlank()) request.addRequestHeader("Cookie",cookie)
                val managerId=manager.enqueue(request)
                newJobs+=MediaDownload(id,plan.title,plan.source,item.kind,item.ext.filter{it.isLetterOrDigit()}.take(8),managerId,generation())
            }
            write(jobs+newJobs)
        } catch(e:Exception) {newJobs.forEach{manager.remove(it.managerId)};throw e}
    }
    @Synchronized fun list():List<MediaDownload> = read().filter{it.generation==generation()}.map {job ->
        if(job.archiveId.isNotBlank()) return@map job.copy(state="已收录",percent=100)
        manager.query(DownloadManager.Query().setFilterById(job.managerId)).use {c ->
            if(!c.moveToFirst()) return@map job.copy(state="下载已移除")
            val status=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val bytes=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val percent=if(total>0)((bytes*100/total).toInt().coerceIn(0,100)) else 0
            val state=when(status) {
                DownloadManager.STATUS_SUCCESSFUL->if(file(job).isFile) "待收录" else "文件已移除"
                DownloadManager.STATUS_FAILED->"下载失败"
                DownloadManager.STATUS_RUNNING->"下载中"
                DownloadManager.STATUS_PAUSED->"等待网络"
                else->"等待下载"
            }
            job.copy(state=state,percent=percent,bytes=bytes)
        }
    }
    @Synchronized fun imported(id:String,archive:String) {val jobs=read();write(jobs.map{if(it.id==id)it.copy(archiveId=archive) else it});jobs.firstOrNull{it.id==id}?.let{manager.remove(it.managerId)}}
    @Synchronized fun remove(id:String) {val jobs=read();jobs.firstOrNull{it.id==id}?.let{manager.remove(it.managerId);file(it).delete()};write(jobs.filter{it.id!=id})}
}
