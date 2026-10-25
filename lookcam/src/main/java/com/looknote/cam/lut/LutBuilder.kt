package com.looknote.cam.lut

import com.looknote.ai.engine.ColorEngine
import com.looknote.app.model.LookParam
import com.looknote.app.model.Recipe

/**
 * Nuong cong thuc thanh 3D LUT (N^3 mau) de GPU xem truoc trong khung ngam.
 * Bo qua do net/do trong (can lan can pixel) - phan nay chi ap khi luu anh.
 * Bo cuc anh LUT: rong N*N, cao N; o (b) nam ngang, trong moi o x = r, y = g.
 */
object LutBuilder {
    const val SIZE = 33

    fun colorOnly(r: Recipe): Recipe = r.withValue(LookParam.SHARPNESS, 0).withValue(LookParam.CLARITY, 0)

    fun identity(n: Int = SIZE): IntArray {
        val w = n * n
        val out = IntArray(w * n)
        for (g in 0 until n) for (b in 0 until n) for (r in 0 until n) {
            val x = b * n + r
            val rr = (r * 255f / (n - 1) + 0.5f).toInt()
            val gg = (g * 255f / (n - 1) + 0.5f).toInt()
            val bb = (b * 255f / (n - 1) + 0.5f).toInt()
            out[g * w + x] = (255 shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
        return out
    }

    /** Tra ve mang ARGB kich thuoc (N*N) x N. recipe = null -> LUT trung tinh. */
    fun build(recipe: Recipe?, strength: Float = 1f, n: Int = SIZE): IntArray {
        val id = identity(n)
        if (recipe == null) return id
        // refLongEdge lon de khong anh huong; do net/do trong da tat
        return ColorEngine.apply(id, n * n, n, colorOnly(recipe), strength, refLongEdge = 4000)
    }

    /** Tra LUT tren CPU (noi suy ba chieu) - dung cho kiem thu doi chieu voi shader. */
    fun lookup(lut: IntArray, n: Int, argb: Int): Int {
        val w = n * n
        fun ch(p: Int, s: Int) = ((p shr s) and 255).toFloat()
        val fr = ((argb shr 16) and 255) / 255f * (n - 1)
        val fg = ((argb shr 8) and 255) / 255f * (n - 1)
        val fb = (argb and 255) / 255f * (n - 1)
        val r0 = fr.toInt().coerceAtMost(n - 2); val g0 = fg.toInt().coerceAtMost(n - 2); val b0 = fb.toInt().coerceAtMost(n - 2)
        val dr = fr - r0; val dg = fg - g0; val db = fb - b0
        val res = FloatArray(3)
        for (k in 0..2) {
            val s = 16 - 8 * k
            var acc = 0f
            for (ib in 0..1) for (ig in 0..1) for (ir in 0..1) {
                val wgt = (if (ir == 1) dr else 1 - dr) * (if (ig == 1) dg else 1 - dg) * (if (ib == 1) db else 1 - db)
                acc += wgt * ch(lut[(g0 + ig) * w + (b0 + ib) * n + (r0 + ir)], s)
            }
            res[k] = acc
        }
        return (255 shl 24) or ((res[0] + 0.5f).toInt() shl 16) or ((res[1] + 0.5f).toInt() shl 8) or (res[2] + 0.5f).toInt()
    }
}
