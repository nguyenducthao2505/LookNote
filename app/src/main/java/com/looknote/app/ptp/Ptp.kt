package com.looknote.app.ptp

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Kenh truyen PTP: gui/nhan mot "container" hoan chinh. */
interface PtpTransport {
    fun send(bytes: ByteArray)
    /** Nhan tron mot container (header 12 byte + du lieu). */
    fun receive(timeoutMs: Int): ByteArray
    fun close()
}

class PtpException(msg: String) : Exception(msg)

object PtpCodes {
    const val TYPE_COMMAND = 1
    const val TYPE_DATA = 2
    const val TYPE_RESPONSE = 3

    const val OC_GET_DEVICE_INFO = 0x1001
    const val OC_OPEN_SESSION = 0x1002
    const val OC_CLOSE_SESSION = 0x1003

    // Lenh mo rong cua Sony (theo tai lieu cong dong/libgphoto2)
    const val OC_SONY_SDIO_CONNECT = 0x9201
    const val OC_SONY_GET_EXT_DEVICE_INFO = 0x9202
    const val OC_SONY_SET_EXT_PROP_VALUE = 0x9205
    const val OC_SONY_CONTROL_DEVICE = 0x9207
    const val OC_SONY_GET_ALL_EXT_PROP_INFO = 0x9209

    const val RC_OK = 0x2001
    const val RC_SESSION_ALREADY_OPEN = 0x201E

    fun responseName(code: Int): String = when (code) {
        0x2001 -> "OK"
        0x2002 -> "General Error"
        0x2003 -> "Session Not Open"
        0x2005 -> "Operation Not Supported"
        0x2006 -> "Parameter Not Supported"
        0x2019 -> "Device Busy"
        0x201A -> "Invalid Parent Object"
        0x201B -> "Invalid DeviceProp Format"
        0x201C -> "Invalid DeviceProp Value"
        0x201D -> "Invalid Parameter"
        0x201E -> "Session Already Open"
        else -> "0x%04X".format(code)
    }

    /** Ten goi y cho mot so thuoc tinh (co the khac giua cac doi may). */
    fun propHint(code: Int): String? = when (code) {
        0x5005 -> "White Balance"
        0x5007 -> "F-Number"
        0x500A -> "Focus Mode"
        0x500B -> "Metering Mode"
        0x500C -> "Flash Mode"
        0x500E -> "Exposure Program"
        0x5010 -> "Exposure Compensation"
        0x5013 -> "Drive Mode"
        0xD20D -> "Shutter Speed (gợi ý)"
        0xD218 -> "Battery Level (gợi ý)"
        0xD21E -> "ISO (gợi ý)"
        else -> null
    }

    fun dataTypeName(t: Int): String = when (t) {
        0x0001 -> "INT8"; 0x0002 -> "UINT8"; 0x0003 -> "INT16"; 0x0004 -> "UINT16"
        0x0005 -> "INT32"; 0x0006 -> "UINT32"; 0x0007 -> "INT64"; 0x0008 -> "UINT64"
        0xFFFF -> "STR"
        else -> if (t and 0x4000 != 0) "ARRAY<" + dataTypeName(t and 0x0FFF) + ">" else "0x%04X".format(t)
    }
}

data class PtpResult(val code: Int, val params: List<Long>, val data: ByteArray?) {
    val ok: Boolean get() = code == PtpCodes.RC_OK
}

/** Doc du lieu little-endian theo chuan PTP. */
class PtpReader(val b: ByteArray, var pos: Int = 0) {
    fun remaining() = b.size - pos
    private fun need(n: Int) { if (pos + n > b.size) throw PtpException("Dữ liệu PTP bị cụt tại $pos (+$n/${b.size})") }
    fun u8(): Int { need(1); return b[pos++].toInt() and 0xFF }
    fun s8(): Int { need(1); return b[pos++].toInt() }
    fun u16(): Int { need(2); val v = (b[pos].toInt() and 0xFF) or ((b[pos + 1].toInt() and 0xFF) shl 8); pos += 2; return v }
    fun s16(): Int = u16().toShort().toInt()
    fun u32(): Long {
        need(4)
        var v = 0L
        for (i in 0 until 4) v = v or ((b[pos + i].toLong() and 0xFF) shl (8 * i))
        pos += 4
        return v
    }
    fun s32(): Long = u32().toInt().toLong()
    fun u64(): Long {
        need(8)
        var v = 0L
        for (i in 0 until 8) v = v or ((b[pos + i].toLong() and 0xFF) shl (8 * i))
        pos += 8
        return v
    }
    fun string(): String {
        val n = u8()
        if (n == 0) return ""
        need(n * 2)
        val sb = StringBuilder()
        for (i in 0 until n) {
            val c = u16()
            if (c != 0) sb.append(c.toChar())
        }
        return sb.toString()
    }
    fun u16Array(): List<Int> { val n = u32(); if (n > 100000) throw PtpException("Mảng quá dài"); return List(n.toInt()) { u16() } }

    /** Doc mot gia tri theo kieu du lieu PTP. Tra ve Long, String hoac List<Long>. */
    fun value(type: Int): Any = when (type) {
        0x0001 -> s8().toLong()
        0x0002 -> u8().toLong()
        0x0003 -> s16().toLong()
        0x0004 -> u16().toLong()
        0x0005 -> s32()
        0x0006 -> u32()
        0x0007, 0x0008 -> u64()
        0xFFFF -> string()
        else -> {
            if (type and 0x4000 != 0) {
                val n = u32()
                if (n > 10000) throw PtpException("Mảng quá dài")
                List(n.toInt()) { (value(type and 0x0FFF) as? Long) ?: 0L }
            } else throw PtpException("Kiểu dữ liệu chưa hỗ trợ: 0x%04X".format(type))
        }
    }
}

object PtpWriter {
    fun container(type: Int, code: Int, transactionId: Long, params: List<Long> = emptyList(), payload: ByteArray? = null): ByteArray {
        val bodyLen = if (type == PtpCodes.TYPE_DATA) (payload?.size ?: 0) else params.size * 4
        val bb = ByteBuffer.allocate(12 + bodyLen).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(12 + bodyLen)
        bb.putShort(type.toShort())
        bb.putShort(code.toShort())
        bb.putInt(transactionId.toInt())
        if (type == PtpCodes.TYPE_DATA) payload?.let { bb.put(it) } else params.forEach { bb.putInt(it.toInt()) }
        return bb.array()
    }

    /** Ma hoa gia tri so theo kieu du lieu PTP. */
    fun encodeValue(type: Int, value: Long): ByteArray {
        val size = when (type) {
            0x0001, 0x0002 -> 1
            0x0003, 0x0004 -> 2
            0x0005, 0x0006 -> 4
            0x0007, 0x0008 -> 8
            else -> throw PtpException("Chỉ hỗ trợ ghi giá trị số (kiểu ${PtpCodes.dataTypeName(type)})")
        }
        val out = ByteArrayOutputStream()
        for (i in 0 until size) out.write(((value shr (8 * i)) and 0xFF).toInt())
        return out.toByteArray()
    }
}

data class DeviceInfo(
    val standardVersion: Int,
    val vendorExtensionId: Long,
    val vendorExtensionVersion: Int,
    val vendorExtensionDesc: String,
    val functionalMode: Int,
    val operations: List<Int>,
    val events: List<Int>,
    val deviceProps: List<Int>,
    val manufacturer: String,
    val model: String,
    val deviceVersion: String,
    val serialNumber: String,
) {
    companion object {
        fun parse(data: ByteArray): DeviceInfo {
            val r = PtpReader(data)
            val std = r.u16()
            val vext = r.u32()
            val vver = r.u16()
            val vdesc = r.string()
            val fmode = r.u16()
            val ops = r.u16Array()
            val evs = r.u16Array()
            val props = r.u16Array()
            r.u16Array() // capture formats
            r.u16Array() // image formats
            val manu = r.string()
            val model = r.string()
            val ver = r.string()
            val serial = runCatching { r.string() }.getOrDefault("")
            return DeviceInfo(std, vext, vver, vdesc, fmode, ops, evs, props, manu, model, ver, serial)
        }
    }
}

/** Mo ta mot thuoc tinh may anh Sony (SDIO). */
data class SonyProp(
    val code: Int,
    val dataType: Int,
    val getSet: Int,
    val isEnabled: Int,
    val factory: Any?,
    val current: Any?,
    val formFlag: Int,
    val range: Triple<Any, Any, Any>? = null,
    val enumValues: List<Any>? = null,
) {
    val writable: Boolean get() = getSet == 1

    fun valueText(v: Any? = current): String = when (v) {
        null -> "—"
        is Long -> {
            val hex = when (dataType) {
                0x0002, 0x0004, 0x0006, 0x0008 -> " (0x" + java.lang.Long.toHexString(v).uppercase() + ")"
                else -> ""
            }
            "$v$hex"
        }
        is String -> "\"$v\""
        is List<*> -> v.joinToString(prefix = "[", postfix = "]")
        else -> v.toString()
    }
}

object SonyPropParser {
    /**
     * Phan tich du lieu tra ve tu lenh 0x9209.
     * Dinh dang chinh xac khac nhau giua cac doi giao thuc, nen thu hai kieu:
     * mot danh sach liet ke, hoac hai danh sach (doi moi).
     */
    fun parse(data: ByteArray, preferDoubleEnum: Boolean): List<SonyProp> {
        val first = runCatching { parseWith(data, preferDoubleEnum) }.getOrNull()
        if (first != null) return first
        val second = runCatching { parseWith(data, !preferDoubleEnum) }.getOrNull()
        if (second != null) return second
        // Tra ve phan doc duoc (khong kiem tra so luong)
        return parseLenient(data, preferDoubleEnum)
    }

    private fun parseWith(data: ByteArray, doubleEnum: Boolean): List<SonyProp>? {
        val r = PtpReader(data)
        val count = r.u64()
        if (count <= 0 || count > 2000) return null
        val out = ArrayList<SonyProp>()
        for (i in 0 until count.toInt()) out.add(readOne(r, doubleEnum))
        return if (r.remaining() == 0) out else null
    }

    private fun parseLenient(data: ByteArray, doubleEnum: Boolean): List<SonyProp> {
        val r = PtpReader(data)
        val out = ArrayList<SonyProp>()
        runCatching {
            val count = r.u64()
            for (i in 0 until minOf(count, 2000L).toInt()) out.add(readOne(r, doubleEnum))
        }
        return out
    }

    private fun readOne(r: PtpReader, doubleEnum: Boolean): SonyProp {
        val code = r.u16()
        val type = r.u16()
        val getSet = r.u8()
        val enabled = r.u8()
        val factory = r.value(type)
        val current = r.value(type)
        val form = r.u8()
        var range: Triple<Any, Any, Any>? = null
        var enums: List<Any>? = null
        when (form) {
            0x01 -> range = Triple(r.value(type), r.value(type), r.value(type))
            0x02 -> {
                val n = r.u16()
                val list = List(n) { r.value(type) }
                enums = list
                if (doubleEnum) {
                    val n2 = r.u16()
                    val list2 = List(n2) { r.value(type) }
                    if (list2.isNotEmpty()) enums = list2
                }
            }
        }
        return SonyProp(code, type, getSet, enabled, factory, current, form, range, enums)
    }
}
