package com.looknote.ai.engine

import com.looknote.app.model.Recipe
import kotlin.math.max
import kotlin.math.min

/** Thiet lap chinh sua mot anh. */
data class EditSettings(
    val recipe: Recipe? = null,
    val colorStrength: Float = 1f,
    val skinStrength: Float = 0f,
    val faceOnly: Boolean = true,
)

/** Nguon/dich pixel tong quat (Bitmap tren Android, BufferedImage trong kiem thu). */
interface PixelSource {
    val width: Int
    val height: Int
    fun read(x: Int, y: Int, w: Int, h: Int): IntArray
}

interface PixelSink {
    fun write(x: Int, y: Int, w: Int, h: Int, px: IntArray)
}

/**
 * Xu ly anh lon theo tung o (tile) co vung chong lan, de khong het bo nho.
 * Thu tu: lam min da -> chinh mau.
 */
object Pipeline {

    fun process(pixels: IntArray, w: Int, h: Int, faces: List<Face>, st: EditSettings): IntArray {
        var px = pixels
        if (st.skinStrength > 0f) px = SkinRetouch.apply(px, w, h, faces, st.skinStrength, st.faceOnly).pixels
        val r = st.recipe
        if (r != null && st.colorStrength > 0f) px = ColorEngine.apply(px, w, h, r, st.colorStrength)
        return px
    }

    fun processTiled(
        src: PixelSource,
        dst: PixelSink,
        faces: List<Face>,
        st: EditSettings,
        tile: Int = 1024,
        progress: (Float) -> Unit = {},
    ) {
        val w = src.width
        val h = src.height
        val longEdge = max(w, h)
        val skinR = if (st.skinStrength > 0f) SkinRetouch.radiusFor(faces, longEdge) else 0
        val colorR = if (st.recipe != null) ColorEngine.maxRadius(longEdge) else 0
        // 3 lan box blur + loc 2 tang trong guided filter + lam mem mat na
        val overlap = max(skinR * 6 + skinR, colorR * 3) + 8
        val tilesX = (w + tile - 1) / tile
        val tilesY = (h + tile - 1) / tile
        val total = tilesX * tilesY
        var done = 0
        for (ty in 0 until tilesY) for (tx in 0 until tilesX) {
            val x0 = tx * tile
            val y0 = ty * tile
            val x1 = min(w, x0 + tile)
            val y1 = min(h, y0 + tile)
            val ex0 = max(0, x0 - overlap)
            val ey0 = max(0, y0 - overlap)
            val ex1 = min(w, x1 + overlap)
            val ey1 = min(h, y1 + overlap)
            val ew = ex1 - ex0
            val eh = ey1 - ey0
            var px = src.read(ex0, ey0, ew, eh)
            if (st.skinStrength > 0f) {
                val local = faces.map { Face(it.cx - ex0, it.cy - ey0, it.eyeDist) }
                px = SkinRetouch.apply(px, ew, eh, local, st.skinStrength, st.faceOnly, longEdge, faces).pixels
            }
            val r = st.recipe
            if (r != null && st.colorStrength > 0f) px = ColorEngine.apply(px, ew, eh, r, st.colorStrength, longEdge)
            // Cat phan loi
            val cw = x1 - x0
            val ch = y1 - y0
            val core = IntArray(cw * ch)
            for (yy in 0 until ch) System.arraycopy(px, (yy + y0 - ey0) * ew + (x0 - ex0), core, yy * cw, cw)
            dst.write(x0, y0, cw, ch, core)
            done++
            progress(done.toFloat() / total)
        }
    }
}
