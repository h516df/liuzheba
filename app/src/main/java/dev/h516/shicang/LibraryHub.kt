@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.h516.shicang

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Content destinations stay separate from collection actions and application settings. */
@Composable internal fun LibraryIndex(selected:String,select:(String)->Unit) {
    GallerySegments(listOf("archives" to "档案","notes" to "笔记","authors" to "作者"),selected,select,
        Modifier.padding(horizontal=20.dp,vertical=8.dp))
}

@Composable internal fun CollectionSheet(close:()->Unit,photos:()->Unit,camera:()->Unit,archive:()->Unit,link:()->Unit,download:()->Unit) {
    ModalBottomSheet(onDismissRequest=close,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),
        containerColor=MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("添加内容",style=MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            CollectionAction("新建档案","先写名称或文字，照片可以以后再加",archive)
            CollectionAction("从相册添加","选择照片或视频，再决定放进哪份档案",photos)
            CollectionAction("拍照添加","拍照后选择保存到哪份档案",camera)
            CollectionAction("保存链接","把网址或分享文字保存到档案",link)
            CollectionAction("下载链接媒体","下载完成后选择档案",download)
            GalleryTextButton(onClick=close,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("取消")}
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable private fun CollectionAction(title:String,subtitle:String,action:()->Unit) {
    Surface(onClick=action,shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().heightIn(min=72.dp).padding(16.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium)
                Text(subtitle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FrameGlyph("展开",MaterialTheme.colorScheme.primary)
        }
    }
}

/** Shared by the visible content index and older list callers; reading never creates a draft. */
@Composable internal fun NotesLibrary(model:AppModel,library:Library) {
    var query by rememberSaveable{mutableStateOf("")}
    var reading by remember{mutableStateOf<LongNote?>(null)}
    var results by remember{mutableStateOf<List<LongNote>>(emptyList())}
    LaunchedEffect(library.notes,library.noteDrafts,query){results=withContext(Dispatchers.Default){
        (library.noteDrafts+library.notes).distinctBy{it.key}.filter{query.isBlank() || it.title.contains(query,true) || it.body.contains(query,true) || library.noteLabel(it).contains(query,true)}.sortedByDescending{it.updated}
    }}
    Column(Modifier.fillMaxSize().imePadding()) {
        GallerySearchField(query,{query=it},"搜索笔记与草稿",Modifier.padding(horizontal=20.dp,vertical=12.dp))
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(results.isEmpty())item {
                Text(if(query.isBlank()) "还没有笔记" else "没有匹配的笔记",style=MaterialTheme.typography.titleMedium)
                Text("在档案、照片视频或作者资料中写下故事，这里会集中显示。",Modifier.padding(top=8.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(results,key={it.key}){note->
                Surface(shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().clickable{reading=note}.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(note.title.ifBlank{library.noteLabel(note)},style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text(note.body.take(240),style=MaterialTheme.typography.bodyMedium,maxLines=3,overflow=TextOverflow.Ellipsis)
                        if(library.noteDrafts.any{it.key==note.key})Text("有未提交草稿",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
    reading?.let {note->NoteReaderDialog(note,library.noteLabel(note),library.noteDrafts.any{it.key==note.key},{reading=null},{reading=null;model.editRelatedNote(note,library)})}
}
