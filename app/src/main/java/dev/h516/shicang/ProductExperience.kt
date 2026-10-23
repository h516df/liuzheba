package dev.h516.shicang

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Two perspectives on the same archives. Neither discovery nor collection owns a second reader. */
@Composable internal fun DiscoveryScreen(model:AppModel,s:AppState,open:(String)->Unit,view:(Archive)->Unit) {
    var seed by rememberSaveable {mutableLongStateOf(kotlin.random.Random.nextLong())}
    val active=s.library.active
    val order=remember(active.map{it.id},seed){discoveryOrder(active.map{it.id},seed,"")}
    val archives=remember(active,order){val byId=active.associateBy{it.id};order.mapNotNull(byId::get)}
    val list=rememberLazyListState()
    val scope=rememberCoroutineScope()
    if(archives.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            ArchiveNameplate("留",Modifier.size(88.dp))
            Text("值得留下的，都在这里",Modifier.padding(top=24.dp),style=MaterialTheme.typography.titleLarge)
            Text("照片、视频，或几句想留下的话。",Modifier.padding(top=10.dp,bottom=24.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            GalleryButton(onClick={model.edit()},enabled=!s.busy){Text("新建第一份档案")}
        }
        return
    }
    LazyColumn(state=list,modifier=Modifier.fillMaxSize().testTag("discovery-stories"),contentPadding=PaddingValues(start=20.dp,end=20.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        item(key="intro") {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("重新遇见你的收藏",style=MaterialTheme.typography.titleMedium)
                    Text("${archives.size} 份档案",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GalleryTextButton(onClick={scope.launch{list.scrollToItem(0);seed=kotlin.random.Random.nextLong()}},enabled=!s.busy){Text("换一组")}
            }
        }
        items(archives,key={it.id}){a->ArchiveStoryCard(a,s.library,model,compact=false,open={open(a.id)},view={view(a)})}
        item(key="end"){Text("这些都是你留下的。",Modifier.fillMaxWidth().padding(vertical=12.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}

/** Shared cover, type and action placement across discovery and the archive wall. */
@Composable internal fun ArchiveStoryCard(a:Archive,library:Library,model:AppModel,compact:Boolean,open:()->Unit,view:()->Unit) {
    val c=MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface)
        .testTag("story-${a.id}")) {
        val cover=a.coverPhoto()
        if(cover!=null) Box(Modifier.fillMaxWidth().aspectRatio(if(compact)1f else 1.28f).clickable(onClickLabel="打开${a.name}的档案",onClick=open)) {
            // Covers can crop; the common viewer always offers the complete original.
            MediaThumb(cover,model,Modifier.fillMaxSize(),"${a.name}的封面")
            Surface(onClick=view,modifier=Modifier.align(Alignment.BottomEnd).padding(8.dp)
                .semantics{contentDescription="浏览${a.name}的照片与视频"},shape=RoundedCornerShape(24.dp),color=Color.Black.copy(alpha=.62f),contentColor=Color.White) {
                Row(Modifier.heightIn(min=48.dp).padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    FrameGlyph("全部照片",Color.White)
                    Text(if(compact)"${a.photos.size}" else a.mediaCountLabel(),style=MaterialTheme.typography.labelMedium)
                }
            }
        }
        Column(Modifier.fillMaxWidth().clickable(onClickLabel="打开${a.name}的档案",onClick=open).padding(if(compact)12.dp else 18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(cover==null)Text("文字档案",style=MaterialTheme.typography.labelMedium,color=c.onSurfaceVariant)
            Text(a.name,style=if(compact)MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,maxLines=if(compact)2 else 3,overflow=TextOverflow.Ellipsis)
            val summary=library.archiveSummary(a)
            if(summary.isNotBlank())Text(summary,style=if(compact)MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,color=c.onSurfaceVariant,maxLines=if(compact)2 else 3,overflow=TextOverflow.Ellipsis)
            val category=library.labelName(a.category)
            if(category.isNotBlank())Text(category,style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
        }
    }
}
