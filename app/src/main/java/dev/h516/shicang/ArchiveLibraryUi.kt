package dev.h516.shicang

/** Ephemeral view state only; never changes an archive or the backup format. */
internal data class ArchiveQuery(val text:String="",val category:String="",val tags:List<String> = emptyList(),val byName:Boolean=false) {
    val filtered get()=text.isNotBlank() || category.isNotBlank() || tags.isNotEmpty()
    fun normalized(library:Library):ArchiveQuery = copy(
        category=category.takeIf{id->library.labels.any{it.id==id && it.kind=="category"}}.orEmpty(),
        tags=tags.distinct().filter{id->library.labels.any{it.id==id && it.kind=="tag"}}
    )
    fun results(library:Library):List<Archive> = library.find(text,category)
        .filter{archive->tags.all{it in archive.tags}}
        .let{matches->if(byName) matches.sortedWith(compareBy<Archive>{it.name.lowercase(java.util.Locale.ROOT)}.thenBy{it.id}) else matches.sortedWith(compareByDescending<Archive>{it.updated}.thenBy{it.id})}
}

internal fun archiveCaption(archive:Archive,library:Library):String {
    val labels=(listOf(archive.category)+archive.tags).map(library::labelName).filter(String::isNotBlank)
    return if(labels.isNotEmpty()) labels.take(2).joinToString(" · ")+if(labels.size>2) " +${labels.size-2}" else ""
    else archive.fields.firstOrNull{it.value.isNotBlank() && Links.normalize(it.value)==null}?.value.orEmpty()
}
