package dev.h516.shicang

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.media3.common.Player
import kotlinx.coroutines.delay

internal fun seekPosition(position:Long, delta:Long, duration:Long):Long =
    (position + delta).coerceIn(0L,duration.coerceAtLeast(0L))

/** Controls share the same player; gestures never replace the saved playback preference. */
@Composable internal fun PlayerControls(player:Player, speed:Float, muted:Boolean,
    speedChange:(Float)->Unit, mute:()->Unit, onClose:()->Unit, playIntentChange:(Boolean)->Unit={},bottomInset:Dp=0.dp) {
    var visible by remember {mutableStateOf(true)}
    var position by remember {mutableLongStateOf(0)}
    var duration by remember {mutableLongStateOf(0)}
    var playing by remember {mutableStateOf(false)}
    var holding by remember {mutableStateOf(false)}
    var scrubbing by remember {mutableStateOf(false)}
    var preview by remember {mutableFloatStateOf(0f)}
    var touch by remember {mutableIntStateOf(0)}
    var feedback by remember {mutableStateOf("")}
    val currentSpeed by rememberUpdatedState(speed)
    val accessibility=androidx.compose.ui.platform.LocalContext.current.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
    fun seek(delta:Long) {player.seekTo(seekPosition(player.currentPosition,delta,player.duration));touch++;feedback=if(delta>0) "快进 10 秒" else "快退 10 秒"}
    LaunchedEffect(player) {while(true){position=player.currentPosition;duration=player.duration.coerceAtLeast(0);playing=player.isPlaying;delay(200)}}
    LaunchedEffect(visible,touch,playing,scrubbing) {if(visible && playing && !scrubbing && !accessibility.isTouchExplorationEnabled){delay(4000);visible=false}}
    LaunchedEffect(feedback) {if(feedback.isNotEmpty()){delay(1200);feedback=""}}
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().semantics{contentDescription="视频手势区域：双击左右快退快进，长按倍速，下方进度条定位"}
            .pointerInput(player) {detectTapGestures(
                onTap={visible=!visible;touch++},
                onDoubleTap={seek(if(it.x<size.width/2) -10000 else 10000)},
                onLongPress={holding=true;player.setPlaybackSpeed(2f)},
                onPress={try{tryAwaitRelease()}finally{holding=false;player.setPlaybackSpeed(currentSpeed)}}
            )})
        AnimatedVisibility(visible,enter=fadeIn(),exit=fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.75f),Color.Transparent))).statusBarsPadding().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick=onClose){Text("收起播放",color=Color.White)}
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick={speedChange(when(speed){1f->1.5f;1.5f->2f;else->1f});touch++}) {Text("${speed}×",color=Color.White)}
                    TextButton(onClick={mute();touch++}) {Text(if(muted) "开启声音" else "静音",color=Color.White)}
                }
                Row(Modifier.align(Alignment.Center),horizontalArrangement=Arrangement.spacedBy(32.dp),verticalAlignment=Alignment.CenterVertically) {
                    SeekButton(false){seek(-10000)}
                    FilledIconButton(onClick={if(player.playWhenReady) {playIntentChange(false);player.pause()} else {playIntentChange(true);if(player.playbackState==Player.STATE_ENDED)player.seekTo(0);player.play()};touch++},
                        modifier=Modifier.size(72.dp).semantics{contentDescription=if(playing) "暂停" else "播放"},shape=CircleShape,
                        colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color.White,contentColor=Color.Black)) {
                        Canvas(Modifier.size(28.dp)) {if(playing){drawRect(Color.Black,Offset(size.width*.2f,0f),androidx.compose.ui.geometry.Size(size.width*.2f,size.height));drawRect(Color.Black,Offset(size.width*.6f,0f),androidx.compose.ui.geometry.Size(size.width*.2f,size.height))}
                            else drawPath(Path().apply{moveTo(size.width*.2f,0f);lineTo(size.width,size.height/2);lineTo(size.width*.2f,size.height);close()},Color.Black)}
                    }
                    SeekButton(true){seek(10000)}
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.85f)))).navigationBarsPadding().padding(bottom=bottomInset).padding(horizontal=24.dp,vertical=20.dp)) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text(durationLabel(if(scrubbing)preview.toLong() else position),color=Color.White);Text(durationLabel(duration),color=Color.White.copy(alpha=.8f))}
                    Slider(value=if(scrubbing)preview.coerceIn(0f,duration.coerceAtLeast(1).toFloat()) else position.coerceIn(0,duration).toFloat(),
                        onValueChange={preview=it;scrubbing=true},onValueChangeFinished={player.seekTo(preview.toLong());scrubbing=false;touch++},
                        valueRange=0f..duration.coerceAtLeast(1).toFloat(),enabled=duration>0,modifier=Modifier.semantics{contentDescription="播放进度"},
                        colors=SliderDefaults.colors(thumbColor=Color.White,activeTrackColor=Color.White,inactiveTrackColor=Color.White.copy(alpha=.3f)))
                    Text("双击快进快退 · 长按 2 倍速 · 拖动下方进度条定位",color=Color.White.copy(alpha=.75f),style=MaterialTheme.typography.labelSmall)
                }
            }
        }
        if(holding || feedback.isNotEmpty() || scrubbing) Surface(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top=80.dp),shape=CircleShape,color=Color.Black.copy(alpha=.75f)) {
            Text(if(holding) "2× 倍速播放中" else if(scrubbing) "定位到 ${durationLabel(preview.toLong())}" else feedback,Modifier.padding(horizontal=18.dp,vertical=10.dp),color=Color.White)
        }
    }
}

@Composable private fun SeekButton(forward:Boolean,click:()->Unit) {
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        IconButton(onClick=click,modifier=Modifier.size(56.dp).background(Color.Black.copy(alpha=.45f),CircleShape).semantics{contentDescription=if(forward)"快进10秒" else "快退10秒"}) {
            Canvas(Modifier.size(25.dp)) {repeat(2){i->val x=i*size.width*.45f;drawPath(Path().apply{if(forward){moveTo(x,0f);lineTo(x+size.width*.45f,size.height/2);lineTo(x,size.height)}else{moveTo(x+size.width*.45f,0f);lineTo(x,size.height/2);lineTo(x+size.width*.45f,size.height)};close()},Color.White)}}
        }
        Text("10 秒",color=Color.White,style=MaterialTheme.typography.labelSmall)
    }
}
