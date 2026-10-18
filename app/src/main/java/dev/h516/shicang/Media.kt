package dev.h516.shicang

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Decode headers only. Original bytes stay untouched, including EXIF and audio. */
internal fun inspectMedia(file:File,declared:String):Pair<String,Long> {
    val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
    BitmapFactory.decodeFile(file.path,bounds)
    if(bounds.outWidth>0 && bounds.outHeight>0) return (bounds.outMimeType ?: "image/jpeg") to 0L
    val reader=MediaMetadataRetriever()
    try {
        reader.setDataSource(file.path)
        val width=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val height=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val duration=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        require(width>0 && height>0 && duration>0) {"无法识别照片或视频，请选择其他文件"}
        val mime=reader.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        return (mime?.takeIf{it.startsWith("video/")} ?: declared.takeIf{it.startsWith("video/")} ?: "video/mp4") to duration
    } finally {reader.release()}
}

private val posterSlots=Semaphore(2)

internal fun durationLabel(ms:Long):String = "%d:%02d".format(ms/60000,(ms/1000)%60)

@Composable internal fun MediaThumb(p:Photo,model:AppModel,modifier:Modifier=Modifier,description:String="照片或视频",scale:ContentScale=ContentScale.Crop) {
    val context=LocalContext.current
    val original=model.photoFile(p)
    val thumbnail by produceState<File?>(if(p.isVideo) null else original,original,p.sha256) {
        if(p.isVideo) value=withContext(Dispatchers.IO) { posterSlots.withPermit {
            // Hash-keyed, disposable posters. Missing caches never lose the original video.
            val folder=File(context.cacheDir,"previews").apply{mkdirs()}
            val file=File(folder,"${p.sha256}.jpg")
            if(file.isFile) file else runCatching {
                val reader=MediaMetadataRetriever()
                try {
                    reader.setDataSource(original.path)
                    val frame=if(Build.VERSION.SDK_INT>=27) reader.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,480,640) else null
                    requireNotNull(frame)
                    val tmp=File(folder,"${p.sha256}-${uid()}.pending")
                    try {tmp.outputStream().use{frame.compress(android.graphics.Bitmap.CompressFormat.JPEG,82,it)};check(tmp.renameTo(file) || file.exists())} finally {frame.recycle();tmp.delete()}
                    // Bound generated posters to 64 MiB; active Coil bitmaps remain separately bounded.
                    var total=folder.listFiles().orEmpty().sumOf{it.length()}
                    folder.listFiles().orEmpty().sortedBy{it.lastModified()}.forEach{f->if(total>64L*1024*1024 && f!=file && f.extension=="jpg"){val n=f.length();if(f.delete()) total-=n}}
                    file
                } finally {reader.release()}
            }.getOrNull()
        }}
    }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        if(thumbnail!=null) AsyncImage(model=thumbnail,contentDescription=description,contentScale=scale,modifier=Modifier.fillMaxSize())
        else Text(if(p.isVideo) "视频" else "加载中",Modifier.align(Alignment.Center),fontSize=12.sp)
        if(p.isVideo) Text("视频 ${durationLabel(p.duration)}",Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha=.65f)).padding(horizontal=5.dp,vertical=3.dp),color=Color.White,fontSize=10.sp)
    }
}

/** A player exists only for the visible, explicitly started video. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable internal fun VideoMedia(p:Photo,model:AppModel,active:Boolean,fit:Boolean,tap:()->Unit) {
    var started by rememberSaveable(p.id){mutableStateOf(false)}
    var muted by rememberSaveable(p.id){mutableStateOf(true)}
    var position by rememberSaveable(p.id){mutableLongStateOf(0L)}
    var speed by rememberSaveable(p.id){mutableFloatStateOf(1f)}
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if(!active || !started) {
            MediaThumb(p,model,Modifier.fillMaxSize().clickable{tap()},scale=if(fit) ContentScale.Fit else ContentScale.Crop)
            if(active) FilledTonalButton(onClick={started=true},modifier=Modifier.align(Alignment.Center)){
                Text(if(position>0) "继续播放 · ${durationLabel(position)}" else "播放视频")
            }
        } else {
            val player=remember(p.id){ExoPlayer.Builder(context).build().apply{
                volume=if(muted) 0f else 1f
                setMediaItem(MediaItem.fromUri(Uri.fromFile(model.photoFile(p))))
                seekTo(position);setPlaybackSpeed(speed);prepare();playWhenReady=true
            }}
            var failure by remember(p.id){mutableStateOf(false)}
            var buffering by remember(p.id){mutableStateOf(true)}
            var playing by remember(p.id){mutableStateOf(false)}
            DisposableEffect(player,lifecycle) {
                var resume=false
                val observer=LifecycleEventObserver{_,event->
                    when(event) {
                        Lifecycle.Event.ON_PAUSE -> {resume=player.playWhenReady;position=player.currentPosition;player.pause()}
                        Lifecycle.Event.ON_RESUME -> if(resume && !failure) {player.play();resume=false}
                        else -> Unit
                    }
                }
                val listener=object:Player.Listener {
                    override fun onPlayerError(error:androidx.media3.common.PlaybackException){failure=true;buffering=false}
                    override fun onIsPlayingChanged(value:Boolean){playing=value}
                    override fun onPlaybackStateChanged(state:Int){
                        buffering=state==Player.STATE_BUFFERING
                        if(state==Player.STATE_ENDED) position=0L
                    }
                }
                lifecycle.lifecycle.addObserver(observer);player.addListener(listener)
                onDispose {
                    position=if(player.playbackState==Player.STATE_ENDED) 0L else player.currentPosition
                    lifecycle.lifecycle.removeObserver(observer);player.removeListener(listener);player.release()
                }
            }
            AndroidView(factory={PlayerView(it).apply{useController=true}},
                update={view->view.player=player;view.keepScreenOn=playing
                    view.resizeMode=if(fit) AspectRatioFrameLayout.RESIZE_MODE_FIT else AspectRatioFrameLayout.RESIZE_MODE_ZOOM},
                modifier=Modifier.fillMaxSize(),onRelease={it.player=null;it.keepScreenOn=false})
            Row(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp).background(Color.Black.copy(alpha=.65f))) {
                TextButton(onClick={speed=when(speed){1f->1.5f;1.5f->2f;else->1f};player.setPlaybackSpeed(speed)}){Text("${speed}×",color=Color.White)}
                TextButton(onClick={muted=!muted;player.volume=if(muted) 0f else 1f}){Text(if(muted) "开启声音" else "静音",color=Color.White)}
                TextButton(onClick={position=player.currentPosition;started=false}){Text("收起播放",color=Color.White)}
            }
            if(buffering && !failure) CircularProgressIndicator(Modifier.align(Alignment.Center),color=Color.White)
            if(failure) Surface(Modifier.align(Alignment.Center),color=Color.Black.copy(alpha=.85f)){
                Column(Modifier.padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally){
                    Text("视频暂时无法播放，原文件仍保留",color=Color.White)
                    Row {
                        TextButton(onClick={failure=false;buffering=true;player.prepare();player.play()}){Text("重试",color=Color.White)}
                        TextButton(onClick={started=false}){Text("返回预览",color=Color.White)}
                    }
                }
            }
        }
    }
}
