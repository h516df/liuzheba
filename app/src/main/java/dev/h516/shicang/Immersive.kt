@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.h516.shicang

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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

@Composable internal fun FullPhotoFrame(
    a:Archive,model:AppModel,appearance:Appearance,initialPhoto:String,
    position:(String)->Unit, next:(()->Unit)?, previous:(()->Unit)?,
    library:()->Unit, details:()->Unit, allPhotos:()->Unit, settings:()->Unit, edit:()->Unit, create:()->Unit,
    busy:Boolean=false, close:(()->Unit)?=null, active:Boolean=true, onPagingAllowed:(Boolean)->Unit={}, discovery:DiscoveryActions?=null
) {
    val pager=rememberPagerState(initialPage=photoIndex(a,initialPhoto),pageCount={a.photos.size.coerceAtLeast(1)})
    var videoPlaying by remember {mutableStateOf(false)}
    var controls by rememberSaveable {mutableStateOf(true)}
    var menu by remember {mutableStateOf(false)}
    var information by remember {mutableStateOf(false)}
    var editingVideo by remember {mutableStateOf<Photo?>(null)}
    var activity by remember {mutableIntStateOf(0)}
    val context=LocalContext.current
    val accessibility=remember {context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager}
    val current=a.photos.getOrNull(pager.currentPage)
    val imageStates=remember(a.id) {mutableStateMapOf<String,ZoomableImageState>()}
    val zoomed=(imageStates[current?.id]?.zoomableState?.zoomFraction ?: 0f)>.001f
    val complete=appearance.skin!="全幅" || appearance.fit=="完整"
    val imageScale=if(complete) ContentScale.Fit else ContentScale.Crop
    val background=if(appearance.skin!="留白") Color.Black else MaterialTheme.colorScheme.background
    LaunchedEffect(pager.settledPage,a.photos,active) {
        a.photos.getOrNull(pager.settledPage)?.let {position(it.id)}
    }
    LaunchedEffect(active) {if(active) {controls=true;activity++}}
    LaunchedEffect(active,zoomed,videoPlaying,menu,information,editingVideo,pager.isScrollInProgress) {
        onPagingAllowed(!zoomed && !videoPlaying && !menu && !information && editingVideo==null && !pager.isScrollInProgress)
    }
    LaunchedEffect(controls,menu,information,activity,active) {
        if(active && controls && !menu && !information && !accessibility.isTouchExplorationEnabled) {
            delay(4000);controls=false
        }
    }
    val preferences=remember {context.getSharedPreferences("device",0)}
    var hint by remember {mutableStateOf(!preferences.getBoolean("frameHintV2",false))}
    fun touch() {activity++;hint=false;preferences.edit().putBoolean("frameHintV2",true).apply()}
    BackHandler(menu) {menu=false}
    Box(Modifier.fillMaxSize().background(background)) {
        val framed=appearance.skin=="留白"
        HorizontalPager(pager,Modifier.fillMaxSize(),
            key={a.photos.getOrNull(it)?.id ?: a.id},userScrollEnabled=!videoPlaying && !zoomed,
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
                    val canvas=if(framed) Modifier.width(width).height(width/ratio).border(1.dp,MaterialTheme.colorScheme.outlineVariant).padding(8.dp) else Modifier.fillMaxSize()
                    Box(canvas) {
                if(p.isVideo) VideoMedia(p,model,active && page==pager.settledPage && !pager.isScrollInProgress && !menu && !information && editingVideo==null && !busy,complete,{videoPlaying=it;touch()}){touch();controls=!controls}
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
        if(active && hint && !videoPlaying) Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp)
            .clickable {touch()},color=Color.Black.copy(alpha=.64f),shape=RoundedCornerShape(16.dp)) {
            Text("轻触显示工具\n左右翻照片与视频 · 上下换档案",Modifier.padding(horizontal=20.dp,vertical=12.dp),color=Color.White,fontSize=13.sp)
        }
        // Navigation and actions sit on the edges; metadata never covers the media until requested.
        AnimatedVisibility(visible=active && controls && !videoPlaying,modifier=Modifier.align(Alignment.TopCenter),enter=fadeIn(tween(180)),exit=fadeOut(tween(160))) {
            Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha=.65f),Color.Transparent)))
                .statusBarsPadding().padding(horizontal=8.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                if(close!=null) TextButton(onClick={touch();close()},colors=ButtonDefaults.textButtonColors(contentColor=Color.White)) {
                    Text("返回",maxLines=1)
                }
                Text(if(a.photos.isEmpty()) "档案" else "${pager.currentPage+1} / ${a.photos.size}",Modifier.weight(1f),color=Color.White,
                    style=MaterialTheme.typography.labelLarge,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                Box {
                    GalleryIcon("更多",Color.White){touch();menu=true}
                    DropdownMenu(menu,{menu=false},shape=RoundedCornerShape(16.dp)) {
                        DropdownMenuItem(text={Text("写档案笔记")},onClick={menu=false;model.noteEditor.open("archive",a.id,a.name)})
                        current?.let{p->DropdownMenuItem(text={Text("写这项媒体的笔记")},onClick={menu=false;model.noteEditor.open("media",p.id,"${a.name} · ${if(p.isVideo) "视频" else "照片"}")})}
                        DropdownMenuItem(text={Text("查看资料")},onClick={menu=false;touch();information=true})
                        discovery?.let { action ->
                            DropdownMenuItem(text={Text(if(action.random) "随机预览 · 已开启" else "固定封面 · 已开启")},onClick={menu=false;action.toggle()})
                            DropdownMenuItem(text={Text("换一轮")},onClick={menu=false;action.refresh()})
                        }
                        if(a.photos.isNotEmpty()) DropdownMenuItem(text={Text("全部照片")},onClick={menu=false;allPhotos()})
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
        AnimatedVisibility(visible=active && controls && !videoPlaying && !hint,modifier=Modifier.align(Alignment.BottomCenter),enter=fadeIn(tween(180)),exit=fadeOut(tween(160))) {
            Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.62f))))
                .navigationBarsPadding().padding(start=16.dp,end=12.dp,top=20.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={touch();information=true},modifier=Modifier.weight(1f).semantics{contentDescription="资料"},
                    colors=ButtonDefaults.textButtonColors(contentColor=Color.White)) {
                    Text(a.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp));FrameGlyph("展开",Color.White)
                }
            }
        }

    }
    editingVideo?.let { video -> VideoEditorDialog(a,video,model){editingVideo=null;touch()} }
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
    if(a.photos.isEmpty()) {
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {Text("还没有照片，可在编辑资料中添加。",Modifier.padding(24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)}
        return
    }
    var choosing by rememberSaveable(a.id){mutableStateOf(false)}
    var chosen by rememberSaveable(a.id){mutableStateOf(listOf<String>())}
    val selected=a.photos.filter{it.id in chosen}
    BackHandler(choosing) {choosing=false;chosen=emptyList()}
    Column(Modifier.fillMaxSize()) {
        FlowRow(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalArrangement=Arrangement.Center,itemVerticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={choosing=!choosing;chosen=emptyList()}){Text(if(choosing)"取消" else "多选")}
            if(choosing) Text("已选 ${selected.size}",style=MaterialTheme.typography.labelMedium)
            if(choosing) TextButton(onClick={chosen=if(selected.size==a.photos.size)emptyList() else a.photos.map{it.id}}){Text(if(selected.size==a.photos.size)"取消全选" else "全选")}
            Spacer(Modifier.weight(1f))
            if(choosing) {
                TextButton(onClick={model.shareMedia(a,selected)},enabled=selected.isNotEmpty()){Text("分享")}
                TextButton(onClick={model.requestExport(a,selected)},enabled=selected.isNotEmpty()){Text("保存 ${selected.size} 项")}
            } else TextButton(onClick={model.requestExport(a,a.photos)}){Text("全部保存")}
        }
        LazyVerticalGrid(GridCells.Fixed(3),Modifier.weight(1f),contentPadding=PaddingValues(8.dp),
            horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            itemsIndexed(a.photos,key={_,p->p.id}) {i,p->
                Box(Modifier.aspectRatio(.75f).clip(RoundedCornerShape(4.dp)).combinedClickable(
                    onClick={if(choosing)chosen=if(p.id in chosen)chosen-p.id else chosen+p.id else select(p.id)},
                    onLongClickLabel="选择这项媒体",onLongClick={choosing=true;chosen=(chosen+p.id).distinct()})
                    .semantics{if(choosing)stateDescription=if(p.id in chosen) "已选择" else "未选择"}) {
                    MediaThumb(p,model,Modifier.fillMaxSize(),"查看第${i+1}项照片或视频")
                    Text("${i+1}",Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha=.5f)).padding(horizontal=6.dp,vertical=2.dp),color=Color.White,fontSize=11.sp)
                    if(choosing) Checkbox(p.id in chosen,{checked->chosen=if(checked)chosen+p.id else chosen-p.id},Modifier.align(Alignment.TopEnd))
                }
            }
        }
    }
}

@Composable fun EditorPhotos(a:Archive,model:AppModel,busy:Boolean) {
    var selected by rememberSaveable(a.id) {mutableStateOf("")}
    val p=a.photos.firstOrNull {it.id==selected}
    Column {
        // Bounded nested lazy grid keeps the editor usable with hundreds of photos.
        LazyVerticalGrid(GridCells.Fixed(3),Modifier.fillMaxWidth().heightIn(min=100.dp,max=300.dp),
            horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            itemsIndexed(a.photos,key={_,photo->photo.id}) {i,photo->
                Box(Modifier.aspectRatio(1f).border(if(selected==photo.id) 3.dp else 0.dp,MaterialTheme.colorScheme.primary)
                    .clickable(enabled=!busy) {selected=photo.id}) {
                    MediaThumb(photo,model,Modifier.fillMaxSize(),"选择第${i+1}项")
                    if(photo.id==a.coverPhoto()?.id) Text("封面",Modifier.background(Color.Black.copy(alpha=.6f)).padding(4.dp),color=Color.White,fontSize=11.sp)
                }
            }
        }
        if(p!=null) {
            val index=a.photos.indexOf(p)
            Text("已选第${index+1}张",fontSize=12.sp,modifier=Modifier.padding(top=8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick={model.change(a.copy(cover=p.id))},enabled=!busy) {Text("设为封面")}
                TextButton(onClick={val photos=a.photos.toMutableList();photos.removeAt(index);photos.add(index-1,p);model.change(a.copy(photos=photos))},enabled=!busy && index>0) {Text("前移")}
                TextButton(onClick={val photos=a.photos.toMutableList();photos.removeAt(index);photos.add(index+1,p);model.change(a.copy(photos=photos))},enabled=!busy && index<a.photos.lastIndex) {Text("后移")}
                TextButton(onClick={model.change(a.copy(photos=a.photos.filter{it.id!=p.id}));selected=""},enabled=!busy) {Text("移除")}
            }
        } else Text("点选照片后可设封面、调整顺序或移除",fontSize=12.sp,modifier=Modifier.padding(top=8.dp))
    }
}
