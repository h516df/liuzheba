package dev.h516.shicang

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class NoteEditing(val note:LongNote,val expected:LongNote?,val label:String,val status:String="",val busy:Boolean=false,val error:String="",val loaded:Boolean=true,val generation:String="",val invalidated:Boolean=false)
class LongNoteEditor(private val scope:CoroutineScope,private val store:()->Store,private val reload:suspend ()->Unit,private val notice:(String)->Unit={}) {
    private val mutable=MutableStateFlow<NoteEditing?>(null)
    val state=mutable.asStateFlow()
    private var writer:Job?=null
    fun open(type:String,id:String,label:String) {
        if(mutable.value!=null)return
        val blank=LongNote(type,id).validated()
        mutable.value=NoteEditing(blank,null,label,busy=true,status="正在打开",loaded=false)
        scope.launch {try {
            val snapshot=store().noteSession(blank);val base=snapshot.saved;val draft=snapshot.draft
            mutable.value=NoteEditing(draft ?: base ?: blank,base,label,if(draft!=null)"已恢复上次草稿" else "原笔记仅在点击保存后改变",generation=snapshot.generation)
        }catch(e:CancellationException){throw e}catch(e:Exception){mutable.update{it?.copy(busy=false,error="笔记打开失败，请退出后重试")}}}
    }
    fun change(title:String,body:String) {
        val s=mutable.value ?: return;if(s.busy || !s.loaded || s.invalidated)return
        if(title.length>LongNote.TITLE_LIMIT || body.length>LongNote.BODY_LIMIT){mutable.value=s.copy(error="标题最多200字，正文最多100000字符；本次超长输入未写入，请分段粘贴");return}
        val next=s.note.copy(title=title,body=body,updated=System.currentTimeMillis())
        mutable.value=s.copy(note=next,status="正在保存草稿…",error="")
        writer?.cancel()
        writer=scope.launch {delay(300);try {store().noteDraft(next,s.generation);if(mutable.value?.note==next)mutable.update{it?.copy(status="草稿已存本机 · 尚未替换原笔记")}}
        catch(e:CancellationException){throw e}catch(e:Exception){if(mutable.value?.note==next)mutable.update{it?.copy(error=if(e is StaleNoteSession)e.message.orEmpty() else "草稿未保存，请检查空间；先不要退出",invalidated=e is StaleNoteSession)}}}
    }
    fun leave()=finish(save=false,discard=false)
    fun save()=finish(save=true,discard=false)
    fun discard()=finish(save=false,discard=true)
    private fun finish(save:Boolean,discard:Boolean) {
        val s=mutable.value ?: return;if(s.busy)return
        if(s.invalidated){if(discard){writer?.cancel();mutable.value=null};return}
        if(!s.loaded){if(!save)mutable.value=null;return}
        mutable.value=s.copy(busy=true,error="")
        scope.launch {
            writer?.cancelAndJoin()
            try {withContext(NonCancellable) {
                if(discard)store().discardNoteDraft(s.note,s.generation)
                else {store().noteDraft(s.note,s.generation);if(save)store().saveNote(s.note,s.expected,s.generation)}
                mutable.value=null // Persistence succeeded; a refresh failure must never resurrect a discarded draft.
                try {reload()}catch(e:Exception){notice("笔记操作已保存，但列表刷新失败；重新打开 App 可读取，不要重复保存")}
            }}catch(e:CancellationException){throw e}catch(e:Exception){mutable.value=s.copy(busy=false,invalidated=e is StaleNoteSession,error=if(e is IllegalArgumentException)e.message.orEmpty() else "保存未完成，内容仍在编辑页，请重试")}
        }
    }
}
