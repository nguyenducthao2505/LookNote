package com.looknote.cam

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.looknote.app.model.Recipe
import com.looknote.app.model.RecipeCodec
import com.looknote.app.model.RecipeStore
import com.looknote.cam.camera.Aspect
import com.looknote.cam.camera.CameraController
import com.looknote.cam.camera.CameraInventory
import com.looknote.cam.camera.CaptureSettings
import com.looknote.cam.camera.LensInfo
import com.looknote.cam.camera.LensOption
import com.looknote.cam.camera.SaveOptions
import com.looknote.cam.camera.ShotSaver
import com.looknote.cam.lut.LutBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class CamState(
    val lenses: List<LensInfo> = emptyList(),
    val options: List<LensOption> = emptyList(),
    val option: LensOption? = null,
    val facing: Int = CameraCharacteristics.LENS_FACING_BACK,
    val settings: CaptureSettings = CaptureSettings(),
    val previewAspect: Float = 3f / 4f,     // rong/cao khung ngam doc
    val recipeId: String? = null,
    val strength: Float = 1f,
    val pro: Boolean = false,
    val grid: Boolean = true,
    val timer: Int = 0,
    val countdown: Int = 0,
    val opening: Boolean = false,
    val capturing: Boolean = false,
    val processing: Int = 0,
    val lastThumb: Bitmap? = null,
    val lastUri: Uri? = null,
    val message: String? = null,
    val ready: Boolean = false,
)

class CamPrefs(context: Context) {
    private val sp = context.getSharedPreferences("lookcam", Context.MODE_PRIVATE)
    var keepOriginal: Boolean get() = sp.getBoolean("keepOriginal", true); set(v) = sp.edit().putBoolean("keepOriginal", v).apply()
    var filteredMaxEdge: Int get() = sp.getInt("filteredMaxEdge", 6144); set(v) = sp.edit().putInt("filteredMaxEdge", v).apply()
    var quality: Int get() = sp.getInt("quality", 95); set(v) = sp.edit().putInt("quality", v.coerceIn(70, 100)).apply()
    var recipeId: String? get() = sp.getString("recipeId", null); set(v) = sp.edit().putString("recipeId", v).apply()
    var raw: Boolean get() = sp.getBoolean("raw", false); set(v) = sp.edit().putBoolean("raw", v).apply()
    var maxRes: Boolean get() = sp.getBoolean("maxRes", false); set(v) = sp.edit().putBoolean("maxRes", v).apply()
}

class CamViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = CamPrefs(app)
    val store = RecipeStore(File(app.filesDir, "recipes.json")).also { it.load() }
    val camera = CameraController(app)

    private val _state = MutableStateFlow(CamState(recipeId = prefs.recipeId, settings = CaptureSettings(raw = prefs.raw, maxRes = prefs.maxRes)))
    val state: StateFlow<CamState> = _state.asStateFlow()

    var lutSink: ((IntArray?) -> Unit)? = null
    private var texture: SurfaceTexture? = null
    private var active = false
    private var openJob: Job? = null
    private val saveLock = Mutex()
    @Volatile var deviceRotation = 0

    fun recipes(): List<Recipe> = store.recipes.value.filter { !it.isPP }
    fun recipe(id: String? = _state.value.recipeId) = id?.let { i -> recipes().firstOrNull { it.id == i } }
    fun lens(): LensInfo? = _state.value.lenses.firstOrNull { it.id == camera.cameraId && it.parentId == null }

    fun consumeMessage() = _state.update { it.copy(message = null) }
    private fun msg(m: String) = _state.update { it.copy(message = m) }

    fun init() {
        if (_state.value.lenses.isNotEmpty()) return
        val all = CameraInventory.all(getApplication())
        val facing = _state.value.facing
        val opts = CameraInventory.options(all, facing)
        _state.update { it.copy(lenses = all, options = opts, option = opts.firstOrNull { o -> o.zoom == 1f } ?: opts.firstOrNull()) }
        updateLut()
    }

    fun onTexture(t: SurfaceTexture) {
        texture = t
        if (active) open()
    }

    fun resume() { active = true; if (texture != null) open() }
    fun pause() { active = false; openJob?.cancel(); camera.close(); _state.update { it.copy(ready = false) } }

    private fun open() {
        val t = texture ?: return
        val opt = _state.value.option ?: run { init(); _state.value.option } ?: run { msg("Không tìm thấy camera"); return }
        openJob?.cancel()
        _state.update { it.copy(opening = true, ready = false) }
        openJob = viewModelScope.launch {
            try {
                val s = _state.value.settings.copy(zoom = opt.zoom)
                val note = camera.open(opt.cameraId, t, s)
                val ps = camera.previewSize
                _state.update {
                    it.copy(opening = false, ready = true, settings = camera.settings,
                        previewAspect = minOf(ps.width, ps.height).toFloat() / maxOf(ps.width, ps.height), message = note ?: it.message)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(opening = false, message = e.message ?: "Không mở được camera") }
            }
        }
    }

    fun selectOption(o: LensOption) {
        val cur = _state.value.option
        _state.update { it.copy(option = o) }
        if (cur != null && cur.cameraId == o.cameraId && _state.value.ready) setSettings(_state.value.settings.copy(zoom = o.zoom))
        else open()
    }

    fun switchFacing() {
        val nf = if (_state.value.facing == CameraCharacteristics.LENS_FACING_BACK) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        val opts = CameraInventory.options(_state.value.lenses, nf)
        if (opts.isEmpty()) { msg("Không có camera hướng này"); return }
        _state.update { it.copy(facing = nf, options = opts, option = opts.firstOrNull { o -> o.zoom == 1f } ?: opts.first(),
            settings = it.settings.copy(manual = false, manualFocus = false, ev = 0)) }
        open()
    }

    fun setSettings(s: CaptureSettings) {
        _state.update { it.copy(settings = s) }
        camera.update(s)
    }

    /** Doi thiet lap can mo lai phien (RAW, do phan giai toi da, ty le khung). */
    fun setSessionSettings(s: CaptureSettings) {
        prefs.raw = s.raw; prefs.maxRes = s.maxRes
        _state.update { it.copy(settings = s) }
        open()
    }

    fun pinchZoom(factor: Float) {
        val s = _state.value.settings
        val range = lens()?.zoomRange ?: (1f to 10f)
        val z = (s.zoom * factor).coerceIn(range.first, range.second)
        if (kotlin.math.abs(z - s.zoom) > 0.01f) setSettings(s.copy(zoom = z))
    }

    fun selectRecipe(id: String?) {
        prefs.recipeId = id
        _state.update { it.copy(recipeId = id) }
        updateLut()
    }

    fun setStrength(v: Float) { _state.update { it.copy(strength = v) }; updateLut() }

    private var lutJob: Job? = null
    private fun updateLut() {
        val r = recipe()
        val strength = _state.value.strength
        lutJob?.cancel()
        lutJob = viewModelScope.launch(Dispatchers.Default) {
            val lut = r?.let { LutBuilder.build(it, strength) }
            withContext(Dispatchers.Main) { lutSink?.invoke(lut) }
        }
    }

    fun togglePro() = _state.update { it.copy(pro = !it.pro) }
    fun toggleGrid() = _state.update { it.copy(grid = !it.grid) }
    fun cycleTimer() = _state.update { it.copy(timer = when (it.timer) { 0 -> 3; 3 -> 10; else -> 0 }) }

    fun shoot() {
        val st = _state.value
        if (!st.ready || st.capturing || st.countdown > 0) return
        viewModelScope.launch {
            for (i in st.timer downTo 1) { _state.update { it.copy(countdown = i) }; delay(1000) }
            _state.update { it.copy(countdown = 0, capturing = true) }
            try {
                val shot = camera.capture(deviceRotation)
                _state.update { it.copy(capturing = false, processing = it.processing + 1) }
                val opts = SaveOptions(recipe(), _state.value.strength, prefs.keepOriginal, prefs.filteredMaxEdge, prefs.quality)
                launch(Dispatchers.Default) {
                    try {
                        val saved = saveLock.withLock { ShotSaver.save(getApplication(), shot, opts) }
                        _state.update { it.copy(processing = it.processing - 1, lastThumb = saved.thumb ?: it.lastThumb, lastUri = saved.filtered ?: saved.original ?: it.lastUri) }
                    } catch (e: OutOfMemoryError) {
                        _state.update { it.copy(processing = it.processing - 1, message = "Thiếu bộ nhớ khi áp màu – giảm kích thước ảnh màu trong Cài đặt.") }
                    } catch (e: Exception) {
                        _state.update { it.copy(processing = it.processing - 1, message = "Lưu ảnh lỗi: ${e.message}") }
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(capturing = false, message = e.message ?: "Chụp lỗi") }
            }
        }
    }

    fun importCode(text: String): Boolean {
        val t = text.trim()
        val list = if (t.startsWith("[")) runCatching { RecipeCodec.listFromString(t) }.getOrNull()
        else RecipeCodec.fromShareCode(t)?.let { listOf(it) }
        if (list == null) return false
        val ok = list.filter { !it.isPP }
        store.importAll(ok)
        msg(if (ok.isEmpty()) "Picture Profile không dùng được trong LookCam." else "Đã thêm ${ok.size} công thức.")
        return true
    }

    override fun onCleared() {
        camera.release()
    }
}
