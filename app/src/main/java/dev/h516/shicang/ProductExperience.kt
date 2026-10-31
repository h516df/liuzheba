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

/** Discover is for revisiting saved works, while the library owns organizing archives. */
@Composable internal fun DiscoveryScreen(
    model:AppModel,s:AppState,positions:Map<String,String>,rememberPhoto:(String,String)->Unit,
    open:(String)->Unit,photos:(String)->Unit,settings:()->Unit,library:()->Unit,
    enabled:Boolean=true
) {
    val active=s.library.active
    if(active.none { it.photos.isNotEmpty() }) {
        Column(Modifier.fillMaxSize().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
            ArchiveNameplate("留",Modifier.size(88.dp))
            Text("值得留下的，都在这里",Modifier.padding(top=24.dp),style=MaterialTheme.typography.titleLarge)
            Text("发现会混合播放已保存的照片和视频；文字仍在档案里。",Modifier.padding(top=10.dp,bottom=24.dp),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            GalleryButton(onClick=library,enabled=!s.busy){Text("打开档案")}
        }
        return
    }
    DiscoveryWorkFeed(active,s.library.labels,model,s.appearance,rememberPhoto,library,open,photos,
        settings,{model.edit(it)},{model.edit()},s.busy,enabled=enabled,navigationInset=72.dp)
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
