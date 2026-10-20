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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AppState(val aiTagSource:Archive?=null,val aiTagLabels:List<Label> = emptyList(),val aiTagPreferences:SearchPreferences?=null,val aiTagSuggestions:List<TagSuggestion> = emptyList(),val aiTagArchive:String="",val aiTagBusy:Boolean=false,val localModelStatus:String="本机 AI 未下载 · 当前仅文字与别名搜索",val modelDownloading:Boolean=false,val protection:ProtectionState=ProtectionState(),val recoveryChoices:List<RecoveryChoice> = emptyList(),val protectionSelection:Uri?=null,val library: Library = Library(),val editor: Archive?=null,val appearance: Appearance=Appearance(),val ready: Boolean=false,val busy: Boolean=false,val progress: String="",val message: String="",val draftStatus: String="",val space: Map<String,Long> = emptyMap(),val shares:List<String> = emptyList(),val incomingMedia:List<Uri> = emptyList(),
    val exportRequest:MediaExportRequest?=null,val downloadsOpen:Boolean=false,val downloadInput:String="",
    val resolving:Boolean=false,val downloadError:String="",val mediaPlan:MediaPlan?=null,val downloads:List<MediaDownload> = emptyList())
class AppModel(app: Application): AndroidViewModel(app) {
    private val localModelDelegate=lazy{LocalTextModel(app)}
    private val localTextModel get()=localModelDelegate.value
    private var modelDownload:Job?=null
    private var tagJob:Job?=null
    private val tagRequests=LatestSuggestionRequest()
    private fun invalidateTagSuggestions(){tagRequests.invalidate();tagJob?.cancel();mutable.update{it.copy(aiTagSuggestions=emptyList(),aiTagArchive="",aiTagBusy=false)}}
    fun suggestTags(){
        val a=mutable.value.editor ?: return;val library=mutable.value.library
        invalidateTagSuggestions();val generation=tagRequests.current
        mutable.update{it.copy(aiTagBusy=true,aiTagSuggestions=emptyList(),aiTagArchive=a.id)}
        tagJob=viewModelScope.launch{try{
            val candidates=localTextModel.suggest(library,a)
            if(tagRequests.isCurrent(generation) && mutable.value.editor==a && mutable.value.library.searchPreferences==library.searchPreferences && mutable.value.library.labels==library.labels)mutable.update{it.copy(aiTagSuggestions=candidates,aiTagSource=a,aiTagLabels=library.labels,aiTagPreferences=library.searchPreferences)}
        }catch(e:CancellationException){throw e}catch(e:Exception){if(tagRequests.isCurrent(generation) && mutable.value.editor==a)message(e.message ?: "标签建议暂不可用")}finally{if(tagRequests.isCurrent(generation))mutable.update{it.copy(aiTagBusy=false)}}}
    }

    internal suspend fun search(library:Library,query:ArchiveQuery):List<SearchHit> = try {localTextModel.search(library,query)} catch(e:CancellationException){throw e} catch(e:Exception){mutable.update{it.copy(localModelStatus="${e.message}；已回退文字搜索")};query.hits(library)}
    fun downloadLocalModel(){if(mutable.value.modelDownloading)return;mutable.value=mutable.value.copy(modelDownloading=true);modelDownload=viewModelScope.launch{try{localTextModel.download{status->mutable.update{it.copy(localModelStatus=status)}}}catch(e:CancellationException){mutable.value=mutable.value.copy(localModelStatus="下载已取消 · 文字搜索仍可用");throw e}catch(e:Exception){mutable.value=mutable.value.copy(localModelStatus="${e.message}；文字搜索仍可用")}finally{mutable.value=mutable.value.copy(modelDownloading=false)}}}
    fun cancelModelDownload(){modelDownload?.cancel()}
    private val updateSession=lazy {dev.h516.update.Updater(app)}
    val updater get()=updateSession.value
    override fun onCleared(){if(localModelDelegate.isInitialized())kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch{localTextModel.close()};if(updateSession.isInitialized())updateSession.value.close();super.onCleared()}
    private val mutable=MutableStateFlow(AppState())
    val state=mutable.asStateFlow()
    lateinit var store: Store; private set
    private var draftJob: Job?=null
    private var operation: Job?=null
    private var resolution:Job?=null
    private val mediaDownloads by lazy {MediaDownloads(app)}
    init {viewModelScope.launch {
        try { store=withContext(Dispatchers.IO) {Store(app)};reload();mutable.value=mutable.value.copy(ready=true,localModelStatus=if(localTextModel.installed)"本机 AI 已下载 · 不上传资料" else mutable.value.localModelStatus)
            launch {store.protection.collect {value->mutable.value=mutable.value.copy(protection=value)}}
            launch {store.runAutomaticProtection()}
            launch {while(true) {runCatching{withContext(Dispatchers.IO){mediaDownloads.list()}}.onSuccess{jobs->mutable.value=mutable.value.copy(downloads=jobs)};delay(1200)}} }
        catch(e:Exception) {mutable.value=mutable.value.copy(message="资料打开失败：${e.message}")}
    }}
    private suspend fun reload() {val l=store.load();val a=store.appearance();mutable.value=mutable.value.copy(library=l,appearance=a)}
    fun receiveShare(text:String) {
        if(text.isBlank()) return
        if(text.length>32000){message("分享内容过长，请只分享链接");return}
        if(SharedLinks.parse(text)==null){if(Links.normalize(text)!=null) openDownloads(text) else message("未找到有效链接");return}
        mutable.value=mutable.value.copy(shares=mutable.value.shares+text)
    }
    fun dismissShare(){mutable.value=mutable.value.copy(shares=mutable.value.shares.drop(1))}
    fun importShare() {
        val text=mutable.value.shares.firstOrNull() ?: return
        if(!mutable.value.ready || mutable.value.busy)return
        draftJob?.cancel()
        work("正在识别分享链接") {
            mutable.value.editor?.let{store.draft(it)}
            val link=withContext(Dispatchers.IO){SharedLinks.resolve(text)} ?: error("链接无法识别")
            val draft=link.archive();store.draft(draft)
            mutable.value=mutable.value.copy(editor=draft,shares=mutable.value.shares.drop(1),draftStatus="草稿已保存 · 请核对识别结果")
        }
    }
    fun requestExport(a:Archive,photos:List<Photo>) {if(photos.isNotEmpty() && !mutable.value.busy) mutable.value=mutable.value.copy(exportRequest=MediaExportRequest(a,photos))}
    fun cancelExport(){mutable.value=mutable.value.copy(exportRequest=null)}
    fun exportMedia() {
        val request=mutable.value.exportRequest ?: return
        cancelExport()
        work("正在保存到相册") {
            val n=MediaExport.save(getApplication(),request,::photoFile){text->mutable.value=mutable.value.copy(progress=text)}
            message("已保存 $n 项到系统相册 · 留着吧")
        }
    }
    fun shareMedia(a:Archive,photos:List<Photo>) {
        runCatching{MediaExport.share(getApplication(),MediaExportRequest(a,photos),::photoFile)}.onFailure{message("暂时无法分享原件")}
    }
    fun receiveMedia(uris:List<Uri>) {
        if(uris.isNotEmpty()) mutable.value=mutable.value.copy(incomingMedia=(mutable.value.incomingMedia+uris).distinct().take(100))
    }
    fun dismissMedia(){mutable.value=mutable.value.copy(incomingMedia=emptyList())}
    fun importIncoming(targetId:String?=null) {
        val uris=mutable.value.incomingMedia;if(uris.isEmpty())return
        draftJob?.cancel()
        work("正在收录分享媒体") {
            mutable.value.editor?.let{store.draft(it)}
            val l=store.load()
            val a=if(targetId==null)Archive(name="分享收录") else l.drafts.firstOrNull{it.id==targetId} ?: l.active.firstOrNull{it.id==targetId} ?: error("档案已不存在")
            val result=store.importPhotos(uris)
            require(result.photos.isNotEmpty()) {result.errors.joinToString("；").ifBlank{"没有可读取的媒体"}}
            val draft=a.copy(photos=a.photos+result.photos)
            store.draft(draft)
            mutable.value=mutable.value.copy(editor=draft,incomingMedia=emptyList(),draftStatus="已收录 ${result.photos.size} 项 · 请保存")
            if(result.errors.isNotEmpty())message("部分媒体未收录：${result.errors.joinToString("；")}")
        }
    }
    fun openDownloads(input:String="") {mutable.value=mutable.value.copy(downloadsOpen=true,downloadInput=input.ifBlank{mutable.value.downloadInput})}
    fun closeDownloads() {resolution?.cancel();mutable.value=mutable.value.copy(downloadsOpen=false,resolving=false)}
    fun downloadInput(input:String) {mutable.value=mutable.value.copy(downloadInput=input.take(32000),mediaPlan=null,downloadError="")}
    fun resolveMedia() {
        if(mutable.value.resolving)return
        val input=mutable.value.downloadInput
        mutable.value=mutable.value.copy(resolving=true,downloadError="",mediaPlan=null)
        resolution=viewModelScope.launch {
            try {
                val plan=MediaLinks.resolve(getApplication(),input)
                if(mutable.value.downloadInput==input)mutable.value=mutable.value.copy(mediaPlan=plan)
            } catch(e:CancellationException){throw e}
              catch(e:Exception){mutable.value=mutable.value.copy(downloadError=e.message ?: "暂时无法解析此链接")}
            finally {mutable.value=mutable.value.copy(resolving=false)}
        }
    }
    fun enqueueMedia(indices:Set<Int>) {
        val plan=mutable.value.mediaPlan ?: return
        runCatching{mediaDownloads.enqueue(plan,plan.items.filterIndexed{index,_->index in indices})}
            .onSuccess{mutable.value=mutable.value.copy(mediaPlan=null,downloadInput="");message("已加入下载，离开页面仍可继续")}
            .onFailure{mutable.value=mutable.value.copy(downloadError=it.message.orEmpty())}
    }
    fun removeDownload(id:String){mediaDownloads.remove(id)}
    fun retryDownload(job:MediaDownload) {downloadInput(job.source);resolveMedia()}
    fun importDownload(job:MediaDownload,targetId:String?=null) {
        if(job.state!="待收录")return
        draftJob?.cancel()
        work("正在收录下载媒体") {
            require(mediaDownloads.list().any{it.id==job.id && it.state=="待收录"}) {"下载记录已变化，请重新选择"}
            mutable.value.editor?.let{store.draft(it)}
            val l=store.load()
            val a=if(targetId==null) Archive(name=job.title,fields=listOf(Field(name="来源链接",type="链接",value=job.source)))
                else l.drafts.firstOrNull{it.id==targetId} ?: l.active.firstOrNull{it.id==targetId} ?: error("档案已不存在")
            val result=store.importPhotos(listOf(Uri.fromFile(mediaDownloads.file(job))))
            require(result.photos.isNotEmpty()){result.errors.joinToString("；").ifBlank{"媒体读取失败"}}
            val draft=a.copy(photos=a.photos+result.photos)
            store.draft(draft)
            // The media is now a durable draft; editing/saving uses the existing archive pipeline.
            mediaDownloads.imported(job.id,draft.id)
            mutable.value=mutable.value.copy(editor=draft,downloadsOpen=false,draftStatus="已收录 · 请保存")
        }
    }
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
        if(!mutable.value.ready || mutable.value.busy) return
        val target=a?.let { formal -> mutable.value.library.drafts.firstOrNull{it.id==formal.id} ?: formal } ?: Archive(fields=listOf(Field()))
        mutable.value=mutable.value.copy(editor=target,draftStatus="正在保存草稿")
        change(target)
    }
    fun change(a: Archive) {
        if(mutable.value.busy) return
        invalidateTagSuggestions()
        mutable.value=mutable.value.copy(editor=a,draftStatus="正在保存草稿")
        draftJob?.cancel()
        draftJob=viewModelScope.launch {delay(200);try{store.draft(a);mutable.value=mutable.value.copy(draftStatus="草稿已保存");reload()}catch(e:CancellationException){throw e}catch(e:Exception){mutable.value=mutable.value.copy(draftStatus="草稿保存失败");message(e.message ?: "草稿保存失败")}}
    }
    fun leaveEditor() {
        invalidateTagSuggestions()
        if(!mutable.value.ready || mutable.value.busy) return
        val a=mutable.value.editor ?: return
        draftJob?.cancel()
        // Keep the editor available on failure; leaving must not discard the debounce window.
        work("保存编辑草稿") {store.draft(a);mutable.value=mutable.value.copy(editor=null,draftStatus="草稿已保存")}
    }
    fun discardEditor() {invalidateTagSuggestions();val a=mutable.value.editor ?: return;draftJob?.cancel();work("放弃编辑") {store.discard(a.id);mutable.value=mutable.value.copy(editor=null)} }
    fun saveEditor() {invalidateTagSuggestions();val a=mutable.value.editor ?: return;draftJob?.cancel();work("保存资料") {store.save(a);mutable.value=mutable.value.copy(editor=null);message("已保存「${a.name.trim()}」到 App，外部保护状态请看上方提示")} }
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
    fun searchPreferences(value:SearchPreferences){invalidateTagSuggestions();work("保存搜索规则") {store.searchPreferences(value)}}
    fun acceptTag(archiveId:String,tagId:String,expectedName:String) {
        val a=mutable.value.editor?.takeIf{it.id==archiveId} ?: return
        if(mutable.value.library.labels.none{it.id==tagId && it.kind=="tag" && it.name==expectedName})return
        change(a.copy(tags=(a.tags+tagId).distinct()))
    }
    fun rejectTag(archiveId:String,tagId:String,never:Boolean) {
        val a=mutable.value.editor?.takeIf{it.id==archiveId} ?: return
        val prefs=mutable.value.library.searchPreferences
        val next=if(never) {val rule=prefs.rule(a.category).copy(category=a.category);prefs.copy(rules=prefs.rules.filter{it.category!=a.category}+rule.copy(excluded=(rule.excluded+tagId).distinct()))}
            else prefs.copy(rejections=(prefs.rejections+TagRejection(archiveId,tagId)).distinct())
        searchPreferences(next)
    }
    fun appearance(a: Appearance)=work("保存外观") {store.appearance(a)}
    fun chooseProtectionFolder(uri:Uri)=work("检查保护文件夹") {
        val choices=store.inspectProtectionFolder(uri)
        if(choices.isEmpty()) {store.selectProtectionFolder(uri);message("留着吧将自动创建保护文件夹并保存原件，请等待“已保护”")}
        else mutable.value=mutable.value.copy(recoveryChoices=choices,protectionSelection=uri)
    }
    fun dismissProtectionSelection(){mutable.value=mutable.value.copy(recoveryChoices=emptyList(),protectionSelection=null)}
    fun useProtectionFolder() {
        val uri=mutable.value.protectionSelection ?: return
        work("启用自动保护") {store.selectProtectionFolder(uri);dismissProtectionSelection();message("开始保护当前资料，已有恢复版本仍保留")}
    }
    fun recoverProtection(choice:RecoveryChoice) {
        val uri=mutable.value.protectionSelection ?: return
        draftJob?.cancel()
        work("从保护文件夹恢复") {
            store.restoreProtectionFolder(uri,choice.name){text->mutable.value=mutable.value.copy(progress=text)}
            store.selectProtectionFolder(uri);dismissProtectionSelection();mutable.value=mutable.value.copy(editor=null)
            message("已校验并恢复全部资料与原件，恢复前资料仍保留，可在设置撤销恢复")
        }
    }
    fun acknowledgeImportWarning()=work("确认已核对原件") {store.acknowledgeImportWarning()}
    fun retryProtection() {if(mutable.value.ready)viewModelScope.launch {store.protectNow(forceVerify=true)}}
    fun backup(uri: Uri)=work("准备备份") {store.backup(uri){s -> mutable.value=mutable.value.copy(progress=s)};message("完整备份已保存并校验，可在所选文件位置找到")}
    fun restoreBackup(uri: Uri)=work("准备恢复") {store.restore(uri){s -> mutable.value=mutable.value.copy(progress=s)};mutable.value=mutable.value.copy(editor=null);message("资料已恢复，恢复前副本保留，可撤销恢复")}
    fun undoRestore()=work("还原恢复前资料") {store.undoRestore();message("已切换回恢复前资料")}
    fun clearCache()=work("清理图片缓存") {store.clearCache();message("图片缓存已清理，原照片与资料保留")}
    fun photoFile(p: Photo)=File(store.photoDir,p.file)
}
