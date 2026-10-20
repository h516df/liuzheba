package dev.h516.shicang

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Neighbor archives exist before the drag. Stable page identity keeps their media alive. */
@Composable internal fun ArchiveFeed(
    archives:List<Archive>, initial:String, model:AppModel, appearance:Appearance,
    positions:Map<String,String>, rememberPhoto:(String,String)->Unit, selected:(String)->Unit,
    library:()->Unit, details:(String)->Unit, photos:(String)->Unit,
    settings:()->Unit, edit:(Archive)->Unit, create:()->Unit, busy:Boolean, enabled:Boolean=true
) {
    val ids=archives.map {it.id}
    val queue=remember(ids) {ArchiveQueue(ids,initial)}
    val pages=remember(queue) {mutableStateListOf<String>().apply{addAll(queue.pages)}}
    val pager=rememberPagerState(pageCount={pages.size})
    val scope=rememberCoroutineScope()
    var gestureEnabled by remember {mutableStateOf(true)}
    val onSelected by rememberUpdatedState(selected)
    LaunchedEffect(pager,queue) {
        snapshotFlow {pager.settledPage}.distinctUntilChanged().collect {index ->
            pages.getOrNull(index)?.let(onSelected)
            if(index>=pages.lastIndex-1) {queue.extend();pages.clear();pages.addAll(queue.pages)}
        }
    }
    VerticalPager(pager,Modifier.fillMaxSize().background(Color.Black),
        key={"$it:${pages[it]}"},beyondViewportPageCount=1,
        userScrollEnabled=enabled && gestureEnabled && archives.size>1) { index ->
        val archive=archives.firstOrNull{it.id==pages[index]} ?: return@VerticalPager
        FullPhotoFrame(archive,model,appearance,positions[archive.id].orEmpty(),
            {rememberPhoto(archive.id,it)},
            if(archives.size>1) ({scope.launch{if(pager.currentPage==pages.lastIndex){queue.extend();pages.clear();pages.addAll(queue.pages)};pager.animateScrollToPage((index+1).coerceAtMost(pages.lastIndex))};Unit}) else null,
            if(index>0) ({scope.launch{pager.animateScrollToPage(index-1)};Unit}) else null,
            library,{details(archive.id)},{photos(archive.id)},settings,{edit(archive)},create,busy,
            active=enabled && index==pager.settledPage && !pager.isScrollInProgress,
            onPagingAllowed={if(index==pager.settledPage) gestureEnabled=it})
    }
}
