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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.selection.SelectionContainer
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
    var reading by rememberSaveable(a.id){mutableStateOf(false)}
    Column(Modifier.fillMaxWidth().padding(vertical=12.dp)) {
        Text("笔记",style=MaterialTheme.typography.titleMedium)
        Text(note?.body?.take(400)?.ifBlank{"尚未填写"} ?: "记下故事、来源和想法，不挤在短字段里。",maxLines=4,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
        GalleryTextButton(onClick={if(note==null)model.noteEditor.open("archive",a.id,a.name) else reading=true}){Text(if(note==null)"写长笔记" else "展开笔记")}
    }
    if(reading && note!=null) NoteReaderDialog(note,a.name,library.noteDrafts.any{it.key==note.key},{reading=false},{reading=false;model.noteEditor.open("archive",a.id,a.name)})
}

@Composable internal fun LongNotesUi(model:AppModel,library:Library,listOpen:Boolean,closeList:()->Unit) {
    val editing by model.noteEditor.state.collectAsState()
    var reading by remember {mutableStateOf<LongNote?>(null)}
    reading?.let {note->NoteReaderDialog(note,library.noteLabel(note),library.noteDrafts.any{it.key==note.key},{reading=null},{reading=null;model.noteEditor.open(note.type,note.target,library.noteLabel(note))})}
    if(listOpen && editing==null && reading==null)Dialog(onDismissRequest=closeList,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            PageBar("全部笔记",closeList)
            NotesLibrary(model,library)
        }}
    }

    editing?.let {value->Dialog(onDismissRequest=model.noteEditor::leave,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        NoteEditorScreen(value,model.noteEditor::change,model.noteEditor::leave,model.noteEditor::save,model.noteEditor::discard)
    }}
}

/** Viewing never starts a draft or changes a relationship; editing is a separate explicit action. */
@Composable internal fun NoteReaderDialog(note:LongNote,label:String,hasDraft:Boolean,close:()->Unit,edit:()->Unit) {
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                GalleryTextButton(onClick=close,modifier=Modifier.testTag("note-reader-back")){Text("返回")}
                GalleryTextButton(onClick=edit){Text(if(hasDraft) "继续编辑草稿" else "编辑笔记")}
            }
            SelectionContainer {Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(note.title.ifBlank{label},style=MaterialTheme.typography.titleLarge)
                if(hasDraft)Text("有未提交草稿；正式笔记仅在保存后改变。",style=MaterialTheme.typography.bodySmall)
                Text(note.body,style=MaterialTheme.typography.bodyLarge,modifier=Modifier.testTag("note-reader"))
            }}
        }}
    }
}

/** Full-screen editor. Only lightweight dialog flags use saved state; body is owned by the ViewModel and persisted to SQLite. */
@Composable internal fun NoteEditorScreen(s:NoteEditing,change:(String,String)->Unit,leave:()->Unit,save:()->Unit,discard:()->Unit) {
    var confirmDiscard by remember {mutableStateOf(false)}
    BackHandler(enabled=!s.busy,onBack=leave)
    Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){GalleryTextButton(onClick=leave,enabled=!s.busy){Text("返回")};GalleryTextButton(onClick=save,enabled=!s.busy && s.loaded && !s.invalidated){Text("保存笔记")}}
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(s.label,style=MaterialTheme.typography.titleLarge)
            Text("独立笔记 · 不改变原媒体和短字段",style=MaterialTheme.typography.bodySmall)
            GalleryTextField(s.note.title,{change(it,s.note.body)},Modifier.fillMaxWidth().testTag("note-title"),label={Text("标题（可选）")},enabled=!s.busy && s.loaded,maxLines=3)
            GalleryTextField(s.note.body,{change(s.note.title,it)},Modifier.fillMaxWidth().heightIn(min=240.dp).testTag("note-body"),label={Text("笔记正文")},enabled=!s.busy && s.loaded,minLines=8,maxLines=16)
            Text("${s.note.body.length} / ${LongNote.BODY_LIMIT} 字符",style=MaterialTheme.typography.labelSmall)
            Text(s.status,style=MaterialTheme.typography.bodySmall)
            if(s.error.isNotBlank())Text(s.error,color=MaterialTheme.colorScheme.error)
            Text(if(s.invalidated)"资料库已切换。可先复制文字，再放弃此编辑会话退出；不会修改新库中的草稿。" else "返回会保留草稿。点击保存才替换原笔记。",style=MaterialTheme.typography.bodySmall)
            GalleryTextButton(onClick={confirmDiscard=true},enabled=!s.busy){Text(if(s.invalidated)"放弃此编辑会话并退出" else "放弃此草稿，保留原笔记")}
            Spacer(Modifier.height(24.dp))
        }
    }}
    if(confirmDiscard)GalleryAlertDialog(onDismissRequest={confirmDiscard=false},title={Text("放弃这份草稿？")},text={Text(if(s.invalidated)"仅关闭这次旧库编辑，未保存的文字将丢弃，不会改写新库及其草稿。" else "已保存的原笔记不变，当前未提交文字将丢弃。")},confirmButton={GalleryTextButton(onClick={confirmDiscard=false;discard()}){Text("放弃草稿")}},dismissButton={GalleryTextButton(onClick={confirmDiscard=false}){Text("继续编辑")}})
}
