package com.looknote.ai.engine

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Khuon mat phat hien duoc: tam giua hai mat va khoang cach hai mat (pixel). */
data class Face(val cx: Float, val cy: Float, val eyeDist: Float)

/**
 * Lam min da tren may: mat na vung da (mau da YCbCr x vung khuon mat)
 * + bo loc giu canh (guided filter). Giu lai mot phan van da de khong bi "nhua".
 */
object SkinRetouch {

    /** Muc do mau da 0..1 theo YCbCr (co bien mem). */
    fun skinProbability(r: Int, g: Int, b: Int): Float {
        val y = 0.299f * r + 0.587f * g + 0.114f * b
        if (y < 35f) return 0f
        val cb = 128f - 0.168736f * r - 0.331264f * g + 0.5f * b
        val cr = 128f + 0.5f * r - 0.418688f * g - 0.081312f * b
        fun band(v: Float, lo: Float, hi: Float, soft: Float): Float = when {
            v < lo - soft || v > hi + soft -> 0f
            v < lo -> (v - (lo - soft)) / soft
            v > hi -> ((hi + soft) - v) / soft
            else -> 1f
        }
        return band(cb, 77f, 127f, 10f) * band(cr, 133f, 173f, 10f)
    }

    /** Trong so vung khuon mat + co (0..1). */
    private fun regionWeight(x: Float, y: Float, faces: List<Face>): Float {
        var best = 0f
        for (f in faces) {
            val e = f.eyeDist
            // Mat: elip tam duoi giua hai mat mot chut
            best = max(best, ellipse(x, y, f.cx, f.cy + 0.45f * e, 1.55f * e, 2.25f * e))
            // Co
            best = max(best, ellipse(x, y, f.cx, f.cy + 3.0f * e, 1.2f * e, 1.1f * e) * 0.9f)
            if (best >= 1f) return 1f
        }
        return best
    }

    private fun ellipse(x: Float, y: Float, cx: Float, cy: Float, rx: Float, ry: Float): Float {
        val dx = (x - cx) / rx
        val dy = (y - cy) / ry
        val d = sqrt(dx * dx + dy * dy)
        return when {
            d <= 0.85f -> 1f
            d >= 1.15f -> 0f
            else -> (1.15f - d) / 0.3f
        }
    }

    /** Guided filter tu dan (I = p) tren mot kenh. */
    private fun guided(p: FloatArray, w: Int, h: Int, r: Int, eps: Float): FloatArray {
        val n = p.size
        val pp = FloatArray(n) { p[it] * p[it] }
        val mean = ColorEngine.boxBlur(p, w, h, r)
        val meanSq = ColorEngine.boxBlur(pp, w, h, r)
        val a = FloatArray(n)
        val bArr = FloatArray(n)
        for (i in 0 until n) {
            val v = meanSq[i] - mean[i] * mean[i]
            val ai = v / (v + eps)
            a[i] = ai
            bArr[i] = mean[i] * (1f - ai)
        }
        val ma = ColorEngine.boxBlur(a, w, h, r)
        val mb = ColorEngine.boxBlur(bArr, w, h, r)
        return FloatArray(n) { ma[it] * p[it] + mb[it] }
    }

    data class Result(val pixels: IntArray, val maskCoverage: Float)

    /**
     * @param strength 0..1
     * @param faceOnly chi lam min trong vung khuon mat (neu khong co mat -> khong lam gi)
     */
    /** Ban kinh loc theo kich thuoc mat (hoac kich thuoc anh). */
    fun radiusFor(faces: List<Face>, longEdge: Int): Int {
        val ref = if (faces.isNotEmpty()) faces.maxOf { it.eyeDist } else longEdge / 14f
        return max(2, (ref / 9f).roundToInt())
    }

    /**
     * @param faces toa do theo anh dang xu ly (neu chia o: da tru vi tri o)
     * @param refLongEdge canh dai cua anh day du (de tinh ban kinh khi chia o)
     */
    fun apply(
        pixels: IntArray, w: Int, h: Int, faces: List<Face>, strength: Float, faceOnly: Boolean,
        refLongEdge: Int = max(w, h), facesForRadius: List<Face> = faces,
    ): Result {
        val s = strength.coerceIn(0f, 1f)
        if (s <= 0f || (faceOnly && faces.isEmpty())) return Result(pixels.copyOf(), 0f)
        val n = w * h
        val radius = radiusFor(facesForRadius, refLongEdge)
        val eps = 0.0006f + 0.0045f * s

        // Mat na
        val mask = FloatArray(n)
        var cover = 0f
        val useRegion = faces.isNotEmpty()
        for (yy in 0 until h) {
            val row = yy * w
            for (xx in 0 until w) {
                val px = pixels[row + xx]
                var m = skinProbability((px shr 16) and 255, (px shr 8) and 255, px and 255)
                if (m > 0f && useRegion) m *= regionWeight(xx.toFloat(), yy.toFloat(), faces)
                mask[row + xx] = m
            }
        }
        val softMask = ColorEngine.blur(mask, w, h, max(2, radius / 2))
        for (i in 0 until n) cover += softMask[i]

        val ch = Array(3) { FloatArray(n) }
        for (i in 0 until n) {
            val px = pixels[i]
            ch[0][i] = ((px shr 16) and 255) / 255f
            ch[1][i] = ((px shr 8) and 255) / 255f
            ch[2][i] = (px and 255) / 255f
        }
        val sm = Array(3) { guided(ch[it], w, h, radius, eps) }

        // Giu lai van min (hat nho) de da tu nhien
        val grainKeep = 0.22f
        val out = IntArray(n)
        for (i in 0 until n) {
            val k = min(1f, softMask[i] * 1.15f) * s * 0.9f
            if (k <= 0.002f) { out[i] = pixels[i]; continue }
            var r = ch[0][i]; var g = ch[1][i]; var b = ch[2][i]
            val sr = sm[0][i] + (r - sm[0][i]) * grainKeep
            val sg = sm[1][i] + (g - sm[1][i]) * grainKeep
            val sb = sm[2][i] + (b - sm[2][i]) * grainKeep
            r += (sr - r) * k; g += (sg - g) * k; b += (sb - b) * k
            out[i] = ColorEngine.pack(pixels[i] ushr 24, r, g, b)
        }
        return Result(out, cover / n)
    }
}
