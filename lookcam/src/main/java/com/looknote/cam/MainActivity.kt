package com.looknote.cam

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraMetadata
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.looknote.cam.camera.Aspect
import com.looknote.cam.camera.CameraInventory
import com.looknote.cam.camera.FlashMode
import com.looknote.cam.ui.LutPreviewRenderer
import java.util.Locale
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

private val Amber = Color(0xFFF2B33D)

class MainActivity : ComponentActivity() {
    private val vm: CamViewModel by viewModels()
    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: LutPreviewRenderer
    private lateinit var orientation: OrientationEventListener
    private var permitted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
        }
        renderer = LutPreviewRenderer(glView) { vm.onTexture(it) }
        glView.setRenderer(renderer)
        glView.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        vm.lutSink = { renderer.setLut(it) }

        orientation = object : OrientationEventListener(this) {
            override fun onOrientationChanged(o: Int) {
                if (o == ORIENTATION_UNKNOWN) return
                vm.deviceRotation = ((o + 45) / 90 * 90) % 360
            }
        }
        permitted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Amber, onPrimary = Color(0xFF1E1400))) {
                var granted by remember { mutableStateOf(permitted) }
                val req = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                    granted = ok; permitted = ok
                    if (ok) { vm.init(); vm.resume() }
                }
                LaunchedEffect(Unit) { if (!granted) req.launch(Manifest.permission.CAMERA) else vm.init() }
                if (granted) CameraScreen(vm, glView)
                else Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LookCam cần quyền Camera để chụp ảnh.", color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { req.launch(Manifest.permission.CAMERA) }) { Text("Cấp quyền") }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        glView.onResume()
        orientation.enable()
        if (permitted) vm.resume()
    }

    override fun onPause() {
        vm.pause()
        orientation.disable()
        glView.onPause()
        super.onPause()
    }
}

private fun evText(ev: Int, step: Float): String {
    val v = ev * step
    return if (v == 0f) "±0" else (if (v > 0) "+" else "") + String.format(Locale.US, "%.1f", v)
}

@Composable
fun CameraScreen(vm: CamViewModel, glView: GLSurfaceView) {
    val st by vm.state.collectAsState()
    val live by vm.camera.live.collectAsState()
    val allRecipes by vm.store.recipes.collectAsState()
    val recipes = remember(allRecipes) { allRecipes.filter { !it.isPP } }
    val snack = remember { SnackbarHostState() }
    var showInfo by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val lens = vm.lens()
    val s = st.settings

    LaunchedEffect(st.message) { st.message?.let { snack.showSnackbar(it); vm.consumeMessage() } }
    LaunchedEffect(focusPoint) { if (focusPoint != null) { kotlinx.coroutines.delay(1500); focusPoint = null } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            // ---- Thanh tren ----
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    val next = when (s.flash) { FlashMode.OFF -> FlashMode.AUTO; FlashMode.AUTO -> FlashMode.ON; FlashMode.ON -> FlashMode.TORCH; FlashMode.TORCH -> FlashMode.OFF }
                    vm.setSettings(s.copy(flash = next))
                }, enabled = lens?.flash == true) {
                    Icon(when (s.flash) { FlashMode.OFF -> Icons.Filled.FlashOff; FlashMode.AUTO -> Icons.Filled.FlashAuto; FlashMode.ON -> Icons.Filled.FlashOn; FlashMode.TORCH -> Icons.Filled.FlashlightOn }, "Flash", tint = Color.White)
                }
                IconButton(onClick = { vm.cycleTimer() }) {
                    Box { Icon(Icons.Filled.Timer, "Hẹn giờ", tint = if (st.timer > 0) Amber else Color.White)
                        if (st.timer > 0) Text("${st.timer}", color = Amber, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomEnd)) }
                }
                IconButton(onClick = { vm.toggleGrid() }) { Icon(Icons.Filled.Grid3x3, "Lưới", tint = if (st.grid) Amber else Color.White) }
                TopChip("RAW", s.raw, enabled = lens?.raw == true) { vm.setSessionSettings(s.copy(raw = !s.raw)) }
                TopChip(lens?.maxResJpeg?.let { "${(it.width.toLong() * it.height / 1_000_000)}MP" } ?: "MAX", s.maxRes, enabled = lens?.ultraHighRes == true) {
                    vm.setSessionSettings(s.copy(maxRes = !s.maxRes))
                }
                TopChip(s.aspect.label, false) { vm.setSessionSettings(s.copy(aspect = if (s.aspect == Aspect.R4_3) Aspect.R16_9 else Aspect.R4_3)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showInfo = true }) { Icon(Icons.Filled.Info, "Thông tin camera", tint = Color.White) }
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Cài đặt", tint = Color.White) }
            }

            // ---- Khung ngam ----
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.fillMaxWidth().aspectRatio(st.previewAspect)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { o ->
                                    focusPoint = o
                                    vm.camera.focusAt(o.x / size.width, o.y / size.height)
                                },
                                onDoubleTap = { vm.camera.resetFocus() },
                            )
                        }
                        .pointerInput(Unit) { detectTransformGestures { _, _, zoom, _ -> if (zoom != 1f) vm.pinchZoom(zoom) } },
                ) {
                    AndroidView(factory = { glView }, modifier = Modifier.fillMaxSize())
                    if (st.grid) Canvas(Modifier.fillMaxSize()) {
                        val c = Color(0x66FFFFFF)
                        for (i in 1..2) {
                            drawLine(c, Offset(size.width * i / 3, 0f), Offset(size.width * i / 3, size.height), 1.5f)
                            drawLine(c, Offset(0f, size.height * i / 3), Offset(size.width, size.height * i / 3), 1.5f)
                        }
                    }
                    focusPoint?.let { p ->
                        Canvas(Modifier.fillMaxSize()) { drawCircle(Amber, 70f, p, style = Stroke(3f)) }
                    }
                    // Thong so dang dung
                    Text(
                        listOfNotNull(
                            vm.recipe(st.recipeId)?.let { "${it.look.code} · ${it.name}" } ?: "Không màu",
                            if (live.iso > 0) "ISO ${live.iso}" else null,
                            if (live.exposureNs > 0) CameraInventory.shutterText(live.exposureNs) else null,
                            String.format(Locale.US, "%.1fx", s.zoom),
                        ).joinToString("  ·  "),
                        color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.TopCenter).padding(8.dp).background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                    if (st.countdown > 0) Text("${st.countdown}", color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
                    if (st.opening) CircularProgressIndicator(Modifier.align(Alignment.Center))
                    if (st.capturing) Box(Modifier.fillMaxSize().background(Color(0x55000000)))
                    // Ong kinh
                    Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        st.options.forEach { o ->
                            val sel = st.option == o
                            Box(
                                Modifier.size(if (sel) 46.dp else 40.dp).clip(CircleShape).background(Color(0xAA000000))
                                    .border(1.dp, if (sel) Amber else Color(0x55FFFFFF), CircleShape).clickable { vm.selectOption(o) },
                                contentAlignment = Alignment.Center,
                            ) { Text(o.label, color = if (sel) Amber else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            // ---- PRO ----
            if (st.pro && lens != null) ProPanel(vm, lens)

            // ---- Cong thuc ----
            LazyRow(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { FilterChip(selected = st.recipeId == null, onClick = { vm.selectRecipe(null) }, label = { Text("Gốc") }) }
                items(recipes, key = { it.id }) { r ->
                    FilterChip(selected = st.recipeId == r.id, onClick = { vm.selectRecipe(r.id) },
                        label = { Text("${r.look.code} · ${r.name}", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(120.dp)) })
                }
            }
            if (st.recipeId != null) Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Mức màu", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(64.dp))
                var v by remember(st.recipeId) { mutableFloatStateOf(st.strength) }
                Slider(value = v, onValueChange = { v = it }, onValueChangeFinished = { vm.setStrength(v) }, modifier = Modifier.weight(1f).height(28.dp))
                Text("${(v * 100).roundToInt()}%", color = Color.White, fontSize = 12.sp, modifier = Modifier.width(40.dp))
            }

            // ---- Nut chup ----
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF222225)).clickable {
                    st.lastUri?.let { u -> runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) } }
                }, contentAlignment = Alignment.Center) {
                    st.lastThumb?.let { Image(it.asImageBitmap(), "Ảnh vừa chụp", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    if (st.processing > 0) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(78.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).padding(7.dp).clip(CircleShape)
                        .background(if (st.capturing) Color.Gray else Color.White).clickable(enabled = st.ready) { vm.shoot() },
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { vm.switchFacing() }) { Icon(Icons.Filled.Cameraswitch, "Đổi camera", tint = Color.White) }
                    Text("PRO", color = if (st.pro) Amber else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        modifier = Modifier.clickable { vm.togglePro() }.padding(4.dp))
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.Center))
    }

    if (showInfo) {
        val report = remember(st.lenses) { CameraInventory.report(st.lenses) }
        val clip = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("Thông tin camera") },
            text = { Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { clip.setText(AnnotatedString(report)) }) { Text("Sao chép") } },
            dismissButton = { TextButton(onClick = { showInfo = false }) { Text("Đóng") } },
        )
    }
    if (showSettings) SettingsDialog(vm) { showSettings = false }
}

@Composable
private fun TopChip(text: String, on: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        color = when { !enabled -> Color(0x55FFFFFF); on -> Color.Black; else -> Color.White },
        modifier = Modifier.padding(horizontal = 3.dp).clip(RoundedCornerShape(5.dp))
            .background(if (on && enabled) Amber else Color.Transparent)
            .border(1.dp, if (enabled) Color(0x88FFFFFF) else Color(0x33FFFFFF), RoundedCornerShape(5.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

private val SHUTTERS = listOf(1 / 8000.0, 1 / 4000.0, 1 / 2000.0, 1 / 1000.0, 1 / 500.0, 1 / 250.0, 1 / 125.0, 1 / 60.0, 1 / 30.0, 1 / 15.0, 1 / 8.0, 1 / 4.0, 0.5, 1.0, 2.0, 4.0, 8.0, 15.0, 30.0)

@Composable
private fun ProPanel(vm: CamViewModel, lens: com.looknote.cam.camera.LensInfo) {
    val st by vm.state.collectAsState()
    val live by vm.camera.live.collectAsState()
    val s = st.settings
    val chars = vm.camera.characteristics()
    Column(Modifier.fillMaxWidth().background(Color(0xFF111113)).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Chỉnh tay ISO/tốc độ", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = s.manual, enabled = lens.manualSensor, onCheckedChange = { on ->
                vm.setSettings(s.copy(manual = on, iso = if (live.iso > 0) live.iso else s.iso, exposureNs = if (live.exposureNs > 0) live.exposureNs else s.exposureNs))
            })
        }
        if (s.manual && lens.isoRange != null && lens.exposureRangeNs != null) {
            val (lo, hi) = lens.isoRange
            var isoPos by remember(s.manual) { mutableFloatStateOf(((ln(s.iso.toFloat()) - ln(lo.toFloat())) / (ln(hi.toFloat()) - ln(lo.toFloat()))).coerceIn(0f, 1f)) }
            val iso = exp(ln(lo.toFloat()) + isoPos * (ln(hi.toFloat()) - ln(lo.toFloat()))).roundToInt()
            ProSlider("ISO $iso", isoPos, { isoPos = it }) { vm.setSettings(vm.state.value.settings.copy(iso = iso)) }
            val speeds = SHUTTERS.map { (it * 1e9).toLong() }.filter { it in lens.exposureRangeNs.first..lens.exposureRangeNs.second }
            if (speeds.isNotEmpty()) {
                var idx by remember(s.manual) { mutableIntStateOf(speeds.indexOfFirst { it >= s.exposureNs }.let { if (it < 0) speeds.lastIndex else it }) }
                ProSlider("Tốc độ " + CameraInventory.shutterText(speeds[idx]), idx / maxOf(1f, speeds.lastIndex.toFloat()),
                    { idx = (it * speeds.lastIndex).roundToInt() }) { vm.setSettings(vm.state.value.settings.copy(exposureNs = speeds[idx])) }
            }
        } else {
            val range = chars?.get(android.hardware.camera2.CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
            val step = chars?.get(android.hardware.camera2.CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: 0f
            if (range != null && range.upper > range.lower) {
                var pos by remember { mutableFloatStateOf((s.ev - range.lower).toFloat() / (range.upper - range.lower)) }
                val ev = (range.lower + pos * (range.upper - range.lower)).roundToInt()
                ProSlider("Bù sáng ${evText(ev, step)} EV", pos, { pos = it }) { vm.setSettings(vm.state.value.settings.copy(ev = ev)) }
            }
        }
        if (lens.minFocusDiopter > 0f) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Lấy nét tay", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Switch(checked = s.manualFocus, onCheckedChange = { vm.setSettings(s.copy(manualFocus = it, focusDiopter = live.focusDiopter)) })
            }
            if (s.manualFocus) {
                var f by remember { mutableFloatStateOf((s.focusDiopter / lens.minFocusDiopter).coerceIn(0f, 1f)) }
                val dist = if (f <= 0.001f) "∞" else String.format(Locale.US, "%.2fm", 1f / (f * lens.minFocusDiopter))
                ProSlider("Khoảng nét $dist", f, { f = it }) { vm.setSettings(vm.state.value.settings.copy(focusDiopter = f * lens.minFocusDiopter)) }
            }
        }
        val awbs = listOf(
            CameraMetadata.CONTROL_AWB_MODE_AUTO to "AWB", CameraMetadata.CONTROL_AWB_MODE_DAYLIGHT to "Ngày",
            CameraMetadata.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT to "Mây", CameraMetadata.CONTROL_AWB_MODE_SHADE to "Râm",
            CameraMetadata.CONTROL_AWB_MODE_INCANDESCENT to "Sợi đốt", CameraMetadata.CONTROL_AWB_MODE_FLUORESCENT to "Huỳnh quang",
        )
        val avail = chars?.get(android.hardware.camera2.CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toList().orEmpty()
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(awbs.filter { it.first in avail }) { (m, l) ->
                FilterChip(selected = s.awb == m, onClick = { vm.setSettings(s.copy(awb = m)) }, label = { Text(l, fontSize = 11.sp) })
            }
        }
    }
}

@Composable
private fun ProSlider(label: String, value: Float, onChange: (Float) -> Unit, onDone: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.width(130.dp))
        Slider(value = value, onValueChange = onChange, onValueChangeFinished = onDone, modifier = Modifier.weight(1f).height(28.dp))
    }
}

@Composable
private fun SettingsDialog(vm: CamViewModel, onClose: () -> Unit) {
    val p = vm.prefs
    var keep by remember { mutableStateOf(p.keepOriginal) }
    var edge by remember { mutableIntStateOf(p.filteredMaxEdge) }
    var quality by remember { mutableStateOf(p.quality.toString()) }
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Cài đặt") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lưu cả ảnh gốc (không màu)", Modifier.weight(1f))
                    Switch(checked = keep, onCheckedChange = { keep = it })
                }
                Text("Kích thước ảnh áp màu (cạnh dài). Ảnh gốc luôn giữ đủ độ phân giải.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(4096, 6144, 8192).forEach { v -> FilterChip(selected = edge == v, onClick = { edge = v }, label = { Text("$v") }) }
                }
                OutlinedTextField(value = quality, onValueChange = { quality = it.filter(Char::isDigit).take(3) }, label = { Text("Chất lượng JPEG ảnh màu (70–100)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                Text("Thêm công thức: dán mã chia sẻ (LOOKNOTE1:…) hoặc JSON xuất từ LookNote", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = code, onValueChange = { code = it }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Text("Ảnh lưu tại DCIM/LookCam. Ảnh màu có đuôi tên theo Look (vd …_FL.jpg).", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                p.keepOriginal = keep; p.filteredMaxEdge = edge; p.quality = quality.toIntOrNull() ?: 95
                if (code.isNotBlank()) vm.importCode(code)
                onClose()
            }) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Đóng") } },
    )
}
