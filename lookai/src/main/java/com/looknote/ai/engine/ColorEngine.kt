package com.looknote.ai.engine

import com.looknote.app.model.CreativeLook
import com.looknote.app.model.LookParam
import com.looknote.app.model.Recipe
import com.looknote.app.model.WhiteBalance
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Bo mo phong cong thuc Creative Look tren anh da xu ly (JPEG/anh dien thoai).
 * Xu ly tren mang pixel ARGB, thuan Kotlin (kiem thu duoc tren JVM).
 *
 * Luu y: day la mo phong gan dung - may anh Sony xu ly tu du lieu RAW,
 * con app chinh tren anh da co san mau.
 */
object ColorEngine {

    /** Dac tinh nen cua tung Creative Look. */
    private data class LookBase(
        val contrast: Float,        // do doc duong cong (am = phang hon)
        val saturation: Float,      // he so bao hoa
        val blackLift: Float,       // nang diem den (0..0.1)
        val whiteCap: Float,        // ha diem trang (0..0.1)
        val brightness: Float,      // dich sang toi trung tam
        val shadowTint: FloatArray, // mau pha vung toi (r,g,b) cong them
        val highTint: FloatArray,   // mau pha vung sang
        val greenDesat: Float = 0f, // giam bao hoa mau xanh la
        val warmSkin: Float = 0f,   // lam am mau da
        val mono: Boolean = false,
        val sepia: Boolean = false,
    )

    private fun t(r: Float, g: Float, b: Float) = floatArrayOf(r, g, b)

    private fun base(look: CreativeLook): LookBase = when (look) {
        CreativeLook.ST -> LookBase(0f, 1.0f, 0f, 0f, 0f, t(0f, 0f, 0f), t(0f, 0f, 0f))
        CreativeLook.PT -> LookBase(-0.08f, 0.95f, 0.005f, 0f, 0.01f, t(0.005f, 0f, 0f), t(0.01f, 0.004f, 0f), warmSkin = 0.25f)
        CreativeLook.NT -> LookBase(-0.15f, 0.78f, 0.01f, 0.01f, 0f, t(0f, 0f, 0f), t(0f, 0f, 0f))
        CreativeLook.VV -> LookBase(0.18f, 1.28f, 0f, 0f, 0f, t(0f, 0f, 0.004f), t(0.006f, 0.003f, 0f))
        CreativeLook.VV2 -> LookBase(0.12f, 1.18f, 0f, 0f, 0.005f, t(0f, 0f, 0f), t(0f, 0f, 0f))
        CreativeLook.FL -> LookBase(-0.05f, 0.88f, 0.02f, 0.02f, 0f, t(-0.008f, 0.004f, 0.012f), t(0.012f, 0.006f, -0.006f), greenDesat = 0.25f)
        CreativeLook.FL2 -> LookBase(-0.08f, 0.85f, 0.025f, 0.02f, 0f, t(-0.004f, 0.006f, 0.008f), t(0.014f, 0.008f, -0.004f), greenDesat = 0.3f)
        CreativeLook.FL3 -> LookBase(0.02f, 0.9f, 0.015f, 0.015f, 0f, t(-0.01f, 0f, 0.016f), t(0.01f, 0.004f, -0.008f), greenDesat = 0.2f)
        CreativeLook.IN -> LookBase(-0.2f, 0.8f, 0.035f, 0.03f, 0.005f, t(0.006f, 0.004f, 0f), t(0.012f, 0.008f, 0f), greenDesat = 0.15f)
        CreativeLook.SH -> LookBase(-0.18f, 0.85f, 0.04f, 0f, 0.06f, t(0f, 0.002f, 0.006f), t(0.004f, 0.004f, 0.006f))
        CreativeLook.BW -> LookBase(0.05f, 0f, 0f, 0f, 0f, t(0f, 0f, 0f), t(0f, 0f, 0f), mono = true)
        CreativeLook.SE -> LookBase(-0.02f, 0f, 0.01f, 0.01f, 0f, t(0f, 0f, 0f), t(0f, 0f, 0f), mono = true, sepia = true)
    }

    /** Doi nhiet do mau dat tren may thanh he so nhan RGB (moc 5500K = trung tinh). */
    fun whiteBalanceGains(r: Recipe): FloatArray {
        val kelvin: Int? = when (r.wb) {
            WhiteBalance.KELVIN -> r.kelvin
            WhiteBalance.DAYLIGHT, WhiteBalance.FLASH -> 5500
            WhiteBalance.SHADE -> 7000
            WhiteBalance.CLOUDY -> 6200
            WhiteBalance.INCANDESCENT -> 3200
            WhiteBalance.FLUOR_WARM -> 3000
            WhiteBalance.FLUOR_COOL -> 4000
            WhiteBalance.FLUOR_DAY_WHITE -> 5000
            WhiteBalance.FLUOR_DAYLIGHT -> 6500
            else -> null // AWB, duoi nuoc, tuy chinh: giu nguyen
        }
        var gr = 1f; var gg = 1f; var gb = 1f
        if (kelvin != null) {
            // Dat K cao hon nguon sang -> anh am hon. Tinh theo log2(K/5500), gioi han ±1.6 stop.
            val s = (ln(kelvin / 5500.0) / ln(2.0)).toFloat().coerceIn(-1.6f, 1.6f)
            gr *= 1f + 0.16f * s
            gb *= 1f - 0.20f * s
            gg *= 1f + 0.02f * s
        }
        // Dich A-B: duong = A (ho phach), am = B (xanh lam). Moi buoc ~1.5%.
        val ab = r.wbAB.toFloat()
        gr *= 1f + 0.015f * ab
        gg *= 1f + 0.004f * ab
        gb *= 1f - 0.018f * ab
        // Dich G-M: duong = G, am = M.
        val gm = r.wbGM.toFloat()
        gg *= 1f + 0.011f * gm
        gr *= 1f - 0.004f * gm
        gb *= 1f - 0.004f * gm
        // Chuan hoa de giu do sang trung binh
        val norm = 0.299f * gr + 0.587f * gg + 0.114f * gb
        return floatArrayOf(gr / norm, gg / norm, gb / norm)
    }

    private fun smooth(x: Float) = x * x * (3f - 2f * x)

    /** Duong cong tong (LUT 1024 muc) cho do sang. */
    fun buildToneCurve(r: Recipe): FloatArray {
        val b = base(r.look)
        val contrast = b.contrast + r.value(LookParam.CONTRAST) * 0.055f
        val highlights = r.value(LookParam.HIGHLIGHTS) * 0.03f
        val shadows = r.value(LookParam.SHADOWS) * 0.03f
        val fade = r.value(LookParam.FADE) * 0.022f
        val n = 1024
        val lut = FloatArray(n)
        for (i in 0 until n) {
            var x = i / (n - 1f)
            x = (x + b.brightness * 4f * x * (1f - x)).coerceIn(0f, 1f)
            // Tuong phan: tron giua duong thang va duong S quanh 0.5 (log-contrast)
            if (contrast != 0f) {
                val k = exp(contrast * 1.6f)
                val xs = x.coerceIn(1e-4f, 1f - 1e-4f)
                val p = xs.toDouble().pow(k.toDouble())
                val q = (1.0 - xs).pow(k.toDouble())
                x = (p / (p + q)).toFloat()
            }
            // Vung sang: tac dong tu 0.45 tro len
            if (highlights != 0f && x > 0.45f) {
                val w = smooth(((x - 0.45f) / 0.55f).coerceIn(0f, 1f))
                x += highlights * w * (1f - x + 0.15f) * 1.4f
            }
            // Vung toi: tac dong duoi 0.55
            if (shadows != 0f && x < 0.55f) {
                val w = smooth(((0.55f - x) / 0.55f).coerceIn(0f, 1f))
                x += shadows * w * (x + 0.12f) * 1.4f
            }
            x = x.coerceIn(0f, 1f)
            // Phai mau + diem den/trang cua Look
            val black = b.blackLift + fade
            val white = 1f - b.whiteCap - fade * 0.25f
            x = black + x * (white - black)
            lut[i] = x.coerceIn(0f, 1f)
        }
        return lut
    }

    /** Lam mo hop (box blur) tach rieng ngang/doc tren mang float, O(n). */
    fun boxBlur(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        if (radius <= 0) return src.copyOf()
        val tmp = FloatArray(src.size)
        val out = FloatArray(src.size)
        val r = radius
        for (y in 0 until h) {
            val row = y * w
            var acc = 0f
            for (x in -r..r) acc += src[row + x.coerceIn(0, w - 1)]
            val inv = 1f / (2 * r + 1)
            for (x in 0 until w) {
                tmp[row + x] = acc * inv
                val add = src[row + (x + r + 1).coerceAtMost(w - 1)]
                val sub = src[row + (x - r).coerceAtLeast(0)]
                acc += add - sub
            }
        }
        for (x in 0 until w) {
            var acc = 0f
            for (y in -r..r) acc += tmp[y.coerceIn(0, h - 1) * w + x]
            val inv = 1f / (2 * r + 1)
            for (y in 0 until h) {
                out[y * w + x] = acc * inv
                val add = tmp[(y + r + 1).coerceAtMost(h - 1) * w + x]
                val sub = tmp[(y - r).coerceAtLeast(0) * w + x]
                acc += add - sub
            }
        }
        return out
    }

    /** Ban kinh lon nhat ma bo loc chi tiet dung (de tinh vung chong lan khi chia o). */
    fun maxRadius(longEdge: Int): Int {
        val scale = max(1f, longEdge / 2000f)
        return max(max(2, longEdge / 90), ((1.0f + 8 * 0.25f) * scale).roundToInt())
    }

    /** Gan dung Gaussian bang 3 lan box blur. */
    fun blur(src: FloatArray, w: Int, h: Int, radius: Int): FloatArray {
        if (radius <= 0) return src.copyOf()
        val r = max(1, (radius / 1.7f).roundToInt())
        return boxBlur(boxBlur(boxBlur(src, w, h, r), w, h, r), w, h, r)
    }

    private const val INV255 = 1f / 255f

    /**
     * Ap cong thuc len anh.
     * @param pixels ARGB, chieu dai w*h
     * @param strength 0..1 muc ap dung
     * @return mang ARGB moi
     */
    fun apply(pixels: IntArray, w: Int, h: Int, r: Recipe, strength: Float = 1f, refLongEdge: Int = max(w, h)): IntArray {
        val b = base(r.look)
        val gains = whiteBalanceGains(r)
        val lut = buildToneCurve(r)
        val lutMax = lut.size - 1
        val satParam = r.value(LookParam.SATURATION)
        val sat = if (b.mono) 0f else (b.saturation * (1f + satParam * (if (satParam > 0) 0.075f else 0.09f))).coerceAtLeast(0f)
        val n = w * h
        val longEdge = refLongEdge

        // Kenh do sang de tinh do net / do trong
        val sharpness = r.value(LookParam.SHARPNESS)
        val range = r.value(LookParam.SHARPNESS_RANGE)
        val clarity = r.value(LookParam.CLARITY)
        var detail: FloatArray? = null
        if (sharpness != 0 || clarity != 0) {
            val y = FloatArray(n)
            for (i in 0 until n) {
                val p = pixels[i]
                y[i] = (0.299f * ((p shr 16) and 255) + 0.587f * ((p shr 8) and 255) + 0.114f * (p and 255)) * INV255
            }
            val d = FloatArray(n)
            if (clarity != 0) {
                val rad = max(2, longEdge / 90)
                val bl = blur(y, w, h, rad)
                val k = clarity * 0.07f
                for (i in 0 until n) {
                    val mid = 1f - abs(y[i] - 0.5f) * 1.6f // tap trung trung tong
                    d[i] += (y[i] - bl[i]) * k * mid.coerceAtLeast(0.15f)
                }
            }
            if (sharpness != 0) {
                // Pham vi net -4..+4 -> ban kinh nho..lon (ti le theo kich thuoc anh)
                val scale = max(1f, longEdge / 2000f)
                val rad = max(1, ((1.0f + (range + 4) * 0.25f) * scale).roundToInt())
                val bl = blur(y, w, h, rad)
                val k = sharpness * (if (sharpness > 0) 0.09f else 0.11f)
                for (i in 0 until n) d[i] += (y[i] - bl[i]) * k
            }
            detail = d
        }

        val out = IntArray(n)
        val s = strength.coerceIn(0f, 1f)
        val sh = b.shadowTint
        val hi = b.highTint
        for (i in 0 until n) {
            val p = pixels[i]
            val a = p ushr 24
            val r0 = ((p shr 16) and 255) * INV255
            val g0 = ((p shr 8) and 255) * INV255
            val b0 = (p and 255) * INV255

            var rr = r0 * gains[0]
            var gg = g0 * gains[1]
            var bb = b0 * gains[2]

            val dd = detail?.get(i) ?: 0f
            if (b.mono) {
                // Den trang: tron kenh giong phim (do nhe hon de da sang)
                val m = (0.33f * rr + 0.5f * gg + 0.17f * bb + dd).coerceIn(0f, 1f)
                val v = lut[(m * lutMax).roundToInt()]
                if (b.sepia) {
                    rr = v * 1.07f + 0.02f; gg = v * 0.95f + 0.01f; bb = v * 0.78f
                } else { rr = v; gg = v; bb = v }
                rr = r0 + (rr - r0) * s
                gg = g0 + (gg - g0) * s
                bb = b0 + (bb - b0) * s
                out[i] = pack(a, rr, gg, bb)
                continue
            }

            var yl = 0.299f * rr + 0.587f * gg + 0.114f * bb

            // Duong cong tong tren do sang, giu ti le mau
            val yIn = (yl + dd).coerceIn(0f, 1f)
            val yOut = lut[(yIn * lutMax).roundToInt()]
            val ratio = if (yl > 1e-4f) yOut / yl else 1f
            rr *= ratio; gg *= ratio; bb *= ratio
            if (yl <= 1e-4f) { rr = yOut; gg = yOut; bb = yOut }
            yl = yOut

            run {
                // Giam xanh la (dac trung FL/IN)
                if (b.greenDesat > 0f) {
                    val gExcess = gg - max(rr, bb)
                    if (gExcess > 0f) {
                        val k = b.greenDesat * min(1f, gExcess * 4f)
                        val avg = (rr + gg + bb) / 3f
                        gg += (avg - gg) * k * 0.6f
                        bb += (avg - bb) * k * 0.2f
                    }
                }
                // Am da (PT): mau cam nhat
                if (b.warmSkin > 0f && rr > gg && gg > bb) {
                    val k = b.warmSkin * min(1f, (rr - bb) * 2f)
                    rr += 0.03f * k; bb -= 0.02f * k
                }
                // Bao hoa
                rr = yl + (rr - yl) * sat
                gg = yl + (gg - yl) * sat
                bb = yl + (bb - yl) * sat
                // Pha mau vung toi / vung sang
                val wS = (1f - yl).let { it * it }
                val wH = yl * yl
                rr += sh[0] * wS * 2f + hi[0] * wH * 2f
                gg += sh[1] * wS * 2f + hi[1] * wH * 2f
                bb += sh[2] * wS * 2f + hi[2] * wH * 2f
            }

            // Tron theo muc ap dung
            rr = r0 + (rr - r0) * s
            gg = g0 + (gg - g0) * s
            bb = b0 + (bb - b0) * s

            out[i] = pack(a, rr, gg, bb)
        }
        return out
    }

    fun pack(a: Int, r: Float, g: Float, b: Float): Int {
        val ri = (r.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        val gi = (g.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        val bi = (b.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (a shl 24) or (ri shl 16) or (gi shl 8) or bi
    }
}
