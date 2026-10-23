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
    return if(dark) darkColorScheme(primary=Color(0xFF0A84FF),onPrimary=Color.White,
        background=Color(0xFF000000),surface=Color(0xFF1C1C1E),surfaceVariant=Color(0xFF2C2C2E),
        onSurface=Color(0xFFF2F2F7),onBackground=Color(0xFFF2F2F7),onSurfaceVariant=Color(0xFFAEAEB2),
        outline=Color(0xFF7E8D83),outlineVariant=Color(0xFF3A3A3C),secondary=Color(0xFFC5C9BC),
        primaryContainer=Color(0xFF123456),onPrimaryContainer=Color(0xFFD6EAFF),secondaryContainer=Color(0xFF2C2C2E),
        onSecondaryContainer=Color(0xFF90C3FF),surfaceContainer=Color(0xFF2C2C2E))
    else lightColorScheme(primary=if(sea) Color(0xFF396877) else Color(0xFF0066D6),onPrimary=Color.White,
        background=if(sea) Color(0xFFF2F6F7) else Color(0xFFF7F8FA),surface=Color.White,
        surfaceVariant=if(sea) Color(0xFFE5EEF0) else Color(0xFFEEF0F3),
        onSurface=Color(0xFF1C1C1E),onBackground=Color(0xFF1C1C1E),onSurfaceVariant=Color(0xFF636366),
        outline=Color(0xFF8E8E93),outlineVariant=Color(0xFFD1D1D6),secondary=Color(0xFF636366),
        primaryContainer=if(sea) Color(0xFFDCECF0) else Color(0xFFE4EFFF),onPrimaryContainer=Color(0xFF0055B3),
        secondaryContainer=Color(0xFFE5E5EA),onSecondaryContainer=Color(0xFF0055B3),surfaceContainer=Color(0xFFE5E5EA))
}

@Composable internal fun QuietInput(value:String,onChange:(String)->Unit,placeholder:String,modifier:Modifier=Modifier,singleLine:Boolean=true,enabled:Boolean=true,keyboard:KeyboardType=KeyboardType.Text) {
    TextField(value,onChange,modifier,singleLine=singleLine,enabled=enabled,placeholder={Text(placeholder,style=MaterialTheme.typography.bodyMedium)},
        shape=RoundedCornerShape(14.dp),keyboardOptions=KeyboardOptions(keyboardType=keyboard),
        colors=TextFieldDefaults.colors(unfocusedIndicatorColor=Color.Transparent,focusedIndicatorColor=Color.Transparent,
            disabledIndicatorColor=Color.Transparent,unfocusedContainerColor=MaterialTheme.colorScheme.surfaceVariant,
            focusedContainerColor=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.5f)),textStyle=MaterialTheme.typography.bodyLarge)
}

@Composable internal fun AppearanceSettings(model:AppModel,s:AppState,close:()->Unit,settings:()->Unit,showActions:Boolean=true) {
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
