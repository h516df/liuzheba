package dev.h516.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.h516.update.ReleaseNotes
import dev.h516.update.UpdateConfig
import dev.h516.update.UpdateState
import dev.h516.update.Updater

/**
 * 「检查更新」页。能看当前版本和中文更新说明，能检查、下载、交给系统安装。
 * 前台限频匿名检查版本；下载和系统安装都由用户明确发起。
 */
@Composable
fun UpdateScreen(updater: Updater, contentPadding: PaddingValues) {
    val state = updater.state
    val context = LocalContext.current
    val currentNotes = remember { updater.currentNotes() }
    val versionName = remember { updater.installedVersionName() }
    val versionCode = remember { updater.installedVersionCode() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "当前版本 $versionName（$versionCode）",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )

        when (state) {
            UpdateState.Unsupported -> Text("当前是测试版或非官方签名版本，公开更新已停用。正式版资料不会被替换。",color=MaterialTheme.colorScheme.onSurfaceVariant)
            UpdateState.Verifying -> Row(verticalAlignment=Alignment.CenterVertically){CircularProgressIndicator(Modifier.size(18.dp),strokeWidth=2.dp);Spacer(Modifier.width(10.dp));Text("正在重新校验安装包…")}
            UpdateState.NeedsInstallPermission -> Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("安装已验证的新版，需要你在系统设置里允许留册安装应用。返回后会继续打开系统安装确认。")
                Text("此许可用于打开更新安装，可随时在系统设置关闭；不会读取或上传档案。你也可以取消，继续使用当前版本。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick=updater::openInstallPermission){Text("前往系统设置")}
                TextButton(onClick=updater::cancel){Text("暂不安装")}
            }
            is UpdateState.Idle -> Button(onClick = { updater.check() }) { Text("检查更新") }

            is UpdateState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("正在检查…", fontSize = 14.sp)
            }

            is UpdateState.Latest -> {
                Text("已是最新版本", fontSize = 15.sp)
                Button(onClick = { updater.check() }) { Text("再检查一次") }
            }

            is UpdateState.Available -> {
                Text("发现新版本 ${state.candidate.notes.version}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "安装包约 ${state.candidate.apk.size / 1024 / 1024 + 1} MB，装之前会校验签名和版本号",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { updater.download() }) { Text("立即更新") }
                    TextButton(onClick = { updater.cancel() }) { Text("稍后") }
                }
            }

            is UpdateState.Downloading -> {
                Text("正在下载 ${state.percent}%", fontSize = 15.sp)
                TextButton(onClick=updater::cancel){Text("取消下载")}
                LinearProgressIndicator(
                    progress = { state.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            is UpdateState.ReadyToInstall -> {
                Text("下载并校验通过，接下来交给系统安装界面", fontSize = 15.sp)
                Button(onClick = { updater.install() }) { Text("打开系统安装") }
            }

            is UpdateState.Failed -> {
                Text(state.message, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
                Button(onClick = { updater.retry() }) { Text("重试") }
            }
        }

        if (state is UpdateState.Available) {
            NotesBlock("新版说明 ${state.candidate.notes.version}", state.candidate.notes)
        }
        Text("更新采用覆盖安装，下载失败不修改现有档案。系统安装需要你确认，请不要卸载旧版。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        currentNotes?.let { NotesBlock("本版说明 ${it.version}", it) }

        if(state!=UpdateState.Unsupported) Row(verticalAlignment=Alignment.CenterVertically) {
            Text("新版本提醒",Modifier.weight(1f))
            androidx.compose.material3.Switch(checked=updater.autoCheckEnabled,onCheckedChange=updater::changeAutoCheckEnabled)
        }
        Text("开启提醒后，解锁并回到前台时至多每天匿名检查一次。只访问更新源，不发送档案、照片或账号；下载由你决定。关闭后仅手动检查，无需登录 GitHub。", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { openBrowser(context, "https://github.com/" + UpdateConfig.REPO) }) {
            Text("查看开源项目")
        }
    }
}

@Composable
private fun NotesBlock(title: String, notes: ReleaseNotes) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        notes.sections.forEach { (heading, lines) ->
            Text(heading, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
            lines.forEach { line ->
                Row {
                    Text("· ", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(line, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun openBrowser(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
