package dev.h516.shicang

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.CanvasOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class VideoEditInfo(val width:Int,val height:Int,val durationMs:Long)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal object VideoEditEngine {
    suspend fun inspect(source:File,expected:Photo):VideoEditInfo=withContext(Dispatchers.IO) {
        require(expected.isVideo && source.isFile && source.length()==expected.bytes && sha(source)==expected.sha256){"原视频校验失败，未修改原片"}
        ensureActive()
        val reader=MediaMetadataRetriever()
        try {
            reader.setDataSource(source.path)
            val width=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val duration=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            require(width>0 && height>0 && width<=4096 && height<=4096 && duration>=250){"此视频尺寸或时长暂不支持编辑，原片仍可播放"}
            if(rotation==90 || rotation==270) VideoEditInfo(height,width,duration) else VideoEditInfo(width,height,duration)
        } finally {reader.release()}
    }
    fun item(source:File,plan:VideoEditPlan):MediaItem=MediaItem.Builder().setUri(Uri.fromFile(source))
        .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(plan.startMs).setEndPositionMs(plan.endMs).build()).build()
    /** Fresh effect objects per renderer: previews and exports never share GPU/bitmap resources. */
    fun effects(plan:VideoEditPlan):List<Effect> = buildList {
        plan.mask?.let { mask ->
            add(OverlayEffect(listOf(VideoMaskOverlay(mask))))
        }
        val c=plan.crop.cropCoordinates();add(Crop(c[0],c[1],c[2],c[3]))
    }
    suspend fun export(context:Context,source:File,photo:Photo,plan:VideoEditPlan,info:VideoEditInfo,progress:(Int?)->Unit):File = withContext(Dispatchers.Main.immediate) {
        plan.validate(info.durationMs)
        val folder=File(context.cacheDir,"video-edits").apply{mkdirs()}
        // Process death may skip finally. Only our expired UUID-named scratch exports are disposable.
        val expiry=System.currentTimeMillis()-24L*60*60*1000
        folder.listFiles().orEmpty().filter{it.isFile && it.extension=="mp4" && Codec.validId(it.nameWithoutExtension) && it.lastModified()<expiry}.forEach{it.delete()}
        require(folder.usableSpace>=plan.requiredBytes(photo.bytes)){"空间不足，请先腾出空间；原视频未改动"}
        val output=File(folder,"${uid()}.mp4")
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                val handler=Handler(Looper.getMainLooper())
                var transformer:Transformer?=null
                var closed=false
                val holder=ProgressHolder()
                val poll=object:Runnable {
                    override fun run() {
                        if(closed || !continuation.isActive)return
                        if(output.length()>Codec.MAX_MEDIA_BYTES || folder.usableSpace<32L*1024*1024) {
                            closed=true;transformer?.cancel();continuation.resumeWithException(IllegalStateException("导出超过 1GB 或剩余空间不足；原片未改动"));return
                        }
                        progress(if(transformer?.getProgress(holder)==Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else null)
                        handler.postDelayed(this,300)
                    }
                }
                val engine=Transformer.Builder(context.applicationContext).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(object:Transformer.Listener {
                        override fun onCompleted(composition:Composition,exportResult:ExportResult) {
                            closed=true;handler.removeCallbacks(poll)
                            if(continuation.isActive)continuation.resume(Unit)
                        }
                        override fun onError(composition:Composition,exportResult:ExportResult,exportException:ExportException) {
                            closed=true;handler.removeCallbacks(poll)
                            if(continuation.isActive)continuation.resumeWithException(IllegalStateException("此设备暂不能导出该视频，请调整裁剪后重试；原片未改动",exportException))
                        }
                    }).build()
                transformer=engine
                continuation.invokeOnCancellation { handler.post {closed=true;handler.removeCallbacks(poll);transformer?.cancel();output.delete()} }
                try {
                    engine.start(EditedMediaItem.Builder(item(source,plan)).setEffects(Effects(emptyList(),effects(plan))).build(),output.path)
                    handler.post(poll)
                } catch(e:Exception) {closed=true;handler.removeCallbacks(poll);engine.cancel();if(continuation.isActive)continuation.resumeWithException(e)}
            }
            require(output.isFile && output.length() in 1..Codec.MAX_MEDIA_BYTES){"导出文件不完整，原片未改动"}
            output
        } catch(e:Exception) {output.delete();throw e}
    }
}

/** One reusable frame-sized overlay, never a list of decoded video frames. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class VideoMaskOverlay(private val mask:VideoRect):CanvasOverlay(true) {
    private val paint=Paint().apply{color=Color.BLACK}
    override fun onDraw(canvas:Canvas,presentationTimeUs:Long) {
        canvas.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR)
        canvas.drawRect(mask.left*canvas.width,mask.top*canvas.height,mask.right*canvas.width,mask.bottom*canvas.height,paint)
    }
}
