package dev.h516.shicang

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AppState(val library: Library = Library(),val editor: Archive?=null,val appearance: Appearance=Appearance(),val ready: Boolean=false,val busy: Boolean=false,val progress: String="",val message: String="",val draftStatus: String="",val space: Map<String,Long> = emptyMap())
class AppModel(app: Application): AndroidViewModel(app) {
    private val mutable=MutableStateFlow(AppState())
    val state=mutable.asStateFlow()
    lateinit var store: Store; private set
    private var draftJob: Job?=null
    private var operation: Job?=null
    init {viewModelScope.launch {
        try { store=withContext(Dispatchers.IO) {Store(app)};reload();mutable.value=mutable.value.copy(ready=true) }
        catch(e:Exception) {mutable.value=mutable.value.copy(message="资料打开失败：${e.message}")}
    }}
    private suspend fun reload() {val l=store.load();val a=store.appearance();mutable.value=mutable.value.copy(library=l,appearance=a)}
    fun refreshSpace() {viewModelScope.launch {try {val value=store.space();mutable.value=mutable.value.copy(space=value)} catch(e:Exception){message("空间统计暂不可用")}}}
    fun dismissMessage() {mutable.value=mutable.value.copy(message="")}
    fun message(s: String) {mutable.value=mutable.value.copy(message=s)}
    private fun work(text: String, block: suspend ()->Unit) {
        if(!mutable.value.ready || mutable.value.busy) return
        mutable.value=mutable.value.copy(busy=true,progress=text)
        operation=viewModelScope.launch { try {block();reload()}
            catch(e:CancellationException) {message("操作已停止，当前资料已重新读取")}
            catch(e: Exception) {message(e.message ?: "操作未完成，请重试")}
            finally {withContext(NonCancellable){runCatching{reload()};mutable.value=mutable.value.copy(busy=false,progress="")}}}
    }
    fun cancelOperation() {operation?.cancel()}
    fun edit(a: Archive?=null) {
        val target=a?.let { formal -> mutable.value.library.drafts.firstOrNull{it.id==formal.id} ?: formal } ?: Archive(fields=listOf(Field()))
        mutable.value=mutable.value.copy(editor=target,draftStatus="正在保存草稿")
        change(target)
    }
    fun change(a: Archive) {
        if(mutable.value.busy) return
        mutable.value=mutable.value.copy(editor=a,draftStatus="正在保存草稿")
        draftJob?.cancel()
        draftJob=viewModelScope.launch {delay(200);try{store.draft(a);mutable.value=mutable.value.copy(draftStatus="草稿已保存");reload()}catch(e:CancellationException){throw e}catch(e:Exception){mutable.value=mutable.value.copy(draftStatus="草稿保存失败");message(e.message ?: "草稿保存失败")}}
    }
    fun leaveEditor() {mutable.value=mutable.value.copy(editor=null)}
    fun discardEditor() {val a=mutable.value.editor ?: return;draftJob?.cancel();work("放弃编辑") {store.discard(a.id);mutable.value=mutable.value.copy(editor=null)} }
    fun saveEditor() {val a=mutable.value.editor ?: return;draftJob?.cancel();work("保存资料") {store.save(a);mutable.value=mutable.value.copy(editor=null);message("已保存「${a.name.trim()}」")} }
    fun importPhotos(id: String,uris: List<Uri>) {
        if(uris.isEmpty()) return
        if(!mutable.value.ready || mutable.value.busy) {viewModelScope.launch {state.first{it.ready && !it.busy};importPhotos(id,uris)};return}
        draftJob?.cancel()
        work("正在导入 ${uris.size} 项照片与视频") {
            val a=mutable.value.editor?.takeIf{it.id==id} ?: store.load().drafts.firstOrNull{it.id==id} ?: error("原编辑草稿不存在，请重新选择照片")
            val result=store.importPhotos(uris);val next=a.copy(photos=a.photos+result.photos)
            store.draft(next);if(mutable.value.editor?.id==id) mutable.value=mutable.value.copy(editor=next,draftStatus="草稿已保存")
            if(result.errors.isNotEmpty()) message("已导入${result.photos.size}项；${result.errors.joinToString("；")}")
        }
    }
    fun preparePhotos(id: String,launch:()->Unit) {
        val a=mutable.value.editor?.takeIf{it.id==id} ?: return
        draftJob?.cancel();work("保存编辑草稿") {store.draft(a);launch()}
    }
    fun trash(a: Archive) = work("移入回收站") {store.trash(a.id);message("已移入回收站，可从设置恢复")}
    fun restore(a: Archive) = work("恢复档案") {store.trash(a.id,true);message("档案已恢复")}
    fun restorePhoto(t: PhotoTrash) = work("恢复照片") {store.restorePhoto(t.id);message("照片已恢复")}
    fun purge()=work("永久清理回收站") {store.purge();message("回收站已清理")}
    fun label(kind: String,name: String,apply: Boolean=true) {
        if(mutable.value.busy) return
        if(apply) draftJob?.cancel()
        work("保存${if(kind=="category") "分类" else "标签"}") {
        val id=store.label(kind,name);mutable.value.editor?.let {if(apply) {val a=if(kind=="category") it.copy(category=id) else it.copy(tags=(it.tags+id).distinct());store.draft(a);mutable.value=mutable.value.copy(editor=a,draftStatus="草稿已保存")}}
    }
    }
    fun renameLabel(item: Label,name: String)=work("修改名称") {store.renameLabel(item.id,name)}
    fun deleteLabel(item: Label) {if(mutable.value.busy) return;draftJob?.cancel();work("移除关联") {store.deleteLabel(item.id);mutable.value.editor?.let {a -> mutable.value=mutable.value.copy(editor=a.copy(category=if(a.category==item.id) "" else a.category,tags=a.tags-item.id))}}}
    fun saveTemplate(name:String,replaceId:String?=null) {
        val a=mutable.value.editor ?: return
        val template=runCatching{Templates.create(name,a.fields)}.getOrElse{message(it.message ?: "模板未保存");return}
        work("保存模板") {store.saveTemplate(if(replaceId!=null) template.copy(id=replaceId) else template);message("模板已保存")}
    }
    fun applyTemplate(t:FieldTemplate) {mutable.value.editor?.let{ a -> val fields=Templates.apply(t,a.fields); if(fields.size>40) message("合并后超过40个字段，请先精简") else change(a.copy(fields=fields))}}
    fun deleteTemplate(t:FieldTemplate)=work("删除模板") {store.deleteTemplate(t.id);message("模板已删除，档案保留")}
    fun reuse(a: Archive) {mutable.value.editor?.let {change(it.copy(fields=it.fields+a.fields.map{f -> f.copy(id=uid(),value="")}))}}
    fun appearance(a: Appearance)=work("保存外观") {store.appearance(a)}
    fun backup(uri: Uri)=work("准备备份") {store.backup(uri){s -> mutable.value=mutable.value.copy(progress=s)};message("完整备份已保存并校验，可在所选文件位置找到")}
    fun restoreBackup(uri: Uri)=work("准备恢复") {store.restore(uri){s -> mutable.value=mutable.value.copy(progress=s)};mutable.value=mutable.value.copy(editor=null);message("资料已恢复，恢复前副本保留，可撤销恢复")}
    fun undoRestore()=work("还原恢复前资料") {store.undoRestore();message("已切换回恢复前资料")}
    fun clearCache()=work("清理图片缓存") {store.clearCache();message("图片缓存已清理，原照片与资料保留")}
    fun photoFile(p: Photo)=File(store.photoDir,p.file)
}
