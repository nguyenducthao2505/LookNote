package com.looknote.ai

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.looknote.ai.engine.ColorEngine
import com.looknote.ai.engine.EditSettings
import com.looknote.ai.engine.Face
import com.looknote.ai.engine.Pipeline
import com.looknote.ai.gemini.GeminiClient
import com.looknote.ai.gemini.GeminiException
import com.looknote.ai.gemini.Suggestion
import com.looknote.ai.util.AndroidImage
import com.looknote.ai.util.BitmapSink
import com.looknote.ai.util.BitmapSource
import com.looknote.app.model.Recipe
import com.looknote.app.model.RecipeCodec
import com.looknote.app.model.RecipeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EditorState(
    val sourceUri: Uri? = null,
    val fullSize: Pair<Int, Int>? = null,
    val base: Bitmap? = null,           // anh xem truoc goc (co the da retouch AI)
    val preview: Bitmap? = null,        // anh xem truoc da xu ly
    val faces: List<Face> = emptyList(),// toa do theo anh xem truoc
    val aiRetouched: Bitmap? = null,    // ket qua retouch AI (do phan giai Gemini)
    val aiModel: String = "",
    val recipeId: String? = null,
    val colorStrength: Float = 1f,
    val skinStrength: Float = 0f,
    val faceOnly: Boolean = true,
    val thumbs: Map<String, Bitmap> = emptyMap(),
    val suggestion: Suggestion? = null,
    val busy: String? = null,
    val progress: Float? = null,
    val message: String? = null,
)

class EditorViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    val store = RecipeStore(File(app.filesDir, "recipes.json")).also { it.load() }

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    private var renderJob: Job? = null
    private var thumbSource: Bitmap? = null

    /** Chi cong thuc Creative Look (Picture Profile khong mo phong duoc tren anh JPEG). */
    fun recipes(): List<Recipe> = store.recipes.value.filter { !it.isPP }

    fun recipe(id: String?): Recipe? = id?.let { i -> recipes().firstOrNull { it.id == i } }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    private fun msg(m: String) = _state.update { it.copy(message = m) }

    fun open(uri: Uri) {
        val ctx = getApplication<Application>()
        _state.update { EditorState(busy = "Đang mở ảnh…", recipeId = it.recipeId, colorStrength = it.colorStrength, faceOnly = it.faceOnly) }
        viewModelScope.launch {
            try {
                val (size, base, faces) = withContext(Dispatchers.Default) {
                    val size = AndroidImage.imageSize(ctx, uri)
                    val base = AndroidImage.load(ctx, uri, 1600)
                    Triple(size, base, AndroidImage.detectFaces(base))
                }
                _state.update { it.copy(sourceUri = uri, fullSize = size, base = base, preview = base, faces = faces, busy = null) }
                makeThumbs(base)
                render()
            } catch (e: Throwable) {
                _state.update { it.copy(busy = null, message = "Không mở được ảnh: ${e.message}") }
            }
        }
    }

    private fun makeThumbs(base: Bitmap) {
        viewModelScope.launch(Dispatchers.Default) {
            val small = AndroidImage.scaled(base, 220)
            thumbSource = small
            val w = small.width
            val h = small.height
            val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
            val map = LinkedHashMap<String, Bitmap>()
            for (r in recipes()) {
                val out = ColorEngine.apply(px, w, h, r)
                map[r.id] = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
                if (map.size % 6 == 0) _state.update { it.copy(thumbs = LinkedHashMap(map)) }
            }
            _state.update { it.copy(thumbs = map) }
        }
    }

    fun selectRecipe(id: String?) { _state.update { it.copy(recipeId = id) }; render() }
    fun setColorStrength(v: Float) { _state.update { it.copy(colorStrength = v) }; render() }
    fun setSkinStrength(v: Float) { _state.update { it.copy(skinStrength = v) }; render() }
    fun setFaceOnly(v: Boolean) { _state.update { it.copy(faceOnly = v) }; render() }

    private fun settings(s: EditorState) = EditSettings(recipe(s.recipeId), s.colorStrength, s.skinStrength, s.faceOnly)

    fun render() {
        val s = _state.value
        val base = s.base ?: return
        renderJob?.cancel()
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            val w = base.width
            val h = base.height
            val px = IntArray(w * h).also { base.getPixels(it, 0, w, 0, 0, w, h) }
            val out = Pipeline.process(px, w, h, s.faces, settings(s))
            val bmp = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
            _state.update { if (it.base === base) it.copy(preview = bmp) else it }
        }
    }

    private fun client(): GeminiClient? {
        val key = prefs.apiKey
        if (key.isBlank()) { msg("Chưa nhập Gemini API key (mục Cài đặt)."); return null }
        return GeminiClient(key)
    }

    fun suggest() {
        val s = _state.value
        val base = s.base ?: return
        val c = client() ?: return
        _state.update { it.copy(busy = "Gemini đang phân tích ảnh…") }
        viewModelScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    c.suggest(AndroidImage.toJpeg(base, 1024, 85), recipes(), prefs.textModelList())
                }
                val skin = if (res.hasPeople) res.skinSmoothing / 100f else 0f
                _state.update {
                    it.copy(busy = null, suggestion = res, recipeId = res.recipeId, colorStrength = 1f,
                        skinStrength = if (it.faces.isNotEmpty() || !it.faceOnly) skin else it.skinStrength)
                }
                render()
            } catch (e: GeminiException) {
                _state.update { it.copy(busy = null, message = e.message) }
            } catch (e: Throwable) {
                _state.update { it.copy(busy = null, message = "Lỗi: ${e.message}") }
            }
        }
    }

    /** Retouch bang Gemini (model tao anh, tinh phi). Ket qua thay anh nen. */
    fun aiRetouch() {
        val s = _state.value
        val uri = s.sourceUri ?: return
        val c = client() ?: return
        val ctx = getApplication<Application>()
        _state.update { it.copy(busy = "Gemini đang retouch da (có thể mất 10–60 giây)…") }
        viewModelScope.launch {
            try {
                val (bmp, model) = withContext(Dispatchers.IO) {
                    val src = AndroidImage.load(ctx, uri, 2048)
                    val (_, bytes, model) = c.retouch(AndroidImage.toJpeg(src, 2048, 92), prefs.imageModelList())
                    src.recycle()
                    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: throw GeminiException("Không giải mã được ảnh Gemini trả về")
                    decoded.copy(Bitmap.Config.ARGB_8888, true) to model
                }
                val base = AndroidImage.scaled(bmp, 1600)
                val faces = withContext(Dispatchers.Default) { AndroidImage.detectFaces(base) }
                _state.update {
                    it.copy(busy = null, aiRetouched = bmp, aiModel = model, base = base, faces = faces, skinStrength = 0f,
                        message = "Đã retouch bằng $model (${bmp.width}×${bmp.height}px).")
                }
                makeThumbs(base)
                render()
            } catch (e: GeminiException) {
                _state.update { it.copy(busy = null, message = e.message) }
            } catch (e: Throwable) {
                _state.update { it.copy(busy = null, message = "Lỗi: ${e.message}") }
            }
        }
    }

    fun undoAiRetouch() {
        val uri = _state.value.sourceUri ?: return
        _state.update { it.copy(aiRetouched = null, aiModel = "") }
        open(uri)
    }

    /** Xu ly anh do phan giai day du (theo cai dat) va luu hoac chia se. */
    fun export(share: Boolean) {
        val s = _state.value
        val uri = s.sourceUri ?: return
        val ctx = getApplication<Application>()
        _state.update { it.copy(busy = if (share) "Đang chuẩn bị ảnh…" else "Đang xuất ảnh chất lượng cao…", progress = 0f) }
        viewModelScope.launch {
            try {
                val out = withContext(Dispatchers.Default) {
                    val maxEdge = prefs.exportMaxEdge.let { if (it <= 0) 12000 else it }
                    val src = s.aiRetouched?.let { AndroidImage.scaled(it, maxEdge) } ?: AndroidImage.load(ctx, uri, maxEdge)
                    val k = src.width.toFloat() / (s.base?.width ?: src.width)
                    val faces = s.faces.map { Face(it.cx * k, it.cy * k, it.eyeDist * k) }
                    val dst = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
                    Pipeline.processTiled(BitmapSource(src), BitmapSink(dst), faces, settings(s)) { p ->
                        _state.update { it.copy(progress = p) }
                    }
                    src.recycle()
                    dst
                }
                val q = prefs.jpegQuality
                withContext(Dispatchers.IO) {
                    if (share) AndroidImage.shareJpeg(ctx, out, q)
                    else {
                        val name = "LookAI_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
                        AndroidImage.saveJpeg(ctx, out, name, q, uri)
                    }
                }
                _state.update {
                    it.copy(busy = null, progress = null,
                        message = if (share) null else "Đã lưu ${out.width}×${out.height}px vào Ảnh › Pictures/LookAI")
                }
                out.recycle()
            } catch (e: OutOfMemoryError) {
                _state.update { it.copy(busy = null, progress = null, message = "Thiếu bộ nhớ – giảm kích thước xuất trong Cài đặt.") }
            } catch (e: Throwable) {
                _state.update { it.copy(busy = null, progress = null, message = "Xuất ảnh thất bại: ${e.message}") }
            }
        }
    }

    fun importCode(text: String): Boolean {
        val trimmed = text.trim()
        val list = when {
            trimmed.startsWith("[") -> runCatching { RecipeCodec.listFromString(trimmed) }.getOrNull()
            else -> RecipeCodec.fromShareCode(trimmed)?.let { listOf(it) }
        } ?: return false
        val usable = list.filter { !it.isPP }
        if (usable.isEmpty()) { msg("Công thức Picture Profile không dùng được trong LookAI."); return true }
        store.importAll(usable)
        thumbSource?.let { _state.value.base?.let { b -> makeThumbs(b) } }
        msg("Đã thêm ${usable.size} công thức.")
        return true
    }
}
