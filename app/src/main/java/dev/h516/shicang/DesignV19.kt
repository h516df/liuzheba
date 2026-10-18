@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.h516.shicang

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun PageBar(title:String,back:()->Unit,action:@Composable RowScope.()->Unit={}) {
    val c=MaterialTheme.colorScheme
    Column {
        Box(Modifier.fillMaxWidth().height(64.dp).padding(horizontal=12.dp)) {
            Text(title,Modifier.align(Alignment.Center),style=MaterialTheme.typography.titleSmall,color=c.onSurface)
            Row(Modifier.fillMaxSize(),verticalAlignment=Alignment.CenterVertically) {
                GalleryIcon("返回",c.onSurface,back)
                Spacer(Modifier.weight(1f));action()
            }
        }
        HorizontalDivider(color=c.outlineVariant.copy(alpha=.35f))
    }
}

@Composable private fun SectionTitle(title:String,action:(@Composable RowScope.()->Unit)?=null) {
    Row(Modifier.fillMaxWidth().heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
        action?.invoke(this)
    }
}

@Composable internal fun IdentityCover(a:Archive,model:AppModel,modifier:Modifier=Modifier) {
    val c=MaterialTheme.colorScheme
    val p=a.coverPhoto()
    if(p!=null) MediaThumb(p,model,modifier.clip(RoundedCornerShape(12.dp)),"${a.name}的封面")
    else Box(modifier.clip(RoundedCornerShape(16.dp)).background(c.surfaceVariant),contentAlignment=Alignment.Center){Text(a.name.take(1).ifBlank{"藏"},fontSize=26.sp,color=c.onSurfaceVariant)}
}

@Composable internal fun LibraryV19(model:AppModel,s:AppState,open:(String)->Unit,view:(Archive)->Unit) {
    var deleteTarget by remember {mutableStateOf<Archive?>(null)}
    var query by rememberSaveable{mutableStateOf("")}
    var category by rememberSaveable{mutableStateOf("")}
    var tags by rememberSaveable{mutableStateOf(listOf<String>())}
    var byName by rememberSaveable{mutableStateOf(false)}
    var filters by rememberSaveable{mutableStateOf(false)}
    val prefs=LocalContext.current.getSharedPreferences("device",0)
    var covers by rememberSaveable{mutableStateOf(prefs.getBoolean("libraryCovers",false))}
    var results by remember{mutableStateOf(emptyList<Archive>())}
    val c=MaterialTheme.colorScheme
    LaunchedEffect(s.library,query,category,tags,byName){results=withContext(Dispatchers.Default){s.library.find(query,category).filter{a->tags.all{it in a.tags}}.let{if(byName) it.sortedBy{a->a.name} else it.sortedByDescending{a->a.updated}}}}
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp)) {
        GrowthEntry(collectionGrowth(s.library))
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            TextField(query,{query=it},Modifier.weight(1f),singleLine=true,
                placeholder={Text("搜索名称、标签、资料",style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis)},
                leadingIcon={FrameGlyph("搜索",c.onSurfaceVariant)},shape=RoundedCornerShape(16.dp),
                textStyle=MaterialTheme.typography.bodyLarge,
                colors=TextFieldDefaults.colors(unfocusedContainerColor=c.surface,focusedContainerColor=c.surface,unfocusedIndicatorColor=Color.Transparent,focusedIndicatorColor=Color.Transparent))
            FilledTonalButton(onClick={filters=true},shape=RoundedCornerShape(16.dp),modifier=Modifier.heightIn(min=56.dp),contentPadding=PaddingValues(horizontal=14.dp)) {
                FrameGlyph("筛选",c.onPrimaryContainer);Spacer(Modifier.width(6.dp))
                Text(if(category.isNotBlank()||tags.isNotEmpty()) "筛选 · ${tags.size+if(category.isNotBlank()) 1 else 0}" else "筛选",style=MaterialTheme.typography.labelMedium)
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically) {
            Text("${results.size} 份档案",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
            TextButton(onClick={byName=!byName},contentPadding=PaddingValues(horizontal=10.dp)){
                Text(if(byName) "按名称" else "最近更新",style=MaterialTheme.typography.labelMedium,color=c.onSurfaceVariant)
            }
            TextButton(onClick={covers=!covers;prefs.edit().putBoolean("libraryCovers",covers).apply()},contentPadding=PaddingValues(horizontal=10.dp)){
                Text(if(covers) "列表" else "封面",style=MaterialTheme.typography.labelMedium)
            }
        }
        if(s.library.drafts.isNotEmpty()) LazyRow(contentPadding=PaddingValues(bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(s.library.drafts,key={it.id}){a->SuggestionChip(onClick={model.edit(a)},enabled=!s.busy,label={Text("继续草稿 · ${a.name.ifBlank{"未命名"}}")})}
        }
        if(results.isEmpty()) Column(Modifier.fillMaxWidth().weight(1f),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
            Surface(shape=RoundedCornerShape(24.dp),color=c.primaryContainer){Box(Modifier.size(72.dp),contentAlignment=Alignment.Center){FrameGlyph("我的",c.onPrimaryContainer)}}
            Text(if(s.library.active.isEmpty()) "把值得记住的，留在这里" else "没有匹配的档案",Modifier.padding(top=20.dp,bottom=6.dp),style=MaterialTheme.typography.titleMedium)
            TextButton(onClick={if(query.isNotBlank()||category.isNotBlank()||tags.isNotEmpty()){query="";category="";tags=emptyList()}else model.edit()}){Text(if(s.library.active.isEmpty()) "新建第一份档案" else "清除搜索与筛选")}
        } else if(covers) LazyVerticalGrid(GridCells.Adaptive(148.dp),verticalArrangement=Arrangement.spacedBy(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
            items(results,key={it.id}){a->
                Column(Modifier.animateItem().clickable{open(a.id)}) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(c.primaryContainer).clickable{if(a.photos.isNotEmpty()) view(a) else open(a.id)},contentAlignment=Alignment.Center) {
                        if(a.photos.isNotEmpty()) IdentityCover(a,model,Modifier.fillMaxSize())
                        else Text(a.name.take(1),style=MaterialTheme.typography.headlineLarge,color=c.onPrimaryContainer)
                    }
                    Text(a.name,Modifier.fillMaxWidth().padding(top=12.dp,bottom=4.dp),style=MaterialTheme.typography.titleMedium,color=c.onSurface,minLines=2,maxLines=2,overflow=TextOverflow.Ellipsis)
                    val categoryName=s.library.labelName(a.category)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(listOfNotNull(categoryName.takeIf{it.isNotBlank()},if(a.photos.isNotEmpty()) "${a.photos.size} 项" else null).joinToString(" · ").ifBlank{"私人档案"},Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                        TextButton(onClick={open(a.id)},modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=4.dp)){
                            Text("资料",style=MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.width(2.dp));FrameGlyph("展开",c.primary)
                        }
                    }
                }
            }
        } else LazyColumn(contentPadding=PaddingValues(bottom=24.dp)) {
            items(results,key={it.id}){a->
                val swipe=rememberSwipeToDismissBoxState(confirmValueChange={value->if(value==SwipeToDismissBoxValue.EndToStart) deleteTarget=a;false})
                SwipeToDismissBox(state=swipe,modifier=Modifier.animateItem(),enableDismissFromStartToEnd=false,gesturesEnabled=!s.busy,
                    backgroundContent={Box(Modifier.fillMaxSize().background(c.errorContainer).padding(20.dp),contentAlignment=Alignment.CenterEnd){Text("移入回收站",color=c.onErrorContainer)}}) {
                Column(Modifier.background(c.background)) {
                    Row(Modifier.fillMaxWidth().clickable{if(a.photos.isNotEmpty()) view(a) else open(a.id)}.padding(top=16.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically) {
                        if(a.photos.isEmpty()) Surface(shape=RoundedCornerShape(16.dp),color=c.primaryContainer){Box(Modifier.size(60.dp),contentAlignment=Alignment.Center){Text(a.name.take(1),style=MaterialTheme.typography.headlineMedium,color=c.onPrimaryContainer)}}
                        else IdentityCover(a,model,Modifier.size(72.dp))
                        Column(Modifier.weight(1f).padding(start=14.dp,end=4.dp)) {
                            Text(a.name,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                            val labels=(listOf(a.category)+a.tags).map(s.library::labelName).filter{it.isNotBlank()}
                            if(labels.isNotEmpty()) Text(labels.take(3).joinToString(" · "),Modifier.padding(top=5.dp),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                            a.fields.firstOrNull{it.value.isNotBlank() && Links.normalize(it.value)==null}?.let{Text(it.value,Modifier.padding(top=5.dp),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)}
                            if(a.photos.isNotEmpty()) Text("${a.photos.size} 项照片与视频",Modifier.padding(top=5.dp),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
                        }
                        TextButton(onClick={open(a.id)},contentPadding=PaddingValues(horizontal=10.dp),modifier=Modifier.heightIn(min=48.dp)){Text("资料",style=MaterialTheme.typography.labelMedium)}
                    }
                    HorizontalDivider(color=c.outlineVariant.copy(alpha=.45f))
                }
                }
            }
        }
    }
    deleteTarget?.let{a->AlertDialog(onDismissRequest={deleteTarget=null},title={Text("移入回收站？")},text={Text("「${a.name}」的资料和媒体可以从回收站恢复。")},confirmButton={TextButton(onClick={deleteTarget=null;model.trash(a)}){Text("移入回收站")}},dismissButton={TextButton(onClick={deleteTarget=null}){Text("取消")}})}
    if(filters) LabelPicker(s.library,category,tags,{category=it.first;tags=it.second;filters=false},{filters=false},"筛选档案")
}

@Composable internal fun DetailV19(model:AppModel,s:AppState,a:Archive,back:()->Unit,zoom:(String)->Unit,album:()->Unit,delete:()->Unit) {
    val c=MaterialTheme.colorScheme
    val context=LocalContext.current
    Column(Modifier.fillMaxSize()) {
        PageBar("档案",back){FilledTonalButton(onClick={model.edit(a)},enabled=!s.busy,shape=RoundedCornerShape(14.dp),modifier=Modifier.heightIn(min=48.dp)){Text("编辑")}}
        LazyColumn(contentPadding=PaddingValues(start=20.dp,end=20.dp,top=20.dp,bottom=32.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end=16.dp)) {
                        Text(a.name,style=MaterialTheme.typography.headlineLarge)
                        val labels=(listOf(a.category)+a.tags).map(s.library::labelName).filter{it.isNotBlank()}
                        if(labels.isNotEmpty()) Text(labels.joinToString(" · "),Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodyMedium,color=c.onSurfaceVariant)
                    }
                    IdentityCover(a,model,Modifier.width(72.dp).height(if(a.photos.isEmpty()) 72.dp else 88.dp))
                }
                Spacer(Modifier.height(16.dp))
            }
            if(a.photos.isNotEmpty()) item {
                SectionTitle("照片与视频"){TextButton(onClick=album){Text("查看全部",style=MaterialTheme.typography.labelMedium)}}
                val photoCount=a.photos.count{!it.isVideo};val videoCount=a.photos.count{it.isVideo}
                Text(listOfNotNull(if(photoCount>0) "$photoCount 张照片" else null,if(videoCount>0) "$videoCount 个视频" else null).joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,modifier=Modifier.padding(bottom=12.dp))
                LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){items(a.photos.take(6),key={it.id}){p->MediaThumb(p,model,Modifier.width(100.dp).height(128.dp).clip(RoundedCornerShape(16.dp)).clickable{zoom(p.id)},"查看${if(p.isVideo) "视频" else "照片"}")}}
                Spacer(Modifier.height(18.dp))
            }
            item{SectionTitle("资料")}
            if(a.fields.isEmpty()) item{Text("还没有补充资料",Modifier.padding(vertical=18.dp),color=c.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)}
            items(a.fields,key={it.id}){f->
                val url=if(f.type=="链接" || f.value.trim().startsWith("http")) Links.normalize(f.value) else null
                val first=a.fields.first().id==f.id;val last=a.fields.last().id==f.id
                Surface(color=c.surface,shape=RoundedCornerShape(topStart=if(first) 16.dp else 0.dp,topEnd=if(first) 16.dp else 0.dp,bottomStart=if(last) 16.dp else 0.dp,bottomEnd=if(last) 16.dp else 0.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).then(if(url!=null) Modifier.clickable{openLink(context,url,model)} else Modifier)) {
                                Text(f.name,style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
                                Text(if(url!=null) Links.title(url) else f.value.ifBlank{"未填写"},Modifier.padding(top=5.dp),style=MaterialTheme.typography.bodyLarge,color=if(url!=null) c.primary else c.onSurface)
                                if(url!=null) Text(Links.host(url),Modifier.padding(top=3.dp),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                            }
                            if(url!=null) {
                                IconButton(onClick={openLink(context,url,model)},modifier=Modifier.size(48.dp).semantics{contentDescription="打开链接"}){FrameGlyph("打开链接",c.primary)}
                                TextButton(onClick={(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText(f.name,url));model.message("链接已复制")},contentPadding=PaddingValues(6.dp),modifier=Modifier.heightIn(min=48.dp)){Text("复制",style=MaterialTheme.typography.labelMedium)}
                            }
                        }
                        if(!last) HorizontalDivider(color=c.outlineVariant.copy(alpha=.55f))
                    }
                }
            }
            item{TextButton(onClick=delete,enabled=!s.busy,modifier=Modifier.padding(top=24.dp)){Text("移入回收站",style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)}}
        }
    }
}

private fun openLink(context:Context,url:String,model:AppModel) {
    runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))}.onFailure{model.message("没有找到可打开此链接的应用，请复制网址后使用浏览器")}
}

@Composable internal fun EditorV19(model:AppModel,s:AppState,choose:(String)->Unit,take:(String)->Unit,back:()->Unit,confirm:(String,()->Unit)->Unit) {
    val a=s.editor ?: return
    val c=MaterialTheme.colorScheme
    var addMedia by remember{mutableStateOf(false)}
    var labels by remember{mutableStateOf(false)}
    var templates by remember{mutableStateOf(false)}
    var saveTemplate by remember{mutableStateOf(false)}
    var templateName by remember{mutableStateOf("")}
    var removed by remember(a.id){mutableStateOf<Pair<Int,Field>?>(null)}
    fun change(value:Archive){model.change(value)}
    BackHandler(templates){templates=false}
    Surface(Modifier.fillMaxSize(),color=c.background) {
        if(templates) Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            PageBar("选择模板",{templates=false})
            LazyColumn(Modifier.padding(horizontal=24.dp),contentPadding=PaddingValues(vertical=20.dp)) {
                item{Text("只添加字段名称与类型，保留已填内容。",color=c.onSurfaceVariant,fontSize=12.sp,modifier=Modifier.padding(bottom=18.dp))}
                if(s.library.templates.isEmpty()) item{Text("还没有模板。在编辑页填好字段名后，选择“存为模板”。",fontSize=14.sp)}
                items(s.library.templates,key={it.id}){t->Column(Modifier.fillMaxWidth().clickable{model.applyTemplate(t);templates=false}.padding(vertical=20.dp)){Text(t.name,fontSize=17.sp);Text(t.fields.joinToString(" · "){it.name},Modifier.padding(top=6.dp),fontSize=12.sp,color=c.onSurfaceVariant);HorizontalDivider(Modifier.padding(top=20.dp),color=c.outlineVariant.copy(alpha=.5f))}}
            }
        } else Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            PageBar("编辑档案",back){Button(onClick=model::saveEditor,enabled=!s.busy,shape=RoundedCornerShape(12.dp),contentPadding=PaddingValues(horizontal=18.dp,vertical=10.dp)){Text(if(s.busy) "处理中" else "保存")}}
            if(s.message.isNotEmpty()) Row(Modifier.fillMaxWidth().background(c.surfaceVariant).padding(start=24.dp),verticalAlignment=Alignment.CenterVertically){Text(s.message,Modifier.weight(1f),fontSize=12.sp);TextButton(onClick=model::dismissMessage){Text("知道了",fontSize=12.sp)}}
            if(s.busy) {LinearProgressIndicator(Modifier.fillMaxWidth());Row(verticalAlignment=Alignment.CenterVertically){Text(s.progress,Modifier.weight(1f).padding(start=24.dp),fontSize=12.sp);TextButton(onClick=model::cancelOperation){Text("取消")}}}
            LazyColumn(contentPadding=PaddingValues(start=20.dp,end=20.dp,top=16.dp,bottom=24.dp)) {
                item {
                    Text("称呼",Modifier.padding(bottom=8.dp),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
                    TextField(a.name,{change(a.copy(name=it))},Modifier.fillMaxWidth(),enabled=!s.busy,singleLine=true,shape=RoundedCornerShape(16.dp),placeholder={Text("名称或称呼",style=MaterialTheme.typography.headlineMedium)},textStyle=MaterialTheme.typography.headlineMedium,colors=TextFieldDefaults.colors(unfocusedContainerColor=c.surface,focusedContainerColor=c.surface,disabledContainerColor=c.surface,unfocusedIndicatorColor=Color.Transparent,focusedIndicatorColor=Color.Transparent))
                    Spacer(Modifier.height(14.dp))
                    SectionTitle("照片与视频"){FilledTonalButton(onClick={addMedia=true},enabled=!s.busy,shape=RoundedCornerShape(14.dp),modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=14.dp)){Text("添加")}}
                    if(a.photos.isEmpty()) Text("可稍后添加，只有资料也能保存。",fontSize=12.sp,color=c.onSurfaceVariant,modifier=Modifier.padding(vertical=10.dp))
                    LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)) {items(a.photos,key={it.id}){p->
                        Column(Modifier.width(104.dp).clip(RoundedCornerShape(16.dp)).background(c.surface)) {
                            Box {
                                MediaThumb(p,model,Modifier.width(104.dp).height(116.dp),"${if(p.isVideo) "视频" else "照片"}${a.photos.indexOf(p)+1}")
                                TextButton(onClick={change(a.copy(photos=a.photos-p))},enabled=!s.busy,modifier=Modifier.align(Alignment.TopEnd).size(48.dp).semantics{contentDescription="移除这项媒体"},contentPadding=PaddingValues(0.dp),colors=ButtonDefaults.textButtonColors(contentColor=Color.White,containerColor=Color.Black.copy(alpha=.5f))){Text("×",fontSize=20.sp)}
                                if(a.coverPhoto()?.id==p.id) Text("封面",Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha=.6f)).padding(4.dp),color=Color.White,fontSize=10.sp)
                            }
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                                TextButton(onClick={val ps=a.photos.toMutableList();val i=ps.indexOf(p);ps.removeAt(i);ps.add(i-1,p);change(a.copy(photos=ps))},enabled=!s.busy && a.photos.indexOf(p)>0,modifier=Modifier.width(48.dp),contentPadding=PaddingValues(0.dp)){Text("←")}
                                TextButton(onClick={val ps=a.photos.toMutableList();val i=ps.indexOf(p);ps.removeAt(i);ps.add(i+1,p);change(a.copy(photos=ps))},enabled=!s.busy && a.photos.indexOf(p)<a.photos.lastIndex,modifier=Modifier.width(48.dp),contentPadding=PaddingValues(0.dp)){Text("→")}
                            }
                            TextButton(onClick={change(a.copy(cover=p.id))},enabled=!s.busy,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),contentPadding=PaddingValues(0.dp)){Text(if(a.coverPhoto()?.id==p.id) "当前封面" else "设为封面",style=MaterialTheme.typography.labelMedium)}
                        }
                    }}
                    Spacer(Modifier.height(22.dp))
                    SectionTitle("自定义字段"){
                        TextButton(onClick={templates=true},enabled=!s.busy,contentPadding=PaddingValues(6.dp)){Text("选择模板",fontSize=12.sp)}
                        TextButton(onClick={saveTemplate=true},enabled=!s.busy,contentPadding=PaddingValues(6.dp)){Text("存为模板",fontSize=12.sp)}
                    }
                    removed?.let{pair->Row(verticalAlignment=Alignment.CenterVertically){Text("已移除字段",Modifier.weight(1f),fontSize=12.sp,color=c.onSurfaceVariant);TextButton(onClick={if(a.fields.size<40){val fs=a.fields.toMutableList();fs.add(pair.first.coerceAtMost(fs.size),pair.second);change(a.copy(fields=fs));removed=null}},enabled=!s.busy && a.fields.size<40){Text("撤销")}}}
                }
                items(a.fields,key={it.id}){f->
                    var typeMenu by remember{mutableStateOf(false)}
                    Column(Modifier.padding(bottom=12.dp).clip(RoundedCornerShape(16.dp)).background(c.surface).padding(12.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            TextField(f.name,{value->change(a.copy(fields=a.fields.map{if(it.id==f.id) it.copy(name=value) else it}))},Modifier.weight(1f),enabled=!s.busy,singleLine=true,placeholder={Text("字段名称",fontSize=13.sp)},textStyle=LocalTextStyle.current.copy(fontSize=14.sp),colors=TextFieldDefaults.colors(unfocusedContainerColor=Color.Transparent,focusedContainerColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent,focusedIndicatorColor=Color.Transparent))
                            Box{TextButton(onClick={typeMenu=true},enabled=!s.busy){Text("${f.type}⌄",fontSize=12.sp)};DropdownMenu(typeMenu,{typeMenu=false}){Templates.types.forEach{type->DropdownMenuItem(text={Text(type)},onClick={change(a.copy(fields=a.fields.map{if(it.id==f.id) it.copy(type=type) else it}));typeMenu=false})}}}
                            TextButton(onClick={removed=a.fields.indexOf(f) to f;change(a.copy(fields=a.fields-f))},enabled=!s.busy,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp),contentPadding=PaddingValues(0.dp)){Text("删除",style=MaterialTheme.typography.labelMedium,color=c.onSurfaceVariant)}
                        }
                        QuietInput(f.value,{value->change(a.copy(fields=a.fields.map{if(it.id==f.id) it.copy(value=value) else it}))},when(f.type){"链接"->"网址或平台分享文字";"日期"->"YYYY-MM-DD";"数字"->"填写数值";else->"填写内容"},Modifier.fillMaxWidth(),singleLine=f.type!in listOf("文本","链接"),enabled=!s.busy,keyboard=when(f.type){"数字"->KeyboardType.Decimal;"链接"->KeyboardType.Uri;else->KeyboardType.Text})
                        if(f.type=="链接") Text("可粘贴分享文字，保存时提取有效网址。",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
                    }
                }
                item {
                    TextButton(onClick={change(a.copy(fields=a.fields+Field()))},enabled=!s.busy && a.fields.size<40){Text("＋ 添加字段")}
                    TextButton(onClick={labels=true},enabled=!s.busy){Text("分类与标签${if(a.category.isNotBlank()||a.tags.isNotEmpty()) " · 已选择" else ""}")}
                    Text(s.draftStatus,Modifier.padding(top=12.dp),color=c.onSurfaceVariant,fontSize=11.sp)
                    TextButton(onClick={confirm("放弃这份草稿？已保存的档案保持原样。",model::discardEditor)},enabled=!s.busy){Text("放弃草稿",fontSize=12.sp,color=c.onSurfaceVariant)}
                }
            }
        }
    }
    if(addMedia) AlertDialog(onDismissRequest={addMedia=false},title={Text("添加照片与视频")},text={Text("选入后保留原文件副本；视频默认静音，轻触播放。")},confirmButton={TextButton(onClick={addMedia=false;choose(a.id)}){Text("从相册选择")}},dismissButton={TextButton(onClick={addMedia=false;take(a.id)}){Text("拍照")}})
    if(labels) LabelPicker(s.library,a.category,a.tags,{change(a.copy(category=it.first,tags=it.second));labels=false},{labels=false},"分类与标签",{kind,name->model.label(kind,name,false)},busy=s.busy)
    if(saveTemplate) {
        val existing=s.library.templates.firstOrNull{normalizedLabel(it.name)==normalizedLabel(templateName)}
        AlertDialog(onDismissRequest={saveTemplate=false},title={Text(if(existing==null) "存为模板" else "更新同名模板")},text={Column{Text("只保存字段名称、类型和顺序，不包含任何资料内容。",fontSize=12.sp);QuietInput(templateName,{templateName=it},"模板名称",Modifier.fillMaxWidth());if(existing!=null) Text("将更新「${existing.name}」。已建档案不受影响。",fontSize=12.sp)}},confirmButton={TextButton(onClick={model.saveTemplate(templateName,existing?.id);saveTemplate=false},enabled=templateName.isNotBlank()&&!s.busy){Text(if(existing==null) "保存模板" else "确认更新")}},dismissButton={TextButton(onClick={saveTemplate=false}){Text("取消")}})
    }
}

@Composable internal fun LabelPicker(library:Library,initialCategory:String,initialTags:List<String>,apply:(Pair<String,List<String>>)->Unit,close:()->Unit,title:String,create:((String,String)->Unit)?=null,busy:Boolean=false) {
    var category by rememberSaveable{mutableStateOf(initialCategory)}
    var tags by rememberSaveable{mutableStateOf(initialTags)}
    var query by rememberSaveable{mutableStateOf("")}
    var kind by rememberSaveable{mutableStateOf("category")}
    var pending by remember{mutableStateOf<Pair<String,String>?>(null)}
    var createError by remember{mutableStateOf("")}
    LaunchedEffect(library.labels,busy){
        val requested=pending
        if(requested!=null && !busy) {
            val label=library.labels.firstOrNull{it.kind==requested.first && normalizedLabel(it.name)==normalizedLabel(requested.second)}
            if(label!=null) {
                if(label.kind=="category") category=label.id else tags=(tags+label.id).distinct()
                query="";createError=""
            } else createError="未能添加，请重试。已选标签仍保留。"
            pending=null
        }
    }
    val c=MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest=close,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=c.background){
        Column(Modifier.fillMaxWidth().fillMaxHeight(.86f).padding(horizontal=24.dp).imePadding()) {
            Row(verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.SemiBold);TextButton(onClick={category="";tags=emptyList()}){Text("清除")};Button(onClick={apply(category to tags)},enabled=pending==null && !busy,shape=RoundedCornerShape(12.dp)){Text("完成")}}
            Row{FilterChip(kind=="category",{kind="category"},label={Text("分类")});Spacer(Modifier.width(10.dp));FilterChip(kind=="tag",{kind="tag"},label={Text("标签 · ${tags.size}")})}
            if(tags.isNotEmpty()) FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                library.labels.filter{it.id in tags}.forEach{label->
                    InputChip(selected=true,onClick={tags=tags-label.id},label={Text(label.name)},
                        trailingIcon={Text("×",modifier=Modifier.semantics{contentDescription="移除标签 ${label.name}"})})
                }
            }
            QuietInput(query,{query=it},"搜索${if(kind=="category") "分类" else "标签"}",Modifier.fillMaxWidth())
            if(createError.isNotEmpty()) Text(createError,color=c.error,style=MaterialTheme.typography.bodySmall)

            LazyColumn(Modifier.weight(1f)) {
                items(library.labels.filter{it.kind==kind && it.name.contains(query,true)},key={it.id}){l->Row(Modifier.fillMaxWidth().clickable{if(kind=="category") category=if(category==l.id) "" else l.id else tags=if(l.id in tags) tags-l.id else tags+l.id}.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Text(l.name,Modifier.weight(1f),fontSize=15.sp);Checkbox(if(kind=="category") category==l.id else l.id in tags,null)}}
                if(create!=null && query.isNotBlank() && library.labels.none{it.kind==kind && normalizedLabel(it.name)==normalizedLabel(query)}) item{TextButton(enabled=pending==null && !busy,onClick={createError="";pending=kind to query.trim();create(kind,query.trim())}){Text("新建${if(kind=="category") "分类" else "标签"}「$query」")}}
            }
            Text(if(kind=="category") "每份档案选择一个分类。" else "可以选择多个标签，筛选时同时匹配。",fontSize=11.sp,color=c.onSurfaceVariant,modifier=Modifier.padding(vertical=14.dp))
        }
    }
}
