@file:OptIn(ExperimentalMaterial3Api::class)

package com.looknote.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.looknote.app.exif.ExifReader
import com.looknote.app.exif.PhotoInfo
import com.looknote.app.model.CreativeLook
import com.looknote.app.render.FrameOptions
import com.looknote.app.render.FrameRenderer
import com.looknote.app.render.FrameStyle
import com.looknote.app.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FrameScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    var uri by remember { mutableStateOf<Uri?>(null) }
    var full by remember { mutableStateOf<Bitmap?>(null) }
    var small by remember { mutableStateOf<Bitmap?>(null) }
    var info by remember { mutableStateOf<PhotoInfo?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }

    var style by remember { mutableStateOf(FrameStyle.WHITE_BAR) }
    var showLens by remember { mutableStateOf(true) }
    var showDate by remember { mutableStateOf(false) }
    var badgeOn by remember { mutableStateOf(true) }
    var badgeLook by remember { mutableStateOf<CreativeLook?>(null) }
    var signature by remember { mutableStateOf("") }

    fun options() = FrameOptions(
        style = style,
        showLens = showLens,
        showDate = showDate,
        badgeText = if (badgeOn) badgeLook?.code.orEmpty() else "",
        signature = signature.trim(),
    )

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u: Uri? ->
        if (u == null) return@rememberLauncherForActivityResult
        uri = u
        busy = true
        scope.launch {
            val pi = withContext(Dispatchers.IO) { runCatching { ExifReader.parse(ImageUtils.readBytes(context, u)) }.getOrNull() }
            val big = withContext(Dispatchers.IO) { runCatching { ImageUtils.loadBitmap(context, u, 3600) }.getOrNull() }
            info = pi
            badgeLook = pi?.sony?.look
            full = big
            small = big?.let { b ->
                withContext(Dispatchers.Default) {
                    val s = 1400f / maxOf(b.width, b.height)
                    if (s >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
                }
            }
            busy = false
            if (big == null) snack.showSnackbar("Không mở được ảnh")
        }
    }

    // Ve lai ban xem truoc khi doi tuy chon
    LaunchedEffect(small, style, showLens, showDate, badgeOn, badgeLook, signature, info) {
        val s = small ?: return@LaunchedEffect
        delay(120)
        val opt = options()
        preview = withContext(Dispatchers.Default) { FrameRenderer.render(s, info, opt) }
    }

    ScreenScaffold(title = "Khung & huy hiệu", snackbarHostState = snack) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.PhotoFilter, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (uri == null) "Chọn ảnh" else "Chọn ảnh khác")
            }
            if (busy) MutedText("Đang xử lý…")

            preview?.let {
                Image(
                    it.asImageBitmap(), contentDescription = "Xem trước",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            if (uri != null && info == null && !busy) {
                MutedText("Ảnh không có EXIF – khung sẽ chỉ hiển thị chữ ký.")
            }

            SectionTitle("Kiểu khung")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FrameStyle.entries) { st ->
                    FilterChip(selected = style == st, onClick = { style = st }, label = { Text(st.label) })
                }
            }

            SwitchRow("Hiện tên ống kính", showLens) { showLens = it }
            SwitchRow("Hiện ngày chụp", showDate) { showDate = it }
            SwitchRow("Huy hiệu Creative Look", badgeOn) { badgeOn = it }
            if (badgeOn) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                    items(CreativeLook.entries) { l ->
                        FilterChip(
                            selected = badgeLook == l,
                            onClick = { badgeLook = if (badgeLook == l) null else l },
                            label = { Text(l.code) },
                        )
                    }
                }
                if (badgeLook == null) MutedText("Chưa chọn Look – huy hiệu sẽ không hiển thị.")
            }
            OutlinedTextField(
                value = signature, onValueChange = { signature = it },
                label = { Text("Chữ ký / tên tác giả (tùy chọn)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = full != null && !busy,
                    onClick = {
                        val f = full ?: return@OutlinedButton
                        busy = true
                        scope.launch {
                            val ok = withContext(Dispatchers.Default) {
                                runCatching {
                                    val out = FrameRenderer.render(f, info, options())
                                    ImageUtils.saveToGallery(context, out, "looknote_${System.currentTimeMillis()}.jpg")
                                }.isSuccess
                            }
                            busy = false
                            snack.showSnackbar(if (ok) "Đã lưu vào Pictures/LookNote" else "Lưu thất bại")
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Lưu ảnh") }
                OutlinedButton(
                    enabled = full != null && !busy,
                    onClick = {
                        val f = full ?: return@OutlinedButton
                        busy = true
                        scope.launch {
                            val out = withContext(Dispatchers.Default) { runCatching { FrameRenderer.render(f, info, options()) }.getOrNull() }
                            busy = false
                            if (out != null) runCatching { ImageUtils.shareBitmap(context, out, "looknote_frame.png") }
                            else snack.showSnackbar("Không tạo được ảnh")
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Chia sẻ") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
