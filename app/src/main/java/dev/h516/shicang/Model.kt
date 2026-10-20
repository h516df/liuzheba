package dev.h516.shicang

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.random.Random

fun uid() = UUID.randomUUID().toString()
data class Field(val id: String = uid(), val name: String = "", val type: String = "文本", val value: String = "")
data class Photo(val id: String, val file: String, val bytes: Long, val sha256: String, val mime: String, val duration: Long = 0) { val isVideo get()=mime.startsWith("video/") }
data class Archive(
    val id: String = uid(), val name: String = "", val category: String = "",
    val tags: List<String> = emptyList(), val fields: List<Field> = emptyList(),
    val photos: List<Photo> = emptyList(), val cover: String = "", val fill: Boolean = false,
    val updated: Long = System.currentTimeMillis(), val deleted: Long = 0, val authorIds: List<String> = emptyList()
) {
    fun coverPhoto() = photos.firstOrNull { it.id == cover } ?: photos.firstOrNull()
}
data class Label(val id: String, val kind: String, val name: String)
data class PhotoTrash(val id: String = uid(), val archiveId: String, val photo: Photo, val index: Int, val removed: Long)
data class Library(val archives: List<Archive> = emptyList(), val drafts: List<Archive> = emptyList(), val labels: List<Label> = emptyList(), val photoTrash: List<PhotoTrash> = emptyList(), val templates: List<FieldTemplate> = emptyList(), val searchPreferences:SearchPreferences = SearchPreferences(), val authors: List<Author> = emptyList(), val notes:List<LongNote> = emptyList(), val noteDrafts:List<LongNote> = emptyList()) {
    val active get() = archives.filter { it.deleted == 0L }
    fun labelName(id: String) = labels.firstOrNull { it.id == id }?.name.orEmpty()
    fun searchable(a: Archive) = listOf(a.name, labelName(a.category), a.tags.joinToString(" ") { labelName(it) }, a.fields.joinToString(" ") { "${it.name} ${it.value}" }, authors.filter { it.id in a.authorIds }.joinToString(" ") { author -> author.name + " " + author.identities.joinToString(" ") { "${it.platform} ${it.nickname} ${it.publicAccount}" } }, notesFor(a).joinToString("\n") { "${it.title}\n${it.body}" }).joinToString(" ")
    fun find(query: String, category: String = "", tag: String = "") = active.filter {
        (category.isEmpty() || it.category == category) && (tag.isEmpty() || tag in it.tags) && searchable(it).contains(query.trim(), ignoreCase = true)
    }
}
object Codec {
    fun identity(i: PlatformIdentity) = JSONObject().put("id",i.id).put("platform",i.platform).put("nickname",i.nickname).put("publicAccount",i.publicAccount).put("platformId",i.platformId).put("idType",i.idType).put("profileUrl",i.profileUrl)
    fun identity(o: JSONObject): PlatformIdentity {
        val i=PlatformIdentity(o.getString("id"),o.getString("platform"),o.getString("nickname"),o.getString("publicAccount"),o.getString("platformId"),o.getString("idType"),o.getString("profileUrl"))
        require(AuthorProfiles.valid(i)) { "平台身份无效" }
        return i
    }
    fun author(a: Author) = JSONObject().put("id",a.id).put("name",a.name).put("updated",a.updated).put("identities",JSONArray(a.identities.map(::identity)))
    fun author(o: JSONObject): Author {
        val a=Author(o.getString("id"),o.getString("name"),o.getJSONArray("identities").objects().map(::identity),o.getLong("updated"))
        require(validId(a.id) && (a.name.isNotBlank() || a.identities.any { it.profileUrl.isNotBlank() || it.publicAccount.isNotBlank() || it.nickname.isNotBlank() || it.platformId.isNotBlank() }) && a.name.length<=200 && a.name.none{it.isISOControl()} && a.updated>=0) { "作者资料无效" }
        require(a.identities.size<=100 && a.identities.map{it.id}.distinct().size==a.identities.size) { "平台身份编号重复或数量过大" }
        val linked=a.identities.filter{it.profileUrl.isNotEmpty()}.map{AuthorProfiles.parse(it.profileUrl)!!.profileUrl}
        require(linked.distinct().size==linked.size) { "作者主页重复" }
        return a
    }
    fun photo(p: Photo) = JSONObject().put("id",p.id).put("file",p.file).put("bytes",p.bytes).put("sha256",p.sha256).put("mime",p.mime).put("duration",p.duration)
    fun photo(o: JSONObject): Photo {
        val id=o.getString("id"); val file=o.getString("file")
        require(validId(id) && file == "$id.original") { "照片路径无效" }
        val sha=o.getString("sha256"); require(sha.matches(Regex("[a-f0-9]{64}"))) { "照片摘要无效" }
        require(o.getLong("bytes") in 1..(if(o.getString("mime").startsWith("video/")) MAX_MEDIA_BYTES else MAX_PHOTO_BYTES)) { "照片大小无效" }
        val mime=o.getString("mime"); require(mime.startsWith("image/") || mime.startsWith("video/")) {"媒体类型不支持"}
        require(o.optLong("duration")>=0) {"时长无效"}
        return Photo(id,file,o.getLong("bytes"),sha,mime,o.optLong("duration"))
    }
    fun archive(a: Archive) = JSONObject().put("id",a.id).put("name",a.name).put("category",a.category).put("tags",JSONArray(a.tags))
        .put("fields",JSONArray(a.fields.map { JSONObject().put("id",it.id).put("name",it.name).put("type",it.type).put("value",it.value) }))
        .put("photos",JSONArray(a.photos.map(::photo))).put("cover",a.cover).put("fill",a.fill).put("updated",a.updated).put("deleted",a.deleted).put("authorIds",JSONArray(a.authorIds))
    fun archive(o: JSONObject): Archive {
        val id=o.getString("id"); require(validId(id)) { "档案编号无效" }
        require(!o.has("authorIds") || o.opt("authorIds") is JSONArray) { "作者关联格式无效" }
        val fs=o.getJSONArray("fields").objects().map { Field(it.getString("id"),it.getString("name"),it.getString("type"),it.getString("value")) }
        require(fs.all { validId(it.id) && it.type in Templates.types }) { "字段格式不支持" }
        return Archive(id,o.getString("name"),o.optString("category"),o.getJSONArray("tags").strings(),fs,o.getJSONArray("photos").objects().map(::photo),o.optString("cover"),o.optBoolean("fill"),o.getLong("updated"),o.optLong("deleted"),(o.optJSONArray("authorIds") ?: JSONArray()).strings().also { ids -> require(ids.size<=1000 && ids.all(::validId) && ids.distinct().size==ids.size) { "作者关联无效" } })
    }
    fun library(l: Library) = JSONObject().put("format",6).put("notes",JSONArray(l.notes.map{it.json()})).put("noteDrafts",JSONArray(l.noteDrafts.map{it.json()})).put("authors",JSONArray(l.authors.map(::author))).put("searchPreferences",l.searchPreferences.json()).put("templates",JSONArray(l.templates.map(Templates::json))).put("archives",JSONArray(l.archives.map(::archive))).put("drafts",JSONArray(l.drafts.map(::archive)))
        .put("labels",JSONArray(l.labels.map { JSONObject().put("id",it.id).put("kind",it.kind).put("name",it.name) }))
        .put("photoTrash",JSONArray(l.photoTrash.map { JSONObject().put("id",it.id).put("archiveId",it.archiveId).put("photo",photo(it.photo)).put("index",it.index).put("removed",it.removed) }))
    fun library(o: JSONObject): Library {
        require(o.getInt("format") in 1..6) { "备份版本不支持" }
        val l=Library(o.getJSONArray("archives").objects().map(::archive),o.getJSONArray("drafts").objects().map(::archive),
            o.getJSONArray("labels").objects().map { Label(it.getString("id"),it.getString("kind"),it.getString("name")) },
            o.getJSONArray("photoTrash").objects().map { PhotoTrash(it.getString("id"),it.getString("archiveId"),photo(it.getJSONObject("photo")),it.getInt("index"),it.getLong("removed")) },
            if(o.getInt("format")>=2) (o.optJSONArray("templates") ?: JSONArray()).objects().map(Templates::read) else emptyList(), SearchPreferences.read(if(o.getInt("format")>=4)o.getJSONObject("searchPreferences") else o.optJSONObject("searchPreferences")), if(o.getInt("format")>=5) o.getJSONArray("authors").objects().map(::author) else emptyList(), if(o.getInt("format")>=6)o.getJSONArray("notes").objects().map(LongNote::read) else emptyList(), if(o.getInt("format")>=6)o.getJSONArray("noteDrafts").objects().map(LongNote::read) else emptyList())
        require(listOf(l.notes,l.noteDrafts).all{it.size<=100000 && it.map{n->n.key}.distinct().size==it.size}) {"笔记重复或数量过大"}
        require(l.authors.size<=100000 && l.authors.map{it.id}.distinct().size==l.authors.size) { "作者编号重复或数量过大" }
        val identityIds=l.authors.flatMap{it.identities}.map{it.id}
        require(identityIds.distinct().size==identityIds.size) { "平台身份编号重复" }
        val authorIds=l.authors.map{it.id}.toSet()
        require((l.archives+l.drafts).all{a -> a.authorIds.all{it in authorIds}}) { "作者关联缺失" }
        require(l.templates.size<=500 && l.templates.map{it.id}.distinct().size==l.templates.size && l.templates.map{normalizedLabel(it.name)}.distinct().size==l.templates.size) {"模板重复或数量过大"}
        require(l.archives.size<=100000 && l.drafts.size<=100000) { "备份档案数量过大" }
        require(l.labels.all { validId(it.id) && it.kind in listOf("category","tag") && it.name.isNotBlank() }) { "分类或标签无效" }
        require(l.labels.map{it.kind to normalizedLabel(it.name)}.distinct().size==l.labels.size) { "分类或标签名称重复" }
        require(l.archives.map { it.id }.distinct().size==l.archives.size && l.drafts.map { it.id }.distinct().size==l.drafts.size && l.labels.map { it.id }.distinct().size==l.labels.size) { "备份编号重复" }
        require(l.archives.all { it.name.isNotBlank() }) { "正式档案缺少名称" }
        require((l.archives+l.drafts).all { a -> a.photos.map {it.id}.distinct().size==a.photos.size && a.fields.map{it.id}.distinct().size==a.fields.size }) { "照片或字段编号重复" }
        val referenced=(l.archives+l.drafts).flatMap{it.photos}+l.photoTrash.map{it.photo}
        require(referenced.groupBy{it.file}.values.all {it.distinct().size==1}) { "照片清单互相矛盾" }
        val labelIds=l.labels.associateBy { it.id }
        require((l.archives+l.drafts).all { a -> (a.category.isEmpty() || labelIds[a.category]?.kind=="category") && a.tags.all { labelIds[it]?.kind=="tag" } }) { "分类或标签关联缺失" }
        require(l.photoTrash.all { validId(it.id) && validId(it.archiveId) && it.index>=0 }) { "回收站关联无效" }
        return l
    }
    fun validId(s: String) = s.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))
    const val MAX_MEDIA_BYTES = 1024L * 1024 * 1024
    const val MAX_PHOTO_BYTES = 200L * 1024 * 1024
}
fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
fun JSONArray.strings() = (0 until length()).map { getString(it) }
fun normalizedLabel(s: String) = s.trim().lowercase(java.util.Locale.ROOT)

/** A shuffle round contains IDs, including archives with no photos. */
class ShuffleDeck(private val random: Random = Random.Default) {
    private var pool: List<String> = emptyList()
    private var remaining = ArrayDeque<String>()
    private val history = mutableListOf<String>()
    private var position = -1
    fun setIds(ids: List<String>, visible: String? = history.getOrNull(position)) {
        val next=ids.distinct().sorted()
        if(next==pool) return
        pool=next; remaining.clear(); history.clear(); position=-1
        if(visible in pool) {
            history.add(visible!!);position=0
            remaining.addAll(pool.filter{it!=visible}.shuffled(random))
        }
    }
    fun next(): String? {
        if(pool.isEmpty()) return null
        if(position+1<history.size) return history[++position]
        if(remaining.isEmpty()) {
            val round=pool.shuffled(random).toMutableList()
            if(round.size>1 && round.first()==history.lastOrNull()) { val x=round[0];round[0]=round[1];round[1]=x }
            remaining.addAll(round)
        }
        val id=remaining.removeFirst();history.add(id);position=history.lastIndex
        if(history.size>200) { history.removeAt(0);position-- }
        return id
    }
    fun previous(): String? = if(position>0) history[--position] else history.getOrNull(position)
}
