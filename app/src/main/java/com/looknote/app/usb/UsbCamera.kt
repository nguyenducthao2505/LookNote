package com.looknote.app.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.looknote.app.ptp.PtpException
import com.looknote.app.ptp.PtpTransport
import java.io.ByteArrayOutputStream

/** Kenh PTP qua cap USB (Android USB Host). */
class UsbPtpTransport(
    private val conn: UsbDeviceConnection,
    private val intf: UsbInterface,
    private val epIn: UsbEndpoint,
    private val epOut: UsbEndpoint,
) : PtpTransport {
    private val readBuf = ByteArray(256 * 1024)

    override fun send(bytes: ByteArray) {
        var off = 0
        while (off < bytes.size) {
            val n = conn.bulkTransfer(epOut, bytes, off, minOf(bytes.size - off, 16384), 5000)
            if (n < 0) throw PtpException("Gửi dữ liệu USB thất bại")
            off += n
        }
        val mps = epOut.maxPacketSize
        if (mps > 0 && bytes.isNotEmpty() && bytes.size % mps == 0) {
            conn.bulkTransfer(epOut, ByteArray(0), 0, 1000) // goi rong ket thuc
        }
    }

    override fun receive(timeoutMs: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var expected = -1
        var emptyReads = 0
        while (true) {
            val n = conn.bulkTransfer(epIn, readBuf, readBuf.size, timeoutMs)
            if (n < 0) throw PtpException("Hết thời gian chờ máy ảnh phản hồi")
            if (n == 0) {
                if (++emptyReads > 5) throw PtpException("Máy ảnh không gửi dữ liệu")
                continue
            }
            out.write(readBuf, 0, n)
            if (expected < 0 && out.size() >= 4) {
                val b = out.toByteArray()
                expected = (b[0].toInt() and 0xFF) or ((b[1].toInt() and 0xFF) shl 8) or
                    ((b[2].toInt() and 0xFF) shl 16) or ((b[3].toInt() and 0xFF) shl 24)
                if (expected < 12 || expected > 64 * 1024 * 1024) throw PtpException("Độ dài gói tin không hợp lệ: $expected")
            }
            if (expected > 0 && out.size() >= expected) break
        }
        return out.toByteArray()
    }

    override fun close() {
        runCatching { conn.releaseInterface(intf) }
        runCatching { conn.close() }
    }
}

object UsbCamera {
    private const val ACTION_PERMISSION = "com.looknote.app.USB_PERMISSION"
    const val SONY_VENDOR_ID = 0x054C

    fun manager(ctx: Context): UsbManager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager

    private fun ptpInterface(dev: UsbDevice): UsbInterface? {
        val all = (0 until dev.interfaceCount).map { dev.getInterface(it) }
        return all.firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE }
            ?: all.firstOrNull { i ->
                val eps = (0 until i.endpointCount).map { i.getEndpoint(it) }
                eps.any { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_IN } &&
                    eps.any { it.type == UsbConstants.USB_ENDPOINT_XFER_BULK && it.direction == UsbConstants.USB_DIR_OUT }
            }
    }

    fun findCameras(ctx: Context): List<UsbDevice> =
        manager(ctx).deviceList.values.filter { it.vendorId == SONY_VENDOR_ID || ptpInterface(it)?.interfaceClass == UsbConstants.USB_CLASS_STILL_IMAGE }

    fun describe(dev: UsbDevice): String {
        val name = listOfNotNull(dev.manufacturerName, dev.productName).joinToString(" ").ifBlank { dev.deviceName }
        return "$name (VID %04X, PID %04X)".format(dev.vendorId, dev.productId)
    }

    fun hasPermission(ctx: Context, dev: UsbDevice) = manager(ctx).hasPermission(dev)

    fun requestPermission(ctx: Context, dev: UsbDevice, onResult: (Boolean) -> Unit) {
        val app = ctx.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.action != ACTION_PERMISSION) return
                runCatching { app.unregisterReceiver(this) }
                onResult(intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) || manager(app).hasPermission(dev))
            }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter(ACTION_PERMISSION), ContextCompat.RECEIVER_NOT_EXPORTED)
        val intent = Intent(ACTION_PERMISSION).setPackage(app.packageName)
        val pi = PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager(app).requestPermission(dev, pi)
    }

    fun open(ctx: Context, dev: UsbDevice): UsbPtpTransport {
        val intf = ptpInterface(dev) ?: throw PtpException("Thiết bị không có giao diện PTP. Hãy đặt máy ảnh ở chế độ PC Remote.")
        val conn = manager(ctx).openDevice(dev) ?: throw PtpException("Không mở được kết nối USB (chưa cấp quyền?)")
        if (!conn.claimInterface(intf, true)) {
            conn.close()
            throw PtpException("Không chiếm được giao diện USB – có thể ứng dụng khác đang dùng máy ảnh")
        }
        var epIn: UsbEndpoint? = null
        var epOut: UsbEndpoint? = null
        for (i in 0 until intf.endpointCount) {
            val e = intf.getEndpoint(i)
            if (e.type != UsbConstants.USB_ENDPOINT_XFER_BULK) continue
            if (e.direction == UsbConstants.USB_DIR_IN) epIn = e else epOut = e
        }
        if (epIn == null || epOut == null) {
            conn.releaseInterface(intf); conn.close()
            throw PtpException("Không tìm thấy cổng dữ liệu USB")
        }
        return UsbPtpTransport(conn, intf, epIn, epOut)
    }
}
