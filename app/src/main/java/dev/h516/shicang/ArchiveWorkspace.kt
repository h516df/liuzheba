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

/** Complete archive reading: text stays with its ordered mixture of images and videos. */
@Composable internal fun ArchiveDetailScreen(
    model:AppModel,s:AppState,a:Archive,back:()->Unit,view:(String)->Unit,edit:()->Unit,
    addMedia:()->Unit,delete:()->Unit,authors:@Composable ()->Unit
) {
    val c=MaterialTheme.colorScheme
    var filter by rememberSaveable(a.id){mutableStateOf(MediaFilter.ALL)}
    var expanded by rememberSaveable(a.id){mutableStateOf(false)}
    var extra by rememberSaveable(a.id){mutableStateOf(a.fields.any{it.value.isNotBlank()})}
    val note=s.library.archiveNote(a.id)
    val media=a.media(filter)
    Column(Modifier.fillMaxSize()) {
        PageBar("档案详情",back){TextButton(onClick=edit,enabled=!s.busy){Text("编辑")}}
        LazyColumn(Modifier.testTag("archive-content"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            item {
                Text(a.name,style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.SemiBold)
                Text(a.mediaCountLabel(),Modifier.padding(top=8.dp),color=c.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                val labels=(listOf(a.category)+a.tags).map(s.library::labelName).filter(String::isNotBlank)
                if(labels.isNotEmpty())FlowRow(Modifier.padding(top=10.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    labels.forEach{SuggestionChip(onClick=edit,label={Text(it)})}
                }
            }
            item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface).padding(18.dp)) {
                    if(!note?.title.isNullOrBlank())Text(note!!.title,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(bottom=8.dp))
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(note?.body?.ifBlank{"写下这份收藏的故事、来源或想法。"} ?: "写下这份收藏的故事、来源或想法。",
                            Modifier.testTag("archive-text"),style=MaterialTheme.typography.bodyLarge,
                            color=if(note?.body.isNullOrBlank())c.onSurfaceVariant else c.onSurface,
                            maxLines=if(expanded)Int.MAX_VALUE else 6,overflow=TextOverflow.Ellipsis)
                    }
                    FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                        if(note!=null)TextButton(onClick={expanded=!expanded}){Text(if(expanded)"收起文字" else "展开全文")}
                        TextButton(onClick=edit,enabled=!s.busy){Text(if(note==null)"写文字" else "编辑文字")}
                    }
                    if(s.library.noteDrafts.any{it.type=="archive" && it.target==a.id})Text("有未完成草稿",style=MaterialTheme.typography.labelSmall,color=c.primary)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text("照片与视频",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    if(a.photos.isNotEmpty())TextButton(onClick=edit,enabled=!s.busy){Text("整理")}
                    FilledTonalButton(onClick=addMedia,enabled=!s.busy){Text("添加")}
                }
                if(a.photos.isNotEmpty())FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    MediaFilter.entries.forEach{value->FilterChip(selected=filter==value,onClick={filter=value},label={Text(value.label)},modifier=Modifier.testTag("archive-filter-${value.name}"))}
                }
            }
            if(media.isEmpty())item {
                Text(if(a.photos.isEmpty())"添加图片或视频，补充这份档案。" else "这里没有${filter.label}",Modifier.padding(vertical=12.dp),color=c.onSurfaceVariant)
            }
            items(media.chunked(3),key={row->row.first().id}) {row->
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    row.forEach{p->
                        MediaThumb(p,model,Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                            .clickable(onClickLabel="查看${if(p.isVideo)"视频" else "图片"}"){view(p.id)}
                            .testTag("archive-media-${p.id}"),"查看第${a.photos.indexOf(p)+1}项${if(p.isVideo)"视频" else "图片"}")
                    }
                    repeat(3-row.size){Spacer(Modifier.weight(1f))}
                }
            }
            item {
                TextButton(onClick={extra=!extra},modifier=Modifier.fillMaxWidth()) {Text(if(extra)"收起补充资料" else "来源、作者与补充资料")}
            }
            if(extra) {
                item{authors()}
                items(a.fields,key={it.id}){ArchiveFieldValue(it,model)}
                item{TextButton(onClick=delete,enabled=!s.busy){Text("移入回收站",color=c.error)}}
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
        LazyVerticalGrid(GridCells.Fixed(3),state=grid,modifier=Modifier.fillMaxWidth().heightIn(min=120.dp,max=420.dp).testTag("editor-media-grid"),
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
            TextButton(onClick={model.change(a.copy(photos=a.photos.filterNot{it.id in selected}));selected=emptyList()},enabled=!busy){Text("移除 ${picked.size} 项")}
        }
        Text("长按拖动排序，点选可设封面。",Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.bodySmall,color=c.onSurfaceVariant)
    }
}
