@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.h516.shicang

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import java.io.File

/** One foreground edit session. Closing/cancelling never deletes the original. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable internal fun VideoEditorDialog(a:Archive,p:Photo,model:AppModel,onClose:()->Unit) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    val source=remember(p.id){model.photoFile(p)}
    var info by remember(p.id){mutableStateOf<VideoEditInfo?>(null)}
    var failure by remember{mutableStateOf("")}
    var plan by remember{mutableStateOf(VideoEditPlan(0,p.duration))}
    var mode by remember{mutableStateOf("裁画面")}
    var preview by remember{mutableStateOf(false)}
    var running by remember{mutableStateOf(false)}
    var saving by remember{mutableStateOf(false)}
    var progress by remember{mutableStateOf<Int?>(null)}
    var job by remember{mutableStateOf<Job?>(null)}
    LaunchedEffect(p.id) {
        try {val value=VideoEditEngine.inspect(source,p);info=value;plan=plan.copy(endMs=value.durationMs)}
        catch(e:CancellationException){throw e}catch(e:Exception){failure=e.message ?: "无法打开视频"}
    }
    fun close(){if(!saving){job?.cancel();onClose()}}
    Dialog(onDismissRequest={close()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        BackHandler {close()}
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text("编辑视频",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                    GalleryTextButton(onClick={close()},enabled=!saving){Text(if(running)"取消并返回" else "返回")}
                }
                Text("先裁掉边缘杂物，或框选固定遮挡。只另存副本，原片和声音保留。",style=MaterialTheme.typography.bodyMedium)
                if(info==null && failure.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
                info?.let { metadata ->
                    if(running) Box(Modifier.fillMaxWidth().height(180.dp),contentAlignment=Alignment.Center){Text("正在生成新副本，原视频保持不变")}
                    else VideoEditPreview(source,metadata,plan,preview,true,mode,{rectangle->
                        if(rectangle.valid()){plan=if(mode=="裁画面")plan.copy(crop=rectangle)else plan.copy(mask=rectangle)}
                    })
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        FilterChip(selected=!preview,onClick={preview=false},enabled=!running,label={Text("框选区域")})
                        FilterChip(selected=preview,onClick={preview=true},enabled=!running,label={Text("预览效果")})
                    }
                    if(!preview) {
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            FilterChip(selected=mode=="裁画面",onClick={mode="裁画面"},enabled=!running,label={Text("裁画面")})
                            FilterChip(selected=mode=="遮挡",onClick={mode="遮挡"},enabled=!running,label={Text("遮挡")})
                            GalleryTextButton(onClick={plan=if(mode=="裁画面")plan.copy(crop=VideoRect())else plan.copy(mask=null)},enabled=!running){Text("重置")}
                        }
                        Text(if(mode=="裁画面")"在视频内拖出要保留的范围；框内会保留。" else "在视频内拖出黑色遮挡区；用于固定按钮，不会还原被挡住的画面。",style=MaterialTheme.typography.bodySmall)
                    }
                    Text("保留片段 · ${durationLabel(plan.startMs)} → ${durationLabel(plan.endMs)}")
                    RangeSlider(value=plan.startMs.toFloat()..plan.endMs.toFloat(),onValueChange={range->
                        val start=range.start.toLong().coerceIn(0,metadata.durationMs-250)
                        val end=range.endInclusive.toLong().coerceIn(start+250,metadata.durationMs)
                        plan=plan.copy(startMs=start,endMs=end)
                    },enabled=!running,valueRange=0f..metadata.durationMs.toFloat(),modifier=Modifier.testTag("video-trim"))
                    var startSeconds by remember(plan.startMs){mutableStateOf((plan.startMs/1000.0).toString())}
                    var endSeconds by remember(plan.endMs){mutableStateOf((plan.endMs/1000.0).toString())}
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        GalleryTextField(startSeconds,{startSeconds=it.take(16)},label={Text("起始秒")},enabled=!running,singleLine=true,modifier=Modifier.weight(1f),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal))
                        GalleryTextField(endSeconds,{endSeconds=it.take(16)},label={Text("结束秒")},enabled=!running,singleLine=true,modifier=Modifier.weight(1f),keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Decimal))
                    }
                    GalleryTextButton(onClick={
                        val from=startSeconds.toDoubleOrNull();val to=endSeconds.toDoubleOrNull()
                        if(from==null || to==null || !from.isFinite() || !to.isFinite() || from<0 || to>metadata.durationMs/1000.0 || to-from<.25)failure="请输入视频时长内的秒数，至少保留 0.25 秒"
                        else {plan=plan.copy(startMs=(from*1000).toLong(),endMs=(to*1000).toLong());failure=""}
                    },enabled=!running){Text("应用精确时间")}
                    Text("长视频可先剪成片段。导出期间请保持此页；离开应用会停止导出，原片不变。",style=MaterialTheme.typography.bodySmall)
                    if(running) {
                        if(progress==null) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress={progress!!/100f},modifier=Modifier.fillMaxWidth())
                        Text(if(saving)"正在保存独立副本" else "正在导出${progress?.let{" $it%"}.orEmpty()}")
                        GalleryTextButton(onClick={job?.cancel()},enabled=!saving){Text("取消导出")}
                    } else GalleryButton(onClick={
                        failure="";running=true;progress=null;preview=true

                    },modifier=Modifier.fillMaxWidth().testTag("video-save-copy")){Text("另存到此档案")}
                }
                if(failure.isNotEmpty()) Text(failure,color=MaterialTheme.colorScheme.error)
            }
        }
    }
    // Started after composition disposes the preview player, avoiding two hardware codecs at once.
    LaunchedEffect(running) {
        if(!running)return@LaunchedEffect
        if(!lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)){running=false;failure="已离开编辑页，未开始导出";return@LaunchedEffect}
        val metadata=info ?: return@LaunchedEffect
        job=kotlinx.coroutines.currentCoroutineContext()[Job]
        var output:File?=null
        try {
            output=VideoEditEngine.export(context,source,p,plan,metadata){progress=it}
            saving=true
            model.saveEditedVideo(a,p,source,output)
            onClose()
        } catch(e:CancellationException){failure="导出已取消，原视频保留";throw e}
        catch(e:Exception){failure=e.message ?: "导出未完成，原视频保留"}
        finally {output?.delete();running=false;saving=false;job=null}
    }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP && running && !saving){job?.cancel();failure="已离开编辑页，导出停止；原片保留"}}
        lifecycle.lifecycle.addObserver(observer)
        onDispose{lifecycle.lifecycle.removeObserver(observer);job?.cancel()}
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable private fun VideoEditPreview(source:File,info:VideoEditInfo,plan:VideoEditPlan,preview:Boolean,enabled:Boolean,mode:String,onSelect:(VideoRect)->Unit) {
    val context=LocalContext.current
    val lifecycle=LocalLifecycleOwner.current
    val player=remember(source){ExoPlayer.Builder(context).setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true).setHandleAudioBecomingNoisy(true).build()}
    var failure by remember{mutableStateOf(false)}
    var start by remember{mutableStateOf(Offset.Zero)}
    var drag by remember{mutableStateOf<VideoRect?>(null)}
    LaunchedEffect(player,plan,preview) {
        player.pause();failure=false
        player.setVideoEffects(if(preview)VideoEditEngine.effects(plan)else emptyList())
        player.setMediaItem(if(preview)VideoEditEngine.item(source,plan)else MediaItem.fromUri(Uri.fromFile(source)))
        if(!preview)player.seekTo(plan.startMs)
        player.prepare()
    }
    LaunchedEffect(enabled){if(!enabled)player.pause()}
    DisposableEffect(player,lifecycle) {
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_PAUSE)player.pause()}
        val listener=object:Player.Listener{override fun onPlayerError(error:androidx.media3.common.PlaybackException){failure=true}}
        lifecycle.lifecycle.addObserver(observer);player.addListener(listener)
        onDispose{lifecycle.lifecycle.removeObserver(observer);player.removeListener(listener);player.release()}
    }
    val cropInk=MaterialTheme.colorScheme.primary
    val ratio=info.width.toFloat()/info.height*(if(preview)(plan.crop.right-plan.crop.left)/(plan.crop.bottom-plan.crop.top)else 1f)
    BoxWithConstraints(Modifier.fillMaxWidth().height(300.dp).background(Color.Black),contentAlignment=Alignment.Center) {
        val width=minOf(maxWidth,maxHeight*ratio);val height=width/ratio
        Box(Modifier.width(width).height(height)) {
            AndroidView(factory={PlayerView(it).apply{useController=true}},update={it.player=player;it.useController=preview && enabled},onRelease={it.player=null},modifier=Modifier.fillMaxSize())
            if(!preview) Canvas(Modifier.fillMaxSize().testTag("video-region").pointerInput(mode,enabled) {
                if(enabled)detectDragGestures(onDragStart={start=it;drag=null},onDragCancel={drag=null},onDragEnd={drag?.let(onSelect);drag=null}){change,_->
                    change.consume();drag=VideoRect.between(start.x/size.width,start.y/size.height,change.position.x/size.width,change.position.y/size.height)
                }
            }) {
                fun rect(r:VideoRect,color:Color,outline:Boolean) {
                    val top=Offset(r.left*size.width,r.top*size.height);val extent=Size((r.right-r.left)*size.width,(r.bottom-r.top)*size.height)
                    if(outline)drawRect(color,top,extent,style=Stroke(3.dp.toPx()))else drawRect(color,top,extent)
                }
                plan.mask?.let{rect(it,Color.Black,false)}
                rect(if(mode=="裁画面")drag ?: plan.crop else plan.crop,cropInk,true)
                if(mode=="遮挡")drag?.let{rect(it,Color.Black,false);rect(it,Color.White,true)}
            }
        }
        if(failure) Text("此设备暂时无法预览，原片未改动",Modifier.background(Color.Black).padding(12.dp),color=Color.White)
    }
}
