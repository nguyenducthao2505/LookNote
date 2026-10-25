package com.looknote.cam.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class FlashMode { OFF, AUTO, ON, TORCH }
enum class Aspect(val label: String, val ratio: Float) { R4_3("4:3", 4f / 3f), R16_9("16:9", 16f / 9f) }

/** Thiet lap chup do nguoi dung chon. */
data class CaptureSettings(
    val zoom: Float = 1f,
    val ev: Int = 0,                    // theo buoc cua may
    val manual: Boolean = false,
    val iso: Int = 100,
    val exposureNs: Long = 8_000_000L,  // 1/125
    val manualFocus: Boolean = false,
    val focusDiopter: Float = 0f,
    val awb: Int = CameraMetadata.CONTROL_AWB_MODE_AUTO,
    val flash: FlashMode = FlashMode.OFF,
    val raw: Boolean = false,
    val maxRes: Boolean = false,
    val aspect: Aspect = Aspect.R4_3,
    val ois: Boolean = true,
)

/** Thong so thuc te may dang dung (hien tren khung ngam). */
data class LiveInfo(val iso: Int = 0, val exposureNs: Long = 0, val focusDiopter: Float = 0f, val afState: Int = 0, val zoom: Float = 1f)

/** Ket qua mot lan chup. */
class Shot(val jpeg: ByteArray, val raw: Image?, val result: TotalCaptureResult?, val chars: CameraCharacteristics, val orientation: Int, val reader: ImageReader?)

class CameraException(msg: String) : Exception(msg)

/**
 * Lop Camera2: mo camera, khung ngam (SurfaceTexture), chup JPEG/RAW, dieu khien tay.
 * Tat ca thao tac camera chay tren mot luong rieng.
 */
class CameraController(private val context: Context) {
    private val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val thread = HandlerThread("LookCam").apply { start() }
    private val handler = Handler(thread.looper)
    private val executor = Executor { handler.post(it) }

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var previewSurface: Surface? = null
    private var jpegReader: ImageReader? = null
    private var rawReader: ImageReader? = null
    private var chars: CameraCharacteristics? = null
    private var maxResActive = false

    var cameraId: String = ""; private set
    var sensorOrientation = 90; private set
    var facing = CameraCharacteristics.LENS_FACING_BACK; private set
    var previewSize: Size = Size(1440, 1080); private set
    var settings = CaptureSettings(); private set

    private val _live = MutableStateFlow(LiveInfo())
    val live: StateFlow<LiveInfo> = _live.asStateFlow()

    @Volatile private var afState = 0
    @Volatile private var aeState = 0
    private var meteringRegion: Array<MeteringRectangle>? = null

    fun characteristics() = chars

    private fun bestSize(sizes: Array<Size>?, aspect: Float, maxPixels: Long = Long.MAX_VALUE): Size? {
        val list = sizes?.filter { it.width.toLong() * it.height <= maxPixels }.orEmpty()
        val matching = list.filter { abs(max(it.width, it.height).toFloat() / min(it.width, it.height) - aspect) < 0.02f }
        return (matching.ifEmpty { list }).maxByOrNull { it.width.toLong() * it.height }
    }

    @SuppressLint("MissingPermission")
    private suspend fun openDevice(id: String): CameraDevice = suspendCancellableCoroutine { cont ->
        cm.openCamera(id, object : CameraDevice.StateCallback() {
            override fun onOpened(d: CameraDevice) { if (cont.isActive) cont.resume(d) else d.close() }
            override fun onDisconnected(d: CameraDevice) { d.close(); if (cont.isActive) cont.resumeWithException(CameraException("Camera bị ngắt")) }
            override fun onError(d: CameraDevice, error: Int) {
                d.close()
                val m = when (error) {
                    ERROR_CAMERA_IN_USE, ERROR_MAX_CAMERAS_IN_USE -> "Camera đang được ứng dụng khác dùng"
                    ERROR_CAMERA_DISABLED -> "Camera bị chặn bởi chính sách thiết bị"
                    else -> "Lỗi camera ($error)"
                }
                if (cont.isActive) cont.resumeWithException(CameraException(m))
            }
        }, handler)
    }

    private suspend fun createSession(d: CameraDevice, outputs: List<OutputConfiguration>): CameraCaptureSession =
        suspendCancellableCoroutine { cont ->
            val cb = object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) { if (cont.isActive) cont.resume(s) }
                override fun onConfigureFailed(s: CameraCaptureSession) {
                    if (cont.isActive) cont.resumeWithException(CameraException("Máy không hỗ trợ tổ hợp luồng này"))
                }
            }
            try {
                d.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, executor, cb))
            } catch (e: Exception) {
                if (cont.isActive) cont.resumeWithException(CameraException("Không tạo được phiên chụp: ${e.message}"))
            }
        }

    /**
     * Mo camera va bat khung ngam. Neu cau hinh nang (RAW / do phan giai toi da) that bai
     * thi tu dong thu lai cau hinh don gian hon. Tra ve thong bao neu da phai ha cau hinh.
     */
    suspend fun open(id: String, texture: SurfaceTexture, s: CaptureSettings): String? {
        close()
        val c = cm.getCameraCharacteristics(id)
        chars = c
        cameraId = id
        sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        facing = c.get(CameraCharacteristics.LENS_FACING) ?: CameraCharacteristics.LENS_FACING_BACK
        val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: throw CameraException("Camera không có cấu hình luồng")

        previewSize = bestSize(map.getOutputSizes(SurfaceTexture::class.java), s.aspect.ratio, 1920L * 1440) ?: Size(1280, 960)
        texture.setDefaultBufferSize(previewSize.width, previewSize.height)
        val surface = Surface(texture)
        previewSurface = surface

        val d = openDevice(id)
        device = d

        val attempts = listOf(s, s.copy(maxRes = false), s.copy(maxRes = false, raw = false)).distinct()
        var lastError: Exception? = null
        for ((i, attempt) in attempts.withIndex()) {
            try {
                configure(d, c, map, surface, attempt)
                settings = attempt
                startPreview()
                return if (i == 0) null else buildString {
                    append("Máy không cho dùng đồng thời")
                    if (s.maxRes && !attempt.maxRes) append(" độ phân giải tối đa")
                    if (s.raw && !attempt.raw) append(" RAW")
                    append(" với ống kính này – đã tắt.")
                }
            } catch (e: Exception) {
                lastError = e
                jpegReader?.close(); jpegReader = null
                rawReader?.close(); rawReader = null
            }
        }
        throw lastError ?: CameraException("Không mở được camera")
    }

    private suspend fun configure(d: CameraDevice, c: CameraCharacteristics, map: android.hardware.camera2.params.StreamConfigurationMap, surface: Surface, s: CaptureSettings) {
        var jpegSize: Size? = null
        maxResActive = false
        if (s.maxRes && Build.VERSION.SDK_INT >= 31) {
            val mr = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
            jpegSize = bestSize(mr?.getOutputSizes(ImageFormat.JPEG), s.aspect.ratio)
            maxResActive = jpegSize != null
        }
        if (jpegSize == null) jpegSize = bestSize(map.getOutputSizes(ImageFormat.JPEG), s.aspect.ratio) ?: throw CameraException("Không có kích thước JPEG")
        val jr = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2)
        jpegReader = jr
        val outs = mutableListOf(OutputConfiguration(surface))
        val jo = OutputConfiguration(jr.surface)
        if (maxResActive && Build.VERSION.SDK_INT >= 31) {
            jo.addSensorPixelModeUsed(CameraMetadata.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION)
        }
        outs.add(jo)
        if (s.raw) {
            val rs = map.getOutputSizes(ImageFormat.RAW_SENSOR)?.maxByOrNull { it.width.toLong() * it.height }
            if (rs != null) {
                val rr = ImageReader.newInstance(rs.width, rs.height, ImageFormat.RAW_SENSOR, 2)
                rawReader = rr
                outs.add(OutputConfiguration(rr.surface))
            }
        }
        session = createSession(d, outs)
    }

    private val previewCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
            afState = result.get(CaptureResult.CONTROL_AF_STATE) ?: 0
            aeState = result.get(CaptureResult.CONTROL_AE_STATE) ?: 0
            val iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 0
            val exp = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L
            val fd = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: 0f
            val cur = _live.value
            if (cur.iso != iso || cur.exposureNs != exp || abs(cur.focusDiopter - fd) > 0.01f || cur.afState != afState) {
                _live.update { LiveInfo(iso, exp, fd, afState, settings.zoom) }
            }
        }
    }

    private fun applyControls(b: CaptureRequest.Builder, s: CaptureSettings, still: Boolean) {
        val c = chars ?: return
        b.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        // Zoom
        if (Build.VERSION.SDK_INT >= 30 && c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE) != null) {
            val zr = c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)!!
            b.set(CaptureRequest.CONTROL_ZOOM_RATIO, s.zoom.coerceIn(zr.lower, zr.upper))
        } else {
            val active = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            val maxZ = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
            if (active != null) {
                val z = s.zoom.coerceIn(1f, maxZ)
                val w = (active.width() / z).roundToInt(); val h = (active.height() / z).roundToInt()
                val l = active.left + (active.width() - w) / 2; val t = active.top + (active.height() - h) / 2
                b.set(CaptureRequest.SCALER_CROP_REGION, Rect(l, t, l + w, t + h))
            }
        }
        // Phoi sang
        if (s.manual) {
            b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            b.set(CaptureRequest.SENSOR_SENSITIVITY, s.iso)
            b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, s.exposureNs)
            b.set(CaptureRequest.SENSOR_FRAME_DURATION, max(s.exposureNs, 33_333_333L))
            b.set(CaptureRequest.FLASH_MODE, if (s.flash == FlashMode.TORCH || (still && s.flash == FlashMode.ON)) CameraMetadata.FLASH_MODE_TORCH else CameraMetadata.FLASH_MODE_OFF)
        } else {
            val aeMode = when (s.flash) {
                FlashMode.AUTO -> CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH
                FlashMode.ON -> CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                else -> CameraMetadata.CONTROL_AE_MODE_ON
            }
            val hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            b.set(CaptureRequest.CONTROL_AE_MODE, if (hasFlash) aeMode else CameraMetadata.CONTROL_AE_MODE_ON)
            b.set(CaptureRequest.FLASH_MODE, if (s.flash == FlashMode.TORCH && hasFlash) CameraMetadata.FLASH_MODE_TORCH else CameraMetadata.FLASH_MODE_OFF)
            b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, s.ev)
        }
        // Lay net
        if (s.manualFocus && (c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f) > 0f) {
            b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
            b.set(CaptureRequest.LENS_FOCUS_DISTANCE, s.focusDiopter)
        } else {
            val modes = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toList().orEmpty()
            val m = if (CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE in modes) CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            else if (CameraMetadata.CONTROL_AF_MODE_AUTO in modes) CameraMetadata.CONTROL_AF_MODE_AUTO else CameraMetadata.CONTROL_AF_MODE_OFF
            b.set(CaptureRequest.CONTROL_AF_MODE, m)
        }
        meteringRegion?.let { r ->
            if ((c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0) > 0) b.set(CaptureRequest.CONTROL_AF_REGIONS, r)
            if ((c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0) > 0) b.set(CaptureRequest.CONTROL_AE_REGIONS, r)
        }
        b.set(CaptureRequest.CONTROL_AWB_MODE, s.awb)
        // Chong rung quang hoc
        val ois = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.toList().orEmpty()
        if (CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON in ois) {
            b.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                if (s.ois) CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON else CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF)
        }
        if (still) {
            b.set(CaptureRequest.JPEG_QUALITY, 97.toByte())
            b.set(CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY)
            b.set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_HIGH_QUALITY)
            b.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY)
            if (Build.VERSION.SDK_INT >= 31 && maxResActive) {
                b.set(CaptureRequest.SENSOR_PIXEL_MODE, CameraMetadata.SENSOR_PIXEL_MODE_MAXIMUM_RESOLUTION)
            }
        }
    }

    private fun startPreview() {
        val d = device ?: return
        val s = session ?: return
        val surf = previewSurface ?: return
        val b = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
        b.addTarget(surf)
        applyControls(b, settings, still = false)
        runCatching { s.setRepeatingRequest(b.build(), previewCallback, handler) }
    }

    /** Doi thiet lap khong can mo lai camera (zoom, EV, tay, AWB, flash). */
    fun update(s: CaptureSettings) {
        settings = s
        _live.update { it.copy(zoom = s.zoom) }
        handler.post { startPreview() }
    }

    /** Cham de lay net / do sang tai diem (x,y) chuan hoa theo khung ngam doc. */
    fun focusAt(nx: Float, ny: Float) {
        val c = chars ?: return
        val active = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: return
        var x = nx; val y = ny
        if (facing == CameraCharacteristics.LENS_FACING_FRONT) x = 1f - x
        // Doi tu toa do man hinh doc sang toa do cam bien
        val (sx, sy) = when (sensorOrientation) {
            90 -> y to 1f - x
            270 -> 1f - y to x
            180 -> 1f - x to 1f - y
            else -> x to y
        }
        val z = max(1f, settings.zoom)
        val cx = active.left + active.width() * (0.5f + (sx - 0.5f) / z)
        val cy = active.top + active.height() * (0.5f + (sy - 0.5f) / z)
        val half = (min(active.width(), active.height()) * 0.08f / z).roundToInt()
        val rect = Rect(
            (cx - half).roundToInt().coerceIn(active.left, active.right - 1),
            (cy - half).roundToInt().coerceIn(active.top, active.bottom - 1),
            (cx + half).roundToInt().coerceIn(active.left + 1, active.right),
            (cy + half).roundToInt().coerceIn(active.top + 1, active.bottom),
        )
        meteringRegion = arrayOf(MeteringRectangle(rect, MeteringRectangle.METERING_WEIGHT_MAX - 1))
        handler.post {
            val d = device ?: return@post
            val s = session ?: return@post
            val surf = previewSurface ?: return@post
            runCatching {
                s.stopRepeating()
                val b = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                b.addTarget(surf)
                applyControls(b, settings, false)
                if (!settings.manualFocus) {
                    b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
                    b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
                }
                s.capture(b.build(), previewCallback, handler)
                val rb = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                rb.addTarget(surf)
                applyControls(rb, settings, false)
                if (!settings.manualFocus) rb.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
                s.setRepeatingRequest(rb.build(), previewCallback, handler)
            }
        }
    }

    /** Bo diem lay net da cham, ve lai lay net lien tuc. */
    fun resetFocus() {
        meteringRegion = null
        handler.post { startPreview() }
    }

    private suspend fun waitFor(timeoutMs: Long, cond: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end && !cond()) delay(25)
    }

    private fun takeLatest(r: ImageReader?) { r?.let { while (true) { val i = it.acquireLatestImage() ?: break; i.close() } } }

    /** Chup mot anh. deviceRotation: goc xoay cua may (0/90/180/270). */
    suspend fun capture(deviceRotation: Int): Shot {
        val d = device ?: throw CameraException("Camera chưa sẵn sàng")
        val s = session ?: throw CameraException("Camera chưa sẵn sàng")
        val c = chars ?: throw CameraException("Camera chưa sẵn sàng")
        val jr = jpegReader ?: throw CameraException("Camera chưa sẵn sàng")
        val rr = rawReader
        val st = settings
        takeLatest(jr); takeLatest(rr)

        // Tien chup: khoa net va phoi sang khi can
        val surf = previewSurface
        if (surf != null && !st.manual) {
            val needAf = !st.manualFocus && afState !in listOf(CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED, CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED)
            val needAe = st.flash == FlashMode.AUTO || st.flash == FlashMode.ON
            if (needAf || needAe) {
                val pb = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                pb.addTarget(surf)
                applyControls(pb, st, false)
                if (needAf) pb.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
                if (needAe) pb.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START)
                runCatching { s.capture(pb.build(), previewCallback, handler) }
                waitFor(1200) {
                    (!needAf || afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED || afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) &&
                        (!needAe || aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED || aeState == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED || aeState == CaptureResult.CONTROL_AE_STATE_LOCKED)
                }
            }
        }

        val orientation = if (facing == CameraCharacteristics.LENS_FACING_FRONT) (sensorOrientation - deviceRotation + 360) % 360
        else (sensorOrientation + deviceRotation) % 360

        val b = d.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
        b.addTarget(jr.surface)
        rr?.let { b.addTarget(it.surface) }
        applyControls(b, st, still = true)
        b.set(CaptureRequest.JPEG_ORIENTATION, orientation)

        val jpegDone = CompletableDeferred<ByteArray>()
        val rawDone = CompletableDeferred<Image?>()
        val resultDone = CompletableDeferred<TotalCaptureResult?>()
        jr.setOnImageAvailableListener({ r ->
            val img = r.acquireNextImage() ?: return@setOnImageAvailableListener
            try {
                val buf = img.planes[0].buffer
                val bytes = ByteArray(buf.remaining()); buf.get(bytes)
                jpegDone.complete(bytes)
            } finally { img.close() }
        }, handler)
        if (rr != null) rr.setOnImageAvailableListener({ r -> r.acquireNextImage()?.let { rawDone.complete(it) } }, handler)
        else rawDone.complete(null)

        s.capture(b.build(), object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                resultDone.complete(result)
            }
            override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                resultDone.complete(null)
                jpegDone.completeExceptionally(CameraException("Chụp thất bại (mã ${failure.reason})"))
            }
        }, handler)

        val timeout = if (st.manual) st.exposureNs / 1_000_000 + 8000 else 10_000
        try {
            val jpeg = withTimeout(timeout) { jpegDone.await() }
            val result = withTimeout(3000) { resultDone.await() }
            val raw = runCatching { withTimeout(3000) { rawDone.await() } }.getOrNull()
            return Shot(jpeg, raw, result, c, orientation, rr)
        } finally {
            // Mo khoa net, quay lai xem truoc
            handler.post {
                runCatching {
                    val ub = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    previewSurface?.let { ub.addTarget(it) }
                    applyControls(ub, settings, false)
                    ub.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
                    s.capture(ub.build(), null, handler)
                }
                startPreview()
            }
        }
    }

    fun close() {
        runCatching { session?.close() }; session = null
        runCatching { device?.close() }; device = null
        runCatching { jpegReader?.close() }; jpegReader = null
        runCatching { rawReader?.close() }; rawReader = null
        runCatching { previewSurface?.release() }; previewSurface = null
    }

    fun release() {
        close()
        thread.quitSafely()
    }
}
