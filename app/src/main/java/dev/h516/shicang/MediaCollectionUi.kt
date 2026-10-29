package dev.h516.shicang

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage

@Composable internal fun MediaCollectionUi(model:AppModel,s:AppState) {
    val context=LocalContext.current
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted ->
        if(granted)model.exportMedia() else {model.cancelExport();model.message("未获得相册写入权限，原件仍保存在留着吧")}
    }
    LaunchedEffect(s.exportRequest) {
        if(s.exportRequest!=null) {
            if(Build.VERSION.SDK_INT>=29 || ContextCompat.checkSelfPermission(context,Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED)model.exportMedia()
            else permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
    if(s.incomingMedia.isNotEmpty()) ArchiveTargetDialog("收录 ${s.incomingMedia.size} 项媒体",s.library.active,s.busy,model::dismissMedia){model.importIncoming(it)}
    if(s.downloadsOpen) DownloadSheet(model,s)
}

@Composable private fun ArchiveTargetDialog(title:String,archives:List<Archive>,busy:Boolean,dismiss:()->Unit,select:(String?)->Unit) {
    GalleryAlertDialog(onDismissRequest={if(!busy)dismiss()},title={Text(title)},text={
        var query by rememberSaveable{mutableStateOf("")}
        LazyColumn(Modifier.heightIn(max=380.dp)) {
            item {GallerySearchField(query,{query=it},"搜索收录位置")}
            item {GalleryTextButton(onClick={select(null)},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("新建档案")}}
            items(archives.filter{query.isBlank() || it.name.contains(query,true)}.sortedByDescending{it.updated},key={it.id}) {a->GalleryTextButton(onClick={select(a.id)},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(a.name,maxLines=2,overflow=TextOverflow.Ellipsis)}}
        }
    },confirmButton={},dismissButton={GalleryTextButton(onClick=dismiss,enabled=!busy){Text("取消")}})
}

@Composable private fun DownloadSheet(model:AppModel,s:AppState) {
    val context=LocalContext.current
    val browser=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode==Activity.RESULT_OK) {
            result.data?.dataString?.let {url->
                if(runCatching{MediaLinks.validUrl(url)}.getOrNull()==url) {
                    model.downloadInput(url)
                    model.resolveMedia()
                }
            }
        }
    }
    var target by remember {mutableStateOf<List<MediaDownload>?>(null)}
    var confirmCleanup by remember {mutableStateOf(false)}
    val completedCount=completedDownloadRecords(s.downloads,s.library.active.map{it.id}.toSet()-s.library.drafts.map{it.id}.toSet()).size
    val plan=s.mediaPlan
    var selection by remember(plan) {mutableStateOf(plan?.items?.indices?.toSet() ?: emptySet())}
    Dialog(onDismissRequest=model::closeDownloads,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                PageBar("链接下载",model::closeDownloads)
                LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    item {
                        GalleryTextField(s.downloadInput,model::downloadInput,Modifier.fillMaxWidth(),enabled=!s.resolving,
                            label={Text("粘贴分享链接或媒体地址")},minLines=2,maxLines=4)
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                            GalleryButton(onClick=model::resolveMedia,enabled=!s.resolving && s.downloadInput.isNotBlank()) {Text(if(s.resolving) "正在解析…" else "解析链接")}
                            if(s.downloadInput.isNotBlank()) GalleryTextButton(onClick={
                                val url=runCatching{MediaLinks.validUrl(s.downloadInput)}.getOrNull()
                                if(url!=null)browser.launch(Intent(context,MediaBrowserActivity::class.java).putExtra("url",url))
                            }){Text("打开网页验证")}
                        }
                        if(s.resolving) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=8.dp))
                        if(s.downloadError.isNotBlank()) Text(s.downloadError,Modifier.padding(top=8.dp),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    }
                    if(plan!=null) {
                        item {
                            Text(plan.title,style=MaterialTheme.typography.titleMedium,maxLines=3,overflow=TextOverflow.Ellipsis)
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                GalleryTextButton(onClick={selection=if(selection.size==plan.items.size) emptySet() else plan.items.indices.toSet()}){Text(if(selection.size==plan.items.size)"取消全选" else "全选")}
                                Spacer(Modifier.weight(1f))
                                GalleryButton(onClick={model.enqueueMedia(selection)},enabled=selection.isNotEmpty()){Text("下载所选 ${selection.size} 项")}
                            }
                        }
                        itemsIndexed(plan.items) {index,item ->
                            Row(Modifier.fillMaxWidth().clickable{selection=if(index in selection)selection-index else selection+index}.padding(vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                                Checkbox(index in selection,{checked->selection=if(checked)selection+index else selection-index})
                                if(item.kind=="image") AsyncImage(item.url,"第${index+1}张图片预览",Modifier.size(72.dp).background(MaterialTheme.colorScheme.surfaceVariant),contentScale=ContentScale.Crop)
                                else Box(Modifier.size(72.dp).background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center){Text("视频")}
                                Text("${index+1} · ${if(item.kind=="image")"图片" else "视频"}",Modifier.padding(start=12.dp),style=MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    item {Text("下载记录",style=MaterialTheme.typography.titleMedium)}
                    if(completedCount>0) item {
                        GalleryTextButton(onClick={confirmCleanup=true},enabled=!s.busy) {
                            Text("清理已收录记录（$completedCount）")
                        }
                    }
                    items(s.downloads.filter{it.state=="待收录"}.groupBy{it.source}.values.filter{it.size>1}.toList(),key={"batch-"+it.first().id}) {batch->
                        GalleryTonalButton(onClick={target=batch},enabled=!s.busy,modifier=Modifier.fillMaxWidth()) {
                            Text("收录同一来源的 ${batch.size} 项",style=MaterialTheme.typography.labelLarge)
                        }
                    }
                    if(s.downloads.isEmpty()) item {Text("下载完成后，选择一份档案收录。",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium)}
                    items(s.downloads.asReversed(),key={it.id}) {job ->
                        Column(Modifier.fillMaxWidth().padding(vertical=6.dp)) {
                            Text(job.title,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyLarge)
                            val draft=s.library.drafts.firstOrNull{it.id==job.archiveId && s.library.active.none{a->a.id==job.archiveId}}
                            val status=if(job.state=="已收录" && draft!=null) "已存为草稿" else job.state
                            Text("${if(job.kind=="video")"视频" else "图片"} · $status${if(job.state=="下载中") " ${job.percent}%" else ""}",Modifier.padding(top=4.dp),color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
                            if(job.failure.isNotBlank()) Text(job.failure,Modifier.padding(top=4.dp),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                            if(job.state in listOf("下载中","等待网络","等待下载")) LinearProgressIndicator(progress={job.percent/100f},modifier=Modifier.fillMaxWidth().padding(top=8.dp))
                            Row {
                                if(draft!=null)GalleryTextButton(onClick={model.closeDownloads();model.edit(draft)},enabled=!s.busy){Text("继续完成收录")}
                                if(job.state=="待收录") GalleryTextButton(onClick={target=listOf(job)},enabled=!s.busy){Text("收录到档案")}
                                if(job.state in listOf("下载失败","文件已移除","下载已移除")) GalleryTextButton(onClick={model.retryDownload(job)}){Text("重新解析")}
                                GalleryTextButton(onClick={model.removeDownload(job.id)},enabled=!s.busy){Text(if(job.state in listOf("下载中","等待网络","等待下载"))"取消下载" else "移除记录")}
                            }
                        }
                    }
                    if(s.busy)item{LinearProgressIndicator(Modifier.fillMaxWidth());Text(s.progress,style=MaterialTheme.typography.bodySmall)}
                    item {Text("支持小红书、抖音、快手、微博、哔哩哔哩及 Instagram 公开作品链接。若解析失败，点「打开网页验证」，在网页确认作品可查看后点「返回并解析当前网页」。只保存可直接读取的原图或视频，需额外授权或受保护的媒体可能无法下载；也可从其他 App 直接分享图片、视频到留着吧。",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)}
                }
            }
        }
    }
    target?.let{jobs->ArchiveTargetDialog("收录 ${jobs.size} 项到档案",s.library.active,s.busy,{target=null}){id->target=null;model.importDownloads(jobs,id)}}
    if(confirmCleanup) GalleryAlertDialog(onDismissRequest={confirmCleanup=false},title={Text("清理下载记录？")},text={
        Text("仅清理已保存到正式档案的 $completedCount 条记录，不删除档案内的原件。尚未完成的草稿、待收录媒体和下载任务都会保留。")
    },confirmButton={GalleryButton(onClick={confirmCleanup=false;model.clearCompletedDownloads()},enabled=!s.busy && completedCount>0){Text("确认清理")}},
        dismissButton={GalleryTextButton(onClick={confirmCleanup=false}){Text("取消")}})
}
