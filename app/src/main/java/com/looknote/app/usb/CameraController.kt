package com.looknote.app.usb

import android.content.Context
import android.hardware.usb.UsbDevice
import com.looknote.app.ptp.DeviceInfo
import com.looknote.app.ptp.PtpSession
import com.looknote.app.ptp.SonyProp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Trang thai ket noi may anh qua USB (thu nghiem). */
data class CameraState(
    val devices: List<UsbDevice> = emptyList(),
    val connected: Boolean = false,
    val busy: Boolean = false,
    val info: DeviceInfo? = null,
    val sonyCodes: List<Int> = emptyList(),
    val props: List<SonyProp> = emptyList(),
    val snapshot: Map<Int, String>? = null,
    val log: List<String> = emptyList(),
    val rawSize: Int = 0,
)

class CameraController(private val appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(CameraState())
    val state: StateFlow<CameraState> = _state.asStateFlow()
    private var session: PtpSession? = null
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun log(msg: String) {
        val line = time.format(Date()) + "  " + msg
        _state.value = _state.value.copy(log = (_state.value.log + line).takeLast(200))
    }

    private fun work(name: String, block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true)
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                log("$name lỗi: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    fun refreshDevices() {
        val list = UsbCamera.findCameras(appContext)
        _state.value = _state.value.copy(devices = list)
        log(if (list.isEmpty()) "Không thấy máy ảnh. Kiểm tra cáp và chế độ PC Remote." else "Tìm thấy ${list.size} thiết bị")
    }

    fun connect(dev: UsbDevice) {
        if (!UsbCamera.hasPermission(appContext, dev)) {
            log("Đang xin quyền truy cập USB…")
            UsbCamera.requestPermission(appContext, dev) { granted ->
                if (granted) connect(dev) else log("Quyền USB bị từ chối")
            }
            return
        }
        work("Kết nối") {
            disconnectInternal()
            val t = UsbCamera.open(appContext, dev)
            val s = PtpSession(t) { log(it) }
            session = s
            val info = s.openSession()
            log("Đã mở phiên: ${info.manufacturer} ${info.model} (FW ${info.deviceVersion})")
            _state.value = _state.value.copy(connected = true, info = info)
            if (info.operations.contains(0x9201)) {
                val codes = s.sonyHandshake()
                _state.value = _state.value.copy(sonyCodes = codes)
                readPropsInternal()
            } else {
                log("Máy không khai báo lệnh điều khiển Sony (0x9201). Kiểm tra chế độ PC Remote.")
            }
        }
    }

    private fun readPropsInternal() {
        val s = session ?: return
        val (props, raw) = s.sonyReadAllProps()
        _state.value = _state.value.copy(props = props, rawSize = raw.size)
        log("Đọc được ${props.size} thuộc tính (${raw.size} byte)")
    }

    fun readProps() = work("Đọc thuộc tính") { readPropsInternal() }

    /** Luu gia tri hien tai lam moc de so sanh sau khi doi cai dat tren may. */
    fun takeSnapshot() {
        val snap = _state.value.props.associate { it.code to it.valueText() }
        _state.value = _state.value.copy(snapshot = snap)
        log("Đã lưu mốc ${snap.size} thuộc tính. Hãy đổi một cài đặt trên máy rồi bấm Đọc lại.")
    }

    fun clearSnapshot() {
        _state.value = _state.value.copy(snapshot = null)
    }

    fun setProp(prop: SonyProp, value: Long) = work("Ghi thuộc tính") {
        val s = session ?: throw IllegalStateException("Chưa kết nối")
        val r = s.sonySetProp(prop.code, prop.dataType, value)
        log("Ghi 0x%04X = %d → %s".format(prop.code, value, if (r.ok) "OK" else "mã 0x%04X".format(r.code)))
        Thread.sleep(300)
        readPropsInternal()
    }

    fun disconnect() = work("Ngắt kết nối") { disconnectInternal(); log("Đã ngắt kết nối") }

    private fun disconnectInternal() {
        session?.let { runCatching { it.close() } }
        session = null
        _state.value = _state.value.copy(connected = false, info = null, props = emptyList(), sonyCodes = emptyList())
    }

    fun exportReport(): String = buildString {
        val st = _state.value
        appendLine("LookNote – báo cáo thuộc tính máy ảnh")
        st.info?.let { appendLine("Máy: ${it.manufacturer} ${it.model}, FW ${it.deviceVersion}, giao thức Sony ${session?.sonyProtocol ?: 0}") }
        st.props.forEach { p ->
            val before = st.snapshot?.get(p.code)
            val changed = before != null && before != p.valueText()
            appendLine(
                "0x%04X  %-12s %s  %s%s".format(
                    p.code,
                    com.looknote.app.ptp.PtpCodes.dataTypeName(p.dataType),
                    if (p.writable) "RW" else "R ",
                    p.valueText(),
                    if (changed) "   (trước: $before)" else "",
                )
            )
        }
    }
}
