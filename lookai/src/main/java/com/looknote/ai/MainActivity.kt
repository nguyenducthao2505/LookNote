@file:OptIn(ExperimentalMaterial3Api::class)

package com.looknote.ai

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looknote.app.model.Recipe

private val Amber = Color(0xFFF2B33D)

class MainActivity : ComponentActivity() {
    private val vm: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val dark = isSystemInDarkTheme()
            androidx.compose.material3.MaterialTheme(
                colorScheme = if (dark) darkColorScheme(primary = Amber, onPrimary = Color(0xFF1E1400), surfaceVariant = Color(0xFF26262A))
                else lightColorScheme(primary = Color(0xFF8A5A00)),
            ) { EditorScreen(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri: Uri? = when (intent?.action) {
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            Intent.ACTION_EDIT -> intent.data
            else -> null
        }
        if (uri != null) vm.open(uri)
    }
}

@Composable
fun EditorScreen(vm: EditorViewModel) {
    val st by vm.state.collectAsState()
    val allRecipes by vm.store.recipes.collectAsState()
    val recipes = remember(allRecipes) { allRecipes.filter { !it.isPP } }
    val snack = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var confirmAi by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.open(uri) }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    LaunchedEffect(st.message) {
        st.message?.let { snack.showSnackbar(it); vm.consumeMessage() }
    }
    LaunchedEffect(Unit) { if (vm.prefs.apiKey.isBlank()) showSettings = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LookAI", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { pick() }) { Icon(Icons.Filled.PhotoLibrary, "Chọn ảnh") }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Settings, "Cài đặt") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Thêm") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Thêm công thức (dán mã từ LookNote)") }, onClick = { menu = false; showImport = true })
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // ----- Vung anh -----
            Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF0B0B0C)), contentAlignment = Alignment.Center) {
                var showOriginal by remember { mutableStateOf(false) }
                val img = if (showOriginal) st.base else st.preview
                if (img != null) {
                    Image(
                        img.asImageBitmap(), contentDescription = "Ảnh",
                        modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                            detectTapGestures(onPress = { showOriginal = true; tryAwaitRelease(); showOriginal = false })
                        },
                        contentScale = ContentScale.Fit,
                    )
                    Text(
                        if (showOriginal) "Ảnh gốc" else "Giữ để xem ảnh gốc",
                        color = Color.White, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                            .background(Color(0x88000000), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                } else if (st.busy == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(onClick = { pick() }) {
                            Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("Chọn ảnh để chỉnh")
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Chỉnh màu theo công thức phim Sony & làm mịn da", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                    }
                }
                if (st.busy != null) {
                    Column(
                        Modifier.align(Alignment.Center).background(Color(0xCC000000), RoundedCornerShape(12.dp)).padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val p = st.progress
                        if (p != null) LinearProgressIndicator(progress = { p }, modifier = Modifier.width(180.dp))
                        else CircularProgressIndicator()
                        Spacer(Modifier.height(10.dp))
                        Text(st.busy ?: "", color = Color.White, fontSize = 13.sp)
                    }
                }
            }

            // ----- Bang dieu khien -----
            if (st.base != null) {
                TabRow(selectedTabIndex = tab) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Màu phim") }, icon = { Icon(Icons.Filled.Palette, null) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Làm mịn da") }, icon = { Icon(Icons.Filled.Face, null) })
                }
                Column(Modifier.fillMaxWidth().height(250.dp).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                    if (tab == 0) ColorPanel(vm, st, recipes) else SkinPanel(vm, st) { confirmAi = true }
                }
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { vm.export(share = true) }, enabled = st.busy == null, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text("Chia sẻ")
                    }
                    Button(onClick = { vm.export(share = false) }, enabled = st.busy == null, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Save, null); Spacer(Modifier.width(6.dp)); Text("Lưu ảnh")
                    }
                }
            }
        }
    }

    if (showSettings) SettingsDialog(vm) { showSettings = false }
    if (showImport) ImportDialog(vm) { showImport = false }
    if (confirmAi) {
        AlertDialog(
            onDismissRequest = { confirmAi = false },
            title = { Text("Retouch bằng Gemini AI?") },
            text = {
                Text(
                    "Ảnh (tối đa 2048px) sẽ được gửi lên Google Gemini để làm mịn da.\n\n" +
                        "• Tính phí theo bảng giá Google (khoảng 0,03–0,07 USD/ảnh), cần API key đã bật thanh toán.\n" +
                        "• Ảnh trả về khoảng 1–2K px và AI vẽ lại ảnh nên chi tiết có thể thay đổi nhẹ.\n" +
                        "• Làm mịn da trên máy (thanh trượt) miễn phí, giữ nguyên độ phân giải.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmAi = false; vm.aiRetouch() }) { Text("Gửi lên Gemini") } },
            dismissButton = { TextButton(onClick = { confirmAi = false }) { Text("Hủy") } },
        )
    }
}

@Composable
private fun ColorPanel(vm: EditorViewModel, st: EditorState, recipes: List<Recipe>) {
    val sug = st.suggestion
    Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = { vm.suggest() }, enabled = st.busy == null) {
            Icon(Icons.Filled.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("Gemini gợi ý")
        }
        Spacer(Modifier.width(10.dp))
        Text(
            vm.recipe(st.recipeId)?.let { "${it.look.code} · ${it.name}" } ?: "Chưa áp màu",
            maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
        )
    }
    if (sug != null) {
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                "Gemini: " + sug.reason + if (sug.hasPeople) "  · Đề xuất làm mịn da ${sug.skinSmoothing}%" else "",
                modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    val ordered = remember(recipes, sug) {
        if (sug == null) recipes
        else {
            val top = (listOf(sug.recipeId) + sug.alternatives).mapNotNull { id -> recipes.firstOrNull { it.id == id } }
            top + recipes.filter { r -> top.none { it.id == r.id } }
        }
    }
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { RecipeTile(null, "Gốc", "—", st.recipeId == null, null) { vm.selectRecipe(null) } }
        items(ordered, key = { it.id }) { r ->
            val star = sug != null && (r.id == sug.recipeId)
            RecipeTile(r.id, (if (star) "★ " else "") + r.name, r.look.code, st.recipeId == r.id, st.thumbs[r.id]) { vm.selectRecipe(r.id) }
        }
    }
    if (st.recipeId != null) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Mức áp dụng", Modifier.width(100.dp), style = MaterialTheme.typography.bodySmall)
            var v by remember(st.recipeId) { mutableFloatStateOf(st.colorStrength) }
            Slider(value = v, onValueChange = { v = it }, onValueChangeFinished = { vm.setColorStrength(v) }, modifier = Modifier.weight(1f))
            Text("${(v * 100).toInt()}%", Modifier.width(44.dp), style = MaterialTheme.typography.bodySmall)
        }
        vm.recipe(st.recipeId)?.let { r ->
            Text(
                "${r.summary()} · WB ${r.wbText()}" + if (r.source.isNotBlank()) " · ${r.source}" else "",
                modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecipeTile(id: String?, name: String, code: String, selected: Boolean, thumb: android.graphics.Bitmap?, onClick: () -> Unit) {
    Column(Modifier.width(84.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(10.dp))
                .border(if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, Color(0x33FFFFFF)), RoundedCornerShape(10.dp))
                .background(Color(0xFF222225)),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) Image(thumb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(if (id == null) "Gốc" else "…", color = Color.White)
            Text(
                code, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).background(Amber, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
            )
        }
        Text(name, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 13.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun SkinPanel(vm: EditorViewModel, st: EditorState, onAi: () -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp)) {
        Text(
            when {
                st.faces.isEmpty() -> "Không phát hiện khuôn mặt. Tắt “Chỉ vùng khuôn mặt” để làm mịn mọi vùng có màu da."
                else -> "Phát hiện ${st.faces.size} khuôn mặt."
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Làm mịn da", Modifier.width(100.dp))
            var v by remember(st.base) { mutableFloatStateOf(st.skinStrength) }
            LaunchedEffect(st.skinStrength) { v = st.skinStrength }
            Slider(value = v, onValueChange = { v = it }, onValueChangeFinished = { vm.setSkinStrength(v) }, modifier = Modifier.weight(1f))
            Text("${(v * 100).toInt()}%", Modifier.width(44.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Chỉ vùng khuôn mặt", Modifier.weight(1f))
            Switch(checked = st.faceOnly, onCheckedChange = { vm.setFaceOnly(it) })
        }
        Spacer(Modifier.height(6.dp))
        if (st.aiRetouched == null) {
            OutlinedButton(onClick = onAi, enabled = st.busy == null, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("Retouch bằng Gemini AI (tính phí)")
            }
        } else {
            Text("Đang dùng ảnh đã retouch bằng ${st.aiModel} (${st.aiRetouched.width}×${st.aiRetouched.height}px).", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { vm.undoAiRetouch() }) { Text("Bỏ retouch AI, quay về ảnh gốc") }
        }
    }
}

@Composable
private fun SettingsDialog(vm: EditorViewModel, onClose: () -> Unit) {
    val p = vm.prefs
    var key by remember { mutableStateOf(p.apiKey) }
    var text by remember { mutableStateOf(p.textModels) }
    var image by remember { mutableStateOf(p.imageModels) }
    var edge by remember { mutableIntStateOf(p.exportMaxEdge) }
    var quality by remember { mutableStateOf(p.jpegQuality.toString()) }
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Cài đặt") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Gemini API key (miễn phí cho chức năng gợi ý công thức). Lấy tại Google AI Studio:", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { uri.openUri("https://aistudio.google.com/apikey") }) { Text("aistudio.google.com/apikey") }
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, label = { Text("API key") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                )
                Text("Key chỉ lưu trên điện thoại này và chỉ gửi tới Google.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Model gợi ý (thử lần lượt)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = image, onValueChange = { image = it }, label = { Text("Model retouch AI (tính phí)") }, modifier = Modifier.fillMaxWidth())
                Text("Kích thước xuất ảnh (cạnh dài)", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2048 to "2048", 3072 to "3072", 4096 to "4096", 0 to "Gốc").forEach { (v, l) ->
                        FilterChip(selected = edge == v, onClick = { edge = v }, label = { Text(l) })
                    }
                }
                OutlinedTextField(
                    value = quality, onValueChange = { quality = it.filter(Char::isDigit).take(3) },
                    label = { Text("Chất lượng JPEG (70–100)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = {
                    text = com.looknote.ai.gemini.GeminiClient.DEFAULT_TEXT_MODELS.joinToString(", ")
                    image = com.looknote.ai.gemini.GeminiClient.DEFAULT_IMAGE_MODELS.joinToString(", ")
                }) { Text("Khôi phục model mặc định") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                p.apiKey = key; p.textModels = text; p.imageModels = image; p.exportMaxEdge = edge
                p.jpegQuality = quality.toIntOrNull() ?: 95
                onClose()
            }) { Text("Lưu") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Đóng") } },
    )
}

@Composable
private fun ImportDialog(vm: EditorViewModel, onClose: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Thêm công thức") },
        text = {
            Column {
                Text("Dán mã chia sẻ (LOOKNOTE1:…) hoặc nội dung tệp JSON xuất từ app LookNote.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = code, onValueChange = { code = it; error = false }, minLines = 3, isError = error, modifier = Modifier.fillMaxWidth())
                if (error) Text("Mã không hợp lệ", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { if (vm.importCode(code)) onClose() else error = true }) { Text("Thêm") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Hủy") } },
    )
}
