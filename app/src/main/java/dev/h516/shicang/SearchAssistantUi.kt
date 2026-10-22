package dev.h516.shicang

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun SearchRulesPage(library:Library,busy:Boolean,save:(SearchPreferences)->Unit,dismiss:()->Unit) {
    var draft by remember(library.searchPreferences){mutableStateOf(library.searchPreferences)}
    var phrase by remember{mutableStateOf("")};var target by remember{mutableStateOf("")}
    var category by remember{mutableStateOf("")};var error by remember{mutableStateOf("")}
    val rule=draft.rule(category).copy(category=category)
    fun update(value:SuggestionRule){draft=draft.copy(rules=draft.rules.filter{it.category!=category}+value)}
    androidx.activity.compose.BackHandler(onBack=dismiss)
    Column(Modifier.fillMaxSize().imePadding()) {
        PageBar("搜索与标签规则",dismiss)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
            GalleryTextButton(onClick=dismiss){Text("取消")}
            GalleryTextButton(enabled=!busy,onClick={runCatching{SearchPreferences.read(draft.json())}.onSuccess{save(it);dismiss()}.onFailure{error=it.message.orEmpty()}}){Text("保存规则")}
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("只在本机使用。别名不改档案、不合并标签；建议须逐项接受，手动标签不会被覆盖。")
        Text("搜索别名（单向，整条查询匹配）",style=MaterialTheme.typography.titleSmall)
        GalleryTextField(phrase,{phrase=it.take(100)},label={Text("我习惯搜的词")},singleLine=true)
        GalleryTextField(target,{target=it.take(100)},label={Text("对应的原名称或关键词")},singleLine=true)
        GalleryTextButton(onClick={if(phrase.isNotBlank() && target.isNotBlank()){draft=draft.copy(aliases=draft.aliases.filter{normalizedLabel(it.phrase)!=normalizedLabel(phrase)}+SearchAlias(phrase.trim(),target.trim()));phrase="";target=""}}){Text("添加 / 更新别名")}
        draft.aliases.forEach{alias->Row {Text("${alias.phrase} → ${alias.target}",Modifier.weight(1f));GalleryTextButton(onClick={draft=draft.copy(aliases=draft.aliases-alias)}){Text("移除")}}}
        Text("标签建议范围",style=MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            FilterChip(category.isEmpty(),{category=""},label={Text("默认")})
            library.labels.filter{it.kind=="category"}.forEach{label->FilterChip(category==label.id,{category=label.id},label={Text(label.name)})}
        }
        Text("分类未单独设置时使用默认规则。优先只调整有依据的候选，不凭空贴标签。")
        Row {Text("每次最多 ${rule.limit} 个",Modifier.weight(1f));(1..3).forEach{n->GalleryTextButton(onClick={update(rule.copy(limit=n))}){Text("$n")}}}
        library.labels.filter{it.kind=="tag"}.forEach{tag->Column {
            Text(tag.name)
            Row {FilterChip(tag.id in rule.preferred,{update(rule.copy(preferred=if(tag.id in rule.preferred)rule.preferred-tag.id else rule.preferred+tag.id))},label={Text("优先")});Spacer(Modifier.width(8.dp));FilterChip(tag.id in rule.excluded,{update(rule.copy(excluded=if(tag.id in rule.excluded)rule.excluded-tag.id else rule.excluded+tag.id))},label={Text("不再建议")})}
        }}
        if(category.isNotEmpty()) GalleryTextButton(onClick={draft=draft.copy(rules=draft.rules.filter{it.category!=category})}){Text("此分类恢复默认规则")}
        if(draft.rejections.isNotEmpty())GalleryTextButton(onClick={draft=draft.copy(rejections=emptyList())}){Text("清除 ${draft.rejections.size} 条单档案拒绝记录")}
        if(error.isNotEmpty())Text(error,color=MaterialTheme.colorScheme.error)
    }
    }
}

@Composable internal fun TagSuggestionsPanel(model:AppModel,s:AppState,a:Archive) {
    var limit by remember(a.id){mutableStateOf<Int?>(null)}
    val rule=s.library.searchPreferences.rule(a.category)
    val suggestions=remember(a,s.library,limit,s.aiTagSuggestions,s.aiTagArchive,s.aiTagSource,s.aiTagLabels,s.aiTagPreferences){
        (SearchAssistant.suggestions(s.library,a,limit?.let{rule.copy(limit=it)})+visibleAiTagSuggestions(s,a)).distinctBy{it.tag.id}.take(limit ?: rule.limit)
    }
    Column(Modifier.fillMaxWidth().padding(vertical=8.dp)) {
        Text("标签候选 · 逐项核对",style=MaterialTheme.typography.titleSmall)
        Text("文字规则与本机 AI 分开标明；仅用现有标签，不自动改资料。",style=MaterialTheme.typography.bodySmall)
        GalleryTextButton(enabled=!s.aiTagBusy&&!s.busy&&!s.modelDownloading,onClick=model::suggestTags){Text(if(s.aiTagBusy) "正在本机分析" else "用本机 AI 补充候选")}
        Row {GalleryTextButton(onClick={limit=if(limit==1)null else 1}){Text(if(limit==1) "本次只看 1 个 · 恢复默认" else "仅本次少建议")}}
        if(suggestions.isEmpty())Text("暂时没有足够依据的候选。",style=MaterialTheme.typography.bodySmall)
        suggestions.forEach{candidate->Column {
            Text("${candidate.tag.name} · ${candidate.reason}",style=MaterialTheme.typography.bodyMedium)
            FlowRow {GalleryTextButton(enabled=!s.busy,onClick={model.acceptTag(a.id,candidate.tag.id,candidate.tag.name)}){Text("接受")};GalleryTextButton(enabled=!s.busy,onClick={model.rejectTag(a.id,candidate.tag.id,false)}){Text("这份不适用")};GalleryTextButton(enabled=!s.busy,onClick={model.rejectTag(a.id,candidate.tag.id,true)}){Text("此范围不再建议")}}
        }}
    }
}

@Composable internal fun LocalModelPanel(model:AppModel,s:AppState) {
    var confirm by remember{mutableStateOf(false)}
    Column {
        Text(s.localModelStatus,style=MaterialTheme.typography.bodySmall)
        if(s.modelDownloading)GalleryTextButton(onClick=model::cancelModelDownload){Text("取消下载")}
        else GalleryTextButton(onClick={confirm=true}){Text("下载 / 校验本机 AI 模型")}
    }
    if(confirm)GalleryAlertDialog(onDismissRequest={confirm=false},title={Text("下载本机中文语义模型？")},text={Column {
        Text("BGE-small-zh-v1.5 · MIT 许可，约 24 MB，需额外存储和运行内存。仅从 Hugging Face 下载固定版本并校验 SHA-256；网站会收到网络 IP，不会收到你的档案或搜索词。")
        Text("下载可能消耗流量。下载后只在本机分析文字，不分析照片视频；AI 可能误匹配，不能推断未记载事实。最多检索 2000 份，请用分类缩小范围。")
        Text("模型许可：https://huggingface.co/BAAI/bge-small-zh-v1.5#license")
    }},confirmButton={GalleryTextButton(onClick={confirm=false;model.downloadLocalModel()}){Text("下载并在本机启用")}},dismissButton={GalleryTextButton(onClick={confirm=false}){Text("暂不下载")}})
}
