package dev.h516.shicang

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Neighbor archives exist before the drag. Stable page identity keeps their media alive. */
@Composable internal fun ArchiveFeed(
    archives:List<Archive>, initial:String, model:AppModel, appearance:Appearance,
    positions:Map<String,String>, rememberPhoto:(String,String)->Unit, selected:(String)->Unit,
    library:()->Unit, details:(String)->Unit, photos:(String)->Unit,
    settings:()->Unit, edit:(Archive)->Unit, create:()->Unit, busy:Boolean, enabled:Boolean=true,close:(()->Unit)?=null
) {
    val ids=archives.filter { it.deleted == 0L }.map {it.id}
    val prefs=LocalContext.current.getSharedPreferences("device",0)
    var random by rememberSaveable {mutableStateOf(prefs.getBoolean("discoveryRandomMedia",true))}
    var seed by rememberSaveable {mutableLongStateOf(kotlin.random.Random.nextLong())}
    val anchor=rememberSaveable {initial}
    val order=remember(ids,seed) {discoveryOrder(ids,seed,anchor)}
    if(order.isEmpty()) return
    // Only the seed, round length and small recent ID map are saved, never full media objects.
    var pageCount by rememberSaveable(seed,order) {mutableIntStateOf(order.size * 2)}
    var viewed by rememberSaveable(seed) {mutableStateOf(hashMapOf<String,String>())}
    val pager=rememberPagerState(pageCount={pageCount})
    val scope=rememberCoroutineScope()
    var gestureEnabled by remember {mutableStateOf(true)}
    val onSelected by rememberUpdatedState(selected)
    LaunchedEffect(pager,seed,order) {
        snapshotFlow {pager.settledPage}.distinctUntilChanged().collect {index ->
            onSelected(order[index % order.size])
            if(index>=pageCount-2 && pageCount<=Int.MAX_VALUE-order.size) pageCount+=order.size
        }
    }
    val actions=DiscoveryActions(random,{
        random=!random;prefs.edit().putBoolean("discoveryRandomMedia",random).apply();viewed=hashMapOf()
    },{
        scope.launch {pager.scrollToPage(0);seed=kotlin.random.Random.nextLong();viewed=hashMapOf()}
    })
    VerticalPager(pager,Modifier.fillMaxSize().background(Color.Black),
        key={"$seed:$it:${order[it % order.size]}"},beyondViewportPageCount=1,
        userScrollEnabled=enabled && gestureEnabled) { index ->
        val source=archives.firstOrNull{it.deleted==0L && it.id==order[index % order.size]} ?: return@VerticalPager
        // Files may have disappeared since backup/import. Never pick trash/drafts or another archive.
        val available by produceState<List<Photo>?>(null,source.id,source.photos) {
            value=withContext(Dispatchers.IO) {source.photos.filter { model.photoFile(it).isFile }}
        }
        val archive=source.copy(photos=available.orEmpty())
        val preview=remember(archive.id,available,index / order.size,seed,random,archive.cover) {
            discoveryPreview(archive,index / order.size,seed,random)
        }
        val pageKey="$index:${archive.id}"
        val shown=viewed[pageKey]?.takeIf { id->archive.photos.any{it.id==id} }
            ?: (if(index==0 && source.id==anchor)positions[source.id]?.takeIf{id->archive.photos.any{it.id==id}} else null)
            ?: preview?.id.orEmpty()
        // A new mode/availability must reset the horizontal pager; recomposition must not.
        key(seed,index,random,available?.map{it.id}) {
            FullPhotoFrame(archive,model,appearance,shown,
                {photo->
                    if(enabled && index==pager.settledPage && !pager.isScrollInProgress) {
                        viewed=HashMap(rememberPhotoPosition(viewed,pageKey,photo));rememberPhoto(archive.id,photo)
                    }
                },
                ({scope.launch{if(pager.currentPage>=pageCount-1)pageCount+=order.size;pager.animateScrollToPage(index+1)};Unit}),
                if(index>0) ({scope.launch{pager.animateScrollToPage(index-1)};Unit}) else null,
                library,{details(archive.id)},{photos(archive.id)},settings,{edit(source)},create,busy,
                close=close,active=enabled && index==pager.settledPage && !pager.isScrollInProgress,
                onPagingAllowed={if(index==pager.settledPage) gestureEnabled=it},discovery=actions)
        }
    }
}
