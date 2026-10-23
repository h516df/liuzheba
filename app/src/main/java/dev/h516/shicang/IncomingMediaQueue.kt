package dev.h516.shicang

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

internal data class PendingSource(val id:String,val uri:Uri)

/** Local-only selection journal. A lost source permission needs reselection, never a fake success. */
internal class IncomingMediaQueue(context:Context) {
    private val prefs=context.getSharedPreferences("device",Context.MODE_PRIVATE)
    fun read(generation:String):List<PendingSource> = runCatching {
        val root=JSONObject(prefs.getString("incomingMediaV1","{}").orEmpty())
        if(root.optString("generation")!=generation)emptyList() else root.getJSONArray("sources").objects().map {
            PendingSource(it.getString("id"),Uri.parse(it.getString("uri")))
        }.also { sources->require(sources.size<=100 && sources.all{Codec.validId(it.id) && it.uri.scheme in listOf("content","file")}) }
    }.getOrDefault(emptyList())
    fun write(generation:String,uris:List<Uri>):List<PendingSource> {
        val before=read(generation).associateBy{it.uri}
        val sources=uris.distinct().take(100).map{uri->require(uri.scheme in listOf("content","file"));before[uri] ?: PendingSource(uid(),uri)}
        val root=JSONObject().put("generation",generation).put("sources",JSONArray(sources.map{JSONObject().put("id",it.id).put("uri",it.uri.toString())}))
        check(prefs.edit().putString("incomingMediaV1",root.toString()).commit()){"待收录记录未能保存，请保留来源后重试"}
        return sources
    }
}
