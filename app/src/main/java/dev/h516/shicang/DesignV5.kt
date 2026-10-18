@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.h516.shicang

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable internal fun appearanceScheme(a:Appearance):ColorScheme {
    val dark=a.theme=="曜石" || (a.theme=="跟随系统" && isSystemInDarkTheme())
    val sea=a.theme=="海盐"
    return if(dark) darkColorScheme(primary=Color(0xFFBBC5FF),onPrimary=Color(0xFF15246B),
        background=Color(0xFF111318),surface=Color(0xFF1C1F27),surfaceVariant=Color(0xFF292D38),
        onSurface=Color(0xFFF0F1F7),onBackground=Color(0xFFF0F1F7),onSurfaceVariant=Color(0xFFB7BDCB),
        outline=Color(0xFF777E90),outlineVariant=Color(0xFF343947),secondary=Color(0xFFC6CCDF),primaryContainer=Color(0xFF293969),onPrimaryContainer=Color(0xFFE1E6FF),secondaryContainer=Color(0xFF303646),onSecondaryContainer=Color(0xFFE2E6F5),surfaceContainer=Color(0xFF232731))
    else lightColorScheme(primary=if(sea) Color(0xFF216578) else Color(0xFF4058D8),onPrimary=Color.White,
        background=if(sea) Color(0xFFF0F6F7) else Color(0xFFF7F7F8),
        surface=Color.White,surfaceVariant=if(sea) Color(0xFFE4EFF1) else Color(0xFFEFF0F2),
        onSurface=Color(0xFF1B2030),onBackground=Color(0xFF1B2030),onSurfaceVariant=Color(0xFF626B80),
        outline=Color(0xFF848DA1),outlineVariant=Color(0xFFE0E4EE),secondary=Color(0xFF545F7D),primaryContainer=if(sea) Color(0xFFDCEFF2) else Color(0xFFE8ECFF),onPrimaryContainer=if(sea) Color(0xFF144958) else Color(0xFF283C9C),secondaryContainer=Color(0xFFEBEEF6),onSecondaryContainer=Color(0xFF3D4860),surfaceContainer=if(sea) Color(0xFFE4EFF1) else Color(0xFFEFF0F2))
}

@Composable internal fun QuietInput(value:String,onChange:(String)->Unit,placeholder:String,modifier:Modifier=Modifier,singleLine:Boolean=true,enabled:Boolean=true,keyboard:KeyboardType=KeyboardType.Text) {
    TextField(value,onChange,modifier,singleLine=singleLine,enabled=enabled,placeholder={Text(placeholder,style=MaterialTheme.typography.bodyMedium)},
        shape=RoundedCornerShape(14.dp),keyboardOptions=KeyboardOptions(keyboardType=keyboard),
        colors=TextFieldDefaults.colors(unfocusedIndicatorColor=Color.Transparent,focusedIndicatorColor=Color.Transparent,
            disabledIndicatorColor=Color.Transparent,unfocusedContainerColor=MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.5f)),textStyle=MaterialTheme.typography.bodyLarge)
}

@Composable internal fun EditorV5(model:AppModel,s:AppState,choose:(String)->Unit,take:(String)->Unit,confirm:(String,()->Unit)->Unit) {
    val a=s.editor ?: return
    var options by rememberSaveable(a.id){mutableStateOf(false)}
    var picker by remember{mutableStateOf(false)}
    var saveTemplate by remember{mutableStateOf(false)}
    var templateName by remember{mutableStateOf("")}
    var labelKind by remember{mutableStateOf("")}
    var labelName by remember{mutableStateOf("")}
    Column(Modifier.fillMaxWidth().heightIn(max=680.dp).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=22.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(if(s.library.archives.any{it.id==a.id}) "编辑档案" else "新建档案",Modifier.weight(1f),fontSize=21.sp,fontWeight=FontWeight.Medium)
            TextButton(onClick=model::leaveEditor){Text("关闭")}
        }
        LazyColumn(Modifier.weight(1f).padding(horizontal=22.dp),contentPadding=PaddingValues(bottom=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("从空白开始",Modifier.weight(1f),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);TextButton(onClick={picker=true},enabled=!s.busy){Text("选择模板",fontSize=12.sp)}}}
            item {Column(verticalArrangement=Arrangement.spacedBy(8.dp)){Text("称呼",fontSize=12.sp);QuietInput(a.name,{model.change(a.copy(name=it))},"名称或称呼",Modifier.fillMaxWidth(),enabled=!s.busy)}}
            item {Row(verticalAlignment=Alignment.CenterVertically){OutlinedButton(onClick={choose(a.id)},enabled=!s.busy,shape=RoundedCornerShape(10.dp)){Text("添加照片")};TextButton(onClick={take(a.id)},enabled=!s.busy){Text("拍照")};Text("可选 · ${a.photos.size} 张",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
            if(a.photos.isNotEmpty()) item{EditorPhotos(a,model,s.busy)}
            item{Text("资料字段",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            items(a.fields,key={it.id}) {f ->
                Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.5f)) {
                    Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        var types by remember{mutableStateOf(false)}
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            QuietInput(f.name,{v->model.change(a.copy(fields=a.fields.map{if(it.id==f.id) it.copy(name=v) else it}))},"字段名称",Modifier.weight(1f),enabled=!s.busy)
                            Box {TextButton(onClick={types=true},enabled=!s.busy){Text(f.type,fontSize=12.sp)}
                                DropdownMenu(types,{types=false}) {Templates.types.forEach{type->DropdownMenuItem(text={Text(type)},onClick={types=false;model.change(a.copy(fields=a.fields.map{if(it.id==f.id) it.copy(type=type) else it}))})}}}
                            TextButton(onClick={val action={model.change(a.copy(fields=a.fields.filter{it.id!=f.id}))};if(f.value.isNotBlank()) confirm("移除这个字段及内容？保存前可放弃编辑。",action) else action()},enabled=!s.busy,contentPadding=PaddingValues(0.dp),modifier=Modifier.width(32.dp)){Text("×",fontSize=19.sp)}
                        }
                        QuietInput(f.value,{v->model.change(a.copy(fields=a.fields.map{if(it.id==f.id) it.copy(value=v) else it}))},if(f.type=="日期") "YYYY-MM-DD" else "填写内容",Modifier.fillMaxWidth(),singleLine=false,enabled=!s.busy,keyboard=if(f.type=="数字") KeyboardType.Decimal else KeyboardType.Text)
                        if(a.fields.firstOrNull()?.id!=f.id) Row {TextButton(onClick={val fields=a.fields.toMutableList();val i=fields.indexOfFirst{it.id==f.id};fields.add(i-1,fields.removeAt(i));model.change(a.copy(fields=fields))},enabled=!s.busy && a.fields.firstOrNull()?.id!=f.id){Text("上移",fontSize=11.sp)}}
                    }
                }
            }
            item{TextButton(onClick={model.change(a.copy(fields=a.fields+Field()))},enabled=!s.busy && a.fields.size<40){Text("＋ 添加字段",fontSize=12.sp)}}
            item{TextButton(onClick={options=!options}){Text("分类与标签（选填）  ${if(options) "−" else "+"}",fontSize=12.sp)}}
            if(options) item {Column {
                Text("分类",fontSize=12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    FilterChip(a.category.isEmpty(),{model.change(a.copy(category=""))},label={Text("未分类")},enabled=!s.busy)
                    s.library.labels.filter{it.kind=="category"}.forEach{l->FilterChip(a.category==l.id,{model.change(a.copy(category=l.id))},label={Text(l.name)},enabled=!s.busy)}
                    TextButton(onClick={labelKind="category"},enabled=!s.busy){Text("＋分类")}
                }
                Text("标签",fontSize=12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    s.library.labels.filter{it.kind=="tag"}.forEach{l->FilterChip(l.id in a.tags,{model.change(a.copy(tags=if(l.id in a.tags) a.tags-l.id else a.tags+l.id))},label={Text(l.name)},enabled=!s.busy)}
                    TextButton(onClick={labelKind="tag"},enabled=!s.busy){Text("＋标签")}
                }
            }}
            item{TextButton(onClick={confirm("放弃这次编辑？已保存的正式档案保留。",model::discardEditor)},enabled=!s.busy){Text("放弃编辑",fontSize=12.sp)}}
        }
        Row(Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=10.dp).navigationBarsPadding(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically){
            OutlinedButton(onClick={saveTemplate=true},enabled=!s.busy,shape=RoundedCornerShape(12.dp)){Text("存为模板",fontSize=12.sp)}
            Button(onClick=model::saveEditor,enabled=!s.busy && a.name.isNotBlank(),modifier=Modifier.weight(1f),shape=RoundedCornerShape(12.dp)){Text(if(s.busy) s.progress else "保存档案")}
        }
        Text(s.draftStatus,Modifier.padding(start=22.dp,bottom=10.dp),fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(s.message.isNotBlank()) Text(s.message,Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=8.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurface)
    }
    if(picker) AlertDialog(onDismissRequest={picker=false},title={Text("选择模板")},text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())){
        if(s.library.templates.isEmpty()) Text("还没有模板。填写字段名称后，可以存为模板。")
        s.library.templates.forEach{t->TextButton(onClick={model.applyTemplate(t);picker=false},enabled=!s.busy){Column(Modifier.fillMaxWidth()){Text(t.name);Text(t.fields.joinToString(" · "){it.name},fontSize=11.sp)}}}
        Text("只应用字段结构；已填写的内容会保留。",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }},confirmButton={TextButton(onClick={picker=false}){Text("关闭")}})
    if(saveTemplate) AlertDialog(onDismissRequest={saveTemplate=false},title={Text("存为模板")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){QuietInput(templateName,{templateName=it},"模板名称",Modifier.fillMaxWidth());Text("保存字段名称、类型和顺序，不保存称呼、照片或字段内容。",fontSize=12.sp)}},confirmButton={TextButton(onClick={model.saveTemplate(templateName);saveTemplate=false;templateName=""},enabled=!s.busy && templateName.isNotBlank()){Text("保存模板")}},dismissButton={TextButton(onClick={saveTemplate=false}){Text("取消")}})
    if(labelKind.isNotEmpty()) AlertDialog(onDismissRequest={labelKind="";labelName=""},title={Text(if(labelKind=="tag") "添加标签" else "添加分类")},text={QuietInput(labelName,{labelName=it},"名称",Modifier.fillMaxWidth())},confirmButton={TextButton(onClick={model.label(labelKind,labelName);labelKind="";labelName=""},enabled=!s.busy && labelName.isNotBlank()){Text("添加")}},dismissButton={TextButton(onClick={labelKind="";labelName=""}){Text("取消")}})
}

@Composable internal fun AppearanceV5(model:AppModel,s:AppState,close:()->Unit,settings:()->Unit,showActions:Boolean=true) {
    Column(Modifier.fillMaxWidth().padding(horizontal=22.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){Text("主题与皮肤",Modifier.weight(1f),fontSize=21.sp,fontWeight=FontWeight.Medium);if(showActions) TextButton(onClick=close){Text("关闭")}}
        Text("主题",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("曜石","月白","海盐").forEach {theme ->
            Surface(Modifier.weight(1f).clickable(enabled=!s.busy){model.appearance(s.appearance.copy(theme=theme))},shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceVariant,
                border=if(s.appearance.theme==theme) BorderStroke(1.dp,MaterialTheme.colorScheme.onSurface) else null){Column(Modifier.padding(14.dp)){Box(Modifier.size(18.dp).background(when(theme){"曜石"->Color(0xFF181B20);"海盐"->Color(0xFFD8E7EC);else->Color(0xFFF6F7F9)},RoundedCornerShape(50)));Spacer(Modifier.height(14.dp));Text(theme,fontSize=13.sp)}}
        }}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("跟随系统",Modifier.weight(1f),fontSize=12.sp);Switch(s.appearance.theme=="跟随系统",{model.appearance(s.appearance.copy(theme=if(it) "跟随系统" else "曜石"))},enabled=!s.busy)}
        Text("画幅皮肤",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("全幅","留白","暗房").forEach{skin->Surface(Modifier.weight(1f).clickable(enabled=!s.busy){model.appearance(s.appearance.copy(skin=skin))},shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceVariant,
            border=if(s.appearance.skin==skin) BorderStroke(1.dp,MaterialTheme.colorScheme.onSurface) else null){Column(Modifier.padding(14.dp)){Box(Modifier.fillMaxWidth().height(64.dp).background(if(skin=="暗房") Color.Black else MaterialTheme.colorScheme.background,RoundedCornerShape(4.dp)),contentAlignment=Alignment.Center){val preview=s.library.active.firstOrNull{it.photos.isNotEmpty()}?.coverPhoto()
                val photoModifier=Modifier.fillMaxSize().padding(if(skin=="留白") 6.dp else 0.dp)
                if(preview!=null) MediaThumb(preview,model,photoModifier,scale=if(skin=="全幅") androidx.compose.ui.layout.ContentScale.Crop else androidx.compose.ui.layout.ContentScale.Fit)
                else Box(Modifier.fillMaxHeight(if(skin=="留白") .75f else 1f).width(if(skin=="全幅") 48.dp else 27.dp).background(Color(0xFF727F74),RoundedCornerShape(2.dp)))};Spacer(Modifier.height(10.dp));Text(skin,fontSize=13.sp);Text(if(skin=="全幅") "铺满屏幕" else "完整照片",fontSize=10.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}
        if(s.appearance.skin=="全幅") Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("照片显示",Modifier.weight(1f),fontSize=12.sp);listOf("完整","铺满").forEach{fit->FilterChip(s.appearance.fit==fit,{model.appearance(s.appearance.copy(fit=fit))},label={Text(fit,fontSize=12.sp)},enabled=!s.busy)}}
        Text("铺满会裁掉边缘；留白和暗房保留整张照片。",fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(showActions) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){TextButton(onClick=settings){Text("更多设置",fontSize=12.sp)};TextButton(onClick=close){Text("回到画幅")}}
        Spacer(Modifier.height(10.dp))
    }
}
