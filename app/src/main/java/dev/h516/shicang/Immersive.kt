@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.h516.shicang

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
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

@Composable fun FullPhotoFrame(
    a:Archive,model:AppModel,appearance:Appearance,initialPhoto:String,
    position:(String)->Unit, next:(()->Unit)?, previous:(()->Unit)?,
    library:()->Unit, details:()->Unit, allPhotos:()->Unit, settings:()->Unit, edit:()->Unit, create:()->Unit,
    busy:Boolean=false, close:(()->Unit)?=null
) {
    val pager=rememberPagerState(initialPage=photoIndex(a,initialPhoto),pageCount={a.photos.size.coerceAtLeast(1)})
    var controls by rememberSaveable {mutableStateOf(true)}
    var menu by remember {mutableStateOf(false)}
    var information by remember {mutableStateOf(false)}
    var activity by remember {mutableIntStateOf(0)}
    val context=LocalContext.current
    val accessibility=remember {context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager}
    val current=a.photos.getOrNull(pager.currentPage)
    val selectedImageState=key(current?.id ?: a.id) {rememberZoomableImageState()}
    val zoomed=(selectedImageState.zoomableState.zoomFraction ?: 0f)>.001f
    val complete=appearance.skin!="全幅" || appearance.fit=="完整"
    val imageScale=if(complete) ContentScale.Fit else ContentScale.Crop
    val background=if(appearance.skin!="留白") Color.Black else MaterialTheme.colorScheme.background
    LaunchedEffect(pager.settledPage,a.photos) {
        a.photos.getOrNull(pager.settledPage)?.let {position(it.id)}
    }
    LaunchedEffect(controls,menu,information,activity) {
        if(controls && !menu && !information && !accessibility.isTouchExplorationEnabled) {
            delay(4000);controls=false
        }
    }
    val preferences=remember {context.getSharedPreferences("device",0)}
    var hint by remember {mutableStateOf(!preferences.getBoolean("frameHintV2",false))}
    fun touch() {activity++;hint=false;preferences.edit().putBoolean("frameHintV2",true).apply()}
    BackHandler(menu) {menu=false}
    Box(Modifier.fillMaxSize().background(background).archiveSwipe(
        enabled=!zoomed && !menu && !information && (next!=null || previous!=null),
        onNext={touch();next?.invoke()},onPrevious={touch();previous?.invoke()}
    )) {
        val framed=appearance.skin=="留白"
        HorizontalPager(pager,Modifier.fillMaxSize(),
            key={a.photos.getOrNull(it)?.id ?: a.id},userScrollEnabled=!zoomed,
            beyondViewportPageCount=0) { page ->
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
                if(p.isVideo) VideoMedia(p,model,page==pager.currentPage && !menu && !information && !busy,complete){touch();controls=!controls}
                else if(page==pager.currentPage) {
                    ZoomPhoto(p,model,{touch();controls=!controls},selectedImageState,!menu && !information,imageScale)
                } else AsyncImage(model=model.photoFile(p),contentDescription="${a.name} · 第${page+1}张",
                    contentScale=imageScale,modifier=Modifier.fillMaxSize())
                    }
                }
            }
        }
        if(hint) Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp)
            .clickable {touch()},color=Color.Black.copy(alpha=.64f),shape=RoundedCornerShape(16.dp)) {
            Text("轻触显示工具\n左右翻照片与视频 · 上下换档案",Modifier.padding(horizontal=20.dp,vertical=12.dp),color=Color.White,fontSize=13.sp)
        }
        if(controls) {
            val ink=if(a.photos.isNotEmpty() && (appearance.skin=="全幅" || appearance.skin=="暗房")) Color.White else MaterialTheme.colorScheme.onSurface
            Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .then(if(a.photos.isNotEmpty() && appearance.skin!="留白") Modifier.background(Brush.verticalGradient(listOf(Color.Transparent,Color.Black.copy(alpha=.7f)))) else Modifier)
                .navigationBarsPadding().padding(horizontal=22.dp)) {
                Row(Modifier.fillMaxWidth().padding(top=36.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).semantics{contentDescription="资料"}.clickable{touch();details()}.padding(vertical=10.dp)) {
                        Text(a.name,color=ink,fontSize=22.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(if(a.photos.isEmpty()) "私人档案" else "${pager.currentPage+1} / ${a.photos.size}",Modifier.padding(top=5.dp),color=ink.copy(alpha=.7f),fontSize=12.sp)
                    }
                    if(a.photos.isNotEmpty()) GalleryIcon("全部照片",ink){touch();allPhotos()}
                    Box {
                        GalleryIcon("更多",ink){touch();menu=true}
                        DropdownMenu(menu,{menu=false},shape=RoundedCornerShape(16.dp)) {
                            DropdownMenuItem(text={Text("主题与皮肤")},onClick={menu=false;settings()})
                            DropdownMenuItem(text={Text("新建档案")},onClick={menu=false;create()},enabled=!busy)
                            DropdownMenuItem(text={Text("编辑档案")},onClick={menu=false;edit()},enabled=!busy)
                            if(next!=null) {
                                DropdownMenuItem(text={Text("上一份")},onClick={menu=false;touch();previous?.invoke()})
                                DropdownMenuItem(text={Text("下一份")},onClick={menu=false;touch();next()})
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(60.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                    if(close==null) {
                        TextButton(onClick={touch()},colors=ButtonDefaults.textButtonColors(contentColor=ink)){Text("发现",fontSize=15.sp,fontWeight=FontWeight.SemiBold)}
                        TextButton(onClick={touch();library()},colors=ButtonDefaults.textButtonColors(contentColor=ink.copy(alpha=.75f))){Text("我的",fontSize=15.sp)}
                    } else TextButton(onClick={close()},colors=ButtonDefaults.textButtonColors(contentColor=ink)){Text("返回档案")}
                }
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

private fun Modifier.archiveSwipe(enabled:Boolean,onNext:()->Unit,onPrevious:()->Unit)=
    pointerInput(enabled) {
        val threshold=56.dp.toPx()
        awaitEachGesture {
            val first=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
            val start=first.position;var axis=SwipeAxis.Undecided;var delta=Offset.Zero;var cancelled=false
            do {
                val event=awaitPointerEvent(PointerEventPass.Initial)
                if(!enabled || event.changes.count {it.pressed}>1) {cancelled=true;break}
                val change=event.changes.firstOrNull{it.id==first.id} ?: break
                delta=change.position-start
                if(axis==SwipeAxis.Undecided) axis=swipeAxis(delta.x,delta.y,viewConfiguration.touchSlop)
                if(axis==SwipeAxis.Horizontal) break
                if(axis==SwipeAxis.Vertical) event.changes.forEach {it.consume()}
            } while(event.changes.any {it.pressed})
            if(!cancelled && axis==SwipeAxis.Vertical) {
                if(delta.y < -threshold) onNext() else if(delta.y>threshold) onPrevious()
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
        .memoryCacheKey("${model.photoFile(p).path}:$retry").crossfade(120)
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
        Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
            Text("还没有照片，可在编辑资料中添加。",Modifier.padding(24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(GridCells.Fixed(3),Modifier.fillMaxSize(),contentPadding=PaddingValues(8.dp),
        horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
        itemsIndexed(a.photos,key={_,p->p.id}) {i,p->
            Box(Modifier.aspectRatio(.75f).clip(RoundedCornerShape(4.dp)).clickable {select(p.id)}) {
                MediaThumb(p,model,Modifier.fillMaxSize(),"查看第${i+1}项照片或视频")
                Text("${i+1}",Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha=.5f)).padding(horizontal=6.dp,vertical=2.dp),color=Color.White,fontSize=11.sp)
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
