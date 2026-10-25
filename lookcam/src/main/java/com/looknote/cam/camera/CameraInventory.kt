package com.looknote.cam.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraExtensionCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Build
import android.util.Size
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Thong tin mot camera (logic hoac vat ly) ma he dieu hanh cho app dung. */
data class LensInfo(
    val id: String,
    val parentId: String?,          // khac null neu la camera vat ly ben trong camera logic
    val facing: Int,
    val focalMm: Float,
    val eqMm: Float,                // tieu cu tuong duong 35mm
    val sensorMm: Pair<Float, Float>,
    val pixelArray: Size?,
    val maxJpeg: Size?,
    val maxResJpeg: Size?,          // che do do phan giai toi da (cam bien 50/200MP)
    val maxRaw: Size?,
    val hardwareLevel: String,
    val raw: Boolean,
    val manualSensor: Boolean,
    val ultraHighRes: Boolean,
    val logical: Boolean,
    val physicalIds: List<String>,
    val zoomRange: Pair<Float, Float>?,
    val minFocusDiopter: Float,
    val isoRange: Pair<Int, Int>?,
    val exposureRangeNs: Pair<Long, Long>?,
    val flash: Boolean,
    val ois: Boolean,
    val sensorOrientation: Int,
    val extensions: List<String>,
) {
    val facingText: String get() = when (facing) {
        CameraCharacteristics.LENS_FACING_BACK -> "Sau"
        CameraCharacteristics.LENS_FACING_FRONT -> "Trước"
        else -> "Ngoài"
    }
}

/** Lua chon ong kinh hien tren man hinh: mo camera nao, voi ty le zoom nao. */
data class LensOption(val label: String, val cameraId: String, val zoom: Float, val facing: Int)

object CameraInventory {

    private fun mp(s: Size?) = if (s == null) "—" else String.format(Locale.US, "%d×%d (%.1f MP)", s.width, s.height, s.width * s.height / 1e6)

    private fun levelName(l: Int?) = when (l) {
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "LEGACY"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "LIMITED"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "FULL"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "LEVEL_3"
        CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "EXTERNAL"
        else -> "?"
    }

    private fun extName(e: Int) = when (e) {
        CameraExtensionCharacteristics.EXTENSION_NIGHT -> "Night"
        CameraExtensionCharacteristics.EXTENSION_HDR -> "HDR"
        CameraExtensionCharacteristics.EXTENSION_BOKEH -> "Bokeh"
        CameraExtensionCharacteristics.EXTENSION_FACE_RETOUCH -> "Face retouch"
        CameraExtensionCharacteristics.EXTENSION_AUTOMATIC -> "Auto"
        else -> "#$e"
    }

    fun describe(cm: CameraManager, id: String, parent: String?): LensInfo? = runCatching {
        val c = cm.getCameraCharacteristics(id)
        val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toList().orEmpty()
        val focal = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0f
        val ss = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        val diag = if (ss != null) sqrt(ss.width * ss.width + ss.height * ss.height) else 0f
        val map = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val maxJpeg = map?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height }
        val maxRaw = map?.getOutputSizes(ImageFormat.RAW_SENSOR)?.maxByOrNull { it.width.toLong() * it.height }
        var uhr = false
        var maxRes: Size? = null
        if (Build.VERSION.SDK_INT >= 31) {
            uhr = caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR)
            if (uhr) maxRes = c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP_MAXIMUM_RESOLUTION)
                ?.getOutputSizes(ImageFormat.JPEG)?.maxByOrNull { it.width.toLong() * it.height }
        }
        val zr = if (Build.VERSION.SDK_INT >= 30) c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let { it.lower to it.upper } else null
        val exts = if (Build.VERSION.SDK_INT >= 31 && parent == null) runCatching {
            cm.getCameraExtensionCharacteristics(id).supportedExtensions.map(::extName)
        }.getOrDefault(emptyList()) else emptyList()
        LensInfo(
            id = id, parentId = parent,
            facing = c.get(CameraCharacteristics.LENS_FACING) ?: -1,
            focalMm = focal,
            eqMm = if (diag > 0f) focal * 43.27f / diag else 0f,
            sensorMm = (ss?.width ?: 0f) to (ss?.height ?: 0f),
            pixelArray = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE),
            maxJpeg = maxJpeg, maxResJpeg = maxRes, maxRaw = maxRaw,
            hardwareLevel = levelName(c.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)),
            raw = caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW) && maxRaw != null,
            manualSensor = caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR),
            ultraHighRes = uhr && maxRes != null,
            logical = caps.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA),
            physicalIds = c.physicalCameraIds.toList().sorted(),
            zoomRange = zr,
            minFocusDiopter = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f,
            isoRange = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)?.let { it.lower to it.upper },
            exposureRangeNs = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)?.let { it.lower to it.upper },
            flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
            ois = (c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.size ?: 0) > 1,
            sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90,
            extensions = exts,
        )
    }.getOrNull()

    /** Liet ke moi camera app duoc dung, ke ca camera vat ly ben trong camera logic. */
    fun all(context: Context): List<LensInfo> {
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val out = ArrayList<LensInfo>()
        val ids = runCatching { cm.cameraIdList.toList() }.getOrDefault(emptyList())
        for (id in ids) {
            val info = describe(cm, id, null) ?: continue
            out.add(info)
            for (p in info.physicalIds) if (out.none { it.id == p && it.parentId == id }) describe(cm, p, id)?.let { out.add(it) }
        }
        return out
    }

    private fun ratioLabel(r: Float): String {
        val v = (r * 10).roundToInt() / 10f
        return (if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()) + "x"
    }

    /** Tao cac nut chon ong kinh cho mot huong (sau/truoc). */
    fun options(all: List<LensInfo>, facing: Int): List<LensOption> {
        val tops = all.filter { it.parentId == null && it.facing == facing }
        if (tops.isEmpty()) return emptyList()
        // Camera chinh: camera logic dau tien (neu co), nguoc lai camera dau tien
        val primary = tops.firstOrNull { it.logical } ?: tops.first()
        val mainEq = primary.eqMm.takeIf { it > 0f } ?: 26f
        val opts = ArrayList<LensOption>()
        val zr = primary.zoomRange
        // Cac ong kinh vat ly ma camera logic chuyen bang ty le zoom
        val physRatios = all.filter { it.parentId == primary.id && it.eqMm > 0f }.map { it.eqMm / mainEq }
        val ratios = sortedSetOf(1f)
        if (zr != null) {
            if (zr.first < 0.95f) ratios.add(zr.first)
            physRatios.filter { it > 1.4f && it <= zr.second }.forEach { ratios.add((it * 10).roundToInt() / 10f) }
            if (zr.second >= 2f && physRatios.none { it in 1.6f..2.4f }) ratios.add(2f)
        }
        ratios.forEach { opts.add(LensOption(ratioLabel(it), primary.id, it, facing)) }
        // Cac camera doc lap khac (neu nha san xuat mo rieng)
        for (t in tops) {
            if (t.id == primary.id || t.eqMm <= 0f) continue
            val r = t.eqMm / mainEq
            if (opts.none { abs(it.zoom - r) < 0.15f * maxOf(1f, r) }) opts.add(LensOption(ratioLabel(r) + "•", t.id, 1f, facing))
        }
        return opts.sortedBy { if (it.cameraId == primary.id) it.zoom else (tops.first { t -> t.id == it.cameraId }.eqMm / mainEq) }
    }

    fun report(all: List<LensInfo>): String = buildString {
        appendLine("Thiết bị: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Số camera app được dùng: ${all.count { it.parentId == null }} (+${all.count { it.parentId != null }} camera vật lý bên trong)")
        appendLine()
        for (l in all) {
            appendLine((if (l.parentId != null) "  └ Vật lý ${l.id} (thuộc ${l.parentId})" else "Camera ${l.id} · ${l.facingText}") + if (l.logical) " · LOGIC" else "")
            val ind = if (l.parentId != null) "    " else "  "
            appendLine(String.format(Locale.US, "%sTiêu cự %.2fmm ≈ %.0fmm (35mm) · cảm biến %.2f×%.2fmm", ind, l.focalMm, l.eqMm, l.sensorMm.first, l.sensorMm.second))
            appendLine("${ind}Điểm ảnh: ${mp(l.pixelArray)} · JPEG tối đa ${mp(l.maxJpeg)}" + if (l.maxResJpeg != null) " · chế độ phân giải tối đa ${mp(l.maxResJpeg)}" else "")
            appendLine("${ind}Mức phần cứng ${l.hardwareLevel} · RAW ${if (l.raw) "có ${mp(l.maxRaw)}" else "không"} · chỉnh tay ${if (l.manualSensor) "có" else "không"} · OIS ${if (l.ois) "có" else "không"} · flash ${if (l.flash) "có" else "không"}")
            l.isoRange?.let { append("${ind}ISO ${it.first}–${it.second}") }
            l.exposureRangeNs?.let { append(String.format(Locale.US, " · tốc độ %s – %.1fs", shutterText(it.first), it.second / 1e9)) }
            l.zoomRange?.let { append(String.format(Locale.US, " · zoom %.2f–%.1fx", it.first, it.second)) }
            appendLine()
            if (l.physicalIds.isNotEmpty()) appendLine("${ind}Camera vật lý: ${l.physicalIds.joinToString()}")
            if (l.extensions.isNotEmpty()) appendLine("${ind}Chế độ mở rộng của hãng: ${l.extensions.joinToString()}")
            appendLine()
        }
    }

    fun shutterText(ns: Long): String {
        val s = ns / 1e9
        return if (s >= 0.3) String.format(Locale.US, "%.1f\"", s) else "1/" + (1.0 / s).roundToInt()
    }
}
