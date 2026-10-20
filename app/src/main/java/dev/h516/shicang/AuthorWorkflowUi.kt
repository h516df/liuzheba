@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.h516.shicang

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private fun authorTitle(a:Author)=a.name.ifBlank{a.identities.firstOrNull{it.nickname.isNotBlank()}?.nickname ?: "未填写称呼的作者"}

@Composable internal fun AuthorArchiveSection(model:AppModel,s:AppState,a:Archive,editable:Boolean) {
    if(a.authorIds.isEmpty() && !editable)return
    Column(Modifier.fillMaxWidth().padding(vertical=12.dp)) {
        Row {
            Text("作者",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            if(editable)TextButton(onClick={model.openAuthors(a.id)},enabled=!s.busy){Text("关联作者")}
        }
        s.library.authors.filter{it.id in a.authorIds}.forEach{author->
            AuthorCard(author,model)
            if(editable)TextButton(onClick={model.linkAuthor(author.id,a.id,false)},enabled=!s.busy){Text("移除关联")}
        }
        if(a.authorIds.isEmpty())Text("可导入公开主页，或关联已有作者。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun AuthorCard(author:Author,model:AppModel,actions:@Composable ()->Unit={}) {
    val context=LocalContext.current
    Surface(Modifier.fillMaxWidth().padding(vertical=6.dp),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(authorTitle(author),style=MaterialTheme.typography.titleMedium)
            author.identities.forEach{identity->
                Text(identity.platform,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                if(identity.nickname.isNotBlank() && identity.nickname!=authorTitle(author))Text("公开昵称：${identity.nickname}")
                if(identity.publicAccount.isNotBlank())Text("公开账号：${identity.publicAccount}")
                if(identity.platformId.isNotBlank())Text("${identity.idType.ifBlank{"平台标识"}}：${identity.platformId}",style=MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick={
                        val verified=AuthorProfiles.parse(identity.profileUrl)?.takeIf{AuthorProfiles.valid(identity)}
                        if(verified==null)model.message("主页链接无效，请重新导入")
                        else runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(verified.profileUrl)).addCategory(Intent.CATEGORY_BROWSABLE))}
                            .onFailure{model.message("没有可打开主页的应用，可以复制链接后打开浏览器")}
                    },enabled=identity.profileUrl.isNotBlank()){Text("打开${identity.platform}主页")}
                    TextButton(onClick={(context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("作者主页",identity.profileUrl));model.message("主页链接已复制")},enabled=identity.profileUrl.isNotBlank()){Text("复制")}
                }
            }
            TextButton(onClick={model.noteEditor.open("author",author.id,authorTitle(author))}){Text("编辑作者笔记")}
            actions()
        }
    }
}

@Composable internal fun AuthorWorkflowUi(model:AppModel,s:AppState) {
    if(!s.authorsOpen)return
    val preview=s.authorPreview
    var leavePreview by remember{mutableStateOf(false)}
    var associate by remember{mutableStateOf<Author?>(null)}
    fun back(){if(preview!=null)leavePreview=true else if(s.authorBase!=null)model.cancelAuthorIdentity() else model.closeAuthors()}
    BackHandler{if(!s.busy)back()}
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            PageBar(if(preview==null) "作者" else "核对作者资料",{back()}) {
                if(preview!=null)Button(onClick=model::saveAuthor,enabled=!s.busy){Text(if(s.library.authors.none{it.id==preview.id} && s.authorTarget!=null) "保存并关联此档案" else "保存作者资料")}
            }
            if(s.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(s.message.isNotBlank())Text(s.message,Modifier.padding(16.dp),color=MaterialTheme.colorScheme.error)
            LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(preview==null) {
                    item {
                        Text(if(s.authorBase==null) "从作者主页开始" else "添加平台主页",style=MaterialTheme.typography.titleMedium)
                        Text("小红书、抖音、快手、微博。只读取公开资料，不使用登录状态。",style=MaterialTheme.typography.bodySmall)
                        OutlinedTextField(s.authorInput,model::authorInput,Modifier.fillMaxWidth().padding(top=12.dp),label={Text("粘贴作者主页分享链接")},enabled=!s.busy,minLines=2,maxLines=4)
                        Button(onClick=model::importAuthor,enabled=!s.busy && s.authorInput.isNotBlank(),modifier=Modifier.padding(top=8.dp)){Text("导入并预览")}
                        if(s.authorNotice.isNotBlank())Text(s.authorNotice,style=MaterialTheme.typography.bodySmall)
                    }
                    if(s.library.authors.isEmpty() && s.authorBase==null)item{Text("还没有作者。资料不足时会保留已验证的主页，缺少的信息可手动补充。")}
                    items(if(s.authorBase==null)s.library.authors else emptyList(),key={it.id}){author->AuthorCard(author,model){
                        FlowRow {
                            TextButton(onClick={model.previewAuthor(author)},enabled=!s.busy){Text("编辑作者卡")}
                            TextButton(onClick={model.addAuthorIdentity(author)},enabled=!s.busy){Text("添加平台")}
                            if(s.authorTarget!=null) {
                                val target=s.editor?.takeIf{it.id==s.authorTarget} ?: s.library.active.firstOrNull{it.id==s.authorTarget}
                                val linked=author.id in target?.authorIds.orEmpty()
                                TextButton(onClick={model.linkAuthor(author.id,s.authorTarget,!linked)},enabled=!s.busy){Text(if(linked)"取消关联" else "关联此档案")}
                            } else TextButton(onClick={associate=author},enabled=!s.busy){Text("关联档案")}
                        }
                    }}
                } else {
                    item {
                        Text(s.authorNotice.ifBlank{"修改只影响这张作者卡，不会合并同名作者。"},style=MaterialTheme.typography.bodySmall)
                        OutlinedTextField(preview.name,{model.changeAuthor(preview.copy(name=it))},Modifier.fillMaxWidth(),label={Text("本地称呼（可选）")},enabled=!s.busy)
                    }
                    items(preview.identities,key={it.id}){identity->
                        fun update(value:PlatformIdentity){model.changeAuthor(preview.copy(identities=preview.identities.map{if(it.id==value.id)value else it}))}
                        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            Text(identity.platform,style=MaterialTheme.typography.titleMedium)
                            OutlinedTextField(identity.nickname,{update(identity.copy(nickname=it))},Modifier.fillMaxWidth(),label={Text("公开昵称（未获取可留空）")},enabled=!s.busy)
                            OutlinedTextField(identity.publicAccount,{update(identity.copy(publicAccount=it))},Modifier.fillMaxWidth(),label={Text("公开账号（不是平台内部ID）")},enabled=!s.busy)
                            if(identity.platformId.isNotBlank())Text("${identity.idType}：${identity.platformId}",style=MaterialTheme.typography.bodySmall)
                            Text(identity.profileUrl,style=MaterialTheme.typography.bodySmall)
                            Text("平台标识来自主页链接；昵称和账号不会由分享文案猜测。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    item{TextButton(onClick={leavePreview=true},enabled=!s.busy){Text("取消这次编辑")}}
                }
            }
        }
    }
    if(leavePreview)AlertDialog(onDismissRequest={leavePreview=false},title={Text("放弃未保存的作者资料？")},text={Text("原有作者卡和档案关联不会改变。")},confirmButton={TextButton(onClick={leavePreview=false;model.previewAuthor(null)}){Text("放弃修改")}},dismissButton={TextButton(onClick={leavePreview=false}){Text("继续编辑")}})
    associate?.let{author->AlertDialog(onDismissRequest={associate=null},title={Text("关联「${authorTitle(author)}」")},text={
        LazyColumn(Modifier.heightIn(max=360.dp)) {
            if(s.library.active.isEmpty())item{Text("请先创建一份档案，再关联作者。")}
            items(s.library.active,key={it.id}){archive->
                val linked=author.id in archive.authorIds
                Row {Checkbox(linked,{model.linkAuthor(author.id,archive.id,it)},enabled=!s.busy);Text(archive.name,Modifier.padding(top=12.dp))}
            }
        }
    },confirmButton={TextButton(onClick={associate=null}){Text("完成")}})}
}
