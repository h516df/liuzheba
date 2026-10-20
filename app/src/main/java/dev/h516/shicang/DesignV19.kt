@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.h516.shicang

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.platform.LocalDensity
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
        Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            GalleryIcon("返回",c.onSurface,back)
            Text(title,Modifier.weight(1f).padding(horizontal=8.dp),style=MaterialTheme.typography.titleMedium,color=c.onSurface)
            action()
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
    var rules by remember{mutableStateOf(false)}
    var aliases by rememberSaveable{mutableStateOf(true)}
    var recent by rememberSaveable{mutableStateOf(0L)}
    var semantic by rememberSaveable{mutableStateOf(false)}
    var filters by rememberSaveable{mutableStateOf(false)}
    val prefs=LocalContext.current.getSharedPreferences("device",0)
    val focus=androidx.compose.ui.platform.LocalFocusManager.current
    var covers by rememberSaveable{mutableStateOf(prefs.getBoolean("libraryCovers",true))}
    val selection=ArchiveQuery(query,category,tags,byName,aliases,recent,semantic).normalized(s.library)
    LaunchedEffect(selection.category,selection.tags) {category=selection.category;tags=selection.tags}
    var hits by remember{mutableStateOf<List<SearchHit>>(emptyList())}
    var results by remember{mutableStateOf<List<Archive>?>(null)}
    var completedQuery by remember{mutableStateOf<ArchiveQuery?>(null)}
    val c=MaterialTheme.colorScheme
    LaunchedEffect(s.library,selection,s.modelDownloading){
        val found=model.search(s.library,selection)
        hits=found;results=found.map{it.archive};completedQuery=selection
    }
    val searching=results==null || completedQuery!=selection
    fun clear(){query="";category="";tags=emptyList();recent=0L;focus.clearFocus()}
    Column(Modifier.fillMaxSize().padding(horizontal=20.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            TextField(query,{query=it},Modifier.weight(1f).semantics{contentDescription="搜索档案"},singleLine=true,
                placeholder={Text("搜索档案",style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis)},
                leadingIcon={FrameGlyph("搜索",c.onSurfaceVariant)},
                trailingIcon={if(query.isNotEmpty()) IconButton(onClick={query=""},modifier=Modifier.semantics{contentDescription="清除搜索"}){Text("×",fontSize=24.sp,color=c.onSurfaceVariant)}},
                shape=RoundedCornerShape(18.dp),textStyle=MaterialTheme.typography.bodyLarge,
                keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions=androidx.compose.foundation.text.KeyboardActions(onSearch={focus.clearFocus()}),
                colors=TextFieldDefaults.colors(unfocusedContainerColor=c.surface,focusedContainerColor=c.surface,unfocusedIndicatorColor=Color.Transparent,focusedIndicatorColor=Color.Transparent))
            FilledTonalIconButton(onClick={focus.clearFocus();filters=true},modifier=Modifier.size(56.dp).semantics{contentDescription=if(selection.category.isNotBlank()||selection.tags.isNotEmpty()) "筛选，已选 ${selection.tags.size+if(selection.category.isNotBlank()) 1 else 0} 项" else "筛选"},
                shape=RoundedCornerShape(18.dp),colors=IconButtonDefaults.filledTonalIconButtonColors(containerColor=if(selection.category.isNotBlank()||selection.tags.isNotEmpty()) c.primaryContainer else c.surface,contentColor=c.primary)) {
                FrameGlyph("筛选",c.primary)
            }
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            FilterChip(aliases,{aliases=!aliases},label={Text("我的别名")})
            FilterChip(recent>0,{recent=if(recent>0)0L else System.currentTimeMillis()-30L*86400000},label={Text("近30天更新")})
            FilterChip(semantic,{semantic=!semantic},label={Text("按意思找 · AI")})
            TextButton(onClick={rules=true}){Text("定制规则")}
        }
        if(semantic) LocalModelPanel(model,s)
        if(selection.category.isNotBlank()||selection.tags.isNotEmpty()) LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(top=8.dp)) {
            if(selection.category.isNotBlank()) item(key="category") {ActiveArchiveFilter(s.library.labelName(selection.category),"移除分类筛选"){category=""}}
            items(selection.tags,key={"tag-$it"}){id->ActiveArchiveFilter(s.library.labelName(id),"移除标签筛选 ${s.library.labelName(id)}"){tags=tags-id}}
        }
        FlowRow(Modifier.fillMaxWidth().padding(top=8.dp,bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(4.dp),verticalArrangement=Arrangement.Center,itemVerticalAlignment=Alignment.CenterVertically) {
            Text(if(searching) "正在查找" else "${results.orEmpty().size} 份档案",(if(LocalDensity.current.fontScale>1.4f) Modifier.fillMaxWidth() else Modifier.weight(1f)).padding(end=8.dp),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
            TextButton(onClick={byName=!byName},contentPadding=PaddingValues(horizontal=8.dp),modifier=Modifier.semantics{contentDescription=if(byName) "排序：按名称" else "排序：最近更新"}){
                Text(if(byName) "按名称" else "最近更新",style=MaterialTheme.typography.labelMedium,color=c.onSurfaceVariant)
            }
            TextButton(onClick={covers=!covers;prefs.edit().putBoolean("libraryCovers",covers).apply()},contentPadding=PaddingValues(horizontal=8.dp)){
                Text(if(covers) "列表" else "封面",style=MaterialTheme.typography.labelMedium)
            }
        }
        if(s.library.drafts.isNotEmpty()) LazyRow(contentPadding=PaddingValues(bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(s.library.drafts,key={it.id}){a->SuggestionChip(onClick={model.edit(a)},enabled=!s.busy,label={Text("继续草稿 · ${a.name.ifBlank{"未命名"}}",maxLines=1,overflow=TextOverflow.Ellipsis)})}
        }
        if(searching) Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center){CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp)}
        else if(results.orEmpty().isEmpty()) Column(Modifier.fillMaxWidth().weight(1f).padding(horizontal=20.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
            ArchiveNameplate("留",Modifier.size(112.dp))
            Text(if(s.library.active.isEmpty()) "把值得记住的，留在这里" else "没有匹配的档案",Modifier.padding(top=24.dp,bottom=8.dp),style=MaterialTheme.typography.titleMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            Text(if(s.library.active.isEmpty()) "一个名字，就可以开始。" else "试试其他关键词，或移除筛选条件。",style=MaterialTheme.typography.bodyMedium,color=c.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            if(s.library.active.isEmpty()) FilledTonalButton(onClick={model.edit()},enabled=!s.busy){Text("新建第一份档案")}
            if(selection.filtered) TextButton(onClick={clear()}){Text("清除搜索与筛选")}
        } else if(covers) LazyVerticalGrid(GridCells.Adaptive(148.dp),verticalArrangement=Arrangement.spacedBy(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
            items(results.orEmpty(),key={it.id}){a->
                val swipe=rememberSwipeToDismissBoxState(confirmValueChange={value->if(value==SwipeToDismissBoxValue.EndToStart) deleteTarget=a;false})
                SwipeToDismissBox(state=swipe,modifier=Modifier.animateItem(),enableDismissFromStartToEnd=false,gesturesEnabled=!s.busy,
                    backgroundContent={Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(c.errorContainer).padding(16.dp),contentAlignment=Alignment.CenterEnd){Text("回收站",color=c.onErrorContainer)}}) {
                    Column {ArchiveShelfCard(a,s.library,model,{open(a.id)},{view(a)});if(query.isNotBlank())Text(hits.firstOrNull{it.archive.id==a.id}?.reason.orEmpty(),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,maxLines=3)}
                }
            }
        } else LazyColumn(verticalArrangement=Arrangement.spacedBy(10.dp),contentPadding=PaddingValues(bottom=24.dp)) {
            items(results.orEmpty(),key={it.id}){a->
                val swipe=rememberSwipeToDismissBoxState(confirmValueChange={value->if(value==SwipeToDismissBoxValue.EndToStart) deleteTarget=a;false})
                SwipeToDismissBox(state=swipe,modifier=Modifier.animateItem(),enableDismissFromStartToEnd=false,gesturesEnabled=!s.busy,
                    backgroundContent={Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(c.errorContainer).padding(20.dp),contentAlignment=Alignment.CenterEnd){Text("移入回收站",color=c.onErrorContainer)}}) {
                    Column {ArchiveShelfRow(a,s.library,model,{open(a.id)},{view(a)});if(query.isNotBlank())Text(hits.firstOrNull{it.archive.id==a.id}?.reason.orEmpty(),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,maxLines=3)}
                }
            }
        }
    }
    deleteTarget?.let{a->AlertDialog(onDismissRequest={deleteTarget=null},title={Text("移入回收站？")},text={Text("「${a.name}」的资料和媒体可以从回收站恢复。")},confirmButton={TextButton(onClick={deleteTarget=null;model.trash(a)}){Text("移入回收站")}},dismissButton={TextButton(onClick={deleteTarget=null}){Text("取消")}})}
    if(rules) SearchRulesDialog(s.library,s.busy,model::searchPreferences){rules=false}
    if(filters) LabelPicker(s.library,selection.category,selection.tags,{category=it.first;tags=it.second;filters=false},{filters=false},"筛选档案")
}

@Composable internal fun DetailV19(model:AppModel,s:AppState,a:Archive,back:()->Unit,zoom:(String)->Unit,album:()->Unit,delete:()->Unit,openRelated:(String)->Unit={}) {
    val c=MaterialTheme.colorScheme
    val context=LocalContext.current
    Column(Modifier.fillMaxSize()) {
        PageBar("档案",back){FilledTonalButton(onClick={model.edit(a)},enabled=!s.busy,shape=RoundedCornerShape(14.dp),modifier=Modifier.heightIn(min=48.dp)){Text("编辑")}}
        LazyColumn(contentPadding=PaddingValues(start=20.dp,end=20.dp,top=20.dp,bottom=32.dp)) {
            item {
                if(a.photos.isEmpty()) ArchiveNameplate(a.name,Modifier.size(72.dp).padding(bottom=4.dp))
                Text(a.name,style=MaterialTheme.typography.headlineLarge,modifier=Modifier.padding(top=8.dp,bottom=12.dp))
                val labels=(listOf(a.category)+a.tags).map(s.library::labelName).filter{it.isNotBlank()}
                if(labels.isNotEmpty()) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    labels.forEach{label->Surface(color=c.secondaryContainer,shape=RoundedCornerShape(8.dp)){
                        Text(label,Modifier.padding(horizontal=10.dp,vertical=6.dp),style=MaterialTheme.typography.bodySmall,color=c.onSecondaryContainer)
                    }}
                }
                Spacer(Modifier.height(24.dp))
            }
            if(a.photos.isNotEmpty()) item {
                val cover=a.coverPhoto() ?: a.photos.first()
                Box(Modifier.fillMaxWidth().aspectRatio(1.15f).clip(RoundedCornerShape(20.dp)).clickable{zoom(cover.id)}) {
                    MediaThumb(cover,model,Modifier.fillMaxSize(),"浏览${a.name}的照片与视频")
                    Surface(modifier=Modifier.align(Alignment.BottomEnd).padding(12.dp),color=Color.Black.copy(alpha=.64f),shape=RoundedCornerShape(12.dp)) {
                        Row(Modifier.padding(horizontal=12.dp,vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            FrameGlyph("全部照片",Color.White);Text("${a.photos.size} 项",color=Color.White,style=MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("照片与视频",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
                    TextButton(onClick=album){Text("查看全部",style=MaterialTheme.typography.labelMedium);FrameGlyph("展开",c.primary)}
                }
                if(a.photos.size>1) LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=12.dp)) {
                    items(a.photos.filter{it.id!=cover.id}.take(5),key={it.id}){p->MediaThumb(p,model,Modifier.size(76.dp).clip(RoundedCornerShape(12.dp)).clickable{zoom(p.id)},"查看${if(p.isVideo) "视频" else "照片"}")}
                }
            }
            item{SectionTitle("资料")}
            if(a.fields.isEmpty()) item{Surface(color=c.surface,shape=RoundedCornerShape(20.dp)){
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("从一点一滴开始",style=MaterialTheme.typography.titleSmall)
                    Text("添加备注、日期或链接，慢慢补全这份档案。",Modifier.padding(top=8.dp),color=c.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)
                    TextButton(onClick={model.edit(a)},enabled=!s.busy,modifier=Modifier.padding(top=8.dp)){Text("补充资料")}
                }
            }}
            items(a.fields,key={it.id}){f->
                val url=if(f.type=="链接" || f.value.trim().startsWith("http")) Links.normalize(f.value) else null
                val first=a.fields.first().id==f.id;val last=a.fields.last().id==f.id
                Surface(color=c.surface,shape=RoundedCornerShape(topStart=if(first) 16.dp else 0.dp,topEnd=if(first) 16.dp else 0.dp,bottomStart=if(last) 16.dp else 0.dp,bottomEnd=if(last) 16.dp else 0.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal=16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(vertical=18.dp),verticalAlignment=Alignment.CenterVertically) {
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
            item {
                val related=SearchAssistant.related(s.library,a)
                if(related.isNotEmpty()) {
                    SectionTitle("相关档案 · 供核对")
                    related.forEach{hit->Column(Modifier.padding(vertical=8.dp)){TextButton(onClick={openRelated(hit.archive.id)}){Text(hit.archive.name)};Text(hit.reason,style=MaterialTheme.typography.bodySmall)}}
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
                    Row(Modifier.fillMaxWidth().clickable(enabled=!s.busy){labels=true}.padding(vertical=14.dp),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("分类与标签",style=MaterialTheme.typography.bodyMedium)
                            val selectedLabels=(listOf(a.category)+a.tags).map(s.library::labelName).filter{it.isNotBlank()}
                            if(selectedLabels.isNotEmpty()) Text(selectedLabels.joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
                        }
                        FrameGlyph("展开",c.onSurfaceVariant)
                    }
                    TagSuggestionsPanel(model,s,a)
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
        Column(Modifier.fillMaxWidth().fillMaxHeight(.86f).padding(horizontal=20.dp).imePadding()) {
            Row(verticalAlignment=Alignment.CenterVertically){Text(title,Modifier.weight(1f),fontSize=20.sp,fontWeight=FontWeight.SemiBold);TextButton(onClick={category="";tags=emptyList()}){Text("清除")};Button(onClick={apply(category to tags)},enabled=pending==null && !busy,shape=RoundedCornerShape(12.dp)){Text("完成")}}
            Row{FilterChip(kind=="category",{kind="category"},label={Text("分类")});Spacer(Modifier.width(10.dp));FilterChip(kind=="tag",{kind="tag"},label={Text("标签 · ${tags.size}")})}
            if(tags.isNotEmpty()) FlowRow(Modifier.heightIn(max=112.dp).verticalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                library.labels.filter{it.id in tags}.forEach{label->
                    InputChip(selected=true,onClick={tags=tags-label.id},label={Text(label.name)},
                        trailingIcon={Text("×",modifier=Modifier.semantics{contentDescription="移除标签 ${label.name}"})})
                }
            }
            QuietInput(query,{query=it},"搜索${if(kind=="category") "分类" else "标签"}",Modifier.fillMaxWidth())
            if(createError.isNotEmpty()) Text(createError,color=c.error,style=MaterialTheme.typography.bodySmall)

            LazyColumn(Modifier.weight(1f)) {
                items(library.labels.filter{it.kind==kind && it.name.contains(query,true)},key={it.id}){l->Row(Modifier.fillMaxWidth().clickable{if(kind=="category") category=if(category==l.id) "" else l.id else tags=if(l.id in tags) tags-l.id else tags+l.id}.padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically){Text(l.name,Modifier.weight(1f),fontSize=15.sp);if(kind=="category") RadioButton(category==l.id,null) else Checkbox(l.id in tags,null)}}
                if(create!=null && query.isNotBlank() && library.labels.none{it.kind==kind && normalizedLabel(it.name)==normalizedLabel(query)}) item{TextButton(enabled=pending==null && !busy,onClick={createError="";pending=kind to query.trim();create(kind,query.trim())}){Text("新建${if(kind=="category") "分类" else "标签"}「$query」")}}
            }
            Text(if(kind=="category") "每份档案选择一个分类。" else "可以选择多个标签，筛选时同时匹配。",fontSize=11.sp,color=c.onSurfaceVariant,modifier=Modifier.padding(vertical=14.dp))
        }
    }
}
