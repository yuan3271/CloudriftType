package com.yuan3271.cloudrift.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yuan3271.cloudrift.data.ImportOutcome

/**
 * 自学习记录的导出与导入.
 *
 * 走系统文件选择器：导出一个 `.txt`，里面是 UserProfile.exportPayload() 生成的单行文本
 * （`CRP1:` + gzip + base64），导入时把同一个字符串读回来。没有第二条格式，所以"导出什么、
 * 导入什么"不会对不上。
 *
 * 这里曾经有二维码导出和扫码/图片导入。去掉的理由是它对不上真实用法：记录本身有几百个工作
 * 习惯时还能塞进二维码，装满自造词就超了，用户会先看到一次失败再去翻文件入口；而换机场景本来
 * 也几乎总是走聊天软件传文件。少两条路径，错误提示也少两种。
 */
@Composable
fun LearningTransferRows(
    exportLearning: () -> String,
    importLearning: (String) -> ImportOutcome,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var outcome by remember { mutableStateOf<ImportOutcome?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    fun report(result: ImportOutcome) {
        outcome = result
    }

    val exportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val payload = exportLearning()
        val written = runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(payload.toByteArray(Charsets.UTF_8))
            } != null
        }.getOrDefault(false)
        if (written) {
            report(ImportOutcome(true, "已导出学习记录（${payload.length} 字符）"))
        } else {
            problem = "写入文件失败"
        }
    }

    val importFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val raw = uri?.let { readText(context, it) }
        if (raw == null) problem = "读取文件失败" else report(importLearning(raw))
    }

    /** The picker lives outside this app; a device without one should say so, not crash. */
    fun launchSafely(block: () -> Unit) {
        runCatching(block).onFailure {
            problem = "打不开文件选择器（${it.message ?: "未知原因"}）"
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "学习记录可以换机带走：导出一个文件，在另一台手机上导入同一个文件即可（与本地记录合并）。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { launchSafely { exportFile.launch(EXPORT_FILE_NAME) } },
                modifier = Modifier.weight(1f),
            ) {
                Text("导出到文件", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = {
                    launchSafely {
                        importFile.launch(arrayOf("text/plain", "application/json", "*/*"))
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("从文件导入", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    outcome?.let { result ->
        AlertDialog(
            onDismissRequest = { outcome = null },
            confirmButton = { TextButton(onClick = { outcome = null }) { Text("知道了") } },
            title = { Text(if (result.ok) "完成" else "导入失败") },
            text = { Text(result.message) },
        )
    }

    problem?.let { message ->
        AlertDialog(
            onDismissRequest = { problem = null },
            confirmButton = { TextButton(onClick = { problem = null }) { Text("知道了") } },
            title = { Text("没有完成") },
            text = { Text(message) },
        )
    }
}

private fun readText(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
}.getOrNull()

private const val EXPORT_FILE_NAME = "cloudrift-learning.txt"
