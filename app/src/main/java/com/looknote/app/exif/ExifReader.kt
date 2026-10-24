package com.looknote.app.exif

import com.looknote.app.model.CreativeLook
import java.util.Locale
import kotlin.math.roundToInt

/** Thong tin chup doc tu EXIF (va MakerNote Sony neu co). */
data class PhotoInfo(
    val make: String? = null,
    val model: String? = null,
    val lens: String? = null,
    val focalLength: Double? = null,
    val focal35: Int? = null,
    val fNumber: Double? = null,
    val exposureTime: Double? = null,
    val iso: Int? = null,
    val exposureBias: Double? = null,
    val dateTime: String? = null,
    val sony: SonyMakerInfo? = null,
) {
    val isSony: Boolean get() = make?.contains("SONY", ignoreCase = true) == true

    fun shutterText(): String? {
        val t = exposureTime ?: return null
        if (t <= 0) return null
        return if (t >= 0.3) {
            val s = if (t == t.roundToInt().toDouble()) t.roundToInt().toString() else String.format(Locale.US, "%.1f", t)
            "$s\""
        } else "1/" + (1.0 / t).roundToInt()
    }

    fun apertureText(): String? = fNumber?.let { "f/" + trim(it) }
    fun focalText(): String? = focalLength?.let { trim(it) + "mm" }
    fun isoText(): String? = iso?.let { "ISO $it" }
    fun biasText(): String? = exposureBias?.let {
        if (it == 0.0) "±0EV" else (if (it > 0) "+" else "") + String.format(Locale.US, "%.1f", it).trimEnd('0').trimEnd('.') + "EV"
    }

    /** Dong thong so dang "35mm  f/1.8  1/250  ISO 100". */
    fun settingsLine(): String =
        listOfNotNull(focalText(), apertureText(), shutterText(), isoText()).joinToString("   ")

    fun cameraName(): String {
        val m = model?.trim().orEmpty()
        val mk = make?.trim().orEmpty()
        return when {
            m.isEmpty() -> mk
            mk.isEmpty() || m.startsWith(mk, ignoreCase = true) -> m
            else -> m
        }
    }

    companion object {
        private fun trim(v: Double): String =
            if (v == v.roundToInt().toDouble()) v.roundToInt().toString() else String.format(Locale.US, "%.1f", v)
    }
}

data class SonyMakerInfo(
    val creativeStyleRaw: String? = null,
    val look: CreativeLook? = null,
    val contrast: Int? = null,
    val saturation: Int? = null,
    val sharpness: Int? = null,
    val wbFineTune: Int? = null,
    val colorTemperature: Int? = null,
    val whiteBalanceRaw: Long? = null,
    val droRaw: Long? = null,
    val lensTypeRaw: Long? = null,
    /** Toan bo the MakerNote (ma the -> gia tri tom tat) de tham khao. */
    val rawTags: List<Pair<Int, String>> = emptyList(),
) {
    fun whiteBalanceText(): String? = when (whiteBalanceRaw) {
        null -> null
        0x00L -> "Tự động"
        0x01L -> "Nhiệt độ màu/bộ lọc"
        0x10L -> "Ánh sáng ngày"
        0x20L -> "Trời mây"
        0x30L -> "Bóng râm"
        0x40L -> "Đèn sợi đốt"
        0x50L -> "Flash"
        0x60L -> "Huỳnh quang"
        0x70L -> "Tùy chỉnh"
        0x80L -> "Dưới nước"
        else -> "Mã $whiteBalanceRaw"
    }
}

class ExifParseException(msg: String) : Exception(msg)

/**
 * Bo doc EXIF/TIFF thuan Kotlin, khong phu thuoc Android.
 * Ho tro JPEG (APP1 Exif) va tep dang TIFF (ARW).
 */
object ExifReader {

    private class Buf(val b: ByteArray, val base: Int, val little: Boolean) {
        fun u8(o: Int): Int = b[o].toInt() and 0xFF
        fun u16(o: Int): Int {
            val a = u8(o); val c = u8(o + 1)
            return if (little) a or (c shl 8) else (a shl 8) or c
        }
        fun u32(o: Int): Long {
            val v = if (little) {
                u8(o).toLong() or (u8(o + 1).toLong() shl 8) or (u8(o + 2).toLong() shl 16) or (u8(o + 3).toLong() shl 24)
            } else {
                (u8(o).toLong() shl 24) or (u8(o + 1).toLong() shl 16) or (u8(o + 2).toLong() shl 8) or u8(o + 3).toLong()
            }
            return v and 0xFFFFFFFFL
        }
        fun s32(o: Int): Int = u32(o).toInt()
        fun s16(o: Int): Int = u16(o).toShort().toInt()
        fun inRange(o: Int, len: Int): Boolean = o >= 0 && len >= 0 && o + len <= b.size
    }

    private class Entry(val tag: Int, val type: Int, val count: Long, val valueOffset: Int)

    private fun typeSize(type: Int): Int = when (type) {
        1, 2, 6, 7 -> 1
        3, 8 -> 2
        4, 9, 11 -> 4
        5, 10, 12 -> 8
        else -> 0
    }

    private fun readIfd(buf: Buf, absOffset: Int): List<Entry> {
        if (!buf.inRange(absOffset, 2)) return emptyList()
        val n = buf.u16(absOffset)
        if (n <= 0 || n > 1000 || !buf.inRange(absOffset + 2, n * 12)) return emptyList()
        val out = ArrayList<Entry>(n)
        for (i in 0 until n) {
            val e = absOffset + 2 + i * 12
            val tag = buf.u16(e)
            val type = buf.u16(e + 2)
            val count = buf.u32(e + 4)
            val size = typeSize(type).toLong() * count
            val vo = if (size in 0..4) e + 8 else buf.base + buf.u32(e + 8).toInt()
            if (typeSize(type) == 0 || size > Int.MAX_VALUE || !buf.inRange(vo, size.toInt())) continue
            out.add(Entry(tag, type, count, vo))
        }
        return out
    }

    private fun nextIfdOffset(buf: Buf, absOffset: Int): Int? {
        if (!buf.inRange(absOffset, 2)) return null
        val n = buf.u16(absOffset)
        val p = absOffset + 2 + n * 12
        if (!buf.inRange(p, 4)) return null
        val v = buf.u32(p).toInt()
        return if (v == 0) null else buf.base + v
    }

    private fun str(buf: Buf, e: Entry): String? {
        if (e.type != 2 && e.type != 7) return null
        val len = e.count.toInt()
        val bytes = buf.b.copyOfRange(e.valueOffset, e.valueOffset + len)
        val end = bytes.indexOf(0).let { if (it < 0) bytes.size else it }
        return String(bytes, 0, end, Charsets.ISO_8859_1).trim().ifEmpty { null }
    }

    private fun num(buf: Buf, e: Entry, index: Int = 0): Double? {
        val sz = typeSize(e.type)
        if (index >= e.count) return null
        val o = e.valueOffset + index * sz
        return when (e.type) {
            1, 7 -> buf.u8(o).toDouble()
            6 -> buf.b[o].toDouble()
            3 -> buf.u16(o).toDouble()
            8 -> buf.s16(o).toDouble()
            4 -> buf.u32(o).toDouble()
            9 -> buf.s32(o).toDouble()
            5 -> { val d = buf.u32(o + 4); if (d == 0L) null else buf.u32(o).toDouble() / d }
            10 -> { val d = buf.s32(o + 4); if (d == 0) null else buf.s32(o).toDouble() / d }
            11 -> java.lang.Float.intBitsToFloat(buf.s32(o)).toDouble()
            12 -> {
                val hi = buf.u32(if (buf.little) o + 4 else o)
                val lo = buf.u32(if (buf.little) o else o + 4)
                java.lang.Double.longBitsToDouble((hi shl 32) or lo)
            }
            else -> null
        }
    }

    private fun describe(buf: Buf, e: Entry): String = when (e.type) {
        2 -> str(buf, e) ?: ""
        7 -> if (e.count <= 16) (0 until e.count.toInt()).joinToString(" ") { "%02X".format(buf.u8(e.valueOffset + it)) }
             else "[${e.count} byte]"
        else -> {
            val n = minOf(e.count.toInt(), 6)
            val vals = (0 until n).mapNotNull { num(buf, e, it) }.joinToString(", ") {
                if (it == Math.floor(it) && !it.isInfinite()) it.toLong().toString() else String.format(Locale.US, "%.4f", it)
            }
            if (e.count > 6) "$vals …(${e.count})" else vals
        }
    }

    /** Tim vi tri bat dau TIFF header trong tep. */
    fun findTiffStart(data: ByteArray): Int {
        if (data.size < 8) throw ExifParseException("Tệp quá nhỏ")
        val b0 = data[0].toInt() and 0xFF
        val b1 = data[1].toInt() and 0xFF
        if ((b0 == 0x49 && b1 == 0x49) || (b0 == 0x4D && b1 == 0x4D)) return 0
        if (b0 != 0xFF || b1 != 0xD8) throw ExifParseException("Không phải tệp JPEG hoặc TIFF/ARW")
        var p = 2
        while (p + 4 <= data.size) {
            if ((data[p].toInt() and 0xFF) != 0xFF) { p++; continue }
            val marker = data[p + 1].toInt() and 0xFF
            if (marker == 0xD8 || marker == 0x01 || marker in 0xD0..0xD7) { p += 2; continue }
            if (marker == 0xDA || marker == 0xD9) break
            val len = ((data[p + 2].toInt() and 0xFF) shl 8) or (data[p + 3].toInt() and 0xFF)
            if (marker == 0xE1 && p + 10 <= data.size &&
                data[p + 4] == 'E'.code.toByte() && data[p + 5] == 'x'.code.toByte() &&
                data[p + 6] == 'i'.code.toByte() && data[p + 7] == 'f'.code.toByte()
            ) return p + 10
            p += 2 + len
        }
        throw ExifParseException("Ảnh không có dữ liệu EXIF")
    }

    fun parse(data: ByteArray): PhotoInfo {
        val start = findTiffStart(data)
        if (start + 8 > data.size) throw ExifParseException("EXIF hỏng")
        val little = when {
            data[start] == 'I'.code.toByte() && data[start + 1] == 'I'.code.toByte() -> true
            data[start] == 'M'.code.toByte() && data[start + 1] == 'M'.code.toByte() -> false
            else -> throw ExifParseException("Thứ tự byte EXIF không hợp lệ")
        }
        val buf = Buf(data, start, little)
        val ifd0Off = start + buf.u32(start + 4).toInt()
        val ifd0 = readIfd(buf, ifd0Off)
        if (ifd0.isEmpty()) throw ExifParseException("Không đọc được IFD0")

        fun List<Entry>.find(tag: Int) = firstOrNull { it.tag == tag }

        val make = ifd0.find(0x010F)?.let { str(buf, it) }
        val model = ifd0.find(0x0110)?.let { str(buf, it) }
        var dateTime = ifd0.find(0x0132)?.let { str(buf, it) }

        val exifPtr = ifd0.find(0x8769)?.let { num(buf, it)?.toLong() }
        val exif = if (exifPtr != null) readIfd(buf, start + exifPtr.toInt()) else emptyList()

        val exposure = exif.find(0x829A)?.let { num(buf, it) }
        val fNumber = exif.find(0x829D)?.let { num(buf, it) }
        val iso = (exif.find(0x8827) ?: exif.find(0x8833))?.let { num(buf, it)?.toInt() }
        val bias = exif.find(0x9204)?.let { num(buf, it) }
        val focal = exif.find(0x920A)?.let { num(buf, it) }
        val focal35 = exif.find(0xA405)?.let { num(buf, it)?.toInt() }?.takeIf { it > 0 }
        val lens = exif.find(0xA434)?.let { str(buf, it) }
        exif.find(0x9003)?.let { str(buf, it) }?.let { dateTime = it }

        val sony = if (make?.contains("SONY", ignoreCase = true) == true) {
            exif.find(0x927C)?.let { parseSonyMakerNote(buf, it) }
        } else null

        return PhotoInfo(
            make = make, model = model, lens = lens,
            focalLength = focal, focal35 = focal35, fNumber = fNumber,
            exposureTime = exposure, iso = iso, exposureBias = bias,
            dateTime = dateTime, sony = sony,
        )
    }

    private fun parseSonyMakerNote(buf: Buf, mn: Entry): SonyMakerInfo? {
        val o = mn.valueOffset
        val header = if (buf.inRange(o, 12)) String(buf.b, o, 12, Charsets.ISO_8859_1) else ""
        val ifdStart = when {
            header.startsWith("SONY DSC") || header.startsWith("SONY CAM") ||
                header.startsWith("SONY MOBILE") || header.startsWith("SONY PI") -> o + 12
            else -> o
        }
        val tags = readIfd(buf, ifdStart)
        if (tags.isEmpty()) return null

        fun t(tag: Int) = tags.firstOrNull { it.tag == tag }
        val cs = t(0xB020)?.let { str(buf, it) }
        fun sint(tag: Int): Int? = t(tag)?.let { num(buf, it)?.toInt() }
        fun ulong(tag: Int): Long? = t(tag)?.let { num(buf, it)?.toLong() }

        return SonyMakerInfo(
            creativeStyleRaw = cs,
            look = CreativeLook.fromMakerNote(cs),
            contrast = sint(0x2004),
            saturation = sint(0x2005),
            sharpness = sint(0x2006),
            wbFineTune = sint(0x0112),
            colorTemperature = ulong(0xB021)?.toInt()?.takeIf { it > 0 },
            whiteBalanceRaw = ulong(0x0115),
            droRaw = ulong(0xB025),
            lensTypeRaw = ulong(0xB027),
            rawTags = tags.map { it.tag to describe(buf, it) },
        )
    }
}
