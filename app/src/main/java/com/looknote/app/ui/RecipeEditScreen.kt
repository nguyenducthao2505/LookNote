@file:OptIn(ExperimentalMaterial3Api::class)

package com.looknote.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.looknote.app.AppGraph
import com.looknote.app.model.CreativeLook
import com.looknote.app.model.LookParam
import com.looknote.app.model.PictureProfile
import com.looknote.app.model.Recipe
import com.looknote.app.model.RecipeKind
import com.looknote.app.model.WbShift
import com.looknote.app.model.WhiteBalance
import kotlin.math.roundToInt

@Composable
fun RecipeEditScreen(id: String, onDone: (String?) -> Unit) {
    val store = AppGraph.store
    val initial = remember(id) {
        if (id == "new") {
            (AppGraph.pendingDraft ?: Recipe()).also { AppGraph.pendingDraft = null }
        } else store.get(id) ?: Recipe()
    }
    var r by remember(id) { mutableStateOf(initial) }
    var nameError by remember { mutableStateOf(false) }

    fun save() {
        if (r.name.isBlank()) { nameError = true; return }
        val toSave = r.copy(builtIn = false)
        store.upsert(toSave)
        onDone(toSave.id)
    }

    ScreenScaffold(
        title = if (id == "new") "Công thức mới" else "Sửa công thức",
        onBack = { onDone(null) },
        actions = {
            IconButton(onClick = { save() }) { Icon(Icons.Filled.Check, contentDescription = "Lưu") }
        },
    ) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = r.name,
                onValueChange = { r = r.copy(name = it); nameError = false },
                label = { Text("Tên công thức") },
                isError = nameError,
                supportingText = { if (nameError) Text("Cần nhập tên") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Loại công thức")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RecipeKind.entries.forEach { k ->
                    FilterChip(
                        selected = r.kind == k,
                        onClick = {
                            r = r.copy(kind = k, pp = if (k == RecipeKind.PICTURE_PROFILE) (r.pp ?: PictureProfile()) else r.pp)
                        },
                        label = { Text(k.label) },
                    )
                }
            }

            if (r.isPP) {
                PictureProfileEditor(r.pp ?: PictureProfile()) { r = r.copy(pp = it) }
            } else {
                SectionTitle("Creative Look")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
                    items(CreativeLook.entries) { l ->
                        FilterChip(selected = r.look == l, onClick = { r = r.copy(look = l) }, label = { Text(l.code) })
                    }
                }
                MutedText("${r.look.label}: ${r.look.note}")

                SectionTitle("Thông số (thứ tự như menu máy ảnh)")
                SectionCard(Modifier.fillMaxWidth()) {
                    LookParam.entries.forEach { p ->
                        IntStepper(
                            label = p.label,
                            value = r.value(p),
                            min = p.min,
                            max = p.max,
                            format = { p.format(it) },
                            onChange = { r = r.withValue(p, it) },
                        )
                    }
                }
            }

            SectionTitle("Cân bằng trắng")
            SectionCard(Modifier.fillMaxWidth()) {
                var wbMenu by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { wbMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(r.wb.label) }
                    DropdownMenu(expanded = wbMenu, onDismissRequest = { wbMenu = false }) {
                        WhiteBalance.entries.forEach { w ->
                            DropdownMenuItem(text = { Text(w.label) }, onClick = { r = r.copy(wb = w); wbMenu = false })
                        }
                    }
                }
                if (r.wb == WhiteBalance.KELVIN) {
                    Spacer(Modifier.height(8.dp))
                    IntStepper(
                        label = "Nhiệt độ màu",
                        value = r.kelvin,
                        min = 2500,
                        max = 9900,
                        step = 100,
                        format = { "${it}K" },
                        onChange = { r = r.copy(kelvin = it) },
                    )
                }
                Spacer(Modifier.height(8.dp))
                DoubleStepper(
                    label = "Trục A–B (hổ phách/xanh lam)",
                    value = r.wbAB,
                    step = WbShift.STEP_AB,
                    format = { WbShift.formatAB(it) },
                    onChange = { r = r.copy(wbAB = it) },
                )
                DoubleStepper(
                    label = "Trục G–M (xanh lục/tím hồng)",
                    value = r.wbGM,
                    step = WbShift.STEP_GM,
                    format = { WbShift.formatGM(it) },
                    onChange = { r = r.copy(wbGM = it) },
                )
            }

            SectionTitle("Khác")
            OutlinedTextField(
                value = r.dro, onValueChange = { r = r.copy(dro = it) },
                label = { Text("DRO / Auto HDR (vd: Auto, Lv3)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = r.exposureComp, onValueChange = { r = r.copy(exposureComp = it) },
                label = { Text("Bù phơi sáng gợi ý (vd: +0.3)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = r.camera, onValueChange = { r = r.copy(camera = it) },
                label = { Text("Máy ảnh (vd: α7C II)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = r.notes, onValueChange = { r = r.copy(notes = it) },
                label = { Text("Ghi chú") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = r.source, onValueChange = { r = r.copy(source = it) },
                label = { Text("Nguồn (tác giả công thức)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Lưu công thức") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun IntStepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int = 1,
    format: (Int) -> String,
    onChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            IconButton(onClick = { onChange((value - step).coerceIn(min, max)) }) {
                Icon(Icons.Filled.Remove, contentDescription = "Giảm")
            }
            Text(
                format(value),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(64.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            IconButton(onClick = { onChange((value + step).coerceIn(min, max)) }) {
                Icon(Icons.Filled.Add, contentDescription = "Tăng")
            }
        }
        val intervals = (max - min) / step
        Slider(
            value = value.toFloat(),
            onValueChange = { v ->
                val snapped = (((v - min) / step).roundToInt() * step + min).coerceIn(min, max)
                if (snapped != value) onChange(snapped)
            },
            valueRange = min.toFloat()..max.toFloat(),
            steps = (intervals - 1).coerceAtLeast(0),
        )
    }
}

@Composable
fun DoubleStepper(
    label: String,
    value: Double,
    step: Double,
    format: (Double) -> String,
    onChange: (Double) -> Unit,
) {
    val lim = WbShift.LIMIT
    IntStepper(
        label = label,
        value = (value / step).roundToInt(),
        min = -(lim / step).roundToInt(),
        max = (lim / step).roundToInt(),
        format = { format(it * step) },
        onChange = { onChange(WbShift.snap(it * step, step)) },
    )
}

@Composable
private fun PictureProfileEditor(p: PictureProfile, onChange: (PictureProfile) -> Unit) {

    SectionTitle("Gamma & màu")
    SectionCard(Modifier.fillMaxWidth()) {
        PpField("Ô PP (vd: PP1)", p.slot) { onChange(p.copy(slot = it)) }
        PpField("Gamma (Movie, Still, Cine1–4, HLG…)", p.gamma) { onChange(p.copy(gamma = it)) }
        PpField("Black Gamma – Range (Wide/Middle/Narrow)", p.blackGammaRange) { onChange(p.copy(blackGammaRange = it)) }
        PpNum("Black Gamma – Level", p.blackGammaLevel, -7, 7) { onChange(p.copy(blackGammaLevel = it)) }
        PpNum("Black Level", p.blackLevel, -15, 15) { onChange(p.copy(blackLevel = it)) }
        PpField("Knee – Mode", p.kneeMode) { onChange(p.copy(kneeMode = it)) }
        PpField("Knee – Point (vd: 75%)", p.kneePoint) { onChange(p.copy(kneePoint = it)) }
        PpNum("Knee – Slope", p.kneeSlope, -5, 5) { onChange(p.copy(kneeSlope = it)) }
        PpField("Color Mode (Movie, Still, Cinema, Pro, ITU709 Matrix, S-Gamut…)", p.colorMode) { onChange(p.copy(colorMode = it)) }
        PpNum("Saturation", p.saturation, -32, 32) { onChange(p.copy(saturation = it)) }
        PpNum("Color Phase", p.colorPhase, -7, 7) { onChange(p.copy(colorPhase = it)) }
    }
    SectionTitle("Độ đậm màu (Color Depth)")
    SectionCard(Modifier.fillMaxWidth()) {
        PpNum("R", p.depthR, -7, 7) { onChange(p.copy(depthR = it)) }
        PpNum("G", p.depthG, -7, 7) { onChange(p.copy(depthG = it)) }
        PpNum("B", p.depthB, -7, 7) { onChange(p.copy(depthB = it)) }
        PpNum("C", p.depthC, -7, 7) { onChange(p.copy(depthC = it)) }
        PpNum("M", p.depthM, -7, 7) { onChange(p.copy(depthM = it)) }
        PpNum("Y", p.depthY, -7, 7) { onChange(p.copy(depthY = it)) }
    }
    SectionTitle("Chi tiết (Detail)")
    SectionCard(Modifier.fillMaxWidth()) {
        PpNum("Level", p.detailLevel, -7, 7) { onChange(p.copy(detailLevel = it)) }
        PpField("Mode (Auto/Manual)", p.detailMode) { onChange(p.copy(detailMode = it)) }
        PpNum("V/H Balance", p.vhBalance, -2, 2) { onChange(p.copy(vhBalance = it)) }
        PpField("B/W Balance (Type1–5)", p.bwBalance) { onChange(p.copy(bwBalance = it)) }
        PpNum("Limit", p.limit, 0, 7) { onChange(p.copy(limit = it)) }
        PpNum("Crispening", p.crispening, 0, 7) { onChange(p.copy(crispening = it)) }
        PpNum("Hi-Light Detail", p.highlightDetail, 0, 4) { onChange(p.copy(highlightDetail = it)) }
    }
}

@Composable
private fun PpField(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValue,
        label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PpNum(label: String, value: Int, min: Int, max: Int, onValue: (Int) -> Unit) {
    IntStepper(
        label = label, value = value, min = min, max = max,
        format = { if (it > 0) "+$it" else it.toString() },
        onChange = onValue,
    )
}
