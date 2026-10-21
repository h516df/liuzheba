@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.h516.shicang

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import coil3.compose.AsyncImage
import dev.h516.update.UpdateScreen
import dev.h516.update.Updater
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Gallery chrome stays quiet; photo-derived colors belong only to the viewer.
private val Ink=Color(0xFF25282B)
private val Paper=Color(0xFFF4F0E7)
private val Muted: Color
    @Composable get()=MaterialTheme.colorScheme.onSurfaceVariant
@Composable fun ShicangApp(model:AppModel, unlocked:Boolean, unlock:()->Unit, setLock:(Boolean)->Unit, choosePhotos:(String)->Unit,takePhoto:(String)->Unit,backup:()->Unit,restore:()->Unit,chooseProtection:()->Unit={}) {
    val s by model.state.collectAsState()
    val noteEditing by model.noteEditor.state.collectAsState()
    var notesOpen by rememberSaveable {mutableStateOf(false)}
    val scheme=appearanceScheme(s.appearance)
    val dark=scheme.background.red<.2f
    MaterialTheme(colorScheme=scheme,typography=Typography(
        headlineLarge=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontSize=28.sp,lineHeight=36.sp,fontWeight=FontWeight.Bold),
        headlineMedium=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontSize=24.sp,lineHeight=32.sp,fontWeight=FontWeight.SemiBold),
        titleMedium=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontSize=17.sp,lineHeight=25.sp,fontWeight=FontWeight.SemiBold),
        titleSmall=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontSize=15.sp,lineHeight=22.sp,fontWeight=FontWeight.Medium),
        bodyLarge=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontSize=15.sp,lineHeight=23.sp),
        bodyMedium=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontSize=14.sp,lineHeight=21.sp),
        bodySmall=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontSize=12.sp,lineHeight=18.sp),
        labelLarge=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontSize=14.sp,fontWeight=FontWeight.Medium),
        labelMedium=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontSize=12.sp,lineHeight=18.sp,fontWeight=FontWeight.Medium),
        titleLarge=androidx.compose.ui.text.TextStyle(fontFamily=FontFamily.SansSerif,fontSize=20.sp,fontWeight=FontWeight.Medium)
    )) {
        val libraryState=androidx.compose.runtime.saveable.rememberSaveableStateHolder()
        var lastEditor by rememberSaveable {mutableStateOf("")}
        var editorWasExisting by rememberSaveable {mutableStateOf(false)}
        var route by rememberSaveable{mutableStateOf("library")}
        var detailReturn by rememberSaveable {mutableStateOf("library")}
        var photosReturn by rememberSaveable {mutableStateOf("detail")}
        var settingsReturn by rememberSaveable {mutableStateOf("frame")}
        var appearanceOpen by rememberSaveable {mutableStateOf(false)}
        var settingsPhoto by rememberSaveable {mutableStateOf("")}
        var selected by rememberSaveable{mutableStateOf("")}
        var zoomPhoto by rememberSaveable{mutableStateOf("")}
        val snackbar=remember{SnackbarHostState()}
        var confirm by remember{mutableStateOf<Pair<String,()->Unit>?>(null)}
        var leaveEditorPrompt by remember{mutableStateOf(false)}
        var collectMenu by remember{mutableStateOf(false)}
        fun requestLeaveEditor() {
            if(s.busy)return
            val editor=s.editor ?: return
            if(s.library.active.firstOrNull{it.id==editor.id}==editor)model.discardEditor() else leaveEditorPrompt=true
        }
        if(s.protectionSelection!=null) AlertDialog(
            onDismissRequest=model::dismissProtectionSelection,
            title={Text("找到已有恢复版本")},
            text={Column {
                Text("显示最近 100 个有效版本，旧文件仍保留。请选择要恢复的版本。恢复前会校验全部原件；当前资料保留为可撤销副本。"+if(s.protectionRestoreOnly) "" else "也可继续保护当前资料，不覆盖旧版本。")
                LazyColumn(Modifier.heightIn(max=320.dp)) {items(s.recoveryChoices,key={it.name}) {choice ->
                    TextButton(onClick={confirm="将恢复所选版本的 ${choice.archives} 份档案、${choice.media} 项原件。当前资料会保留为可撤销副本。继续吗？" to {model.recoverProtection(choice)}},enabled=!s.busy) {
                        Text("${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(choice.time))}\n${choice.archives} 份档案 · ${choice.media} 项原件")
                    }
                }}
            }},
            confirmButton={if(!s.protectionRestoreOnly)TextButton(onClick=model::useProtectionFolder,enabled=!s.busy){Text("保护当前资料")}},
            dismissButton={TextButton(onClick=model::dismissProtectionSelection,enabled=!s.busy){Text("取消")}}
        )
        val context=LocalContext.current
        val updater=model.updater
        val owner=LocalLifecycleOwner.current
        DisposableEffect(owner,unlocked,s.ready) {
            val observer=LifecycleEventObserver { _,event ->
                if(event==Lifecycle.Event.ON_RESUME && unlocked && s.ready)updater.onForeground()
                if(event==Lifecycle.Event.ON_PAUSE)updater.onBackground()
            }
            owner.lifecycle.addObserver(observer)
            if(unlocked && s.ready && owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))updater.onForeground()
            onDispose{owner.lifecycle.removeObserver(observer);updater.onBackground()}
        }
        LaunchedEffect(s.message,unlocked,s.editor) {if(unlocked && s.editor==null && s.message.isNotEmpty()){snackbar.showSnackbar(s.message);model.dismissMessage()}}
        LaunchedEffect(route,s.ready) {if(route=="settings" && s.ready) model.refreshSpace()}
        val editing=s.editor!=null
        val compactHeader=androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp<360 || androidx.compose.ui.platform.LocalDensity.current.fontScale>1.4f
        var frameArchive by rememberSaveable {mutableStateOf("")}
        var positions by rememberSaveable {mutableStateOf(hashMapOf<String,String>())}
        val mediaActive=!appearanceOpen && !s.authorsOpen && noteEditing==null && !notesOpen && !s.downloadsOpen && s.exportRequest==null && s.shares.isEmpty() && s.protectionSelection==null && confirm==null
        val immersive=unlocked && s.ready && !editing && (route=="frame" || zoomPhoto.isNotEmpty())
        DisposableEffect(immersive,dark) {
            val window=(context as? android.app.Activity)?.window
            val bars=window?.let {androidx.core.view.WindowCompat.getInsetsController(it,it.decorView)}
            bars?.isAppearanceLightStatusBars=if(immersive) false else !dark
            bars?.isAppearanceLightNavigationBars=if(immersive) false else !dark
            if(immersive) {
                bars?.systemBarsBehavior=androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                bars?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else bars?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            onDispose {bars?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())}
        }
        fun rememberPhoto(archiveId:String,photoId:String) {
            positions=rememberPhotoPosition(positions,archiveId,photoId)
        }

        fun openDetails(id:String) {
            if(route!="detail" && !(route=="photos" && photosReturn=="detail")) detailReturn=route
            selected=id;zoomPhoto="";route="detail"
        }
        fun openPhotos(id:String) {
            if(route!="photos" && !(route=="detail" && detailReturn=="photos")) photosReturn=route
            selected=id;zoomPhoto="";route="photos"
        }
        fun openSettings(photo:String="") {
            settingsReturn=route;settingsPhoto=photo;zoomPhoto="";route="settings"
        }
        fun backPage() {
            when {
                zoomPhoto.isNotEmpty()->zoomPhoto=""
                editing->requestLeaveEditor()
                route=="detail"->route=detailReturn
                route=="photos"->route=photosReturn
                route=="settings"->{route=settingsReturn;zoomPhoto=settingsPhoto;settingsPhoto=""}
                route in listOf("bin","labels","updates","backup")->route="settings"
                else->route="library"
            }
        }
        LaunchedEffect(s.editor,s.busy,s.library) {
            if(s.editor!=null && lastEditor!=s.editor!!.id) {
                lastEditor=s.editor!!.id
                editorWasExisting=s.library.active.any{it.id==lastEditor}
            }
            else if(s.editor==null && lastEditor.isNotEmpty() && !s.busy) {
                val id=lastEditor;lastEditor=""
                if(!editorWasExisting && s.library.drafts.none{it.id==id} && s.library.active.any{it.id==id}) openDetails(id)
            }
        }
        BackHandler(enabled=editing || route!="library" || zoomPhoto.isNotEmpty()) {backPage()}
        Scaffold(containerColor=scheme.background,contentWindowInsets=if(immersive) WindowInsets(0,0,0,0) else ScaffoldDefaults.contentWindowInsets,snackbarHost={if(unlocked) Box(Modifier.padding(bottom=if(immersive) 96.dp else 0.dp)){SnackbarHost(snackbar)}},
            topBar={if(!immersive && !editing && route!="detail") Column(Modifier.statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().heightIn(min=76.dp).padding(horizontal=20.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(editing || route !in listOf("frame","library","settings")) GalleryIcon("返回",scheme.onSurface){backPage()}
                    Column(Modifier.weight(1f)) {Text(if(editing) "编辑档案" else when(route){"frame"->"发现";"library"->"档案";"detail"->"档案详情";"photos"->"照片与视频 · ${s.library.active.firstOrNull{it.id==selected}?.photos?.size ?: 0}";"settings"->"我的";"backup"->"备份与恢复";"bin"->"回收站";"labels"->"标签与分类";"updates"->"检查更新";else->"留着吧"},fontSize=if(route=="library") 27.sp else 20.sp,fontFamily=FontFamily.SansSerif,fontWeight=FontWeight.SemiBold)
                        if(route=="frame" && !editing) Text("留着吧 · 私人档案",fontSize=11.sp,color=Muted)}
                    if(unlocked && s.ready && !editing && route in listOf("frame","library")) {
                        Box {
                            FilledTonalIconButton(onClick={collectMenu=true},modifier=Modifier.size(48.dp).semantics{contentDescription="导入与作者"},colors=IconButtonDefaults.filledTonalIconButtonColors(containerColor=scheme.surface,contentColor=scheme.onSurfaceVariant)){FrameGlyph("保存到相册",scheme.onSurfaceVariant)}
                            DropdownMenu(collectMenu,{collectMenu=false}) {
                                DropdownMenuItem(text={Text("全部笔记")},onClick={collectMenu=false;notesOpen=true})
                                DropdownMenuItem(text={Text("作者主页与作者卡")},onClick={collectMenu=false;model.openAuthors()})
                                DropdownMenuItem(text={Text("从链接下载媒体")},onClick={collectMenu=false;model.openDownloads()})
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        if(!s.busy) Button(onClick={model.edit()},shape=RoundedCornerShape(16.dp),modifier=Modifier.heightIn(min=48.dp).semantics{contentDescription="＋ 新建"},contentPadding=PaddingValues(horizontal=16.dp,vertical=10.dp)){
                            FrameGlyph("新建",scheme.onPrimary);if(!compactHeader){Spacer(Modifier.width(5.dp));Text("新建",style=MaterialTheme.typography.labelLarge)}
                        }
                    }
                }
                if(unlocked && s.ready && !editing && route=="library" && updater.canOfferReminder()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal=20.dp)){
                        Text("开启新版本提醒？仅在前台匿名检查版本，不发送档案，每天至多一次。",style=MaterialTheme.typography.bodySmall,color=scheme.onSurfaceVariant)
                        Row {TextButton(onClick={updater.changeAutoCheckEnabled(true)}){Text("开启提醒")};TextButton(onClick=updater::dismissReminder){Text("以后再说")}}
                    }
                }
                if(unlocked && s.ready && !editing && route=="library" && !updater.promptDismissed) updater.hint()?.let { hint ->
                    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically){
                        TextButton(onClick={route="updates"},modifier=Modifier.weight(1f)){Text(hint,style=MaterialTheme.typography.labelLarge)}
                        TextButton(onClick=updater::dismissPrompt){Text("稍后")}
                    }
                }
                if(unlocked && s.ready && !editing && route=="library" && s.protection.showLibraryNotice) {
                    val needsAttention=s.protection.needsAttention
                    Surface(modifier=Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=4.dp),shape=RoundedCornerShape(12.dp),color=if(needsAttention)scheme.errorContainer else scheme.surface) {
                        TextButton(onClick={route="backup"},modifier=Modifier.fillMaxWidth()) {
                            Text("${s.protection.status} · ${if(needsAttention) "点此处理，原文件请保留" else if(s.protection.location.isEmpty()) "尚未开启外部保护" else "外部保护进行中"}",style=MaterialTheme.typography.labelMedium,color=if(needsAttention)scheme.onErrorContainer else scheme.onSurfaceVariant)
                        }
                    }
                }
                if(s.busy){LinearProgressIndicator(Modifier.fillMaxWidth());Row(verticalAlignment=Alignment.CenterVertically){Text(s.progress,Modifier.weight(1f).padding(horizontal=24.dp,vertical=4.dp),fontSize=12.sp);if(s.progress.contains("备份")||s.progress.contains("恢复")||s.progress.contains("导入")||s.progress.contains("打包")||s.progress.contains("写入")) TextButton(onClick=model::cancelOperation){Text("取消")}}}
             }},bottomBar={if(!editing && zoomPhoto.isEmpty() && route in listOf("frame","library","settings") && unlocked && s.ready) {
                NavigationBar(containerColor=scheme.background,tonalElevation=0.dp) {
                    listOf("frame" to "发现","library" to "档案","settings" to "我的").forEach { (destination,label) ->
                        NavigationBarItem(selected=route==destination,onClick={route=destination;settingsReturn="library";settingsPhoto=""},
                            modifier=Modifier.testTag("nav-$destination").semantics{contentDescription="切换到$label"},icon={FrameGlyph(if(destination=="settings") "设置" else label,scheme.onSurfaceVariant)},label={Text(label)},
                            colors=NavigationBarItemDefaults.colors(indicatorColor=scheme.primaryContainer,selectedTextColor=scheme.primary))
                    }
                }
            }}) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    editing && unlocked->Unit // The full-page editor owns navigation and accessibility while open.
                    zoomPhoto.isNotEmpty() && unlocked->Unit // Save the underlying page before fullscreen changes its viewport; hidden pages must not remeasure or expose semantics.
                    !s.ready->Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally){CircularProgressIndicator();Text(if(s.message.isNotEmpty()) s.message else "正在打开资料",Modifier.padding(16.dp))}
                    !unlocked->Column(Modifier.align(Alignment.Center).padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("你的留着吧，妥善留存",fontSize=23.sp);Text("解锁后查看私人档案",Modifier.padding(20.dp),color=Muted);Button(onClick=unlock){Text("解锁留着吧")}}

                    route=="frame" && !editing && zoomPhoto.isEmpty()->{
                        val active=remember(s.library.archives){s.library.active.filter{it.photos.isNotEmpty()}}
                        val ids=remember(active){active.map{it.id}}
                        if(active.isEmpty()) Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Text("让照片成为主角",fontSize=23.sp);Text("添加照片或视频后，在这里全屏翻看。纯文字档案仍保留在档案页。",Modifier.padding(vertical=16.dp),color=Muted);Button(onClick={route="library"}){Text("去档案添加媒体")}}
                        else libraryState.SaveableStateProvider("discovery") {key(ids) {ArchiveFeed(active,frameArchive,model,s.appearance,positions,::rememberPhoto,
                            {frameArchive=it},{route="library"},::openDetails,::openPhotos,
                            {appearanceOpen=true},{model.edit(it)},{model.edit()},s.busy,enabled=mediaActive)}}
                    }
                    route=="photos"->libraryState.SaveableStateProvider("photos:$selected") {val a=s.library.active.firstOrNull{it.id==selected}
                        if(a!=null) AllPhotos(a,model){rememberPhoto(a.id,it);zoomPhoto=it}
                        else Empty("照片集已不存在","返回档案"){route="library"}}

                    route=="library"->libraryState.SaveableStateProvider("library") {LibraryV19(model,s,{openDetails(it)},{a->selected=a.id;zoomPhoto=positions[a.id]?.takeIf{id->a.photos.any{it.id==id}} ?: a.coverPhoto()?.id.orEmpty()})}
                    route=="detail"->libraryState.SaveableStateProvider("detail:$selected") {val a=s.library.active.firstOrNull{it.id==selected};if(a==null) Empty("档案已移入回收站或不存在","返回档案"){route="library"} else DetailV19(model,s,a,{backPage()},{rememberPhoto(a.id,it);zoomPhoto=it},{openPhotos(a.id)},{confirm="将「${a.name}」移入回收站？可以恢复。" to {model.trash(a);route="library"}},::openDetails,{choosePhotos(a.id)},{AuthorArchiveSection(model,s,a,false);ArchiveNoteSection(model,s.library,a)})}
                    route=="settings"->libraryState.SaveableStateProvider("settings") {Settings(model,s,setLock,{route=it},{title,action->confirm=title to action})}
                    route=="backup"->BackupScreen(model,s,chooseProtection,backup,{confirm="恢复将切换当前档案。成功后保留恢复前副本，失败时原资料继续可用。" to restore},{title,action->confirm=title to action})
                    route=="bin"->Bin(model,s){title,action->confirm=title to action}
                    route=="labels"->LabelsScreen(model,s,confirm={title,action->confirm=title to action})
                    route=="updates"->UpdateScreen(updater,PaddingValues(0.dp))
                }
                if(unlocked && s.ready && !editing && zoomPhoto.isNotEmpty()) {

                    val a=s.library.active.firstOrNull {it.id==selected && it.photos.any {p->p.id==zoomPhoto}}
                        ?: s.library.active.firstOrNull {it.photos.any {p->p.id==zoomPhoto}}
                    if(a!=null) key(a.id,"viewer") {FullPhotoFrame(a,model,s.appearance,zoomPhoto,
                        {rememberPhoto(a.id,it)},null,null,{zoomPhoto="";route="library"},
                        {openDetails(a.id)},{openPhotos(a.id)},
                        {appearanceOpen=true},{model.edit(a)},{model.edit()},s.busy,{zoomPhoto=""},active=mediaActive)}
                    else LaunchedEffect(Unit){zoomPhoto=""}

                }
            }
        }
        if(unlocked && editing) EditorV19(model,s,choosePhotos,takePhoto,{requestLeaveEditor()},{title,action->confirm=title to action},{s.editor?.let{AuthorArchiveSection(model,s,it,true)}})
        if(unlocked && s.ready && s.authorsOpen) AuthorWorkflowUi(model,s)
        if(unlocked && s.ready) LongNotesUi(model,s.library,notesOpen,{notesOpen=false})
        if(unlocked && leaveEditorPrompt) AlertDialog(onDismissRequest={leaveEditorPrompt=false},title={Text("保留这次编辑？")},text={Text("保存草稿后返回，下次可以继续；已保存的档案暂不改变。")},confirmButton={TextButton(onClick={leaveEditorPrompt=false;model.leaveEditor()}){Text("保留草稿并返回")}},dismissButton={Row{TextButton(onClick={leaveEditorPrompt=false}){Text("继续编辑")};TextButton(onClick={leaveEditorPrompt=false;model.discardEditor()}){Text("放弃修改")}}})
        if(unlocked && appearanceOpen) ModalBottomSheet(onDismissRequest={appearanceOpen=false},
            sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=scheme.surface,tonalElevation=0.dp,
            shape=RoundedCornerShape(topStart=26.dp,topEnd=26.dp)) {
            AppearanceV5(model,s,{appearanceOpen=false},{appearanceOpen=false;openSettings(settingsPhotoPosition(s.library,positions,zoomPhoto))})
        }
        if(unlocked && s.ready) MediaCollectionUi(model,s)
        if(unlocked && s.ready && s.shares.isNotEmpty()) AlertDialog(onDismissRequest={if(!s.busy)model.dismissShare()},
            title={Text("拾取分享链接")},text={Column {Text(SharedLinks.parse(s.shares.first())?.let{"${it.platform} · ${it.kind}\n${it.name}"}.orEmpty());Text(if(s.editor!=null) "当前编辑会保留为草稿。新建后可核对名称、类型和链接。" else "自动填写平台、类型、名称与链接，保存前可修改。",Modifier.padding(top=12.dp))}},
            confirmButton={TextButton(onClick={
                if(SharedLinks.parse(s.shares.first())?.kind=="个人主页") {val text=s.shares.first();model.dismissShare();model.openAuthors(input=text);model.importAuthor()}
                else model.importShare()
            },enabled=!s.busy){Text(if(s.busy) "识别中…" else if(SharedLinks.parse(s.shares.first())?.kind=="个人主页") "预览作者资料" else "创建档案草稿")}},
            dismissButton={Row {TextButton(onClick={val text=s.shares.first();model.dismissShare();model.openAuthors(input=text)},enabled=!s.busy){Text("导入作者")};TextButton(onClick={val text=s.shares.first();model.dismissShare();model.openDownloads(text)},enabled=!s.busy){Text("下载媒体")};TextButton(onClick=model::dismissShare,enabled=!s.busy){Text("取消")}}})
        if(unlocked) confirm?.let {(title,action)->AlertDialog(onDismissRequest={confirm=null},title={Text("确认操作")},text={Text(title)},confirmButton={TextButton(onClick={confirm=null;action()}){Text("确认")}},dismissButton={TextButton(onClick={confirm=null}){Text("取消")}})}
    }
}
@Composable private fun Empty(title:String,action:String,onClick:()->Unit) {Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){NavGlyph(false);Text(title,Modifier.padding(vertical=20.dp),fontSize=20.sp);Button(onClick=onClick){Text(action)}}}
@Composable private fun NavGlyph(grid:Boolean) {
    val color=MaterialTheme.colorScheme.primary
    Canvas(Modifier.size(24.dp)) {
        val stroke=Stroke(1.6.dp.toPx())
        if(grid) for(row in 0..1) for(col in 0..1) drawRoundRect(color,Offset((3+col*11).dp.toPx(),(3+row*11).dp.toPx()),Size(7.dp.toPx(),7.dp.toPx()),androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),style=stroke)
        else {drawRoundRect(color,Offset(3.dp.toPx(),3.dp.toPx()),Size(18.dp.toPx(),18.dp.toPx()),androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),style=stroke);drawRect(color,Offset(7.dp.toPx(),7.dp.toPx()),Size(10.dp.toPx(),10.dp.toPx()),style=stroke)}
    }
}
@Composable internal fun NameCard(a:Archive,modifier:Modifier=Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.background),contentAlignment=Alignment.Center) {
        Column(Modifier.padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Text(a.name,fontSize=32.sp,fontWeight=FontWeight.Normal,maxLines=3,overflow=TextOverflow.Ellipsis)
            Text("私人档案",Modifier.padding(top=12.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun Cover(a:Archive,model:AppModel,modifier:Modifier=Modifier) {
    val p=a.coverPhoto()
    if(p==null) NameCard(a,modifier) else {
        var failed by remember(p.id){mutableStateOf(false)}
        var attempt by remember(p.id){mutableIntStateOf(0)}
        val context=LocalContext.current
        val file=model.photoFile(p)
        val request=remember(file,attempt){coil3.request.ImageRequest.Builder(context).data(file).memoryCacheKey("${file.path}:$attempt").build()}
        Box(modifier.background(MaterialTheme.colorScheme.surface)) {
            if(failed) NameCard(a,Modifier.fillMaxSize())
            AsyncImage(model=request,contentDescription="${a.name}的封面",contentScale=ContentScale.Crop,modifier=Modifier.fillMaxSize(),onError={failed=true},onSuccess={failed=false})
            if(failed) TextButton(onClick={attempt++;failed=false},modifier=Modifier.align(Alignment.BottomCenter).background(MaterialTheme.colorScheme.surface)){Text("照片读取失败 · 重试",fontSize=11.sp)}
        }
    }
}
@Composable private fun Tag(text:String) {Surface(shape=RoundedCornerShape(50),color=MaterialTheme.colorScheme.primary.copy(alpha=.09f)){Text(text,Modifier.padding(horizontal=12.dp,vertical=5.dp),fontSize=11.sp,color=MaterialTheme.colorScheme.primary)}}
@Composable private fun LibraryScreen(model:AppModel,s:AppState,open:(String)->Unit) {
    var query by rememberSaveable{mutableStateOf("")}
    var search by rememberSaveable{mutableStateOf(false)}
    var category by rememberSaveable{mutableStateOf("")};var tag by rememberSaveable{mutableStateOf("")};var sortName by rememberSaveable{mutableStateOf(false)}
    var results by remember{mutableStateOf(s.library.active)}
    LaunchedEffect(query,category,tag,s.library,sortName){results=withContext(Dispatchers.Default){val found=s.library.find(query,category,tag);if(sortName) found.sortedBy{it.name} else found.sortedByDescending{it.updated}}}
    val heroId=results.firstOrNull{it.photos.isNotEmpty()}?.id
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=22.dp),contentPadding=PaddingValues(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment=Alignment.CenterVertically){Text("${results.size} 份收藏",Modifier.weight(1f),fontSize=12.sp,color=Muted);TextButton(onClick={search=!search;if(!search) query=""}){Text(if(search) "收起搜索" else "搜索",fontSize=12.sp)};TextButton(onClick={sortName=!sortName}){Text(if(sortName) "按名称" else "最近添加",fontSize=12.sp)}}
            if(search) QuietInput(query,{query=it},"搜索称呼、标签或资料",Modifier.fillMaxWidth())
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                val categories=listOf(Label("","category","全部"))+s.library.labels.filter{it.kind=="category"}
                categories.forEach{l->Column(Modifier.clickable{category=l.id}.padding(vertical=10.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(l.name,fontSize=14.sp,color=if(category==l.id) MaterialTheme.colorScheme.onSurface else Muted);Spacer(Modifier.height(8.dp));Box(Modifier.height(2.dp).width(18.dp).background(if(category==l.id) MaterialTheme.colorScheme.onSurface else Color.Transparent))}}
            }
            if(s.library.labels.any{it.kind=="tag"}) Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){TextButton(onClick={tag=""}){Text("全部标签",fontSize=11.sp)};s.library.labels.filter{it.kind=="tag"}.forEach{l->FilterChip(tag==l.id,{tag=if(tag==l.id) "" else l.id},label={Text(l.name,fontSize=11.sp)})}}
        }
        if(s.library.drafts.isNotEmpty()) item{Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){s.library.drafts.forEach{draft->TextButton(onClick={model.edit(draft)},enabled=!s.busy){Text("继续草稿 · ${draft.name.ifBlank{"未命名"}}",fontSize=12.sp)}}}}
        if(results.isEmpty()) item{Column(Modifier.fillMaxWidth().padding(vertical=40.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(if(query.isBlank() && category.isBlank() && tag.isBlank()) "这里还没有收藏" else "没有匹配的档案",color=Muted);TextButton(onClick={if(query.isNotBlank() || category.isNotBlank() || tag.isNotBlank()){query="";category="";tag=""}else model.edit()}){Text(if(query.isNotBlank() || category.isNotBlank() || tag.isNotBlank()) "清除筛选" else "新建档案")}}}
        items(results,key={it.id}) {a->
            val metadata=(listOf(s.library.labelName(a.category))+a.fields.filter{it.value.isNotBlank()}.take(2).map{it.value}).filter{it.isNotBlank()}.joinToString(" · ")
            if(a.id==heroId) Column(Modifier.fillMaxWidth().clickable{open(a.id)}) {
                Cover(a,model,Modifier.fillMaxWidth().aspectRatio(1.22f).clip(RoundedCornerShape(16.dp)))
                Row(Modifier.fillMaxWidth().padding(top=12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(a.name,fontSize=19.sp,maxLines=2,overflow=TextOverflow.Ellipsis);if(metadata.isNotBlank()) Text(metadata,Modifier.padding(top=4.dp),fontSize=11.sp,color=Muted,maxLines=1,overflow=TextOverflow.Ellipsis)};Text("${a.photos.size} 张",fontSize=11.sp,color=Muted)}
            } else Column {
                Row(Modifier.fillMaxWidth().clickable{open(a.id)}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(a.photos.isNotEmpty()){Cover(a,model,Modifier.width(70.dp).height(82.dp).clip(RoundedCornerShape(10.dp)));Spacer(Modifier.width(14.dp))}
                    Column(Modifier.weight(1f)){Text(a.name,fontSize=17.sp,maxLines=2,overflow=TextOverflow.Ellipsis);Text(metadata.ifBlank{"私人档案"},Modifier.padding(top=6.dp),fontSize=12.sp,color=Muted,maxLines=2,overflow=TextOverflow.Ellipsis)}
                    Text(if(a.photos.isEmpty()) "资料 ›" else "${a.photos.size} 张 ›",Modifier.padding(start=8.dp),fontSize=11.sp,color=Muted)
                }
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.45f))
            }
        }
    }
}
@Composable private fun Detail(model:AppModel,s:AppState,a:Archive,zoom:(String)->Unit,allPhotos:()->Unit,delete:()->Unit) {
    val context=LocalContext.current
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp),contentPadding=PaddingValues(bottom=32.dp)) {
        item {Text(a.name,fontSize=24.sp,fontWeight=FontWeight.Medium);Row(Modifier.horizontalScroll(rememberScrollState()).padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){(listOf(a.category)+a.tags).filter{it.isNotEmpty()}.forEach{Tag(s.library.labelName(it))}}}
        item {Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){Button(onClick={model.edit(a)},enabled=!s.busy){Text("编辑资料")};OutlinedButton(onClick=delete,enabled=!s.busy){Text("移入回收站")}}}
        if(a.photos.isEmpty()) item {Text("还没有照片，资料已完整保存。可以随时编辑并添加照片。",color=Muted,fontSize=13.sp)}
        else {item {TextButton(onClick=allPhotos){Text("照片集 · ${a.photos.size} · 全部照片")};Box(Modifier.height(260.dp)){AllPhotos(a,model,zoom)}}}
        item {Text("自定义信息",fontSize=16.sp,fontWeight=FontWeight.Medium)}
        if(a.fields.isEmpty()) item {Text("还没有补充信息",color=Muted)}
        items(a.fields,key={it.id}){f->Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface){Column(Modifier.fillMaxWidth().padding(16.dp)){Text(f.name,fontSize=12.sp,color=Muted);Text(f.value.ifBlank{"未填写"},Modifier.padding(top=5.dp),fontSize=16.sp);if(f.value.startsWith("https://")||f.value.startsWith("http://")) TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(f.value)).addCategory(Intent.CATEGORY_BROWSABLE))}.onFailure{model.message("没有可打开此链接的应用")}}){Text("打开链接")}}}}
    }
}
@Composable private fun Settings(model:AppModel,s:AppState,setLock:(Boolean)->Unit,navigate:(String)->Unit,confirm:(String,()->Unit)->Unit) {
    val c=MaterialTheme.colorScheme
    var appearance by rememberSaveable{mutableStateOf(false)}
    var storage by rememberSaveable{mutableStateOf(false)}
    var templates by rememberSaveable{mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=12.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        item {
            Text("整理与外观",Modifier.padding(start=4.dp,bottom=10.dp),style=MaterialTheme.typography.titleSmall,color=c.onSurfaceVariant)
            Column(Modifier.clip(RoundedCornerShape(20.dp)).background(c.surface).animateContentSize(tween(220)).padding(horizontal=16.dp)) {
                SettingButton("外观","${s.appearance.theme} · ${s.appearance.skin}"){appearance=!appearance}
                AnimatedVisibility(appearance){AppearanceV5(model,s,{}, {},showActions=false)}
                SettingButton("标签与分类"," "){navigate("labels")}
                SettingButton("回收站","${s.library.archives.count{it.deleted!=0L}+s.library.photoTrash.size} 项"){navigate("bin")}
            }
        }
        item {
            Text("资料管理",Modifier.padding(start=4.dp,bottom=10.dp),style=MaterialTheme.typography.titleSmall,color=c.onSurfaceVariant)
            Column(Modifier.clip(RoundedCornerShape(20.dp)).background(c.surface).animateContentSize(tween(220)).padding(horizontal=16.dp)) {
                SettingButton("备份与恢复",if(s.protection.needsAttention) "${s.protection.status} · 请处理" else if(s.protection.location.isEmpty()) "未开启自动保护" else "自动保护已开启"){navigate("backup")}
                SettingButton("存储空间",s.space.values.sum().let{"%.1f MB".format(it/1048576.0)}){storage=!storage}
                AnimatedVisibility(storage){Column(Modifier.padding(vertical=12.dp)) {
                    s.space.forEach{(name,size)->Row(Modifier.fillMaxWidth().padding(vertical=6.dp)){Text(name,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium);Text("%.1f MB".format(size/1048576.0),color=Muted,style=MaterialTheme.typography.bodyMedium)}}
                    TextButton(onClick={model.clearCache();coil3.SingletonImageLoader.get(model.getApplication()).memoryCache?.clear()},enabled=!s.busy){Text("只清理图片缓存")}
                }}
                if(s.library.templates.isNotEmpty()) {
                    SettingButton("字段模板","${s.library.templates.size} 个"){templates=!templates}
                    AnimatedVisibility(templates) {
                        Column {
                            s.library.templates.forEach { t ->
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Text(t.name,Modifier.weight(1f))
                                    TextButton(onClick={confirm("删除模板「${t.name}」？已建档案及字段保留。",{model.deleteTemplate(t)})},enabled=!s.busy) {Text("删除")}
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Text("隐私",Modifier.padding(start=4.dp,bottom=10.dp),style=MaterialTheme.typography.titleSmall,color=c.onSurfaceVariant)
            Column(Modifier.clip(RoundedCornerShape(20.dp)).background(c.surface).padding(horizontal=16.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min=68.dp),verticalAlignment=Alignment.CenterVertically){Text("应用锁",Modifier.weight(1f));Switch(checked=model.store.isLocked,onCheckedChange=setLock,enabled=!s.busy)}
                HorizontalDivider(color=c.outlineVariant.copy(alpha=.35f))
                RecentTasksPreference()
            }
        }
        item {GrowthEntry(collectionGrowth(s.library))}
        item {SettingButton("关于与检查更新",model.updater.hint() ?: "留着吧 v${BuildConfig.VERSION_NAME}"){navigate("updates")}}
    }
}
/** A single place for continuous local protection and portable snapshots, without changing either engine. */
@Composable internal fun BackupScreen(model:AppModel,s:AppState,chooseProtection:()->Unit,backup:()->Unit,restore:()->Unit,confirm:(String,()->Unit)->Unit) {
    var recoveryOptions by rememberSaveable {mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize().testTag("backup-screen"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {Text("自动保存到本机目录，或导出备份用于换机。",style=MaterialTheme.typography.bodyMedium)}
        item {ProtectionPanel(s.protection,s.busy,{confirm(PROTECTION_SETUP_NOTICE,{if(model.prepareProtectionSelection(false))chooseProtection()})},model::retryProtection,{confirm("请确认：你已核对导入失败或中断的原文件，知道这些原文件不在当前保护范围内，并会继续保留或重新导入。",model::acknowledgeImportWarning)})}
        item {
            SettingButton("导出备份文件（换机或留存）",if(model.store.lastBackup()>0) "最近导出：${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(model.store.lastBackup()))}" else "保存一份完整资料副本",!s.busy) {
                confirm("备份文件未加密，包含完整档案、笔记、原始照片与视频、草稿和回收站。原始媒体可能保留拍摄位置。\n\n应用锁不会保护导出的备份。请保存到可信位置，勿公开上传或随意转发。同一手机损坏或丢失时，备份也可能一起丢失。\n\n单个完整备份的展开总量最多20GiB（不是媒体库总容量），需要足够临时空间。超过时不会导出不完整备份。\n\n确认后先检查容量，再选择保存位置；取消不创建备份。",backup)
            }
            SettingButton("恢复已有资料","从保护目录或备份文件找回",!s.busy){recoveryOptions=true}
            SettingButton("撤销最近一次恢复",if(model.store.canUndoRestore()) "切回恢复前的资料" else "暂无可撤销的恢复",!s.busy && model.store.canUndoRestore()) {confirm("切回最近一次恢复前的资料？当前恢复结果会被替换，请先保存需要保留的新内容。",model::undoRestore)}
        }
    }
    if(recoveryOptions)AlertDialog(onDismissRequest={recoveryOptions=false},title={Text("从哪里恢复？")},text={Column {
        TextButton(onClick={recoveryOptions=false;confirm(PROTECTION_RECOVERY_NOTICE,{if(model.prepareProtectionSelection(true))chooseProtection()})}){Text("选择原保护目录")}
        TextButton(onClick={recoveryOptions=false;restore()}){Text("选择备份文件")}
    }},confirmButton={TextButton(onClick={recoveryOptions=false}){Text("取消")}})
}
@Composable private fun Choice(title:String,choices:List<String>,selected:String,onSelect:(String)->Unit) {Column {Text(title,fontSize=12.sp,color=Muted);Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){choices.forEach{FilterChip(selected=it==selected,onClick={onSelect(it)},label={Text(it)})}}}}
@Composable private fun SettingButton(title:String,subtitle:String,enabled:Boolean=true,onClick:()->Unit) {
    Column(Modifier.fillMaxWidth().clickable(enabled=enabled,onClick=onClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min=60.dp).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end=16.dp)) {
                Text(title,fontSize=15.sp,fontWeight=FontWeight.Normal,color=if(enabled) MaterialTheme.colorScheme.onSurface else Muted)
                if(subtitle.isNotBlank()) Text(subtitle,Modifier.padding(top=5.dp),fontSize=12.sp,color=Muted)
            }
            if(enabled) FrameGlyph("展开",Muted)
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.55f))
    }
}
@Composable private fun Bin(model:AppModel,s:AppState,confirm:(String,()->Unit)->Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
        item {Text("回收站不会自动过期。恢复保留资料和照片顺序。",fontSize=12.sp,color=Muted)}
        items(s.library.archives.filter{it.deleted!=0L},key={it.id}){a->Row(verticalAlignment=Alignment.CenterVertically){Text(a.name,Modifier.weight(1f));TextButton(onClick={model.restore(a)},enabled=!s.busy){Text("恢复档案")}}}
        items(s.library.photoTrash,key={it.id}){t->Row(verticalAlignment=Alignment.CenterVertically){MediaThumb(t.photo,model,Modifier.size(60.dp),"回收站照片或视频");Text(s.library.archives.firstOrNull{it.id==t.archiveId}?.name ?: "原档案已删除",Modifier.weight(1f).padding(8.dp));TextButton(onClick={model.restorePhoto(t)},enabled=!s.busy){Text("恢复媒体")}}}
        item {OutlinedButton(onClick={confirm("永久清理回收站及不再引用的照片？这一步不能撤销，手机相册原文件保留。",model::purge)},enabled=!s.busy){Text("永久清理回收站")}}
    }
}
@Composable internal fun LabelsScreen(model:AppModel,s:AppState,confirm:(String,()->Unit)->Unit,initialKind:String="tag") {
    var editing by remember{mutableStateOf<Label?>(null)}
    var name by remember{mutableStateOf("")}
    var creating by remember{mutableStateOf(false)}
    var kind by rememberSaveable{mutableStateOf(initialKind)}
    var query by rememberSaveable{mutableStateOf("")}
    val c=MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp)) {
        Text("标签可多选；分类是可选的单一归档。",Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(kind=="tag",{kind="tag";query=""},label={Text("标签")})
            FilterChip(kind=="category",{kind="category";query=""},label={Text("分类")})
            Spacer(Modifier.weight(1f))
            TextButton(onClick={name="";creating=true},enabled=!s.busy){Text("新建")}
        }
        QuietInput(query,{query=it},"搜索${if(kind=="category") "分类" else "标签"}",Modifier.fillMaxWidth())
        val matching=s.library.labels.filter{it.kind==kind && it.name.contains(query,true)}
        if(matching.isEmpty()) Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){Text(if(query.isBlank()) "还没有${if(kind=="category") "分类" else "标签"}" else "没有找到匹配项",color=c.onSurfaceVariant)}
        else LazyColumn(contentPadding=PaddingValues(top=12.dp,bottom=24.dp)) {
            items(matching,key={it.id}){label->
                var menu by remember{mutableStateOf(false)}
                Row(Modifier.fillMaxWidth().clickable(enabled=!s.busy){editing=label;name=label.name}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(label.name,style=MaterialTheme.typography.bodyLarge)
                        val count=s.library.active.count{if(label.kind=="category") it.category==label.id else label.id in it.tags}
                        Text("$count 份档案",Modifier.padding(top=4.dp),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
                    }
                    Box {
                        TextButton(onClick={menu=true},enabled=!s.busy){Text("管理")}
                        DropdownMenu(menu,{menu=false}) {
                            DropdownMenuItem(text={Text("改名")},onClick={menu=false;editing=label;name=label.name})
                            DropdownMenuItem(text={Text("全局删除${if(label.kind=="category") "分类" else "标签"}",color=c.error)},onClick={
                                menu=false
                                val archives=s.library.archives.count{if(label.kind=="category") it.category==label.id else label.id in it.tags}
                                val currentDrafts=s.library.drafts.filterNot{it.id==s.editor?.id}+listOfNotNull(s.editor)
                                val drafts=currentDrafts.count{if(label.kind=="category") it.category==label.id else label.id in it.tags}
                                confirm("删除「${label.name}」？将从 $archives 份档案（含回收站）和 $drafts 份草稿移除关联，无法自动恢复这些关联。档案、照片和视频不会删除。若只想移出当前档案，请返回编辑页取消勾选。",{model.deleteLabel(label)})
                            })
                        }
                    }
                }
                HorizontalDivider(color=c.outlineVariant.copy(alpha=.4f))
            }
        }
    }
    if(creating || editing!=null) AlertDialog(onDismissRequest={creating=false;editing=null},title={Text(if(creating) "新建${if(kind=="category") "分类" else "标签"}" else "修改名称")},text={OutlinedTextField(name,{name=it},singleLine=true,label={Text("名称")})},confirmButton={TextButton(onClick={if(creating)model.label(kind,name,false)else editing?.let{model.renameLabel(it,name)};creating=false;editing=null},enabled=name.isNotBlank()&&!s.busy){Text("保存")}},dismissButton={TextButton(onClick={creating=false;editing=null}){Text("取消")}})
}
