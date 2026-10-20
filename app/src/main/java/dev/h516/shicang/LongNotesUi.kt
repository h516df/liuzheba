@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.h516.shicang

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun ArchiveNoteSection(model:AppModel,library:Library,a:Archive) {
    val note=library.notes.firstOrNull{it.type=="archive" && it.target==a.id}
    Column(Modifier.fillMaxWidth().padding(vertical=12.dp)) {
        Text("笔记",style=MaterialTheme.typography.titleMedium)
        Text(note?.body?.take(400)?.ifBlank{"尚未填写"} ?: "记下故事、来源和想法，不挤在短字段里。",maxLines=4,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
        TextButton(onClick={model.noteEditor.open("archive",a.id,a.name)}){Text(if(note==null)"写长笔记" else "展开笔记")}
    }
}

@Composable internal fun LongNotesUi(model:AppModel,library:Library,listOpen:Boolean,closeList:()->Unit) {
    val editing by model.noteEditor.state.collectAsState()
    if(listOpen && editing==null)Dialog(onDismissRequest=closeList,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        var query by remember {mutableStateOf("")}
        var results by remember {mutableStateOf<List<LongNote>>(emptyList())}
        LaunchedEffect(library.notes,library.noteDrafts,query){results=withContext(Dispatchers.Default){
            (library.noteDrafts+library.notes).distinctBy{it.key}.filter{query.isBlank() || it.title.contains(query,true) || it.body.contains(query,true) || library.noteLabel(it).contains(query,true)}.sortedByDescending{it.updated}
        }}
        Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick=closeList){Text("返回")};Text("全部笔记",Modifier.padding(16.dp),style=MaterialTheme.typography.titleLarge)}
            OutlinedTextField(query,{query=it},label={Text("搜索笔记与草稿")},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp),singleLine=true)
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(16.dp)) {
                if(results.isEmpty())item{Text("还没有匹配笔记。到档案详情、媒体更多菜单或作者卡中写笔记。")}
                items(results,key={it.key}) {note->
                    TextButton(onClick={model.noteEditor.open(note.type,note.target,library.noteLabel(note))},modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {Text(note.title.ifBlank{library.noteLabel(note)},maxLines=2,overflow=TextOverflow.Ellipsis);Text(note.body.take(240),maxLines=3,overflow=TextOverflow.Ellipsis);if(library.noteDrafts.any{it.key==note.key})Text("有未提交草稿",style=MaterialTheme.typography.labelSmall)}
                    }
                }
            }
        }}
    }
    editing?.let {value->Dialog(onDismissRequest=model.noteEditor::leave,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        NoteEditorScreen(value,model.noteEditor::change,model.noteEditor::leave,model.noteEditor::save,model.noteEditor::discard)
    }}
}

/** Full-screen editor. Only lightweight dialog flags use saved state; body is owned by the ViewModel and persisted to SQLite. */
@Composable internal fun NoteEditorScreen(s:NoteEditing,change:(String,String)->Unit,leave:()->Unit,save:()->Unit,discard:()->Unit) {
    var confirmDiscard by remember {mutableStateOf(false)}
    BackHandler(enabled=!s.busy,onBack=leave)
    Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick=leave,enabled=!s.busy){Text("返回")};TextButton(onClick=save,enabled=!s.busy && s.loaded && !s.invalidated){Text("保存笔记")}}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(s.label,style=MaterialTheme.typography.titleLarge)
            Text("独立笔记 · 不改变原媒体和短字段",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(s.note.title,{change(it,s.note.body)},Modifier.fillMaxWidth().testTag("note-title"),label={Text("标题（可选）")},enabled=!s.busy && s.loaded,maxLines=3)
            OutlinedTextField(s.note.body,{change(s.note.title,it)},Modifier.fillMaxWidth().heightIn(min=240.dp).testTag("note-body"),label={Text("笔记正文")},enabled=!s.busy && s.loaded,minLines=8,maxLines=16)
            Text("${s.note.body.length} / ${LongNote.BODY_LIMIT} 字符",style=MaterialTheme.typography.labelSmall)
            Text(s.status,style=MaterialTheme.typography.bodySmall)
            if(s.error.isNotBlank())Text(s.error,color=MaterialTheme.colorScheme.error)
            Text(if(s.invalidated)"资料库已切换。可先复制文字，再放弃此编辑会话退出；不会修改新库中的草稿。" else "返回会保留草稿。点击保存才替换原笔记。",style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={confirmDiscard=true},enabled=!s.busy){Text(if(s.invalidated)"放弃此编辑会话并退出" else "放弃此草稿，保留原笔记")}
            Spacer(Modifier.height(24.dp))
        }
    }}
    if(confirmDiscard)AlertDialog(onDismissRequest={confirmDiscard=false},title={Text("放弃这份草稿？")},text={Text(if(s.invalidated)"仅关闭这次旧库编辑，未保存的文字将丢弃，不会改写新库及其草稿。" else "已保存的原笔记不变，当前未提交文字将丢弃。")},confirmButton={TextButton(onClick={confirmDiscard=false;discard()}){Text("放弃草稿")}},dismissButton={TextButton(onClick={confirmDiscard=false}){Text("继续编辑")}})
}
