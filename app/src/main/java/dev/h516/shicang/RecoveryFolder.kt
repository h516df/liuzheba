package dev.h516.shicang

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract as Documents
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/** User-owned SAF recovery data. Never overwrite or delete a previously created document. */
internal class RecoveryFolder(private val context: Context, val tree: Uri) {
    data class Document(val uri: Uri, val name: String, val size: Long, val modified: Long, val mime: String)
    data class Snapshot(val document: Document, val time: Long, val library: JSONObject, val blobs: Map<String,String>)
    private val resolver=context.contentResolver
    private val root=Documents.buildDocumentUriUsingTree(tree,Documents.getTreeDocumentId(tree))
    private var folder: Uri?=null
    private val rootIsRecovery=Documents.getTreeDocumentId(tree).substringAfterLast('/').substringAfter(':')==DIRECTORY
    val location: String get()=Documents.getTreeDocumentId(tree)+(if(rootIsRecovery) "" else "/"+DIRECTORY)
    private val columns=arrayOf(Documents.Document.COLUMN_DOCUMENT_ID,Documents.Document.COLUMN_DISPLAY_NAME,Documents.Document.COLUMN_SIZE,Documents.Document.COLUMN_LAST_MODIFIED,Documents.Document.COLUMN_MIME_TYPE)
    private fun query(uri:Uri)=if(android.os.Build.VERSION.SDK_INT>=26) resolver.query(uri,columns,android.os.Bundle(),null) else resolver.query(uri,columns,null,null,null)
    private fun document(uri:Uri):Document= requireNotNull(query(uri)){"无法读取保护文件"}.use {c->
        require(c.moveToFirst()) {"保护文件已消失"}
        Document(Documents.buildDocumentUriUsingTree(tree,c.getString(0)),c.getString(1),c.getLong(2),c.getLong(3),c.getString(4))
    }

    private fun children(parent:Uri):List<Document> {
        val uri=Documents.buildChildDocumentsUriUsingTree(tree,Documents.getDocumentId(parent))
        return requireNotNull(query(uri)) {"无法读取保护文件夹，请重新授权"}.use { c ->
            buildList { while(c.moveToNext()) {
                require(size<250000) {"保护文件夹条目过多，请选择新的本机位置；旧版本仍保留"}
                add(Document(Documents.buildDocumentUriUsingTree(tree,c.getString(0)),c.getString(1),c.getLong(2),c.getLong(3),c.getString(4)))
            } }
        }
    }
    private fun directory(create:Boolean):Uri? {
        folder?.let{return it}
        if(rootIsRecovery) {
            require(document(root).mime==Documents.Document.MIME_TYPE_DIR) {"请选择保护文件夹"}
            folder=root;return root
        }
        val matches=children(root).filter{it.name==DIRECTORY}
        require(matches.size<=1 && matches.none{it.mime!=Documents.Document.MIME_TYPE_DIR}) {"同名保护位置不是唯一文件夹，请选择其他位置"}
        return (matches.singleOrNull()?.uri ?: if(create) Documents.createDocument(resolver,root,Documents.Document.MIME_TYPE_DIR,DIRECTORY) else null).also{folder=it}
    }
    private fun create(name:String,mime:String):Uri= requireNotNull(Documents.createDocument(resolver,requireNotNull(directory(true)),mime,name)) {"无法创建保护文件，请检查文件夹权限和剩余空间"}
    private fun input(uri:Uri)=requireNotNull(resolver.openInputStream(uri)) {"无法读取保护文件"}
    private suspend fun digest(uri:Uri, expectedBytes:Long):String {
        val operation=currentCoroutineContext();val hash=MessageDigest.getInstance("SHA-256");var count=0L
        input(uri).use { stream -> val b=ByteArray(65536);while(true){operation.ensureActive();val n=stream.read(b);if(n<0)break;count+=n;require(count<=expectedBytes){"保护文件大小不符"};hash.update(b,0,n)} }
        require(count==expectedBytes){"保护文件不完整"};return hash.digest().hex()
    }
    private suspend fun readBounded(uri:Uri,max:Long):ByteArray {
        val operation=currentCoroutineContext();val bytes=ByteArrayOutputStream()
        input(uri).use { stream -> val b=ByteArray(65536);var count=0L;while(true){operation.ensureActive();val n=stream.read(b);if(n<0)break;count+=n;require(count<=max){"保护清单超过上限"};bytes.write(b,0,n)} }
        return bytes.toByteArray()
    }
    suspend fun choices():List<RecoveryChoice> {
        val dir=directory(false) ?: return emptyList()
        // Invalid/interrupted files are not recovery points. Do not delete them or user files.
        val result=mutableListOf<RecoveryChoice>()
        for(doc in children(dir).filter{SNAPSHOT.matches(it.name)}.sortedByDescending{it.name}) {
            try {
                val snapshot=readSnapshot(doc);val library=Codec.library(snapshot.library)
                result.add(RecoveryChoice(doc.name,snapshot.time,library.archives.size,allRecoveryPhotos(library).size))
                if(result.size==100)break
            } catch(e:kotlinx.coroutines.CancellationException){throw e} catch(_:Exception){ /* Not a verified recovery point. */ }
        }
        return result
    }
    private suspend fun readSnapshot(doc:Document):Snapshot {
        val match= requireNotNull(SNAPSHOT.matchEntire(doc.name))
        val bytes=readBounded(doc.uri,BackupLimits.DEFAULT.manifestBytes)
        require(MessageDigest.getInstance("SHA-256").digest(bytes).hex()==match.groupValues[2]) {"保护清单校验失败"}
        val json=JSONObject(bytes.toString(Charsets.UTF_8));require(json.getInt("recoveryFormat")==1)
        val library=json.getJSONObject("library");val l=Codec.library(library);Appearance.read(library.getJSONObject("appearance"))
        val ps=allRecoveryPhotos(l)
        val blobs=json.getJSONObject("blobs");require(blobs.length()==ps.size)
        val mapping=ps.associate {p -> p.file to blobs.getString(p.file).also {require(BLOB.matches(it))} }
        BackupLimits.DEFAULT.validateExport(bytes.size.toLong(),ps.asSequence().map{it.bytes})
        return Snapshot(doc,match.groupValues[1].toLong(),library,mapping)
    }
    suspend fun protect(library:JSONObject,source:(Photo)->File,forceVerify:Boolean=false,progress:(String)->Unit):Snapshot {
        val operation=currentCoroutineContext();val l=Codec.library(library);val ps=allRecoveryPhotos(l)
        // Check capacity before touching the external location, including wrapper overhead below.
        BackupLimits.DEFAULT.validateExport(library.toString().toByteArray().size.toLong(),ps.asSequence().map{it.bytes})
        val dir=requireNotNull(directory(true));var docs=children(dir)
        val mapping=JSONObject()
        ps.forEachIndexed { index,p ->
            operation.ensureActive();progress("正在保护原件 ${index+1}/${ps.size}")
            val candidates=docs.filter{it.name=="${p.sha256}.original" || it.name.startsWith("${p.sha256}-")}
            var found:Document?=null
            for(doc in candidates) {
                if(!BLOB.matches(doc.name))continue
                val valid=try {digest(doc.uri,p.bytes)==p.sha256} catch(e:kotlinx.coroutines.CancellationException){throw e} catch(_:Exception){false}
                if(valid){found=doc;break}
            }
            if(found==null) {
                val file=source(p);require(file.isFile && file.length()==p.bytes){"原件缺失或大小不符，请保留原文件"}
                val name=if(candidates.isEmpty()) "${p.sha256}.original" else "${p.sha256}-${uid()}.original"
                val uri=create(name,"application/octet-stream")
                val hash=MessageDigest.getInstance("SHA-256");var count=0L
                requireNotNull(resolver.openOutputStream(uri,"w")).use { out -> file.inputStream().use { stream -> val b=ByteArray(65536);while(true){operation.ensureActive();val n=stream.read(b);if(n<0)break;count+=n;require(count<=p.bytes);hash.update(b,0,n);out.write(b,0,n)} } }
                require(count==p.bytes && hash.digest().hex()==p.sha256){"原件校验失败，保护尚未完成"}
                require(digest(uri,p.bytes)==p.sha256){"外部原件回读校验失败"}
                found=document(uri);docs=docs+found;require(found.name==name){"系统更改了文件名，请重试"}
            }
            mapping.put(p.file,found.name)
        }
        val body=JSONObject().put("recoveryFormat",1).put("library",library).put("blobs",mapping).toString().toByteArray(Charsets.UTF_8)
        BackupLimits.DEFAULT.validateExport(body.size.toLong(),ps.asSequence().map{it.bytes})
        val hash=MessageDigest.getInstance("SHA-256").digest(body).hex()
        // No new version for unchanged content; still verify all needed original files above.
        for(previous in docs.filter{SNAPSHOT.matches(it.name) && it.name.endsWith("-$hash.json")}) {
            try {return readSnapshot(previous)} catch(e:kotlinx.coroutines.CancellationException){throw e} catch(_:Exception) { /* An interrupted manifest is not a recovery point. Append a fresh one. */ }
        }
        progress("正在校验恢复清单")
        val timestamp=maxOf(System.currentTimeMillis(),(docs.mapNotNull{SNAPSHOT.matchEntire(it.name)?.groupValues?.get(1)?.toLongOrNull()}.maxOrNull() ?: 0L)+1)
        val name="liuce-v1-$timestamp-$hash.json"
        val uri=create(name,"application/json")
        requireNotNull(resolver.openOutputStream(uri,"w")).use {it.write(body)}
        operation.ensureActive();require(digest(uri,body.size.toLong())==hash){"恢复清单回读校验失败"}
        return readSnapshot(document(uri))
    }
    suspend fun recover(snapshotName:String, destination:File,progress:(String)->Unit):JSONObject {
        val dir=requireNotNull(directory(false)){"保护文件夹不存在"}
        val docs=children(dir);val snapshot=readSnapshot(docs.single{it.name==snapshotName})
        val operation=currentCoroutineContext();val ps=allRecoveryPhotos(Codec.library(snapshot.library))
        ps.forEachIndexed { index,p ->
            operation.ensureActive();val name=snapshot.blobs.getValue(p.file)
            val doc=docs.singleOrNull{it.name==name} ?: error("恢复原件缺失：第 ${index+1} 项，当前资料未变更")
            progress("校验恢复原件 ${index+1}/${ps.size}")
            val target=File(destination,p.file);val hash=MessageDigest.getInstance("SHA-256");var count=0L
            input(doc.uri).use { stream ->target.outputStream().use {out -> val b=ByteArray(65536);while(true){operation.ensureActive();val n=stream.read(b);if(n<0)break;count+=n;require(count<=p.bytes){"恢复原件超出清单大小"};hash.update(b,0,n);out.write(b,0,n)} } }
            require(count==p.bytes && hash.digest().hex()==p.sha256){"恢复原件校验失败，当前资料未变更"}
        }
        return snapshot.library
    }
    companion object {
        const val DIRECTORY="留册自动保护"
        private val SNAPSHOT=Regex("liuce-v1-([0-9]{13})-([a-f0-9]{64})\\.json")
        private val BLOB=Regex("[a-f0-9]{64}(-[a-f0-9-]{36})?\\.original")
        fun validateLocalTree(uri:Uri) {
            require(uri.scheme=="content" && uri.authority=="com.android.externalstorage.documents" && Documents.isTreeUri(uri)) {"请选择本机存储或存储卡中的文件夹，不使用网盘"}
            val path=Documents.getTreeDocumentId(uri).substringAfter(':').trim('/').lowercase()
            require(path!="android" && path!="android/data" && !path.startsWith("android/data/") && path!="android/obb" && !path.startsWith("android/obb/")) {"请选择应用私有目录之外的位置，例如 Documents"}
        }
    }
}
internal fun allRecoveryPhotos(l:Library)=(l.archives+l.drafts).flatMap{it.photos}.plus(l.photoTrash.map{it.photo}).distinctBy{it.file}
