@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.h516.shicang

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One reading destination for both roots. Editing and supplemental records are progressive. */
@Composable internal fun ArchiveDetailScreen(
    model:AppModel,s:AppState,a:Archive,back:()->Unit,view:(String)->Unit,edit:()->Unit,
    addMedia:()->Unit,delete:()->Unit,authors:@Composable ()->Unit
) {
    val c=MaterialTheme.colorScheme
    var filter by rememberSaveable(a.id){mutableStateOf(MediaFilter.ALL)}
    var expanded by rememberSaveable(a.id){mutableStateOf(false)}
    var extra by rememberSaveable(a.id){mutableStateOf(false)}
    var menu by remember{mutableStateOf(false)}
    val note=s.library.archiveNote(a.id)
    var textOverflow by remember(a.id,note?.body){mutableStateOf(false)}
    val media=a.media(filter)
    val hasDraft=s.library.drafts.any{it.id==a.id} || s.library.noteDrafts.any{it.type=="archive" && it.target==a.id}
    Column(Modifier.fillMaxSize()) {
        PageBar("档案详情",back){
            GalleryTextButton(onClick=edit,enabled=!s.busy){Text("编辑")}
            Box {
                IconButton(onClick={menu=true},modifier=Modifier.semantics{contentDescription="档案选项"}){FrameGlyph("更多",c.onSurface)}
                DropdownMenu(menu,{menu=false}) {
                    DropdownMenuItem(text={Text("添加照片或视频")},onClick={menu=false;addMedia()},enabled=!s.busy)
                    DropdownMenuItem(text={Text("移入回收站",color=c.error)},onClick={menu=false;delete()},enabled=!s.busy)
                }
            }
        }
        LazyColumn(Modifier.testTag("archive-content"),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=20.dp,bottom=32.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            item {
                Text(a.name,style=MaterialTheme.typography.headlineLarge)
                val labels=(listOf(a.category)+a.tags).map(s.library::labelName).filter(String::isNotBlank)
                Text((listOf(a.mediaCountLabel())+labels).joinToString(" · "),Modifier.padding(top=8.dp),color=c.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                if(hasDraft)Text("有未保存的修改，继续编辑可找回。",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodySmall,color=c.primary)
            }
            a.coverPhoto()?.let{cover->item(key="cover") {
                Box(Modifier.fillMaxWidth().aspectRatio(1.28f).clip(RoundedCornerShape(16.dp))
                    .clickable(onClickLabel="沉浸浏览"){view(cover.id)}) {
                    MediaThumb(cover,model,Modifier.fillMaxSize(),"${a.name}的封面",androidx.compose.ui.layout.ContentScale.Fit)
                    Surface(onClick={view(cover.id)},modifier=Modifier.align(Alignment.BottomEnd).padding(10.dp),
                        color=Color.Black.copy(alpha=.62f),contentColor=Color.White,shape=RoundedCornerShape(24.dp)) {
                        Row(Modifier.heightIn(min=48.dp).padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            FrameGlyph("全部照片",Color.White);Text("沉浸浏览",style=MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }}
            if(note!=null && (note.body.isNotBlank() || note.title.isNotBlank())) item {
                Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    if(note.title.isNotBlank())Text(note.title,style=MaterialTheme.typography.titleMedium)
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(note.body,Modifier.testTag("archive-text"),style=MaterialTheme.typography.bodyLarge,
                            maxLines=if(expanded)Int.MAX_VALUE else 10,overflow=TextOverflow.Ellipsis,
                            onTextLayout={if(!expanded)textOverflow=it.hasVisualOverflow})
                    }
                    if(expanded || textOverflow)GalleryTextButton(onClick={expanded=!expanded},contentPadding=PaddingValues(0.dp)){Text(if(expanded)"收起文字" else "展开全文")}
                }
            }
            if(a.photos.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("照片与视频",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                        Text(a.photos.size.toString(),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
                    }
                    // Filtering is useful for mixed archives; a single media type needs no extra controls.
                    if(a.photos.any{it.isVideo} && a.photos.any{!it.isVideo})FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        MediaFilter.entries.forEach{value->FilterChip(selected=filter==value,onClick={filter=value},label={Text(value.label)},modifier=Modifier.testTag("archive-filter-${value.name}"))}
                    }
                }
                items(media.chunked(3),key={row->row.first().id}) {row->
                    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        row.forEach{p->MediaThumb(p,model,Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp))
                            .clickable(onClickLabel="查看${if(p.isVideo)"视频" else "图片"}"){view(p.id)}.testTag("archive-media-${p.id}"),
                            "查看第${a.photos.indexOf(p)+1}项${if(p.isVideo)"视频" else "图片"}")}
                        repeat(3-row.size){Spacer(Modifier.weight(1f))}
                    }
                }
            }
            if(a.fields.isNotEmpty() || a.authorIds.isNotEmpty())item {
                HorizontalDivider(color=c.outlineVariant.copy(alpha=.4f))
                Row(Modifier.fillMaxWidth().clickable{extra=!extra}.padding(vertical=16.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text("来源与补充资料",Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                    Text(if(extra)"收起" else "展开",style=MaterialTheme.typography.labelMedium,color=c.onSurfaceVariant)
                }
            }
            if(extra) {
                item{authors()}
                items(a.fields,key={it.id}){ArchiveFieldValue(it,model)}
            }
        }
    }
}

internal fun moveMediaBefore(a:Archive,id:String,target:String):Archive {
    val source=a.photos.indexOfFirst{it.id==id};val destination=a.photos.indexOfFirst{it.id==target}
    if(source<0 || destination<0 || source==destination)return a
    return a.copy(photos=a.photos.toMutableList().apply{add(destination,removeAt(source))})
}

/** Bounded grid; dragging changes only order in the current draft, never the original files. */
@Composable internal fun EditorMediaGrid(a:Archive,model:AppModel,busy:Boolean) {
    val c=MaterialTheme.colorScheme
    val grid=rememberLazyGridState()
    var choosing by rememberSaveable(a.id){mutableStateOf(false)}
    var selected by rememberSaveable(a.id){mutableStateOf(listOf<String>())}
    var removed by remember(a.id){mutableStateOf<Archive?>(null)}
    var dragging by remember{mutableStateOf<String?>(null)}
    var pointer by remember{mutableStateOf(Offset.Zero)}
    val current by rememberUpdatedState(a)
    val enabled by rememberUpdatedState(!busy)
    val picked=a.photos.filter{it.id in selected}
    Column {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(a.mediaCountLabel(),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
            TextButton(onClick={choosing=!choosing;selected=emptyList()},enabled=!busy){Text(if(choosing)"完成选择" else "多选")}
            if(choosing)TextButton(onClick={selected=if(picked.size==a.photos.size)emptyList() else a.photos.map{it.id}},enabled=!busy){Text("全选")}
        }
        if(a.photos.isNotEmpty()) LazyVerticalGrid(GridCells.Fixed(3),state=grid,modifier=Modifier.fillMaxWidth().heightIn(min=120.dp,max=420.dp).testTag("editor-media-grid"),
            horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            items(a.photos,key={it.id}){p->
                Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                    .then(if(p.id in selected || dragging==p.id)Modifier.border(3.dp,c.primary,RoundedCornerShape(12.dp))else Modifier)
                    .pointerInput(p.id,choosing){if(!choosing)detectDragGesturesAfterLongPress(
                        onDragStart={start->if(enabled){dragging=p.id;val item=grid.layoutInfo.visibleItemsInfo.firstOrNull{it.key==p.id};pointer=Offset((item?.offset?.x ?: 0)+start.x,(item?.offset?.y ?: 0)+start.y)}},
                        onDragEnd={dragging=null},onDragCancel={dragging=null},
                        onDrag={change,delta->if(enabled && dragging==p.id){change.consume();pointer+=delta
                            val target=grid.layoutInfo.visibleItemsInfo.firstOrNull{pointer.x>=it.offset.x && pointer.x<it.offset.x+it.size.width && pointer.y>=it.offset.y && pointer.y<it.offset.y+it.size.height}
                            val targetId=target?.key as? String
                            if(targetId!=null && targetId!=p.id)model.change(moveMediaBefore(current,p.id,targetId))
                        }})}
                    .clickable(enabled=!busy){selected=if(choosing){if(p.id in selected)selected-p.id else selected+p.id}else listOf(p.id)}
                    .testTag("editor-media-${p.id}")) {
                    MediaThumb(p,model,Modifier.fillMaxSize(),"${if(p.isVideo)"视频" else "图片"} ${a.photos.indexOf(p)+1}")
                    if(p.id==a.coverPhoto()?.id)Text("封面",Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha=.65f)).padding(5.dp),color=Color.White,style=MaterialTheme.typography.labelSmall)
                    if(choosing)Checkbox(p.id in selected,{checked->selected=if(checked)(selected+p.id).distinct() else selected-p.id},Modifier.align(Alignment.TopEnd),enabled=!busy)
                }
            }
        }
        if(picked.isNotEmpty())FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            if(picked.size==1) {
                val p=picked.single();val index=a.photos.indexOf(p)
                TextButton(onClick={model.change(a.copy(cover=p.id))},enabled=!busy){Text("设为封面")}
                TextButton(onClick={model.change(moveMediaBefore(a,p.id,a.photos[index-1].id))},enabled=!busy && index>0){Text("前移")}
                TextButton(onClick={model.change(moveMediaBefore(a,p.id,a.photos[index+1].id))},enabled=!busy && index<a.photos.lastIndex){Text("后移")}
            }
            TextButton(onClick={removed=a;model.change(a.copy(photos=a.photos.filterNot{it.id in selected}));selected=emptyList()},enabled=!busy){Text("移除 ${picked.size} 项")}
        }
        removed?.let{before->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("已从草稿移除",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
            TextButton(onClick={
                // Restore only removed items/order/cover; preserve text and edits made since removal.
                val now=current;val oldIds=before.photos.map{it.id}.toSet()
                model.change(now.copy(photos=before.photos+now.photos.filterNot{it.id in oldIds},cover=before.cover));removed=null
            },enabled=!busy){Text("撤销移除")}
        }}
        if(a.photos.isNotEmpty()) Text("长按拖动排序，点选可设封面。",Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
    }
}
