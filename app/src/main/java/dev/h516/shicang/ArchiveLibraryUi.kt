package dev.h516.shicang

/** Ephemeral view state only; never changes an archive or the backup format. */
internal data class ArchiveQuery(val text:String="",val category:String="",val tags:List<String> = emptyList(),val byName:Boolean=false,val aliases:Boolean=true,val updatedAfter:Long=0,val semantic:Boolean=false) {
    val filtered get()=text.isNotBlank() || category.isNotBlank() || tags.isNotEmpty() || updatedAfter>0
    fun normalized(library:Library):ArchiveQuery = copy(
        category=category.takeIf{id->library.labels.any{it.id==id && it.kind=="category"}}.orEmpty(),
        tags=tags.distinct().filter{id->library.labels.any{it.id==id && it.kind=="tag"}}
    )
    fun hits(library:Library):List<SearchHit> = SearchAssistant.exact(library,text,aliases)
        .filter{hit->val archive=hit.archive;(category.isEmpty() || archive.category==category) && tags.all{it in archive.tags} && archive.updated>=updatedAfter}
        .let{matches->if(byName)matches.sortedWith(compareByDescending<SearchHit>{if(text.isBlank())0 else it.rank}.thenBy{it.archive.name.lowercase(java.util.Locale.ROOT)}.thenBy{it.archive.id}) else if(text.isBlank())matches.sortedWith(compareByDescending<SearchHit>{it.archive.updated}.thenBy{it.archive.id}) else matches}
    fun results(library:Library):List<Archive> = hits(library).map{it.archive}

}

internal fun archiveCaption(archive:Archive,library:Library):String {
    val labels=(archive.tags+listOf(archive.category)).map(library::labelName).filter(String::isNotBlank)
    return if(labels.isNotEmpty()) labels.take(2).joinToString(" · ")+if(labels.size>2) " +${labels.size-2}" else ""
    else archive.fields.firstOrNull{it.value.isNotBlank() && Links.normalize(it.value)==null}?.value.orEmpty()
}

/** Frequent tags first, with stable ties. No new setting or mutation of existing categories. */
internal fun archiveQuickTags(library:Library):List<Label> {
    val uses=mutableMapOf<String,Int>()
    library.archives.forEach{archive->if(archive.deleted==0L) archive.tags.distinct().forEach{id->uses[id]=(uses[id] ?: 0)+1}}
    return library.labels.filter{it.kind=="tag"}.sortedWith(compareByDescending<Label>{uses[it.id] ?: 0}.thenBy{it.name.lowercase(java.util.Locale.ROOT)}.thenBy{it.id})
}
