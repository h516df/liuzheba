package dev.h516.shicang

import org.json.JSONArray
import org.json.JSONObject

/** Portable user-authored rules. No secrets, inferred attributes or model cache. */
data class SearchAlias(val phrase:String,val target:String)
data class SuggestionRule(val category:String="",val preferred:List<String> = emptyList(),val excluded:List<String> = emptyList(),val limit:Int=3)
data class TagRejection(val archive:String,val tag:String)
data class SearchPreferences(val aliases:List<SearchAlias> = emptyList(),val rules:List<SuggestionRule> = emptyList(),val rejections:List<TagRejection> = emptyList()) {
    fun rule(category:String)=rules.firstOrNull{it.category==category} ?: rules.firstOrNull{it.category.isEmpty()} ?: SuggestionRule()
    fun json()=JSONObject().put("aliases",JSONArray(aliases.map{JSONObject().put("phrase",it.phrase).put("target",it.target)}))
        .put("rules",JSONArray(rules.map{JSONObject().put("category",it.category).put("preferred",JSONArray(it.preferred)).put("excluded",JSONArray(it.excluded)).put("limit",it.limit)}))
        .put("rejections",JSONArray(rejections.map{JSONObject().put("archive",it.archive).put("tag",it.tag)}))
    companion object {
        fun read(o:JSONObject?):SearchPreferences {
            if(o==null)return SearchPreferences()
            val a=o.getJSONArray("aliases").objects().map{SearchAlias(it.getString("phrase"),it.getString("target"))}
            val r=o.getJSONArray("rules").objects().map{SuggestionRule(it.getString("category"),it.getJSONArray("preferred").strings(),it.getJSONArray("excluded").strings(),it.getInt("limit"))}
            val rejected=o.getJSONArray("rejections").objects().map{TagRejection(it.getString("archive"),it.getString("tag"))}
            require(a.size<=500 && r.size<=501 && rejected.size<=100000){"搜索规则数量过大"}
            require(a.all{it.phrase.isNotBlank() && it.phrase.length<=100 && it.target.isNotBlank() && it.target.length<=100}){"别名格式无效"}
            require(a.map{normalizedLabel(it.phrase)}.distinct().size==a.size){"搜索别名重复"}
            require(r.map{it.category}.distinct().size==r.size && r.all{it.limit in 1..3 && (it.category.isEmpty() || Codec.validId(it.category)) && (it.preferred+it.excluded).size<=1000 && (it.preferred+it.excluded).all(Codec::validId)}){"建议规则无效"}
            require(rejected.all{Codec.validId(it.archive) && Codec.validId(it.tag)}){"标签反馈无效"}
            return SearchPreferences(a,r,rejected.distinct())
        }
    }
}

data class SearchHit(val archive:Archive,val reason:String,val rank:Int=0,val semanticScore:Float?=null)
data class TagSuggestion(val tag:Label,val reason:String)
object SearchAssistant {
    fun exact(library:Library,query:String,useAliases:Boolean=true):List<SearchHit> {
        val q=query.trim();if(q.isEmpty())return library.active.map{SearchHit(it,"")}
        val alias=if(useAliases) library.searchPreferences.aliases.firstOrNull{normalizedLabel(it.phrase)==normalizedLabel(q)} else null
        return library.active.mapNotNull { a ->
            val text=library.searchable(a)
            val id=a.id.equals(q,true);val name=a.name.equals(q,true)
            val direct=text.contains(q,true)
            val alternate=alias?.target?.takeIf{it!=q && (a.id==it || text.contains(it,true))}
            when {
                id || name -> SearchHit(a,if(id) "编号完全匹配" else "名称完全匹配",3)
                direct -> SearchHit(a,"文字匹配：${snippet(text,q)}",2)
                alternate!=null -> SearchHit(a,"自定义别名「$q」→「$alternate」：${snippet(text,alternate)}",1)
                else -> null
            }
        }.sortedWith(compareByDescending<SearchHit>{it.rank}.thenByDescending{it.archive.updated}.thenBy{it.archive.id})
    }
    private fun snippet(text:String,q:String):String {
        val start=(text.indexOf(q,ignoreCase=true).coerceAtLeast(0)-16).coerceAtLeast(0)
        return (if(start>0) "…" else "")+text.substring(start).take(90).replace('\n',' ')
    }
    fun suggestions(library:Library,a:Archive,override:SuggestionRule?=null):List<TagSuggestion> {
        val prefs=library.searchPreferences;val rule=override ?: prefs.rule(a.category)
        val text=(listOf(a.name)+a.fields.map{it.value}).joinToString(" ")
        return library.labels.filter { tag -> tag.kind=="tag" && tag.id !in a.tags && tag.id !in rule.excluded && TagRejection(a.id,tag.id) !in prefs.rejections }
            .mapNotNull { tag ->
                val alias=prefs.aliases.firstOrNull{normalizedLabel(it.target)==normalizedLabel(tag.name) && text.contains(it.phrase,true)}
                when {
                    text.contains(tag.name,true) -> TagSuggestion(tag,"资料提到「${tag.name}」")
                    alias!=null -> TagSuggestion(tag,"资料提到你的别名「${alias.phrase}」")
                    else -> null
                }
            }.sortedWith(compareByDescending<TagSuggestion>{it.tag.id in rule.preferred}.thenBy{it.tag.name}).take(rule.limit.coerceIn(1,3))
    }
    fun related(library:Library,a:Archive):List<SearchHit> = library.active.filter{it.id!=a.id}.mapNotNull {other->
        val sameName=normalizedLabel(a.name).isNotEmpty() && normalizedLabel(a.name)==normalizedLabel(other.name)
        val shared=a.tags.intersect(other.tags.toSet())
        val identicalMedia=a.photos.map{it.sha256}.intersect(other.photos.map{it.sha256}.toSet()).isNotEmpty()
        val links=a.fields.mapNotNull{Links.normalize(it.value)}.toSet()
        val sharedLink=other.fields.mapNotNull{Links.normalize(it.value)}.any{it in links}
        when {
            identicalMedia->SearchHit(other,"存在相同 SHA-256 原件；档案不一定重复，不会删除",5)
            sharedLink->SearchHit(other,"来源链接相同；请核对是否为同一份记录",4)
            sameName->SearchHit(other,"疑似重复：名称相同，请人工核对；不会合并或删除",3)
            shared.isNotEmpty()->SearchHit(other,"共同标签：${shared.take(3).joinToString("、",transform=library::labelName)}",1)
            else->null
        }
    }.sortedWith(compareByDescending<SearchHit>{it.rank}.thenByDescending{it.archive.updated}).take(5)
}

/** A previous request may neither publish nor clear the state of a newer one. */
internal class LatestSuggestionRequest {
    var current=0L;private set
    fun invalidate(){current++}
    fun isCurrent(token:Long)=token==current
}
internal fun visibleAiTagSuggestions(s:AppState,a:Archive):List<TagSuggestion> =
    if(s.aiTagArchive==a.id && s.aiTagSource==a && s.aiTagLabels==s.library.labels && s.aiTagPreferences==s.library.searchPreferences)s.aiTagSuggestions else emptyList()
