package dev.h516.shicang

import kotlin.math.abs

fun rememberPhotoPosition(old:Map<String,String>,archiveId:String,photoId:String):LinkedHashMap<String,String> =
    LinkedHashMap(old).apply {
        remove(archiveId);put(archiveId,photoId)
        if(size>200) remove(keys.first())
    }

/** Photo IDs keep a browsing position stable across reorder and cover changes. */
fun photoIndex(a: Archive, photoId: String): Int =
    a.photos.indexOfFirst { it.id == photoId }.takeIf { it >= 0 }
        ?: a.photos.indexOfFirst { it.id == a.cover }.coerceAtLeast(0)

/** Settings return to the latest page, rather than the page that opened the viewer. */
fun settingsPhotoPosition(library:Library,positions:Map<String,String>,openedPhoto:String):String {
    val a=library.active.firstOrNull{it.photos.any{photo->photo.id==openedPhoto}} ?: return openedPhoto
    val remembered=positions[a.id].orEmpty()
    return a.photos.firstOrNull{it.id==remembered}?.id ?: openedPhoto
}

enum class SwipeAxis { Undecided, Horizontal, Vertical }
fun swipeAxis(x: Float, y: Float, slop: Float): SwipeAxis = when {
    abs(x) > slop && abs(x) > abs(y) * 1.2f -> SwipeAxis.Horizontal
    abs(y) > slop && abs(y) > abs(x) * 1.2f -> SwipeAxis.Vertical
    else -> SwipeAxis.Undecided
}

/** A prepared random round doesn't consume the next archive when a drag is canceled. */
class ArchiveQueue(ids:List<String>,initial:String="",private val random:kotlin.random.Random=kotlin.random.Random.Default) {
    private val pool=ids.distinct()
    val pages=mutableListOf<String>()
    init {
        if(initial in pool) pages+=initial
        pages+=pool.filter{it!=initial}.shuffled(random)
        if(pages.isNotEmpty()) extend()
    }
    fun extend() {
        val round=pool.shuffled(random).toMutableList()
        if(round.size>1 && round.first()==pages.lastOrNull()) {val first=round[0];round[0]=round[1];round[1]=first}
        pages+=round
    }
}
