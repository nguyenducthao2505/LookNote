package com.looknote.app.ptp

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Phien PTP tren mot kenh truyen bat ky (USB hien tai).
 * Moi giao dich: gui lenh -> (gui/nhan du lieu) -> nhan phan hoi.
 */
class PtpSession(private val t: PtpTransport, private val log: (String) -> Unit = {}) {
    private var tid = 0L
    var sonyProtocol: Int = 0
        private set

    @Synchronized
    fun transaction(code: Int, params: List<Long> = emptyList(), dataOut: ByteArray? = null, timeoutMs: Int = 5000): PtpResult {
        tid++
        t.send(PtpWriter.container(PtpCodes.TYPE_COMMAND, code, tid, params))
        if (dataOut != null) t.send(PtpWriter.container(PtpCodes.TYPE_DATA, code, tid, payload = dataOut))

        var data: ByteArray? = null
        repeat(4) {
            val c = t.receive(timeoutMs)
            if (c.size < 12) throw PtpException("Gói tin quá ngắn (${c.size} byte)")
            val bb = ByteBuffer.wrap(c).order(ByteOrder.LITTLE_ENDIAN)
            val len = bb.getInt(0)
            val type = bb.getShort(4).toInt() and 0xFFFF
            val rcode = bb.getShort(6).toInt() and 0xFFFF
            when (type) {
                PtpCodes.TYPE_DATA -> data = c.copyOfRange(12, minOf(len, c.size))
                PtpCodes.TYPE_RESPONSE -> {
                    val n = (minOf(len, c.size) - 12) / 4
                    val ps = List(n) { bb.getInt(12 + it * 4).toLong() and 0xFFFFFFFFL }
                    log("0x%04X → %s%s".format(code, PtpCodes.responseName(rcode), data?.let { " (${it.size} byte)" } ?: ""))
                    return PtpResult(rcode, ps, data)
                }
                else -> log("Bỏ qua gói loại $type")
            }
        }
        throw PtpException("Không nhận được phản hồi cho lệnh 0x%04X".format(code))
    }

    fun openSession(): DeviceInfo {
        val info = transaction(PtpCodes.OC_GET_DEVICE_INFO)
        val di = info.data?.let { DeviceInfo.parse(it) } ?: throw PtpException("Máy không trả DeviceInfo")
        val r = transaction(PtpCodes.OC_OPEN_SESSION, listOf(1L))
        if (!r.ok && r.code != PtpCodes.RC_SESSION_ALREADY_OPEN) {
            throw PtpException("Không mở được phiên: " + PtpCodes.responseName(r.code))
        }
        return di
    }

    /**
     * Bat tay che do dieu khien tu xa cua Sony (PC Remote).
     * Tra ve danh sach ma thuoc tinh mo rong ma may khai bao.
     */
    fun sonyHandshake(): List<Int> {
        transaction(PtpCodes.OC_SONY_SDIO_CONNECT, listOf(1L, 0L, 0L))
        transaction(PtpCodes.OC_SONY_SDIO_CONNECT, listOf(2L, 0L, 0L))
        var codes: List<Int> = emptyList()
        for (ver in listOf(0x12CL, 0xC8L)) {
            val r = transaction(PtpCodes.OC_SONY_GET_EXT_DEVICE_INFO, listOf(ver))
            val d = r.data
            if (r.ok && d != null && d.size >= 2) {
                val rd = PtpReader(d)
                sonyProtocol = rd.u16()
                codes = runCatching { rd.u16Array() }.getOrDefault(emptyList())
                log("Giao thức Sony: $sonyProtocol, ${codes.size} thuộc tính")
                break
            }
        }
        transaction(PtpCodes.OC_SONY_SDIO_CONNECT, listOf(3L, 0L, 0L))
        return codes
    }

    fun sonyReadAllProps(): Pair<List<SonyProp>, ByteArray> {
        var r = transaction(PtpCodes.OC_SONY_GET_ALL_EXT_PROP_INFO, timeoutMs = 8000)
        if (!r.ok || r.data == null) {
            r = transaction(PtpCodes.OC_SONY_GET_ALL_EXT_PROP_INFO, listOf(1L), timeoutMs = 8000)
        }
        val d = r.data ?: throw PtpException("Máy không trả danh sách thuộc tính (" + PtpCodes.responseName(r.code) + ")")
        return SonyPropParser.parse(d, preferDoubleEnum = sonyProtocol >= 300) to d
    }

    fun sonySetProp(code: Int, dataType: Int, value: Long): PtpResult =
        transaction(PtpCodes.OC_SONY_SET_EXT_PROP_VALUE, listOf(code.toLong()), PtpWriter.encodeValue(dataType, value))

    fun sonyControl(code: Int, dataType: Int, value: Long): PtpResult =
        transaction(PtpCodes.OC_SONY_CONTROL_DEVICE, listOf(code.toLong()), PtpWriter.encodeValue(dataType, value))

    fun close() {
        runCatching { transaction(PtpCodes.OC_CLOSE_SESSION, timeoutMs = 1500) }
        t.close()
    }
}
