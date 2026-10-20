package dev.h516.shicang

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal const val PROTECTION_SETUP_NOTICE="留着吧会自动创建“留册自动保护”文件夹（沿用旧名称，兼容已有备份），之后自动保存资料与原始照片、视频；不用每次导出。首次只需在系统界面允许访问本机 Documents 等位置，无需手动创建文件夹。\n\n保护文件未加密，包含草稿、回收站和原始媒体（可能含拍摄位置）；应用锁不保护外部文件夹。请选择可信的本机位置，不要选择网盘或公开共享目录。\n\n必须等到“已保护”再考虑删除原文件。重装或清除数据后，重新选择同一位置并确认恢复。"

@Composable internal fun ProtectionPanel(state:ProtectionState,busy:Boolean,choose:()->Unit,retry:()->Unit,acknowledge:()->Unit={}) {
    val colors=MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth().padding(bottom=20.dp),colors=CardDefaults.cardColors(containerColor=colors.surfaceContainer)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("自动保护 · ${state.status}",style=MaterialTheme.typography.titleMedium)
            Text(state.detail,style=MaterialTheme.typography.bodyMedium)
            if(state.location.isNotEmpty()) Text("保存位置：${state.location}",style=MaterialTheme.typography.bodySmall)
            if(state.lastSuccess>0) Text("上次校验：${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(state.lastSuccess))}",style=MaterialTheme.typography.bodySmall)
            if(state.location.isNotEmpty()) Text("离开页面仍会尝试继续；系统中断后下次打开续做。旧版本保留，不自动清理。",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
            Text("App 内保存不等于外部保护。仅在显示“已保护”后再考虑删除原文件；同一手机损坏或丢失仍可能一起丢失。",style=MaterialTheme.typography.bodySmall,color=colors.onSurfaceVariant)
            if(state.status=="待核对") TextButton(onClick=acknowledge,enabled=!busy){Text("我已核对未收录的原文件")}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                TextButton(onClick=choose,enabled=!busy,modifier=Modifier.weight(1f)) {Text(if(state.location.isEmpty()) "开启 / 找回资料" else "重新选择 / 恢复")}
                if(state.location.isNotEmpty()) TextButton(onClick=retry,enabled=!busy,modifier=Modifier.weight(1f)){Text("立即校验重试")}
            }
        }
    }
}
