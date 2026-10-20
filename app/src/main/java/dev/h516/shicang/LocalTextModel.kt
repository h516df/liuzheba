package dev.h516.shicang

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.LongBuffer
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/** Only fixed public model downloads use the network. Archive/query text never leaves this process. */
internal class LocalTextModel(context:Context) {
    private val directory=File(context.filesDir,"local-text-model-bge-v1")
    private val mutex=Mutex()
    private var closed=false // Guarded by mutex; a disposed owner must never reopen native resources.
    private var session:OrtSession?=null
    private var tokenizer:LocalWordPiece?=null
    private val vectors=object:LinkedHashMap<String,FloatArray>(256,.75f,true){override fun removeEldestEntry(eldest:MutableMap.MutableEntry<String,FloatArray>?)=size>256}
    val installed get()=File(directory,"model.onnx").length()==MODEL_BYTES && File(directory,"vocab.txt").length()==VOCAB_BYTES
    suspend fun download(progress:(String)->Unit)=withContext(Dispatchers.IO) {mutex.withLock {
        check(!closed){"本机模型已关闭"}
        directory.mkdirs()
        fetch("onnx/model_int8.onnx","model.onnx",MODEL_BYTES,MODEL_SHA,progress)
        fetch("vocab.txt","vocab.txt",VOCAB_BYTES,VOCAB_SHA,progress)
        coroutineContext.ensureActive();open();coroutineContext.ensureActive();progress("本机 AI 已就绪 · 不上传资料")
    }}
    private suspend fun fetch(remote:String,name:String,size:Long,sha:String,progress:(String)->Unit) {
        coroutineContext.ensureActive()
        val target=File(directory,name)
        if(target.isFile && target.length()==size && digest(target)==sha)return
        val partial=File(directory,"$name.partial")
        try {
            var url=URL(BASE+remote);var connection:HttpURLConnection?=null
            for(attempt in 0..5) {
                coroutineContext.ensureActive()
                require(url.protocol=="https" && (url.host=="huggingface.co" || url.host.endsWith(".hf.co"))){"模型下载来源不受信任"}
                val next=(url.openConnection() as HttpURLConnection).apply{instanceFollowRedirects=false;connectTimeout=20000;readTimeout=30000;setRequestProperty("Accept-Encoding","identity")}
                val code=try{next.responseCode}catch(e:Exception){next.disconnect();throw e}
                if(code in 300..399){val location=next.getHeaderField("Location");next.disconnect();require(!location.isNullOrEmpty());url=URL(url,location);continue}
                if(code!=200){next.disconnect();error("模型下载失败（$code），文字搜索仍可用")};connection=next;break
            }
            val active=requireNotNull(connection){"模型下载跳转过多"}
            try {
                require(active.contentLengthLong<0 || active.contentLengthLong==size){"模型大小与固定版本不符"}
                var bytes=0L
                active.inputStream.use{input->partial.outputStream().use{output->val buffer=ByteArray(65536);while(true){coroutineContext.ensureActive();val n=input.read(buffer);if(n<0)break;bytes+=n;require(bytes<=size){"模型超过下载上限"};output.write(buffer,0,n);progress("下载本机模型 ${(bytes*100/size)}% · 可取消")}}}
                coroutineContext.ensureActive()
                require(bytes==size && digest(partial)==sha){"模型校验失败，未启用"}
                coroutineContext.ensureActive()
                require(partial.renameTo(target)){"模型保存失败"}
            } finally {active.disconnect()}
        } finally {partial.delete()}
    }
    private suspend fun open() {
        check(!closed){"本机模型已关闭"}
        if(session!=null)return
        require(installed && digest(File(directory,"model.onnx"))==MODEL_SHA && digest(File(directory,"vocab.txt"))==VOCAB_SHA){"本机模型未安装或校验失败"}
        coroutineContext.ensureActive()
        val env=OrtEnvironment.getEnvironment()
        val next=OrtSession.SessionOptions().use{options->options.setIntraOpNumThreads(2);env.createSession(File(directory,"model.onnx").absolutePath,options)}
        require(next.inputNames==setOf("input_ids","attention_mask","token_type_ids")){next.close();"模型输入不支持"}
        session=next;tokenizer=LocalWordPiece(File(directory,"vocab.txt").readLines())
    }
    private fun embed(text:String):FloatArray {
        vectors[text]?.let{return it}
        val ids=requireNotNull(tokenizer).encode(text);val env=OrtEnvironment.getEnvironment();val shape=longArrayOf(1,ids.size.toLong())
        val tensors=mapOf("input_ids" to OnnxTensor.createTensor(env,LongBuffer.wrap(ids),shape),"attention_mask" to OnnxTensor.createTensor(env,LongBuffer.wrap(LongArray(ids.size){1}),shape),"token_type_ids" to OnnxTensor.createTensor(env,LongBuffer.wrap(LongArray(ids.size)),shape))
        try {requireNotNull(session).run(tensors).use{result->
            @Suppress("UNCHECKED_CAST") val output=result[0].value as Array<Array<FloatArray>>
            val vector=output[0][0].copyOf();val norm=sqrt(vector.sumOf{it.toDouble()*it}.toFloat())
            require(norm.isFinite() && norm>0 && vector.size==512){"模型输出无效"}
            for(i in vector.indices)vector[i]/=norm
            vectors[text]=vector;return vector
        }} finally {tensors.values.forEach{it.close()}}
    }
    suspend fun search(library:Library,query:ArchiveQuery):List<SearchHit> = withContext(Dispatchers.Default) {
        val exact=query.hits(library)
        if(!query.semantic || query.text.isBlank() || !installed)return@withContext exact
        if(!mutex.tryLock())return@withContext exact
        try {
        if(closed)return@withContext exact
        open();coroutineContext.ensureActive()
        val q=embed("为这个句子生成表示以用于检索相关文章："+query.text.take(2000))
        val candidates=library.active.filter{a->(query.category.isBlank() || a.category==query.category) && query.tags.all{it in a.tags} && a.updated>=query.updatedAfter}
        // Explicitly bounded; no silent claim to have searched a larger collection semantically.
        require(candidates.size<=2000){"AI 本次最多检索 2000 份，请先按分类或日期缩小范围；文字结果仍可用"}
        val ids=exact.map{it.archive.id}.toSet()
        val semantic=candidates.filter{it.id !in ids}.mapNotNull{a->
            coroutineContext.ensureActive();val v=embed(library.searchable(a).take(6000));val score=q.indices.sumOf{q[it].toDouble()*v[it]}.toFloat()
            if(score>=.56f)SearchHit(a,"AI 语义相近（不是事实确认）：${library.searchable(a).take(90)}",0,score) else null
        }.sortedByDescending{it.semanticScore}.take(30)
        exact+semantic
        } finally {mutex.unlock()}
    }
    suspend fun suggest(library:Library,a:Archive):List<TagSuggestion> = withContext(Dispatchers.Default){mutex.withLock {
        check(!closed){"本机模型已关闭"}
        require(installed){"先下载本机 AI 模型，文字规则候选仍可用"};open()
        val rule=library.searchPreferences.rule(a.category)
        val labels=library.labels.filter{it.kind=="tag" && it.id !in a.tags && it.id !in rule.excluded && TagRejection(a.id,it.id) !in library.searchPreferences.rejections}
        require(labels.size<=500){"本次候选词表最多500个标签"}
        val vector=embed(library.searchable(a).take(6000))
        labels.mapNotNull{tag->coroutineContext.ensureActive();val candidate=embed("为这个句子生成表示以用于检索相关文章："+tag.name);val score=vector.indices.sumOf{vector[it].toDouble()*candidate[it]}.toFloat()
            if(score>=.56f)TagSuggestion(tag,"本机 AI 语义相近，请核对") to score else null
        }.sortedWith(compareByDescending<Pair<TagSuggestion,Float>>{it.first.tag.id in rule.preferred}.thenByDescending{it.second}).take(rule.limit).map{it.first}
    }}
    suspend fun close()=mutex.withLock{closed=true;try{session?.close()}finally{session=null;tokenizer=null;vectors.clear()}}
    companion object {
        const val BASE="https://huggingface.co/Xenova/bge-small-zh-v1.5/resolve/75c43b069aac4d136ba6bc1122f995fedcfd2781/"
        const val MODEL_BYTES=23903394L;const val VOCAB_BYTES=109540L
        const val MODEL_SHA="b9837c19ce154ff0726d398ee77abbc03a7faf0476c6f93016c84e531be7ebb5"
        const val VOCAB_SHA="45bbac6b341c319adc98a532532882e91a9cefc0329aa57bac9ae761c27b291c"
        private suspend fun digest(file:File):String {val hash=MessageDigest.getInstance("SHA-256");file.inputStream().use{stream->val b=ByteArray(65536);while(true){coroutineContext.ensureActive();val n=stream.read(b);if(n<0)break;hash.update(b,0,n)}};return hash.digest().joinToString(""){"%02x".format(it)}}
    }
}
