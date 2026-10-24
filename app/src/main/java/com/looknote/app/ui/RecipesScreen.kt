@file:OptIn(ExperimentalMaterial3Api::class)

package com.looknote.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looknote.app.AppGraph
import com.looknote.app.model.CreativeLook
import com.looknote.app.model.Recipe
import com.looknote.app.model.RecipeCodec
import com.looknote.app.util.ImageUtils
import com.looknote.app.util.Qr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RecipesScreen(onOpen: (String) -> Unit, onNew: () -> Unit) {
    val store = AppGraph.store
    val all by store.recipes.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    var query by remember { mutableStateOf("") }
    var lookFilter by remember { mutableStateOf<CreativeLook?>(null) }
    var favOnly by remember { mutableStateOf(false) }
    var ppOnly by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var pasteDialog by remember { mutableStateOf(false) }
    var confirmRestore by remember { mutableStateOf(false) }

    fun message(m: String) { scope.launch { snack.showSnackbar(m) } }

    fun openDraft(r: Recipe?) {
        if (r == null) { message("Không đọc được mã công thức"); return }
        AppGraph.pendingDraft = r
        onNew()
    }

    val qrPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.Default) {
                runCatching { Qr.decode(ImageUtils.loadBitmap(context, uri, 2000)) }.getOrNull()
            }
            if (text == null) message("Không tìm thấy mã QR trong ảnh") else openDraft(RecipeCodec.fromShareCode(text))
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(store.exportAll().toByteArray(Charsets.UTF_8)) }
                }.isSuccess
            }
            message(if (ok) "Đã xuất ${all.size} công thức" else "Xuất tệp thất bại")
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val n = withContext(Dispatchers.IO) {
                runCatching {
                    val text = String(ImageUtils.readBytes(context, uri, 20 * 1024 * 1024), Charsets.UTF_8)
                    store.importAll(RecipeCodec.listFromString(text))
                }.getOrNull()
            }
            message(if (n == null) "Tệp không hợp lệ" else "Đã nhập $n công thức")
        }
    }

    val list = all.filter { r ->
        (lookFilter == null || (!r.isPP && r.look == lookFilter)) &&
            (!ppOnly || r.isPP) &&
            (!favOnly || r.favorite) &&
            (query.isBlank() || r.name.contains(query, true) || r.notes.contains(query, true) || r.camera.contains(query, true) || r.source.contains(query, true))
    }

    ScreenScaffold(
        title = "Sổ công thức",
        snackbarHostState = snack,
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Tùy chọn") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Nhập từ ảnh mã QR") }, onClick = {
                        menu = false
                        qrPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    })
                    DropdownMenuItem(text = { Text("Dán mã công thức") }, onClick = { menu = false; pasteDialog = true })
                    DropdownMenuItem(text = { Text("Xuất tất cả (tệp JSON)") }, onClick = {
                        menu = false; exporter.launch("looknote-cong-thuc.json")
                    })
                    DropdownMenuItem(text = { Text("Nhập từ tệp JSON") }, onClick = {
                        menu = false; importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                    })
                    DropdownMenuItem(text = { Text("Khôi phục công thức mẫu") }, onClick = { menu = false; confirmRestore = true })
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { AppGraph.pendingDraft = null; onNew() }) {
                Icon(Icons.Filled.Add, contentDescription = "Thêm công thức")
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Tìm theo tên, ghi chú, máy ảnh") },
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(selected = favOnly, onClick = { favOnly = !favOnly }, label = { Text("★ Yêu thích") })
                }
                item {
                    FilterChip(selected = lookFilter == null && !ppOnly, onClick = { lookFilter = null; ppOnly = false }, label = { Text("Tất cả") })
                }
                item {
                    FilterChip(selected = ppOnly, onClick = { ppOnly = !ppOnly; lookFilter = null }, label = { Text("Picture Profile") })
                }
                items(CreativeLook.entries) { l ->
                    FilterChip(
                        selected = lookFilter == l,
                        onClick = { lookFilter = if (lookFilter == l) null else l; ppOnly = false },
                        label = { Text(l.code) },
                    )
                }
            }
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    MutedText("Chưa có công thức phù hợp")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(list, key = { it.id }) { r ->
                        RecipeRow(r, onClick = { onOpen(r.id) }, onFav = { store.toggleFavorite(r.id) })
                    }
                }
            }
        }
    }

    if (pasteDialog) {
        var code by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pasteDialog = false },
            title = { Text("Dán mã công thức") },
            text = {
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    placeholder = { Text(RecipeCodec.SHARE_PREFIX + "…") },
                    minLines = 3,
                )
            },
            confirmButton = {
                TextButton(onClick = { pasteDialog = false; openDraft(RecipeCodec.fromShareCode(code)) }) { Text("Nhập") }
            },
            dismissButton = { TextButton(onClick = { pasteDialog = false }) { Text("Hủy") } },
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Khôi phục công thức mẫu?") },
            text = { Text("Các công thức mẫu sẽ được đặt lại như ban đầu. Công thức của bạn không bị ảnh hưởng.") },
            confirmButton = {
                TextButton(onClick = { confirmRestore = false; store.restoreBuiltIns(); message("Đã khôi phục") }) { Text("Khôi phục") }
            },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Hủy") } },
        )
    }
}

@Composable
private fun RecipeRow(r: Recipe, onClick: () -> Unit, onFav: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            LookBadge(r.badge())
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    r.name.ifBlank { "Không tên" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                MutedText(r.summary(), Modifier.padding(top = 2.dp))
                MutedText("WB: " + r.wbText() + if (r.builtIn) "   ·   Mẫu" else "")
            }
            IconButton(onClick = onFav) {
                Icon(
                    if (r.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Yêu thích",
                    tint = if (r.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
