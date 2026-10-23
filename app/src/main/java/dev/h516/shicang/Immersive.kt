@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.h516.shicang

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState
import me.saket.telephoto.zoomable.ZoomableImageState
import java.io.File

@Composable private fun ImmersiveRoundButton(label:String,modifier:Modifier=Modifier,onClick:()->Unit) {
    val interaction=remember{MutableInteractionSource()}
    val pressed by interaction.collectIsPressedAsState()
    Box(modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha=.24f))
        .border(.5.dp,Color.White.copy(alpha=.2f),CircleShape)
        .alpha(if(pressed) .55f else 1f)
        .clickable(interactionSource=interaction,indication=null,role=Role.Button,onClick=onClick).semantics{contentDescription=label},contentAlignment=Alignment.Center) {
        FrameGlyph(label,Color.White)
    }
}

@Composable internal fun FullPhotoFrame(
    a:Archive,model:AppModel,appearance:Appearance,initialPhoto:String,
    position:(String)->Unit, next:(()->Unit)?, previous:(()->Unit)?,
    library:()->Unit, details:()->Unit, allPhotos:()->Unit, settings:()->Unit, edit:()->Unit, create:()->Unit,
    busy:Boolean=false, close:(()->Unit)?=null, active:Boolean=true, onPagingAllowed:(Boolean)->Unit={}, discovery:DiscoveryActions?=null
) {
    val pager=rememberPagerState(initialPage=photoIndex(a,initialPhoto),pageCount={a.photos.size.coerceAtLeast(1)})
    var playingVideo by remember {mutableStateOf<String?>(null)}
    var controls by rememberSaveable {mutableStateOf(true)}
    var menu by remember {mutableStateOf(false)}
    var information by remember {mutableStateOf(false)}
    var reading by rememberSaveable(a.id){mutableStateOf(false)}
    val archiveState by model.state.collectAsState()
    val archiveNote=archiveState.library.archiveNote(a.id)
    var editingVideo by remember {mutableStateOf<Photo?>(null)}
    var activity by remember {mutableIntStateOf(0)}
    val context=LocalContext.current
    val accessibility=remember {context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager}
    val current=a.photos.getOrNull(pager.currentPage)
    val videoPlaying=playingVideo!=null && playingVideo==current?.id
    LaunchedEffect(videoPlaying){if(!videoPlaying){controls=true;activity++}}
    val imageStates=remember(a.id) {mutableStateMapOf<String,ZoomableImageState>()}
    val zoomed=(imageStates[current?.id]?.zoomableState?.zoomFraction ?: 0f)>.001f
    val complete=appearance.skin!="全幅" || appearance.fit=="完整"
    val imageScale=if(complete) ContentScale.Fit else ContentScale.Crop
    val background=if(appearance.skin!="留白") Color.Black else MaterialTheme.colorScheme.background
    // Discover draws behind the floating navigation; keep its tools above that layer.
    val bottomInset=8.dp
    LaunchedEffect(pager.settledPage,a.photos,active) {
        a.photos.getOrNull(pager.settledPage)?.let {position(it.id)}
    }
    LaunchedEffect(active) {if(active) {controls=true;activity++}}
    LaunchedEffect(active,zoomed,videoPlaying,menu,information,reading,editingVideo,pager.isScrollInProgress) {
        onPagingAllowed(!zoomed && !menu && !information && !reading && editingVideo==null && !pager.isScrollInProgress)
    }
    LaunchedEffect(controls,menu,information,reading,activity,active) {
        if(active && controls && !menu && !information && !reading && !accessibility.isTouchExplorationEnabled) {
            delay(4000);controls=false
        }
    }
    val preferences=remember {context.getSharedPreferences("device",0)}
    var hint by remember {mutableStateOf(!preferences.getBoolean("frameHintV2",false))}
    fun touch() {activity++;hint=false;preferences.edit().putBoolean("frameHintV2",true).apply()}
    BackHandler(menu) {menu=false}
    Box(Modifier.fillMaxSize().background(background).then(if(active)Modifier else Modifier.clearAndSetSemantics{})) {
        val framed=appearance.skin=="留白"
        HorizontalPager(pager,Modifier.fillMaxSize(),
            key={a.photos.getOrNull(it)?.id ?: a.id},userScrollEnabled=!zoomed && !reading && editingVideo==null,
            beyondViewportPageCount=1) { page ->
            val p=a.photos.getOrNull(page)
            if(p==null) NameCard(a,Modifier.fillMaxSize().clickable {touch();controls=!controls})
            else key(p.id) {
                val ratio by produceState(2f/3f,framed,model.photoFile(p)) {
                    if(framed) value=withContext(Dispatchers.IO) {
                        val file=model.photoFile(p)
                        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
                        BitmapFactory.decodeFile(file.path,bounds)
                        val turn=runCatching{androidx.exifinterface.media.ExifInterface(file).rotationDegrees}.getOrDefault(0)
                        val raw=bounds.outWidth.coerceAtLeast(1).toFloat()/bounds.outHeight.coerceAtLeast(1)
                        if(turn==90 || turn==270) 1f/raw else raw
                    }
                }
                BoxWithConstraints(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
                    val width=minOf((maxWidth-40.dp).coerceAtLeast(1.dp),(maxHeight-140.dp).coerceAtLeast(1.dp)*ratio)
                    val canvas=if(framed && !p.isVideo) Modifier.width(width).height(width/ratio).border(1.dp,MaterialTheme.colorScheme.outlineVariant).padding(8.dp) else Modifier.fillMaxSize()
                    Box(canvas) {
                if(p.isVideo) VideoMedia(p,model,active && page==pager.settledPage && !pager.isScrollInProgress && !menu && !information && !reading && editingVideo==null && !busy,true,{playing->if(playing)playingVideo=p.id else if(playingVideo==p.id)playingVideo=null;touch()},controlsInset=if(discovery!=null)bottomInset else 0.dp,readText=archiveNote?.let{{touch();reading=true}}){touch();controls=!controls}
                else {
                    // Keep the renderer and its decoded image stable across the pager midpoint.
                    val imageState=rememberZoomableImageState()
                    DisposableEffect(p.id,imageState) {
                        imageStates[p.id]=imageState
                        onDispose {imageStates.remove(p.id)}
                    }
                    LaunchedEffect(page==pager.settledPage) {
                        if(page!=pager.settledPage) imageState.zoomableState.resetZoom()
                    }
                    ZoomPhoto(p,model,{touch();controls=!controls},imageState,active && page==pager.currentPage && !menu && !information,imageScale)
                }
                    }
                }
            }
        }
        if(active && hint && !videoPlaying) Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal=24.dp).padding(bottom=bottomInset+16.dp)
            .clickable {touch()},color=Color.Black.copy(alpha=.64f),shape=RoundedCornerShape(16.dp)) {
            Text("轻触显示工具\n左右翻照片与视频 · 上下换档案",Modifier.padding(horizontal=20.dp,vertical=12.dp),color=Color.White,fontSize=13.sp)
        }
        // Navigation and actions sit on the edges; metadata never covers the media until requested.
        AnimatedVisibility(visible=active && controls && !videoPlaying && editingVideo==null && !reading && !information,modifier=Modifier.align(Alignment.TopCenter),enter=fadeIn(tween(180)),exit=fadeOut(tween(160))) {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.45f),Color.Transparent)))
                .statusBarsPadding().padding(start=20.dp,end=12.dp,top=8.dp,bottom=24.dp)) {
                if(close!=null) GalleryTextButton(onClick={touch();close()},modifier=Modifier.align(Alignment.CenterStart),
                    colors=ButtonDefaults.textButtonColors(contentColor=Color.White)){Text("返回",maxLines=1)}
                else if(discovery!=null) Text("发现",Modifier.align(Alignment.CenterStart),color=Color.White,fontSize=17.sp,fontWeight=FontWeight.SemiBold)
                Text(if(a.photos.isEmpty()) "档案" else "${pager.currentPage+1} / ${a.photos.size}",Modifier.align(Alignment.Center),color=Color.White.copy(alpha=.85f),
                    fontSize=13.sp,fontWeight=FontWeight.Medium)
                Row(Modifier.align(Alignment.CenterEnd),horizontalArrangement=Arrangement.spacedBy(4.dp)) {

                    if(discovery!=null) ImmersiveRoundButton("资料"){touch();information=true}
                Box {
                    ImmersiveRoundButton("更多"){touch();menu=true}
                    DropdownMenu(menu,{menu=false},shape=RoundedCornerShape(16.dp)) {
                        current?.let{p->DropdownMenuItem(text={Text("写这项媒体的笔记")},onClick={menu=false;model.noteEditor.open("media",p.id,"${a.name} · ${if(p.isVideo) "视频" else "照片"}")})}
                        DropdownMenuItem(text={Text("查看资料")},onClick={menu=false;touch();information=true})
                        discovery?.let { action ->
                            DropdownMenuItem(text={Text(if(action.random) "随机预览 · 已开启" else "固定封面 · 已开启")},onClick={menu=false;action.toggle()})
                            DropdownMenuItem(text={Text("换一轮")},onClick={menu=false;action.refresh()})
                        }
                        if(a.photos.isNotEmpty()) DropdownMenuItem(text={Text("全部媒体")},onClick={menu=false;allPhotos()})
                        if(current!=null) {
                            if(current.isVideo) DropdownMenuItem(text={Text("编辑视频")},onClick={menu=false;editingVideo=current},enabled=!busy)
                            DropdownMenuItem(text={Text("保存到相册")},onClick={menu=false;model.requestExport(a,listOf(current))})
                            DropdownMenuItem(text={Text("分享原件")},onClick={menu=false;model.shareMedia(a,listOf(current))})
                        }
                        DropdownMenuItem(text={Text("主题与皮肤")},onClick={menu=false;settings()})
                        DropdownMenuItem(text={Text("新建档案")},onClick={menu=false;create()},enabled=!busy)
                        DropdownMenuItem(text={Text("编辑档案")},onClick={menu=false;edit()},enabled=!busy)
                        if(previous!=null) DropdownMenuItem(text={Text("上一份")},onClick={menu=false;touch();previous()})
                        if(next!=null) DropdownMenuItem(text={Text("下一份")},onClick={menu=false;touch();next()})
                    }
                }
                }
            }
        }
        AnimatedVisibility(visible=active && controls && !videoPlaying && editingVideo==null && !reading && !information && !hint,modifier=Modifier.align(Alignment.BottomCenter),enter=fadeIn(tween(180)),exit=fadeOut(tween(160))) {
            Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.62f))))
                .navigationBarsPadding().padding(start=24.dp,end=20.dp,top=40.dp,bottom=bottomInset),verticalAlignment=Alignment.CenterVertically) {
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp)
                    .clickable(interactionSource=remember{MutableInteractionSource()},indication=null,role=Role.Button,onClick={touch();if(discovery!=null) details() else information=true})
                    .semantics{contentDescription=if(discovery!=null) "打开档案：${a.name}" else "资料"}
                    .padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(a.name,color=Color.White,fontSize=22.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(archiveState.library.archiveSummary(a).ifBlank{if(discovery!=null) "查看档案" else "查看资料"},color=Color.White.copy(alpha=.72f),fontSize=13.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    if(archiveNote!=null)TextButton(onClick={touch();reading=true}){Text("阅读文字",color=Color.White)}
                    else FrameGlyph("展开",Color.White.copy(alpha=.8f))
                }
            }
        }

    }
    editingVideo?.let { video -> VideoEditorDialog(a,video,model){editingVideo=null;touch()} }
    if(reading && archiveNote!=null)NoteReaderDialog(archiveNote,a.name,archiveState.library.noteDrafts.any{it.key==archiveNote.key},
        {reading=false;touch()},{reading=false;edit()})
    if(information) {
        val state by model.state.collectAsState()
        ModalBottomSheet(onDismissRequest={information=false;touch()},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
            Column(Modifier.fillMaxWidth().heightIn(max=560.dp).verticalScroll(rememberScrollState()).padding(horizontal=24.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(a.name,Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
                    TextButton(onClick={information=false;touch()}){Text("收起")}
                }
                val authors=state.library.authors.filter{it.id in a.authorIds}
                if(authors.isNotEmpty()) {
                    Text("作者",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(authors.joinToString(" · "){it.name.ifBlank{it.identities.firstOrNull()?.nickname.orEmpty()}},style=MaterialTheme.typography.bodyLarge)
                }
                val labels=(listOf(a.category)+a.tags).map(state.library::labelName).filter{it.isNotBlank()}
                if(labels.isNotEmpty()) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    labels.forEach{label->SuggestionChip(onClick={information=false;details()},label={Text(label)})}
                }
                if(archiveNote!=null) {
                    Text(archiveNote.body,maxLines=5,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyLarge)
                    TextButton(onClick={information=false;reading=true;touch()}){Text("阅读文字")}
                }
                a.fields.filter{it.value.isNotBlank()}.take(4).forEach{field->
                    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(field.name,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(field.value,style=MaterialTheme.typography.bodyLarge,maxLines=3,overflow=TextOverflow.Ellipsis)
                    }
                }
                FilledTonalButton(onClick={information=false;details()},modifier=Modifier.fillMaxWidth()){Text("打开完整档案")}
                Spacer(Modifier.height(12.dp))
            }
        }
    }

}

/** Small visible marks with a full 48dp touch target and explicit accessibility names. */
@Composable internal fun GalleryIcon(kind:String,ink:Color=Color.White,onClick:()->Unit) {
    IconButton(onClick=onClick,modifier=Modifier.size(48.dp).semantics {contentDescription=kind}) {
        Box(Modifier.size(36.dp),contentAlignment=Alignment.Center) {
            FrameGlyph(kind,ink)
        }
    }
}

@Composable internal fun FrameGlyph(kind:String,ink:Color) {
    Canvas(Modifier.size(21.dp)) {
        val stroke=1.7.dp.toPx();val w=size.width;val h=size.height
        fun line(x1:Float,y1:Float,x2:Float,y2:Float)=drawLine(ink,Offset(w*x1,h*y1),Offset(w*x2,h*y2),stroke)
        when(kind) {
            "全部照片" -> {
                drawRoundRect(ink,Offset(w*.08f,h*.12f),androidx.compose.ui.geometry.Size(w*.84f,h*.76f),androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),style=Stroke(stroke))
                drawCircle(ink,w*.08f,Offset(w*.67f,h*.34f),style=Stroke(stroke))
                drawLine(ink,Offset(w*.12f,h*.74f),Offset(w*.39f,h*.44f),stroke)
                drawLine(ink,Offset(w*.39f,h*.44f),Offset(w*.62f,h*.73f),stroke)
            }
            "保存到相册" -> {line(.5f,.08f,.5f,.62f);line(.28f,.43f,.5f,.65f);line(.5f,.65f,.72f,.43f);line(.15f,.7f,.15f,.88f);line(.15f,.88f,.85f,.88f);line(.85f,.88f,.85f,.7f)}
            "档案" -> for(y in 0..1) for(x in 0..1) drawRoundRect(ink,Offset(w*(.10f+x*.46f),h*(.10f+y*.46f)),androidx.compose.ui.geometry.Size(w*.32f,h*.32f),androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),style=Stroke(stroke))
            "更多" -> for(x in 0..2) drawCircle(ink,1.3.dp.toPx(),Offset(w*(.2f+x*.3f),h*.5f))
            "新建" -> {drawLine(ink,Offset(w*.18f,h*.5f),Offset(w*.82f,h*.5f),stroke);drawLine(ink,Offset(w*.5f,h*.18f),Offset(w*.5f,h*.82f),stroke)}
            "设置" -> for(i in 0..2){val y=h*(.24f+i*.26f);drawLine(ink,Offset(w*.12f,y),Offset(w*.88f,y),stroke);drawCircle(ink,w*.075f,Offset(w*(if(i==1) .67f else .33f),y),style=Stroke(stroke*1.6f))}
            "筛选" -> {line(.12f,.18f,.88f,.18f);line(.12f,.18f,.43f,.54f);line(.88f,.18f,.57f,.54f);line(.43f,.54f,.43f,.84f);line(.57f,.54f,.57f,.76f);line(.43f,.84f,.57f,.76f)}
            "搜索" -> {drawCircle(ink,w*.28f,Offset(w*.42f,h*.42f),style=Stroke(stroke));drawLine(ink,Offset(w*.63f,h*.63f),Offset(w*.87f,h*.87f),stroke)}
            "发现" -> {drawCircle(ink,w*.39f,Offset(w*.5f,h*.5f),style=Stroke(stroke));drawLine(ink,Offset(w*.63f,h*.28f),Offset(w*.37f,h*.72f),stroke);drawLine(ink,Offset(w*.37f,h*.72f),Offset(w*.31f,h*.44f),stroke);drawLine(ink,Offset(w*.31f,h*.44f),Offset(w*.63f,h*.28f),stroke)}
            "我的" -> {drawRoundRect(ink,Offset(w*.15f,h*.2f),androidx.compose.ui.geometry.Size(w*.7f,h*.68f),androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),style=Stroke(stroke));drawLine(ink,Offset(w*.28f,h*.08f),Offset(w*.72f,h*.08f),stroke);drawLine(ink,Offset(w*.33f,h*.45f),Offset(w*.67f,h*.45f),stroke);drawLine(ink,Offset(w*.33f,h*.62f),Offset(w*.57f,h*.62f),stroke)}
            "展开" -> {drawLine(ink,Offset(w*.38f,h*.25f),Offset(w*.63f,h*.5f),stroke);drawLine(ink,Offset(w*.63f,h*.5f),Offset(w*.38f,h*.75f),stroke)}
            "打开链接" -> {drawLine(ink,Offset(w*.28f,h*.72f),Offset(w*.77f,h*.23f),stroke);drawLine(ink,Offset(w*.41f,h*.23f),Offset(w*.77f,h*.23f),stroke);drawLine(ink,Offset(w*.77f,h*.23f),Offset(w*.77f,h*.59f),stroke)}
            "资料" -> {drawCircle(ink,w*.4f,Offset(w*.5f,h*.5f),style=Stroke(stroke));drawCircle(ink,stroke*.65f,Offset(w*.5f,h*.32f));drawLine(ink,Offset(w*.5f,h*.48f),Offset(w*.5f,h*.7f),stroke)}
            else -> {drawLine(ink,Offset(w*.62f,h*.20f),Offset(w*.32f,h*.5f),stroke);drawLine(ink,Offset(w*.32f,h*.5f),Offset(w*.62f,h*.80f),stroke)}
        }
    }
}

@Composable private fun ZoomPhoto(p:Photo,model:AppModel,tap:()->Unit,state:ZoomableImageState,backAllowed:Boolean,imageScale:ContentScale) {
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    var failure by remember {mutableStateOf(false)}
    var retry by remember {mutableIntStateOf(0)}
    var loading by remember {mutableStateOf(false)}
    val zoomed=(state.zoomableState.zoomFraction ?: 0f)>.001f
    LaunchedEffect(state.isImageDisplayed,failure,retry) {
        loading=false
        if(!state.isImageDisplayed && !failure) {delay(300);loading=true}
    }
    BackHandler(zoomed && backAllowed) {scope.launch {state.zoomableState.resetZoom()}}
    val request=remember(p.id,retry) {ImageRequest.Builder(context).data(model.photoFile(p))
        .memoryCacheKey("${model.photoFile(p).path}:$retry").crossfade(false)
        .listener(onError={_,_->failure=true},onSuccess={_,_->failure=false}).build()}
    Box(Modifier.fillMaxSize()) {
        ZoomableAsyncImage(model=request,state=state,contentDescription=if(imageScale==ContentScale.Fit) "完整照片，双击或双指缩放" else "全幅照片，双击或双指缩放",
            contentScale=imageScale,modifier=Modifier.fillMaxSize().semantics {stateDescription=if(state.isImageDisplayed) "照片已显示" else "照片加载中"},onClick={tap()})
        if(zoomed) Surface(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(18.dp),color=Color.Black.copy(alpha=.5f),shape=RoundedCornerShape(14.dp)) {
            TextButton(onClick={scope.launch {state.zoomableState.resetZoom()}}) {Text("还原大小",color=Color.White)}
        }
        if(loading && !failure) CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp),color=Color.White,strokeWidth=2.dp)
        if(failure) Surface(Modifier.align(Alignment.Center),color=Color.Black.copy(alpha=.65f),shape=RoundedCornerShape(12.dp)) {
            TextButton(onClick={failure=false;retry++}) {Text("照片读取失败 · 重试",color=Color.White)}
        }
    }
}

@Composable fun AllPhotos(a:Archive,model:AppModel,select:(String)->Unit) {
    var filter by rememberSaveable(a.id){mutableStateOf(MediaFilter.ALL)}
    var choosing by rememberSaveable(a.id){mutableStateOf(false)}
    var chosen by rememberSaveable(a.id){mutableStateOf(listOf<String>())}
    val media=a.media(filter)
    val selected=a.photos.filter{it.id in chosen}
    BackHandler(choosing){choosing=false;chosen=emptyList()}
    Column(Modifier.fillMaxSize()) {
        FlowRow(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            MediaFilter.entries.forEach{value->FilterChip(filter==value,{filter=value},label={Text(value.label)})}
            TextButton(onClick={choosing=!choosing;chosen=emptyList()}){Text(if(choosing)"完成" else "多选")}
        }
        if(choosing)FlowRow(Modifier.padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            TextButton(onClick={chosen=if(media.all{it.id in chosen})chosen-media.map{it.id}.toSet() else (chosen+media.map{it.id}).distinct()}){Text("全选")}
            TextButton(onClick={model.shareMedia(a,selected)},enabled=selected.isNotEmpty()){Text("分享")}
            TextButton(onClick={model.requestExport(a,selected)},enabled=selected.isNotEmpty()){Text("保存 ${selected.size} 项")}
        }
        if(media.isEmpty())Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Text("这里还没有${if(filter==MediaFilter.ALL)"媒体" else filter.label}")}
        else LazyVerticalGrid(GridCells.Fixed(3),Modifier.weight(1f),contentPadding=PaddingValues(8.dp),
            horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            items(media,key={it.id}) {p->
                Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).combinedClickable(
                    onClick={if(choosing)chosen=if(p.id in chosen)chosen-p.id else chosen+p.id else select(p.id)},
                    onLongClick={choosing=true;chosen=(chosen+p.id).distinct()})) {
                    MediaThumb(p,model,Modifier.fillMaxSize(),"查看第${a.photos.indexOf(p)+1}项照片或视频")
                    if(choosing)Checkbox(p.id in chosen,{checked->chosen=if(checked)(chosen+p.id).distinct()else chosen-p.id},Modifier.align(Alignment.TopEnd))
                }
            }
        }
    }
}
