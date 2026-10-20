package dev.h516.shicang

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class Appearance(val style:String="极简画廊", val mode:String="浅色", val accent:String="苔绿",
    val theme:String="月白", val skin:String="全幅", val fit:String="铺满") {
    fun json()=JSONObject().put("style",style).put("mode",mode).put("accent",accent).put("theme",theme).put("skin",skin).put("fit",fit)
    companion object {
        fun read(o:JSONObject):Appearance {
            val mode=o.optString("mode","跟随系统")
            val theme=o.optString("theme",when(mode){"深色"->"曜石";"浅色"->"月白";else->"跟随系统"})
            val skin=o.optString("skin",if(o.optString("style")=="纸质相框") "留白" else "暗房")
            val fit=o.optString("fit","完整")
            require(theme in listOf("曜石","月白","海盐","跟随系统") && skin in listOf("全幅","留白","暗房") && fit in listOf("完整","铺满")) {"外观设置不支持"}
            return Appearance(o.optString("style","极简画廊"),mode,o.optString("accent","苔绿"),theme,skin,fit)
        }
    }
}

data class ImportResult(val photos: List<Photo>, val errors: List<String>)
class Store(private val context: Context) {
    private val prefs=context.getSharedPreferences("device",Context.MODE_PRIVATE)
    private val gate=Mutex()
    private val protectionGate=Mutex()
    private var generation=prefs.getString("generation","initial")!!
    private var root=File(context.filesDir,"libraries/$generation").apply { mkdirs() }
    private var db=open(root)
    val photoDir get()=File(root,"photos").apply { mkdirs() }
    val isLocked get()=prefs.getBoolean("appLock",false)
    fun setLock(value: Boolean) { check(prefs.edit().putBoolean("appLock",value).commit()) }
    private fun open(dir: File): SQLiteDatabase {
        dir.mkdirs()
        return SQLiteDatabase.openOrCreateDatabase(File(dir,"library.db"),null).apply {
            require(version<=1) { "资料版本比当前应用新，请升级应用后再打开" }
            execSQL("CREATE TABLE IF NOT EXISTS objects(kind TEXT NOT NULL,id TEXT NOT NULL,json TEXT NOT NULL,PRIMARY KEY(kind,id))")
            execSQL("PRAGMA user_version=1")
        }
    }
    /** Caller must cancel its jobs first; wait for any active transaction before releasing SQLite. */
    suspend fun close()=withContext(Dispatchers.IO) {gate.withLock {if(db.isOpen)db.close()}}
    private fun rows(kind: String) = db.rawQuery("SELECT json FROM objects WHERE kind=?",arrayOf(kind)).use { c -> buildList { while(c.moveToNext()) add(JSONObject(c.getString(0))) } }
    private val protectionMutable=kotlinx.coroutines.flow.MutableStateFlow(readProtection())
    val protection: kotlinx.coroutines.flow.StateFlow<ProtectionState> = protectionMutable
    private val protectionChanges=kotlinx.coroutines.channels.Channel<Unit>(kotlinx.coroutines.channels.Channel.CONFLATED)
    private var recoveryFolder: RecoveryFolder?=null
    private fun readProtection():ProtectionState {
        val location=prefs.getString("protectionLocation","").orEmpty()
        return ProtectionState(if(location.isEmpty()) "未开启" else "待保护",if(location.isEmpty()) "目前仅保存在 App 内，清除数据或卸载会丢失" else "正在重新检查保护位置",location,prefs.getLong("protectionSuccess",0),prefs.getInt("protectionMedia",0))
    }
    private fun markProtectionDirty() {
        // Persist before a library change. A crash can cause a conservative pending state, never false success.
        check(prefs.edit().putBoolean("protectionDirty",true).commit()) {"保护状态无法保存，资料未变更"}
        if(protectionMutable.value.location.isNotEmpty()) protectionMutable.value=protectionMutable.value.copy(status="待保护",detail="最新修改已保存在 App 内，正在等待外部保护")
        protectionChanges.trySend(Unit)
    }
    private fun folder():RecoveryFolder {
        return recoveryFolder ?: RecoveryFolder(context,Uri.parse(requireNotNull(prefs.getString("protectionTree",null)){"请先选择本机保存位置"})).also{recoveryFolder=it}
    }
    suspend fun inspectProtectionFolder(uri:Uri):List<RecoveryChoice> = withContext(Dispatchers.IO) {
        RecoveryFolder.validateLocalTree(uri)
        RecoveryFolder(context,uri).choices()
    }
    suspend fun selectProtectionFolder(uri:Uri)=withContext(Dispatchers.IO) {gate.withLock {
        RecoveryFolder.validateLocalTree(uri)
        val next=RecoveryFolder(context,uri)
        check(prefs.edit().putString("protectionTree",uri.toString()).putString("protectionLocation",next.location).putBoolean("protectionDirty",true).putLong("protectionSuccess",0).putInt("protectionMedia",0).commit())
        recoveryFolder=next;protectionMutable.value=readProtection();protectionChanges.trySend(Unit)
    }}
    suspend fun runAutomaticProtection() {
        // Called once by the ViewModel. Sequential collection does not cancel an in-flight copy.
        if(prefs.contains("protectionTree")) protectNow(forceVerify=true)
        for(ignored in protectionChanges) {
            kotlinx.coroutines.delay(3000)
            while(protectionChanges.tryReceive().isSuccess) kotlinx.coroutines.delay(3000)
            if(prefs.contains("protectionTree") && prefs.getBoolean("protectionDirty",true)) protectNow()
        }
    }
    suspend fun protectNow(forceVerify:Boolean=false)=withContext(Dispatchers.IO) {
        if(!protectionGate.tryLock())return@withContext
        try {gate.withLock {
        if(!prefs.contains("protectionTree")) return@withLock
        val old=protectionMutable.value
        protectionMutable.value=old.copy(status="待保护",detail="正在检查外部保护")
        try {
            check(prefs.edit().putBoolean("protectionDirty",true).commit()) {"保护状态无法保存，请检查手机剩余空间"}
            val library=loadUnsafe();val manifest=Codec.library(library).put("appearance",rows("settings").firstOrNull() ?: Appearance().json()).put("incompleteImport",!prefs.getString("protectionImportWarning","").isNullOrBlank())
            folder().protect(manifest,{File(photoDir,it.file)},forceVerify) {text->protectionMutable.value=protectionMutable.value.copy(status="待保护",detail=text)}
            coroutineContext.ensureActive()
            val now=System.currentTimeMillis();val count=allRecoveryPhotos(library).size
            check(prefs.edit().putBoolean("protectionDirty",false).putLong("protectionSuccess",now).putInt("protectionMedia",count).commit())
            val warning=prefs.getString("protectionImportWarning","").orEmpty()
            protectionMutable.value=protectionMutable.value.copy(status=if(warning.isEmpty()) "已保护" else "待核对",detail=if(warning.isEmpty()) "当前资料与 $count 项原件已在外部保存并校验" else "已保护已收录的 $count 项原件，但$warning。未收录的原文件请勿删除",lastSuccess=now,mediaCount=count)
        } catch(e:CancellationException) {
            protectionMutable.value=old.copy(status="待保护",detail="保护中断，重新打开 App 后继续；请先保留原文件")
            throw e
        } catch(e:Exception) {
            protectionMutable.value=old.copy(status="保护失败",detail="${e.message ?: "请检查空间与权限"}。旧恢复版本仍保留，请先保留原文件")
        }
    }} finally {protectionGate.unlock()}
    }
    suspend fun acknowledgeImportWarning()=withContext(Dispatchers.IO) {gate.withLock {
        check(prefs.edit().remove("protectionImportWarning").remove("protectionPendingMedia").remove("protectionPriorWarning").commit());markProtectionDirty()
    }}
    suspend fun restoreProtectionFolder(uri:Uri,name:String,progress:(String)->Unit)=withContext(Dispatchers.IO) {gate.withLock {
        RecoveryFolder.validateLocalTree(uri)
        val newId=uid();val stage=File(context.filesDir,"libraries/$newId").apply{mkdirs()};val dir=File(stage,"photos").apply{mkdirs()}
        var committed=false
        try {
            val json=RecoveryFolder(context,uri).recover(name,dir,progress);val library=Codec.library(json)
            coroutineContext.ensureActive();markProtectionDirty()
            installRestoredLibrary(stage,newId,json,library);committed=true
        } finally {if(!committed && prefs.getString("generation",null)!=newId)stage.deleteRecursively()}
    }}
    private fun settleImportedMedia() {
        val pending=prefs.getString("protectionPendingMedia",null) ?: return
        val files=org.json.JSONArray(pending).strings()
        val referenced=allRecoveryPhotos(loadUnsafe()).map{it.file}.toSet()
        if(files.all{it in referenced}) check(prefs.edit().remove("protectionPendingMedia").putString("protectionImportWarning",prefs.getString("protectionPriorWarning","")).remove("protectionPriorWarning").commit())
    }
    private fun put(kind: String,id: String,json: JSONObject, target: SQLiteDatabase=db) { if(target===db)markProtectionDirty();target.execSQL("INSERT OR REPLACE INTO objects(kind,id,json) VALUES(?,?,?)",arrayOf(kind,id,json.toString())) }
    private fun remove(kind: String,id: String) { markProtectionDirty();db.execSQL("DELETE FROM objects WHERE kind=? AND id=?",arrayOf(kind,id)) }
    private fun loadUnsafe()=Library(rows("archive").map(Codec::archive),rows("draft").map(Codec::archive),
        rows("label").map { Label(it.getString("id"),it.getString("kind"),it.getString("name")) },
        rows("photoTrash").map { PhotoTrash(it.getString("id"),it.getString("archiveId"),Codec.photo(it.getJSONObject("photo")),it.getInt("index"),it.getLong("removed")) }, rows("template").map(Templates::read), SearchPreferences.read(rows("searchPreferences").firstOrNull()), rows("author").map(Codec::author),rows("longNote").map(LongNote::read),rows("longNoteDraft").map(LongNote::read))
    suspend fun load()=withContext(Dispatchers.IO) { gate.withLock { loadUnsafe() } }
    suspend fun noteSession(note:LongNote)=withContext(Dispatchers.IO) {gate.withLock {
        note.validated();NoteSession(rows("longNote").map(LongNote::read).firstOrNull{it.key==note.key},rows("longNoteDraft").map(LongNote::read).firstOrNull{it.key==note.key},generation)
    }}
    private fun requireNoteGeneration(token:String){if(token!=generation)throw StaleNoteSession()}
    suspend fun noteDraft(note:LongNote,token:String)=withContext(Dispatchers.IO) {gate.withLock {
        requireNoteGeneration(token);note.validated();put("longNoteDraft",note.key,note.json())
    }}
    suspend fun discardNoteDraft(note:LongNote,token:String)=withContext(Dispatchers.IO) {gate.withLock {requireNoteGeneration(token);remove("longNoteDraft",note.validated().key)}}
    suspend fun saveNote(note:LongNote,expected:LongNote?,token:String)=withContext(Dispatchers.IO) {gate.withLock {
        requireNoteGeneration(token);note.validated()
        val existing=rows("longNote").map(LongNote::read).firstOrNull{it.key==note.key}
        require(existing==expected){"笔记已在别处改变；草稿已保留，请重新打开核对"}
        db.beginTransaction();try {put("longNote",note.key,note.copy(updated=System.currentTimeMillis()).json());remove("longNoteDraft",note.key);db.setTransactionSuccessful()}finally{db.endTransaction()}
    }}
    suspend fun searchPreferences(value:SearchPreferences)=withContext(Dispatchers.IO) {gate.withLock {put("searchPreferences","rules",SearchPreferences.read(value.json()).json())}}
    suspend fun appearance()=withContext(Dispatchers.IO) { gate.withLock { rows("settings").firstOrNull()?.let(Appearance::read) ?: Appearance() } }
    suspend fun appearance(a: Appearance)=withContext(Dispatchers.IO) { gate.withLock { put("settings","appearance",a.json()) } }
    suspend fun saveTemplate(t:FieldTemplate)=withContext(Dispatchers.IO) {gate.withLock {
        Templates.validate(t)
        val existing=rows("template").map(Templates::read)
        require(existing.size<500 || existing.any{it.id==t.id}) {"模板已达上限"}
        require(existing.none{it.id!=t.id && normalizedLabel(it.name)==normalizedLabel(t.name)}) {"同名模板已存在"}
        put("template",t.id,Templates.json(t))
    }}
    suspend fun saveAuthor(input:Author,draft:Archive?=null,linkTo:String?=null)=withContext(Dispatchers.IO) {gate.withLock {
        val author=Codec.author(Codec.author(input.copy(name=input.name.trim(),updated=System.currentTimeMillis())))
        require(author.name.isNotBlank() || author.identities.any{it.nickname.isNotBlank() || it.profileUrl.isNotBlank()}) {"请填写作者称呼或主页"}
        val library=loadUnsafe();val others=library.authors.filter{it.id!=author.id}
        require(others.size<100000 && author.identities.none{identity->others.any{other->other.identities.any{it.id==identity.id}}}) {"作者或平台身份编号重复"}
        val target=linkTo?.let{id->library.active.firstOrNull{it.id==id} ?: error("档案已不存在")}
        if(draft!=null)require(draft.authorIds.all{id->id==author.id || others.any{it.id==id}}){"作者关联已变更"}
        fun linked(a:Archive)=a.copy(authorIds=(a.authorIds+author.id).distinct(),updated=System.currentTimeMillis())
        db.beginTransaction();try {
            put("author",author.id,Codec.author(author))
            if(draft!=null)put("draft",draft.id,Codec.archive(linked(draft)))
            else if(target!=null) {
                put("archive",target.id,Codec.archive(linked(target)))
                library.drafts.firstOrNull{it.id==target.id}?.let{put("draft",it.id,Codec.archive(linked(it)))}
            }
            db.setTransactionSuccessful()
        }finally{db.endTransaction()}
    }}
    suspend fun linkAuthor(authorId:String,archiveId:String,linked:Boolean)=withContext(Dispatchers.IO) {gate.withLock {
        val library=loadUnsafe()
        require(library.authors.any{it.id==authorId}) {"作者已不存在"}
        val archive=library.active.firstOrNull{it.id==archiveId} ?: error("档案已不存在")
        fun links(a:Archive)=a.copy(authorIds=if(linked)(a.authorIds+authorId).distinct() else a.authorIds-authorId,updated=System.currentTimeMillis())
        db.beginTransaction();try {
            put("archive",archive.id,Codec.archive(links(archive)))
            library.drafts.firstOrNull{it.id==archiveId}?.let{put("draft",it.id,Codec.archive(links(it)))}
            db.setTransactionSuccessful()
        }finally{db.endTransaction()}
    }}
    suspend fun deleteTemplate(id:String)=withContext(Dispatchers.IO) {gate.withLock {remove("template",id)}}
    suspend fun draft(a: Archive)=withContext(Dispatchers.IO) { gate.withLock { put("draft",a.id,Codec.archive(a));settleImportedMedia() } }
    suspend fun discard(id: String)=withContext(Dispatchers.IO) { gate.withLock { remove("draft",id) } }
    private fun saveTrash(t: PhotoTrash) { put("photoTrash",t.id,JSONObject().put("id",t.id).put("archiveId",t.archiveId).put("photo",Codec.photo(t.photo)).put("index",t.index).put("removed",t.removed)) }
    suspend fun save(input: Archive)=withContext(Dispatchers.IO) { gate.withLock {
        val a=input.copy(fields=input.fields.filter{it.name.isNotBlank() || it.value.isNotBlank()}.map {f -> if(f.type=="链接" && f.value.isNotBlank()) f.copy(value=Links.normalize(f.value) ?: error("${f.name.ifBlank{"链接"}}：请填写有效的网址或分享链接")) else f})
        require(a.name.isNotBlank()) { "请填写名称或称呼" }
        require(a.fields.all { it.name.isNotBlank() && (it.value.isBlank() || when(it.type) {
            "数字" -> it.value.toBigDecimalOrNull()!=null
            "链接" -> Links.normalize(it.value)!=null
            "日期" -> runCatching { java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT).apply { isLenient=false }.parse(it.value)?.let { d -> java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.ROOT).format(d)==it.value } ?: false }.getOrDefault(false)
            else -> true
        }) }) { "字段名不能留空；数字填写数值，日期使用 YYYY-MM-DD" }
        require(a.fields.size<=40 && a.fields.map{normalizedLabel(it.name)}.distinct().size==a.fields.size) {"字段最多 40 个，名称不能重复"}
        require(a.photos.all { File(photoDir,it.file).isFile }) { "照片尚未保存，请等待导入完成" }
        val library=loadUnsafe()
        require((a.category.isEmpty() || library.labels.any{it.id==a.category && it.kind=="category"}) && a.tags.all{id -> library.labels.any{it.id==id && it.kind=="tag"}}) {"分类或标签已变更，请重新选择"}
        require(a.authorIds.distinct().size==a.authorIds.size && a.authorIds.all{id->library.authors.any{it.id==id}}) {"作者关联已变更，请重新选择"}
        val old=library.archives.firstOrNull { it.id==a.id }
        db.beginTransaction()
        try {
            old?.photos?.forEachIndexed { index,p -> if(a.photos.none { it.id==p.id }) saveTrash(PhotoTrash(archiveId=a.id,photo=p,index=index,removed=System.currentTimeMillis())) }
            put("archive",a.id,Codec.archive(a.copy(name=a.name.trim(),cover=a.coverPhoto()?.id.orEmpty(),updated=System.currentTimeMillis(),deleted=0)))
            remove("draft",a.id); db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        settleImportedMedia()
    } }
    suspend fun trash(id: String, restore: Boolean=false)=withContext(Dispatchers.IO) { gate.withLock {
        val a=loadUnsafe().archives.firstOrNull { it.id==id } ?: return@withLock
        put("archive",id,Codec.archive(a.copy(deleted=if(restore) 0 else System.currentTimeMillis(),updated=System.currentTimeMillis())))
    } }
    suspend fun restorePhoto(id: String)=withContext(Dispatchers.IO) { gate.withLock {
        val l=loadUnsafe(); val t=l.photoTrash.firstOrNull { it.id==id } ?: return@withLock
        val a=l.archives.firstOrNull { it.id==t.archiveId } ?: error("原档案已经永久删除")
        val ps=a.photos.toMutableList().apply { if(none { it.id==t.photo.id }) add(t.index.coerceAtMost(size),t.photo) }
        db.beginTransaction();try { put("archive",a.id,Codec.archive(a.copy(photos=ps,updated=System.currentTimeMillis()))); remove("photoTrash",id);db.setTransactionSuccessful() } finally {db.endTransaction()}
    } }
    suspend fun label(kind: String, name: String): String=withContext(Dispatchers.IO) { gate.withLock {
        require(name.isNotBlank()); val existing=loadUnsafe().labels.firstOrNull { it.kind==kind && normalizedLabel(it.name)==normalizedLabel(name) }
        existing?.id ?: uid().also { put("label",it,JSONObject().put("id",it).put("kind",kind).put("name",name.trim())) }
    } }
    suspend fun renameLabel(id: String,name: String)=withContext(Dispatchers.IO) { gate.withLock {
        require(name.isNotBlank()) { "名称不能留空" }; val l=loadUnsafe();val item=l.labels.first { it.id==id }
        require(l.labels.none { it.id!=id && it.kind==item.kind && normalizedLabel(it.name)==normalizedLabel(name) }) { "同名分类或标签已存在" }
        put("label",id,JSONObject().put("id",id).put("kind",item.kind).put("name",name.trim()))
    } }
    suspend fun deleteLabel(id: String)=withContext(Dispatchers.IO) { gate.withLock {
        val l=loadUnsafe();db.beginTransaction();try {
            l.archives.forEach { put("archive",it.id,Codec.archive(it.copy(category=if(it.category==id) "" else it.category,tags=it.tags-id))) }
            l.drafts.forEach { put("draft",it.id,Codec.archive(it.copy(category=if(it.category==id) "" else it.category,tags=it.tags-id))) }
            remove("label",id);db.setTransactionSuccessful()
        } finally {db.endTransaction()}
    } }
    suspend fun importPhotos(uris: List<Uri>): ImportResult=withContext(Dispatchers.IO) { gate.withLock {
        val priorWarning=prefs.getString("protectionImportWarning","").orEmpty()
        check(prefs.edit().putString("protectionImportWarning","有一批导入尚未完整结束").commit());markProtectionDirty()
        val good=mutableListOf<Photo>();val errors=mutableListOf<String>()
        val operation=coroutineContext
        try {
        uris.forEachIndexed { index,uri ->
            operation.ensureActive()
            val id=uid();val tmp=File(photoDir,"$id.pending"); val final=File(photoDir,"$id.original")
            try {
                val declared=context.contentResolver.getType(uri).orEmpty()
                val digest=MessageDigest.getInstance("SHA-256")
                val maximum=if(declared.startsWith("image/")) Codec.MAX_PHOTO_BYTES else Codec.MAX_MEDIA_BYTES
                val expected=LargeMediaIo.declaredSize(context.contentResolver,uri)
                expected?.let {require(it<=maximum){"所选媒体超过安全上限：视频6GB，照片200MiB"};LargeMediaIo.requireSpace(photoDir.usableSpace,it)}
                val bytes=context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "无法读取所选媒体" }
                    tmp.outputStream().use { out -> LargeMediaIo.copy(input,out,maximum,available={photoDir.usableSpace},
                        check={operation.ensureActive()},onBytes={buffer,count->digest.update(buffer,0,count)}) }
                }
                val metadata=inspectMedia(tmp,declared)
                require(bytes<=(if(metadata.first.startsWith("video/")) Codec.MAX_MEDIA_BYTES else Codec.MAX_PHOTO_BYTES)) {"单张照片最多200MB"}
                require(tmp.renameTo(final)) { "照片保存失败" }
                good.add(Photo(id,final.name,bytes,digest.digest().hex(),metadata.first,metadata.second))
            } catch(e:CancellationException) {tmp.delete();throw e}
            catch(e: Exception) {tmp.delete();errors.add("第${index+1}项：${e.message ?: "导入失败"}")}
        }
        } catch(e:CancellationException) {good.forEach{File(photoDir,it.file).delete()};throw e}
        val pending=org.json.JSONArray(prefs.getString("protectionPendingMedia","[]")).strings()+good.map{it.file}
        val warning=if(errors.isNotEmpty()) "有 ${errors.size} 项媒体导入失败，请核对原文件并重新导入" else priorWarning
        check(prefs.edit().putString("protectionPendingMedia",org.json.JSONArray(pending.distinct()).toString()).putString("protectionPriorWarning",warning).putString("protectionImportWarning",if(warning.isNotEmpty()) warning else "有已导入媒体尚未完整写入档案").commit())
        ImportResult(good,errors)
    } }
    /** Append a separate derivative to the current archive, never replace its source or stale metadata. */
    internal suspend fun appendEditedVideo(archiveId:String,original:Photo,source:File,edited:File):Photo=withContext(Dispatchers.IO) { gate.withLock {
        val operation=coroutineContext
        val library=loadUnsafe()
        val archive=library.active.firstOrNull{it.id==archiveId} ?: error("原档案已变更，请重新打开视频")
        require(archive.photos.any{it==original} && source.canonicalFile==File(photoDir,original.file).canonicalFile){"原视频已变更，请重新打开"}
        require(source.isFile && source.length()==original.bytes && sha(source){operation.ensureActive()}==original.sha256){"原视频已变更，未保存编辑副本"}
        require(library.drafts.none{it.id==archiveId}){"该档案有未保存草稿，请先保存再编辑视频"}
        require(edited.isFile && edited.length() in 1..Codec.MAX_MEDIA_BYTES){"编辑副本不完整或超过 6GB 安全上限"}
        LargeMediaIo.requireSpace(photoDir.usableSpace,edited.length())
        val id=uid();val pending=File(photoDir,"$id.pending");val final=File(photoDir,"$id.original")
        var committed=false
        try {
            edited.inputStream().use{input->pending.outputStream().use{output->LargeMediaIo.copy(input,output,Codec.MAX_MEDIA_BYTES,edited.length(),{photoDir.usableSpace},{operation.ensureActive()})}}
            require(pending.length()==edited.length() && sha(pending){operation.ensureActive()}==sha(edited){operation.ensureActive()}){"副本校验失败"}
            val metadata=inspectMedia(pending,"video/mp4")
            require(metadata.first.startsWith("video/") && metadata.second>0){"副本不是有效视频"}
            val photo=Photo(id,final.name,pending.length(),sha(pending){operation.ensureActive()},metadata.first,metadata.second)
            operation.ensureActive()
            withContext(kotlinx.coroutines.NonCancellable) {
                check(pending.renameTo(final)){"副本保存失败"}
                db.beginTransaction()
                try {put("archive",archive.id,Codec.archive(archive.copy(photos=archive.photos+photo,updated=System.currentTimeMillis())));db.setTransactionSuccessful()}
                finally {db.endTransaction()}
                committed=true
            }
            photo
        } finally {pending.delete();if(!committed)final.delete()}
    } }
    private fun photos(l: Library)=(l.archives+l.drafts).flatMap { it.photos }.plus(l.photoTrash.map {it.photo}).distinctBy {it.file}
    suspend fun space()=withContext(Dispatchers.IO) { gate.withLock { mapOf("照片与视频" to photoDir.walkTopDown().filter{it.isFile}.sumOf{it.length()},"图片缓存" to File(context.cacheDir,"previews").walkTopDown().filter{it.isFile}.sumOf{it.length()},"恢复前副本" to File(context.filesDir,"libraries").listFiles().orEmpty().filter{it.name!=generation}.sumOf { f -> f.walkTopDown().filter{it.isFile}.sumOf{it.length()} }) } }
    suspend fun purge()=withContext(Dispatchers.IO) { gate.withLock {
        val l=loadUnsafe();db.beginTransaction();try {
            l.archives.filter {it.deleted!=0L}.forEach {remove("archive",it.id);remove("draft",it.id)}
            markProtectionDirty();db.execSQL("DELETE FROM objects WHERE kind='photoTrash'");db.setTransactionSuccessful()
        } finally {db.endTransaction()}
        val used=photos(loadUnsafe()).map{it.file}.toSet();photoDir.listFiles().orEmpty().filter {it.name !in used}.forEach {check(it.delete()) {"文件暂时无法清理，资料已更新，可重试"}}
    } }
    suspend fun clearCache()=withContext(Dispatchers.IO) { File(context.cacheDir,"previews").deleteRecursively();Unit }
    /** Preflight before opening the system destination picker; the writer checks again against fresh state. */
    suspend fun validateBackupCapacity()=withContext(Dispatchers.IO) {gate.withLock {
        val l=loadUnsafe();val ps=photos(l)
        val manifestBytes=Codec.library(l).put("appearance",rows("settings").firstOrNull() ?: Appearance().json()).toString().toByteArray(Charsets.UTF_8).size.toLong()
        BackupLimits.DEFAULT.validateExport(manifestBytes,ps.asSequence().map{it.bytes})
        LargeMediaIo.requireSpace(context.cacheDir.usableSpace,ps.sumOf{it.bytes}+manifestBytes+1024L*1024)
    }}
    suspend fun backup(uri: Uri, progress: (String)->Unit)=withContext(Dispatchers.IO) { gate.withLock {
        val operation=coroutineContext
        val l=loadUnsafe();val ps=photos(l);val appearance=rows("settings").firstOrNull() ?: Appearance().json()
        val manifest=Codec.library(l).put("appearance",appearance).toString().toByteArray(Charsets.UTF_8)
        // Reject unrestorable archives before creating a zip or touching a previous backup.
        BackupLimits.DEFAULT.validateExport(manifest.size.toLong(),ps.asSequence().map { it.bytes })
        val tmp=File(context.cacheDir,"backup-${uid()}.zip")
        var destinationOpened=false
        try {
            LargeMediaIo.requireSpace(context.cacheDir.usableSpace,ps.sumOf{it.bytes}+manifest.size+1024L*1024)
            ZipOutputStream(LargeMediaIo.guardedOutput(tmp){operation.ensureActive()}.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"));zip.write(manifest);zip.closeEntry()
                // Media is already compressed. DEFLATED level 0 keeps streaming/ZIP64 without wasting CPU re-compressing GB videos.
                zip.setLevel(java.util.zip.Deflater.NO_COMPRESSION)
                ps.forEachIndexed { i,p ->
                    operation.ensureActive()
                    val f=File(photoDir,p.file);require(f.isFile && f.length()==p.bytes) { "照片校验失败，备份未完成" }
                    val digest=MessageDigest.getInstance("SHA-256")
                    progress("打包媒体 ${i+1}/${ps.size}");zip.putNextEntry(ZipEntry("photos/${p.file}"))
                    f.inputStream().use {LargeMediaIo.copy(it,zip,p.bytes,p.bytes,check={operation.ensureActive()},onBytes={b,n->digest.update(b,0,n)})}
                    require(digest.digest().hex()==p.sha256){"照片校验失败，备份未完成"};zip.closeEntry()
                }
            }
            progress("写入所选位置")
            operation.ensureActive()
            context.contentResolver.openOutputStream(uri,"w").use { out -> requireNotNull(out);destinationOpened=true;tmp.inputStream().use {copyChecked(it,out){operation.ensureActive()}} }
            // Read back the destination, not just the temporary zip.
            val actual=context.contentResolver.openInputStream(uri).use { requireNotNull(it);hashStream(it){operation.ensureActive()} }
            require(actual==sha(tmp){operation.ensureActive()}) { "备份位置校验失败，请重新备份" }
            prefs.edit().putLong("lastBackup",System.currentTimeMillis()).putString("backupLocation",uri.toString()).apply()
        } catch(e:Exception) { if(destinationOpened) runCatching { android.provider.DocumentsContract.deleteDocument(context.contentResolver,uri) };throw e }
        finally {tmp.delete()}
    } }
    suspend fun restore(uri: Uri, progress: (String)->Unit)=withContext(Dispatchers.IO) { gate.withLock {
        val operation=coroutineContext
        val newId=uid();val stage=File(context.filesDir,"libraries/$newId").apply{mkdirs()};val dir=File(stage,"photos").apply{mkdirs()}
        var committed=false
        try {
            var manifest: JSONObject?=null;var total=0L;val entries=mutableSetOf<String>()
            context.contentResolver.openInputStream(uri).use { input -> requireNotNull(input);ZipInputStream(input.buffered()).use { zip ->
                while(true) {
                    operation.ensureActive()
                    val entry=zip.nextEntry ?: break;val n=entry.name
                    require(!entry.isDirectory && entries.add(n) && entries.size<=BackupLimits.DEFAULT.entries && (n=="manifest.json" || n.matches(Regex("photos/[a-f0-9-]{36}\\.original")))) { "备份包含异常或重复条目" }
                    val max=if(n=="manifest.json") BackupLimits.DEFAULT.manifestBytes else BackupLimits.DEFAULT.mediaBytes
                    val dst=if(n=="manifest.json") File(stage,"manifest.json") else File(dir,n.removePrefix("photos/"))
                    require(entry.size<0 || entry.size<=max){"备份条目大小超过安全上限"}
                    dst.outputStream().use {out ->
                        LargeMediaIo.copy(zip,out,minOf(max,BackupLimits.DEFAULT.totalBytes-total),
                            available={stage.usableSpace},check={operation.ensureActive()},onBytes={_,count->total+=count})
                    }
                    if(n=="manifest.json") manifest=JSONObject(dst.readText(Charsets.UTF_8))
                    progress("校验恢复文件 ${entries.size}");zip.closeEntry()
                }
            } }
            val o=requireNotNull(manifest) { "缺少备份清单" };val l=Codec.library(o);val ps=photos(l)
            require(entries==ps.map { "photos/${it.file}" }.toSet()+"manifest.json") { "备份照片清单不一致" }
            ps.forEach { operation.ensureActive();val f=File(dir,it.file);require(f.length()==it.bytes && sha(f){operation.ensureActive()}==it.sha256) {"备份照片损坏"} }
            progress("切换到已校验资料")
            operation.ensureActive();markProtectionDirty()
            installRestoredLibrary(stage,newId,o,l);committed=true
        } finally {if(!committed && prefs.getString("generation",null)!=newId) stage.deleteRecursively()}
    } }
    private fun installRestoredLibrary(stage:File,newId:String,o:JSONObject,l:Library) {
        val next=open(stage);var installed=false;try {
                next.beginTransaction();try {
                    l.archives.forEach{put("archive",it.id,Codec.archive(it),next)};l.drafts.forEach{put("draft",it.id,Codec.archive(it),next)}
                    l.labels.forEach{put("label",it.id,JSONObject().put("id",it.id).put("kind",it.kind).put("name",it.name),next)}
                    l.photoTrash.forEach{put("photoTrash",it.id,JSONObject().put("id",it.id).put("archiveId",it.archiveId).put("photo",Codec.photo(it.photo)).put("index",it.index).put("removed",it.removed),next)}
                    l.templates.forEach{put("template",it.id,Templates.json(it),next)}
                    l.authors.forEach{put("author",it.id,Codec.author(it),next)}
                    l.notes.forEach{put("longNote",it.key,it.json(),next)}
                    l.noteDrafts.forEach{put("longNoteDraft",it.key,it.json(),next)}
                    put("searchPreferences","rules",l.searchPreferences.json(),next)
                    put("settings","appearance",Appearance.read(o.optJSONObject("appearance") ?: JSONObject()).json(),next)
                    next.setTransactionSuccessful()
                } finally {next.endTransaction()}
        val edit=prefs.edit().putString("previousGeneration",generation).putString("generation",newId)
        if(o.optBoolean("incompleteImport") && prefs.getString("protectionImportWarning","").isNullOrBlank()) edit.putString("protectionImportWarning","此恢复版本包含未完成或部分失败的导入，请核对未收录的原文件")
        check(edit.commit()) { "恢复状态保存失败" }
        val previous=db;db=next;generation=newId;root=stage;installed=true
        runCatching{previous.close()}
        } finally {if(!installed)next.close()}
    }
    fun lastBackup()=prefs.getLong("lastBackup",0)
    fun canUndoRestore():Boolean {
        val old=prefs.getString("previousGeneration",null) ?: return false
        return (Codec.validId(old)||old=="initial") && File(context.filesDir,"libraries/$old/library.db").isFile
    }
    suspend fun undoRestore()=withContext(Dispatchers.IO) {gate.withLock {
        val old=prefs.getString("previousGeneration",null) ?: error("没有恢复前副本")
        require(Codec.validId(old)||old=="initial");val next=File(context.filesDir,"libraries/$old");require(File(next,"library.db").isFile)
        val prepared=open(next);var installed=false
        try {
            markProtectionDirty();check(prefs.edit().putString("generation",old).putString("previousGeneration",generation).commit())
            val previous=db;db=prepared;generation=old;root=next;installed=true;runCatching{previous.close()}
        } finally {if(!installed)prepared.close()}
    }}
}
fun ByteArray.hex()=joinToString("") {"%02x".format(it)}
fun hashStream(input: java.io.InputStream,check:()->Unit={}): String {val d=MessageDigest.getInstance("SHA-256");val b=ByteArray(65536);while(true){check();val n=input.read(b);if(n<0) break;d.update(b,0,n)};return d.digest().hex()}
fun sha(file: File,check:()->Unit={})=FileInputStream(file).use{hashStream(it,check)}
internal fun copyChecked(input:java.io.InputStream,output:java.io.OutputStream,check:()->Unit) {val b=ByteArray(65536);while(true){check();val n=input.read(b);if(n<0)break;output.write(b,0,n)}}
