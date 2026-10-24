package com.looknote.app.model

import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/** Cac Creative Look cua may anh Sony Alpha (theo menu may). */
enum class CreativeLook(val code: String, val label: String, val note: String) {
    ST("ST", "Standard", "Cân bằng, dùng cho đa số cảnh"),
    PT("PT", "Portrait", "Tông da mềm, nhẹ nhàng"),
    NT("NT", "Neutral", "Bão hòa và độ nét thấp, hợp hậu kỳ"),
    VV("VV", "Vivid", "Màu rực, tương phản cao"),
    VV2("VV2", "Vivid 2", "Rực nhưng giữ sắc độ trong trẻo"),
    FL("FL", "Film", "Tương phản dịu, trời xanh trong, cảm giác điện ảnh"),
    FL2("FL2", "Film 2", "Biến thể FL (chỉ có trên một số máy đời mới)"),
    FL3("FL3", "Film 3", "Biến thể FL (chỉ có trên một số máy đời mới)"),
    IN("IN", "Instant", "Tương phản thấp, màu trầm, phẳng"),
    SH("SH", "Soft Highkey", "Sáng, trong, nhẹ nhàng"),
    BW("BW", "Black & White", "Đen trắng"),
    SE("SE", "Sepia", "Nâu cổ điển");

    companion object {
        fun fromCode(code: String?): CreativeLook? =
            entries.firstOrNull { it.code.equals(code?.trim(), ignoreCase = true) }

        /** Anh xa chuoi CreativeStyle trong MakerNote Sony sang Creative Look. */
        fun fromMakerNote(raw: String?): CreativeLook? {
            if (raw.isNullOrBlank()) return null
            val k = raw.trim().replace(" ", "").replace("&", "").lowercase()
            return when (k) {
                "standard", "st" -> ST
                "portrait", "pt" -> PT
                "neutral", "nt" -> NT
                "vivid", "vv" -> VV
                "vivid2", "vv2" -> VV2
                "fl", "film" -> FL
                "fl2" -> FL2
                "fl3" -> FL3
                "in", "instant" -> IN
                "sh", "softhighkey" -> SH
                "bw", "blackwhite", "b/w" -> BW
                "sepia", "se" -> SE
                else -> null
            }
        }
    }
}

/** 8 thong so phu cua Creative Look, dung thu tu trong menu may anh. */
enum class LookParam(val key: String, val label: String, val min: Int, val max: Int, val default: Int) {
    CONTRAST("contrast", "Tương phản", -9, 9, 0),
    HIGHLIGHTS("highlights", "Vùng sáng", -9, 9, 0),
    SHADOWS("shadows", "Vùng tối", -9, 9, 0),
    FADE("fade", "Phai màu", 0, 9, 0),
    SATURATION("saturation", "Bão hòa", -9, 9, 0),
    SHARPNESS("sharpness", "Độ nét", -9, 9, 0),
    SHARPNESS_RANGE("sharpnessRange", "Phạm vi nét", -4, 4, 0),
    CLARITY("clarity", "Độ trong", -9, 9, 0);

    fun format(v: Int): String = when {
        this == FADE -> v.toString()
        v > 0 -> "+$v"
        else -> v.toString()
    }

    companion object {
        fun fromKey(k: String): LookParam? = entries.firstOrNull { it.key == k }
    }
}

enum class WhiteBalance(val key: String, val label: String, val short: String) {
    AUTO("auto", "Tự động (AWB)", "AWB"),
    DAYLIGHT("daylight", "Ánh sáng ngày", "Daylight"),
    SHADE("shade", "Bóng râm", "Shade"),
    CLOUDY("cloudy", "Trời mây", "Cloudy"),
    INCANDESCENT("incandescent", "Đèn sợi đốt", "Incandescent"),
    FLUOR_WARM("fluorWarm", "Huỳnh quang: trắng ấm", "Fluor. Warm"),
    FLUOR_COOL("fluorCool", "Huỳnh quang: trắng lạnh", "Fluor. Cool"),
    FLUOR_DAY_WHITE("fluorDayWhite", "Huỳnh quang: trắng ngày", "Fluor. Day White"),
    FLUOR_DAYLIGHT("fluorDaylight", "Huỳnh quang: ánh sáng ngày", "Fluor. Daylight"),
    FLASH("flash", "Đèn flash", "Flash"),
    UNDERWATER("underwater", "Dưới nước tự động", "Underwater"),
    KELVIN("kelvin", "Nhiệt độ màu (K)", "K"),
    CUSTOM("custom", "Tùy chỉnh", "Custom");

    companion object {
        fun fromKey(k: String?): WhiteBalance = entries.firstOrNull { it.key == k } ?: AUTO
    }
}

/** Loai cong thuc: Creative Look hoac Picture Profile. */
enum class RecipeKind(val key: String, val label: String) {
    CREATIVE_LOOK("cl", "Creative Look"),
    PICTURE_PROFILE("pp", "Picture Profile");

    companion object {
        fun fromKey(k: String?): RecipeKind = entries.firstOrNull { it.key == k } ?: CREATIVE_LOOK
    }
}

/** Thong so Picture Profile (PP) cua Sony. Gia tri so theo thang cua may. */
data class PictureProfile(
    val slot: String = "",
    val gamma: String = "",
    val blackGammaRange: String = "",
    val blackGammaLevel: Int = 0,
    val blackLevel: Int = 0,
    val kneeMode: String = "Manual",
    val kneePoint: String = "",
    val kneeSlope: Int = 0,
    val colorMode: String = "",
    val saturation: Int = 0,
    val colorPhase: Int = 0,
    val depthR: Int = 0,
    val depthG: Int = 0,
    val depthB: Int = 0,
    val depthC: Int = 0,
    val depthM: Int = 0,
    val depthY: Int = 0,
    val detailLevel: Int = 0,
    val detailMode: String = "Manual",
    val vhBalance: Int = 0,
    val bwBalance: String = "Type1",
    val limit: Int = 0,
    val crispening: Int = 0,
    val highlightDetail: Int = 0,
) {
    /** Danh sach (nhom, nhan, gia tri) de hien thi. */
    fun rows(): List<Triple<String, String, String>> {
        fun sg(v: Int) = if (v > 0) "+$v" else v.toString()
        val g = "Gamma & màu"
        val d = "Chi tiết"
        val c = "Độ đậm màu"
        return listOf(
            Triple(g, "Gamma", gamma.ifBlank { "—" }),
            Triple(g, "Black Gamma", listOf(blackGammaRange, sg(blackGammaLevel)).filter { it.isNotBlank() }.joinToString(" ")),
            Triple(g, "Black Level", sg(blackLevel)),
            Triple(g, "Knee", listOf(kneeMode, kneePoint, sg(kneeSlope)).filter { it.isNotBlank() }.joinToString(" ")),
            Triple(g, "Color Mode", colorMode.ifBlank { "—" }),
            Triple(g, "Saturation", sg(saturation)),
            Triple(g, "Color Phase", sg(colorPhase)),
            Triple(c, "R", sg(depthR)), Triple(c, "G", sg(depthG)), Triple(c, "B", sg(depthB)),
            Triple(c, "C", sg(depthC)), Triple(c, "M", sg(depthM)), Triple(c, "Y", sg(depthY)),
            Triple(d, "Level", sg(detailLevel)),
            Triple(d, "Mode", detailMode),
            Triple(d, "V/H Balance", sg(vhBalance)),
            Triple(d, "B/W Balance", bwBalance),
            Triple(d, "Limit", limit.toString()),
            Triple(d, "Crispening", crispening.toString()),
            Triple(d, "Hi-Light Detail", highlightDetail.toString()),
        )
    }
}

/** Gioi han dich chuyen can bang trang ma may Sony chap nhan. */
object WbShift {
    const val LIMIT = 7.0
    const val STEP_AB = 0.5
    const val STEP_GM = 0.25

    fun snap(value: Double, step: Double): Double {
        val v = (value / step).roundToInt() * step
        return v.coerceIn(-LIMIT, LIMIT)
    }

    private fun num(v: Double): String {
        val a = abs(v)
        return if (a == a.toInt().toDouble()) a.toInt().toString()
        else a.toString().trimEnd('0').trimEnd('.')
    }

    /** Duong = A (ho phach), am = B (xanh lam). */
    fun formatAB(v: Double): String = when {
        v > 0 -> "A" + num(v)
        v < 0 -> "B" + num(v)
        else -> "0"
    }

    /** Duong = G (xanh luc), am = M (tim hong). */
    fun formatGM(v: Double): String = when {
        v > 0 -> "G" + num(v)
        v < 0 -> "M" + num(v)
        else -> "0"
    }
}

data class Recipe(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val kind: RecipeKind = RecipeKind.CREATIVE_LOOK,
    val look: CreativeLook = CreativeLook.ST,
    val pp: PictureProfile? = null,
    val params: Map<LookParam, Int> = emptyMap(),
    val wb: WhiteBalance = WhiteBalance.AUTO,
    val kelvin: Int = 5500,
    val wbAB: Double = 0.0,
    val wbGM: Double = 0.0,
    val dro: String = "",
    val exposureComp: String = "",
    val camera: String = "",
    val notes: String = "",
    val source: String = "",
    val favorite: Boolean = false,
    val builtIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val isPP: Boolean get() = kind == RecipeKind.PICTURE_PROFILE

    /** Chu tren huy hieu: ma Look hoac "PP". */
    fun badge(): String = if (isPP) "PP" else look.code

    fun value(p: LookParam): Int = (params[p] ?: p.default).coerceIn(p.min, p.max)

    fun withValue(p: LookParam, v: Int): Recipe =
        copy(params = params + (p to v.coerceIn(p.min, p.max)))

    fun wbText(): String {
        val base = if (wb == WhiteBalance.KELVIN) "${kelvin}K" else wb.short
        val shifts = listOf(WbShift.formatAB(wbAB), WbShift.formatGM(wbGM)).filter { it != "0" }
        return if (shifts.isEmpty()) base else base + " · " + shifts.joinToString(" ")
    }

    /** Dong tom tat ngan, vi du: "FL · C+2 H-1 S+1 ...". */
    fun summary(): String {
        if (isPP) {
            val p = pp ?: PictureProfile()
            return listOf(p.slot, p.gamma, p.colorMode).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Picture Profile" }
        }
        val nonDefault = LookParam.entries.filter { value(it) != it.default }
            .joinToString(" ") { abbreviation(it) + it.format(value(it)) }
        return if (nonDefault.isEmpty()) look.code else "${look.code} · $nonDefault"
    }

    /** Ban van ban de sao chep/chia se. */
    fun toPlainText(): String = buildString {
        appendLine(name.ifBlank { "Công thức không tên" })
        if (isPP) {
            val p = pp ?: PictureProfile()
            appendLine("Picture Profile" + if (p.slot.isNotBlank()) " (${p.slot})" else "")
            p.rows().forEach { (_, k, v) -> appendLine("$k: $v") }
        } else {
            appendLine("Creative Look: ${look.code} (${look.label})")
            LookParam.entries.forEach { appendLine("${it.label}: ${it.format(value(it))}") }
        }
        appendLine("Cân bằng trắng: ${wbText()}")
        if (dro.isNotBlank()) appendLine("DRO/HDR: $dro")
        if (exposureComp.isNotBlank()) appendLine("Bù phơi sáng: $exposureComp")
        if (camera.isNotBlank()) appendLine("Máy ảnh: $camera")
        if (notes.isNotBlank()) appendLine("Ghi chú: $notes")
        if (source.isNotBlank()) appendLine("Nguồn: $source")
    }.trimEnd()

    companion object {
        fun abbreviation(p: LookParam): String = when (p) {
            LookParam.CONTRAST -> "Ct"
            LookParam.HIGHLIGHTS -> "Hl"
            LookParam.SHADOWS -> "Sh"
            LookParam.FADE -> "Fd"
            LookParam.SATURATION -> "Sa"
            LookParam.SHARPNESS -> "Sp"
            LookParam.SHARPNESS_RANGE -> "SR"
            LookParam.CLARITY -> "Cl"
        }
    }
}
