package dev.h516.shicang

import org.json.JSONObject

/** One independent long note per archive, media item or author. Short fields are never migrated or overwritten. */
data class LongNote(val type:String,val target:String,val title:String="",val body:String="",val updated:Long=0) {
    val key get()="$type:$target"
    fun json()=JSONObject().put("type",type).put("target",target).put("title",title).put("body",body).put("updated",updated)
    fun validated():LongNote { require(type in setOf("archive","media","author") && Codec.validId(target)){"笔记关联无效"};require(title.length<=TITLE_LIMIT && body.length<=BODY_LIMIT && updated>=0){"笔记超出长度限制"};return this }
    companion object {
        const val BODY_LIMIT=100_000
        const val TITLE_LIMIT=200
        fun read(o:JSONObject):LongNote {
            require(listOf("type","target","title","body").all{o.opt(it) is String} && (o.opt("updated") is Long || o.opt("updated") is Int)){"笔记格式无效"}
            return LongNote(o.getString("type"),o.getString("target"),o.getString("title"),o.getString("body"),o.getLong("updated")).validated()
        }
    }
}
fun Library.notesFor(a:Archive)=notes.filter{it.type=="archive" && it.target==a.id || it.type=="media" && a.photos.any{p->p.id==it.target} || it.type=="author" && it.target in a.authorIds}
fun Library.noteLabel(note:LongNote):String=when(note.type){
    "archive"->(archives+drafts).firstOrNull{it.id==note.target}?.name
    "author"->authors.firstOrNull{it.id==note.target}?.name
    else->(archives+drafts).firstOrNull{a->a.photos.any{it.id==note.target}}?.let{"${it.name} · 媒体笔记"}
}?.ifBlank{"未命名关联"} ?: "原关联已移除 · 笔记仍保留"

data class NoteSession(val saved:LongNote?,val draft:LongNote?,val generation:String)

internal class StaleNoteSession:IllegalArgumentException("资料库已恢复或切换，当前编辑内容未写入新库；可复制文字后放弃此编辑会话")
