@file:OptIn(ExperimentalMaterial3Api::class)

package com.looknote.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.looknote.app.AppGraph
import com.looknote.app.exif.ExifReader
import com.looknote.app.exif.PhotoInfo
import com.looknote.app.model.CreativeLook
import com.looknote.app.model.LookParam
import com.looknote.app.model.Recipe
import com.looknote.app.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AnalyzeScreen(onCreateRecipe: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var info by remember { mutableStateOf<PhotoInfo?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var showRaw by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        loading = true; error = null; info = null; preview = null; showRaw = false
        scope.launch {
            val parsed = withContext(Dispatchers.IO) {
                runCatching { ExifReader.parse(ImageUtils.readBytes(context, uri)) }
            }
            parsed.onSuccess { info = it }.onFailure { error = it.message ?: "Không đọc được ảnh" }
            preview = withContext(Dispatchers.IO) { runCatching { ImageUtils.loadBitmap(context, uri, 1600) }.getOrNull() }
            loading = false
        }
    }

    ScreenScaffold(title = "Đọc thông số từ ảnh") { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Image, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Chọn ảnh JPEG/ARW gốc từ máy Sony")
            }
            MutedText("Dùng ảnh gốc chép từ thẻ nhớ hoặc qua Creators' App. Ảnh đã qua mạng xã hội/Zalo thường bị xóa EXIF.")

            if (loading) MutedText("Đang đọc…")
            error?.let { Text(it, color = WarningColor) }

            preview?.let {
                Image(
                    it.asImageBitmap(), contentDescription = "Ảnh đã chọn",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                    contentScale = ContentScale.Fit,
                )
            }

            info?.let { pi ->
                SectionCard(Modifier.fillMaxWidth()) {
                    SectionTitle("Thông tin chụp")
                    KV("Máy ảnh", pi.cameraName())
                    KV("Ống kính", pi.lens)
                    KV("Tiêu cự", listOfNotNull(pi.focalText(), pi.focal35?.let { "($it mm tương đương)" }).joinToString(" ").ifBlank { null })
                    KV("Khẩu độ", pi.apertureText())
                    KV("Tốc độ", pi.shutterText())
                    KV("ISO", pi.iso?.toString())
                    KV("Bù sáng", pi.biasText())
                    KV("Thời gian", pi.dateTime)
                }

                val s = pi.sony
                if (!pi.isSony) {
                    MutedText("Ảnh không phải từ máy Sony – không có thông số Creative Look.")
                } else if (s == null) {
                    MutedText("Không tìm thấy MakerNote Sony trong ảnh (có thể ảnh đã bị chỉnh sửa/xuất lại).")
                } else {
                    SectionCard(Modifier.fillMaxWidth()) {
                        SectionTitle("Thiết lập màu Sony (nhận diện)")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (s.look != null) LookBadge(s.look.code, large = true) else Text("—")
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(s.look?.label ?: "Chưa nhận diện được Look", fontWeight = FontWeight.SemiBold)
                                MutedText("Giá trị gốc: " + (s.creativeStyleRaw ?: "không có"))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        KV("Tương phản", s.contrast?.let { LookParam.CONTRAST.format(it) })
                        KV("Bão hòa", s.saturation?.let { LookParam.SATURATION.format(it) })
                        KV("Độ nét", s.sharpness?.let { LookParam.SHARPNESS.format(it) })
                        KV("Cân bằng trắng", s.whiteBalanceText())
                        KV("Nhiệt độ màu", s.colorTemperature?.let { "${it}K" })
                        KV("Tinh chỉnh WB (mã)", s.wbFineTune?.toString())
                        KV("DRO (mã)", s.droRaw?.toString())
                        MutedText(
                            "Các thông số Vùng sáng, Vùng tối, Phai màu, Độ trong, Phạm vi nét và trục A–B/G–M " +
                                "được Sony lưu ở vùng mã hóa, chưa đọc được – cần nhập tay khi lưu công thức.",
                        )
                    }
                    TextButton(onClick = { showRaw = !showRaw }) {
                        Text(if (showRaw) "Ẩn thẻ MakerNote" else "Xem toàn bộ thẻ MakerNote (${s.rawTags.size})")
                    }
                    if (showRaw) {
                        SectionCard(Modifier.fillMaxWidth()) {
                            s.rawTags.forEach { (tag, v) ->
                                Text(
                                    "0x%04X  %s".format(tag, v),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        val look = s?.look ?: CreativeLook.ST
                        var r = Recipe(
                            name = "Từ ảnh " + pi.cameraName().ifBlank { "Sony" },
                            look = look,
                            camera = pi.cameraName(),
                            notes = "Tạo từ ảnh" + (pi.dateTime?.let { " chụp $it" } ?: "") + ". Kiểm tra lại các thông số chưa đọc được.",
                        )
                        s?.contrast?.let { r = r.withValue(LookParam.CONTRAST, it) }
                        s?.saturation?.let { r = r.withValue(LookParam.SATURATION, it) }
                        s?.sharpness?.let { r = r.withValue(LookParam.SHARPNESS, it) }
                        AppGraph.pendingDraft = r
                        onCreateRecipe()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Lưu thành công thức") }
            }
        }
    }
}

@Composable
private fun KV(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
