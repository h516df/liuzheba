@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.h516.shicang

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Fullscreen discovery mixes saved works. Archive management is an independent view. */
@Composable internal fun DiscoveryWorkFeed(
    archives: List<Archive>, labels: List<Label>, model: AppModel, appearance: Appearance,
    rememberPhoto: (String, String) -> Unit, library: () -> Unit,
    details: (String) -> Unit, photos: (String) -> Unit,
    settings: () -> Unit, edit: (Archive) -> Unit, create: () -> Unit,
    busy: Boolean, enabled: Boolean = true, navigationInset: Dp = 0.dp
) {
    var mediaType by rememberSaveable { mutableStateOf("全部") }
    var category by rememberSaveable { mutableStateOf("") }
    var filtersOpen by remember { mutableStateOf(false) }
    val sourceWorks = remember(archives, mediaType, category) {
        filterDiscoveryWorks(discoveryWorks(archives), mediaType, category)
    }
    val available by produceState<List<DiscoveryWork>?>(null, sourceWorks) {
        value = withContext(Dispatchers.IO) {
            sourceWorks.mapNotNull { work ->
                work.media.filter { model.photoFile(it).isFile }.takeIf { it.isNotEmpty() }
                    ?.let { work.copy(media = it) }
            }
        }
    }
    val usable = available
    val presentIds=usable.orEmpty().flatMap{it.media}.map{it.id}.toSet()
    val missingCount=if(usable==null)0 else sourceWorks.sumOf{it.media.count{photo->photo.id !in presentIds}}
    val affectedArchive=if(missingCount>0)sourceWorks.firstOrNull{work->work.media.any{it.id !in presentIds}}?.archive else null
    if (usable == null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White)
        }
    } else if (usable.isEmpty()) {
        Column(Modifier.fillMaxSize().background(Color.Black), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Text(if (sourceWorks.isEmpty()) "当前筛选没有珍藏作品" else "这些作品的原件不可用", color = Color.White)
            affectedArchive?.let{archive->TextButton(onClick={details(archive.id)}){Text("查看原档案")}}
            TextButton(onClick = { mediaType = "全部";category = "" }) { Text("查看全部珍藏") }
        }
    } else {
        // A different temporary filter starts its own pager; stored archive media is unchanged.
        Box(Modifier.fillMaxSize()) {
            key(mediaType, category) {
                DiscoveryWorkPages(usable, model, appearance, rememberPhoto, library, details, photos,
                    settings, edit, create, busy, enabled && !filtersOpen, navigationInset, { filtersOpen = true })
            }
            affectedArchive?.let{archive->
                Row(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top=64.dp)
                    .background(Color.Black.copy(alpha=.78f)).padding(horizontal=12.dp,vertical=4.dp),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("$missingCount 项原件不可用，已跳过",color=Color.White,style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={details(archive.id)}){Text("查看原档案")}
                }
            }
        }
    }
    if (filtersOpen) ModalBottomSheet(onDismissRequest = { filtersOpen = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("筛选珍藏", style = MaterialTheme.typography.headlineSmall)
            Text("默认混刷全部；选择仅影响发现页，不移动或删除档案。",
                color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("全部", "视频", "图片").forEach { type ->
                    FilterChip(selected = mediaType == type, onClick = { mediaType = type;filtersOpen = false }, label = { Text(type) })
                }
            }
            Text("按档案分类", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(selected = category.isEmpty(), onClick = { category = "";filtersOpen = false }, label = { Text("所有分类") })
                labels.filter { it.kind == "category" && archives.any { a -> a.category == it.id && a.deleted == 0L } }
                    .forEach { label ->
                        FilterChip(selected = category == label.id, onClick = { category = label.id;filtersOpen = false }, label = { Text(label.name) })
                    }
            }
            TextButton(onClick = { mediaType = "全部";category = "";filtersOpen = false }) { Text("恢复全部混刷") }
        }
    }
}

@Composable private fun DiscoveryWorkPages(
    usable: List<DiscoveryWork>, model: AppModel, appearance: Appearance,
    rememberPhoto: (String, String) -> Unit, library: () -> Unit,
    details: (String) -> Unit, photos: (String) -> Unit,
    settings: () -> Unit, edit: (Archive) -> Unit, create: () -> Unit,
    busy: Boolean, enabled: Boolean, navigationInset: Dp, openFilters: () -> Unit
) {
    var seed by rememberSaveable { mutableLongStateOf(kotlin.random.Random.nextLong()) }
    val order = remember(usable, seed) { discoveryWorkOrder(usable, seed) }
    var pageCount by rememberSaveable(seed, order.map { it.key }) { mutableIntStateOf(order.size * 2) }
    val pager = rememberPagerState(pageCount = { pageCount })
    var gestureEnabled by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager, seed, order) {
        snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { index ->
            if (index >= pageCount - 2 && pageCount <= Int.MAX_VALUE - order.size) pageCount += order.size
        }
    }
    val actions = DiscoveryActions(random = true, toggle = {}, refresh = {
        scope.launch { pager.scrollToPage(0);seed = kotlin.random.Random.nextLong() }
    }, workMode = true, filter = openFilters)
    VerticalPager(pager, Modifier.fillMaxSize().background(Color.Black),
        key = { "$seed:$it:${order[it % order.size].key}" }, beyondViewportPageCount = 1,
        userScrollEnabled = enabled && gestureEnabled) { index ->
        val work = order[index % order.size]
        val archive = work.archive.copy(photos = work.media, cover = work.media.first().id)
        key(seed, index, work.key) {
            Box(Modifier.fillMaxSize()) {
            FullPhotoFrame(archive, model, appearance, work.media.first().id,
                { photo ->
                    if (enabled && index == pager.settledPage && !pager.isScrollInProgress)
                        rememberPhoto(work.archive.id, photo)
                },
                { scope.launch { if (pager.currentPage >= pageCount - 1) pageCount += order.size;pager.animateScrollToPage(index + 1) } },
                if (index > 0) ({ scope.launch { pager.animateScrollToPage(index - 1) } }) else null,
                library, { details(work.archive.id) }, { photos(work.archive.id) }, settings,
                { edit(work.archive) }, create, busy,
                active = enabled && index == pager.settledPage && !pager.isScrollInProgress,
                onPagingAllowed = { if (index == pager.settledPage) gestureEnabled = it },
                discovery = actions, autoplay = true, navigationInset = navigationInset,
                workCounter = if(work.media.size==1) "${index % order.size+1} / ${order.size}" else null)
            if (work.media.size < work.expectedCount) Text("部分原件不可用",
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 76.dp)
                    .background(Color.Black.copy(alpha = .67f)).padding(horizontal = 12.dp, vertical = 7.dp),
                color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}
