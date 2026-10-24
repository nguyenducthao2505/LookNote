@file:OptIn(ExperimentalMaterial3Api::class)

package com.looknote.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.looknote.app.AppGraph
import com.looknote.app.model.LookParam
import com.looknote.app.model.RecipeCodec
import com.looknote.app.render.RecipeCardRenderer
import com.looknote.app.util.ImageUtils
import com.looknote.app.util.Qr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun RecipeDetailScreen(id: String, onBack: () -> Unit, onEdit: () -> Unit, onDuplicate: () -> Unit) {
    val store = AppGraph.store
    val all by store.recipes.collectAsState()
    val r = all.firstOrNull { it.id == id }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var card by remember { mutableStateOf<Bitmap?>(null) }

    if (r == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    LaunchedEffect(r) {
        card = withContext(Dispatchers.Default) { RecipeCardRenderer.render(r) }
    }

    fun message(m: String) { scope.launch { snack.showSnackbar(m) } }

    ScreenScaffold(
        title = r.name.ifBlank { "Công thức" },
        onBack = onBack,
        snackbarHostState = snack,
        actions = {
            IconButton(onClick = { store.toggleFavorite(r.id) }) {
                Icon(if (r.favorite) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = "Yêu thích")
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Sửa") }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Xóa") }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LookBadge(r.badge(), large = true)
                Spacer(Modifier.width(12.dp))
                Column {
                    if (r.isPP) {
                        Text("Picture Profile" + (r.pp?.slot?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.titleMedium)
                        MutedText("Cài trong MENU › Picture Profile của máy (α7C II có PP1–PP11)")
                    } else {
                        Text("Creative Look: ${r.look.label}", style = MaterialTheme.typography.titleMedium)
                        MutedText(r.look.note)
                    }
                }
            }

            SectionCard(Modifier.fillMaxWidth()) {
                val ppData = r.pp
                if (r.isPP && ppData != null) {
                    var group = ""
                    ppData.rows().forEach { (g, k, v) ->
                        if (g != group) { group = g; SectionTitle(g) }
                        InfoRow(k, v)
                    }
                } else {
                    LookParam.entries.forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                            Text(p.label, Modifier.weight(1f))
                            Text(p.format(r.value(p)), fontWeight = FontWeight.Bold)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text("Cân bằng trắng", Modifier.weight(1f))
                    Text(r.wbText(), fontWeight = FontWeight.Bold)
                }
                if (r.dro.isNotBlank()) InfoRow("DRO/Auto HDR", r.dro)
                if (r.exposureComp.isNotBlank()) InfoRow("Bù phơi sáng", r.exposureComp)
                if (r.camera.isNotBlank()) InfoRow("Máy ảnh", r.camera)
                if (r.source.isNotBlank()) InfoRow("Nguồn", r.source)
            }

            if (r.notes.isNotBlank()) {
                SectionCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Ghi chú")
                    Text(r.notes)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(r.toPlainText()))
                    message("Đã sao chép thông số")
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Sao chép")
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        qrBitmap = withContext(Dispatchers.Default) { Qr.encode(RecipeCodec.toShareCode(r), 720) }
                    }
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.QrCode, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text("Mã QR")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    clipboard.setText(AnnotatedString(RecipeCodec.toShareCode(r)))
                    message("Đã sao chép mã chia sẻ")
                }, modifier = Modifier.weight(1f)) { Text("Sao chép mã") }
                OutlinedButton(onClick = {
                    AppGraph.pendingDraft = r.copy(
                        id = UUID.randomUUID().toString(),
                        name = r.name + " (bản sao)",
                        builtIn = false,
                        favorite = false,
                        createdAt = System.currentTimeMillis(),
                    )
                    onDuplicate()
                }, modifier = Modifier.weight(1f)) { Text("Nhân bản") }
            }

            SectionTitle("Thẻ công thức")
            val c = card
            if (c != null) {
                Image(
                    bitmap = c.asImageBitmap(),
                    contentDescription = "Thẻ công thức",
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                runCatching { ImageUtils.saveToGallery(context, c, "looknote_${System.currentTimeMillis()}.png", png = true) }.isSuccess
                            }
                            message(if (ok) "Đã lưu vào Ảnh › Pictures/LookNote" else "Lưu thất bại")
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Lưu thẻ") }
                    OutlinedButton(onClick = {
                        runCatching { ImageUtils.shareBitmap(context, c, "looknote_card.png", r.toPlainText()) }
                            .onFailure { message("Không chia sẻ được") }
                    }, modifier = Modifier.weight(1f)) { Text("Chia sẻ thẻ") }
                }
            } else {
                MutedText("Đang tạo thẻ…")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    qrBitmap?.let { q ->
        AlertDialog(
            onDismissRequest = { qrBitmap = null },
            title = { Text("Mã QR công thức") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(q.asImageBitmap(), contentDescription = "Mã QR", modifier = Modifier.size(260.dp))
                    MutedText("Người nhận dùng LookNote › Sổ công thức › Nhập từ ảnh mã QR")
                }
            },
            confirmButton = { TextButton(onClick = { qrBitmap = null }) { Text("Đóng") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Xóa công thức?") },
            text = { Text("“${r.name}” sẽ bị xóa khỏi máy này.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; store.delete(r.id) }) { Text("Xóa", color = WarningColor) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Hủy") } },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold)
    }
}
