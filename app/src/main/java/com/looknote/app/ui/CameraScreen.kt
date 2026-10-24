@file:OptIn(ExperimentalMaterial3Api::class)

package com.looknote.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.looknote.app.AppGraph
import com.looknote.app.ptp.PtpCodes
import com.looknote.app.ptp.SonyProp
import com.looknote.app.usb.UsbCamera
import com.looknote.app.util.ImageUtils

@Composable
fun CameraScreen() {
    val ctrl = AppGraph.camera
    val st by ctrl.state.collectAsState()
    val context = LocalContext.current
    var changedOnly by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SonyProp?>(null) }
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { ctrl.refreshDevices() }

    ScreenScaffold(
        title = "Máy ảnh (USB – thử nghiệm)",
        actions = {
            if (st.props.isNotEmpty()) {
                IconButton(onClick = { ImageUtils.shareText(context, ctrl.exportReport()) }) {
                    Icon(Icons.Filled.Share, contentDescription = "Chia sẻ báo cáo")
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("Kết nối") })
                FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("Thuộc tính (${st.props.size})") })
                FilterChip(selected = tab == 2, onClick = { tab = 2 }, label = { Text("Nhật ký") })
            }
            when (tab) {
                0 -> ConnectTab(st.devices.isNotEmpty())
                1 -> PropsTab(changedOnly, { changedOnly = it }, onEdit = { editing = it })
                else -> LogTab(st.log)
            }
        }
    }

    editing?.let { p ->
        EditPropDialog(p, onDismiss = { editing = null }, onSet = { v -> ctrl.setProp(p, v); editing = null })
    }
}

@Composable
private fun ConnectTab(hasDevices: Boolean) {
    val ctrl = AppGraph.camera
    val st by ctrl.state.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SectionCard(Modifier.fillMaxWidth()) {
            SectionTitle("Chuẩn bị (Sony α7C II)")
            Text("1. Trên máy ảnh: MENU › Mạng › Chuyển/Điều khiển từ xa › Chức năng PC Remote › PC Remote: Bật; Phương thức kết nối: USB.")
            Text("2. Dùng cáp USB‑C ↔ USB‑C hỗ trợ truyền dữ liệu (không dùng cáp chỉ để sạc).")
            Text("3. Cắm cáp, bấm “Tìm máy ảnh”, chọn máy và cho phép LookNote truy cập USB.")
            MutedText("Tên mục menu có thể khác đôi chút theo phiên bản firmware và ngôn ngữ của máy.")
        }

        OutlinedButton(onClick = { ctrl.refreshDevices() }, modifier = Modifier.fillMaxWidth(), enabled = !st.busy) {
            Text("Tìm máy ảnh")
        }
        if (!hasDevices) MutedText("Chưa thấy thiết bị USB nào phù hợp.")
        st.devices.forEach { d ->
            Button(onClick = { ctrl.connect(d) }, modifier = Modifier.fillMaxWidth(), enabled = !st.busy) {
                Text("Kết nối: " + UsbCamera.describe(d))
            }
        }

        st.info?.let { info ->
            SectionCard(Modifier.fillMaxWidth()) {
                SectionTitle("Đã kết nối")
                Text("${info.manufacturer} ${info.model}", fontWeight = FontWeight.Bold)
                MutedText("Firmware: ${info.deviceVersion}  ·  Serial: ${info.serialNumber.ifBlank { "—" }}")
                MutedText("Lệnh hỗ trợ: ${info.operations.size}  ·  Thuộc tính Sony khai báo: ${st.sonyCodes.size}")
                val battery = st.props.firstOrNull { it.code == 0xD218 }?.current
                if (battery != null) MutedText("Pin (gợi ý, mã 0xD218): $battery")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { ctrl.readProps() }, enabled = !st.busy, modifier = Modifier.weight(1f)) { Text("Đọc lại") }
                OutlinedButton(onClick = { ctrl.disconnect() }, enabled = !st.busy, modifier = Modifier.weight(1f)) { Text("Ngắt kết nối") }
            }
        }

        SectionCard(Modifier.fillMaxWidth()) {
            SectionTitle("Tìm mã thuộc tính Creative Look")
            Text(
                "Sony không công bố mã lệnh ghi Creative Look. Cách dò trên chính máy α7C II:\n" +
                    "• Tab Thuộc tính › bấm “Lưu mốc”.\n" +
                    "• Trên máy ảnh, đổi đúng MỘT thiết lập (vd: Creative Look từ ST sang FL, hoặc Tương phản +1).\n" +
                    "• Bấm “Đọc lại” rồi bật “Chỉ hiện thay đổi” – mã nào đổi giá trị chính là mã của thiết lập đó.\n" +
                    "• Chạm vào thuộc tính để thử ghi giá trị khác từ điện thoại.\n" +
                    "• Bấm biểu tượng Chia sẻ để gửi báo cáo – dùng để hoàn thiện chức năng “Gửi công thức sang máy”.",
            )
        }
    }
}

@Composable
private fun PropsTab(changedOnly: Boolean, setChangedOnly: (Boolean) -> Unit, onEdit: (SonyProp) -> Unit) {
    val ctrl = AppGraph.camera
    val st by ctrl.state.collectAsState()
    val snap = st.snapshot
    val list = st.props.filter { p ->
        !changedOnly || (snap != null && snap[p.code] != null && snap[p.code] != p.valueText())
    }
    Column(Modifier.fillMaxSize().padding(top = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { ctrl.takeSnapshot() }, enabled = st.props.isNotEmpty()) { Text("Lưu mốc") }
            OutlinedButton(onClick = { ctrl.readProps() }, enabled = st.connected && !st.busy) { Text("Đọc lại") }
            FilterChip(
                selected = changedOnly,
                onClick = { setChangedOnly(!changedOnly) },
                enabled = snap != null,
                label = { Text("Chỉ hiện thay đổi") },
            )
        }
        if (snap != null) MutedText("Đang so sánh với mốc ${snap.size} thuộc tính", Modifier.padding(vertical = 4.dp))
        if (st.props.isEmpty()) {
            MutedText("Chưa có dữ liệu. Kết nối máy ảnh ở tab Kết nối.", Modifier.padding(top = 16.dp))
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(list) { p ->
                val before = snap?.get(p.code)
                val changed = before != null && before != p.valueText()
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(if (changed) Modifier.background(MaterialTheme.colorScheme.primaryContainer) else Modifier)
                        .clickable(enabled = p.writable) { onEdit(p) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                ) {
                    Row {
                        Text("0x%04X".format(p.code), fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.padding(horizontal = 6.dp))
                        Text(PtpCodes.dataTypeName(p.dataType), fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.padding(horizontal = 6.dp))
                        Text(if (p.writable) "ghi được" else "chỉ đọc", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("= " + p.valueText(), fontFamily = FontFamily.Monospace)
                    if (changed) Text("trước: $before", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                    PtpCodes.propHint(p.code)?.let { MutedText(it) }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun LogTab(lines: List<String>) {
    LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
        items(lines.reversed()) { l ->
            Text(l, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

@Composable
private fun EditPropDialog(p: SonyProp, onDismiss: () -> Unit, onSet: (Long) -> Unit) {
    var text by remember(p.code) { mutableStateOf((p.current as? Long)?.toString() ?: "") }
    val enums = p.enumValues?.filterIsInstance<Long>().orEmpty()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ghi thuộc tính 0x%04X".format(p.code)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                MutedText("Kiểu ${PtpCodes.dataTypeName(p.dataType)} · hiện tại ${p.valueText()}")
                p.range?.let { (a, b, c) -> MutedText("Khoảng: $a … $b, bước $c") }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = text, onValueChange = { text = it.trim() }, label = { Text("Giá trị (số nguyên)") }, singleLine = true)
                if (enums.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    MutedText("Giá trị máy cho phép:")
                    enums.forEach { v ->
                        TextButton(onClick = { text = v.toString() }) {
                            Text("$v (0x" + java.lang.Long.toHexString(v).uppercase() + ")")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Lưu ý: chỉ ghi khi biết rõ ý nghĩa của mã. Giá trị sai sẽ bị máy từ chối, không gây hỏng máy, nhưng có thể đổi cài đặt bất ngờ.",
                    color = WarningColor,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = if (text.startsWith("0x", true)) text.drop(2).toLongOrNull(16) else text.toLongOrNull()
                if (v != null) onSet(v)
            }) { Text("Ghi") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}
