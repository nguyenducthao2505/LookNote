package com.looknote.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.looknote.app.model.LookParam
import com.looknote.app.model.Recipe
import com.looknote.app.model.RecipeCodec
import com.looknote.app.util.Qr

/** Ve "the cong thuc" kem ma QR de luu/chia se. */
object RecipeCardRenderer {
    private const val W = 1080
    private const val H = 1600

    private val BG = Color.rgb(18, 18, 20)
    private val PANEL = Color.rgb(32, 32, 36)
    private val ACCENT = Color.rgb(242, 179, 61)
    private val TEXT = Color.WHITE
    private val MUTED = Color.rgb(160, 160, 168)

    private fun p(color: Int, size: Float, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            textAlign = align
        }

    fun render(r: Recipe, withQr: Boolean = true): Bitmap {
        val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(BG)
        val m = 72f

        // Tieu de
        val title = p(TEXT, 64f, bold = true)
        val titleLayout = StaticLayout.Builder.obtain(r.name.ifBlank { "Công thức" }, 0, r.name.ifBlank { "Công thức" }.length, title, (W - 2 * m - 220).toInt())
            .setMaxLines(2).setEllipsize(android.text.TextUtils.TruncateAt.END).build()
        c.save(); c.translate(m, m); titleLayout.draw(c); c.restore()

        // Huy hieu Look
        val badge = RectF(W - m - 190f, m, W - m, m + 120f)
        c.drawRoundRect(badge, 24f, 24f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT })
        val code = r.badge()
        val bp = p(Color.BLACK, if (code.length > 2) 58f else 70f, bold = true, align = Paint.Align.CENTER)
        val fm = bp.fontMetrics
        c.drawText(code, badge.centerX(), badge.centerY() - (fm.ascent + fm.descent) / 2, bp)

        var y = m + maxOf(titleLayout.height.toFloat(), 120f) + 20f
        val subtitle = if (r.isPP) "Picture Profile" + (r.pp?.slot?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
        else "Creative Look · ${r.look.label}"
        c.drawText(subtitle, m, y + 40f, p(MUTED, 34f))
        y += 80f

        val colW = (W - 2 * m) / 2
        val ppData = r.pp
        if (r.isPP && ppData != null) {
            // Bang PP: 2 cot x 10 dong
            val rows = ppData.rows()
            val per = (rows.size + 1) / 2
            val rowH = 46f
            val panel = RectF(m, y, W - m, y + per * rowH + 40f)
            c.drawRoundRect(panel, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PANEL })
            rows.forEachIndexed { i, (_, k, v) ->
                val col = i / per
                val row = i % per
                val x = m + 30f + col * colW
                val yy = y + 52f + row * rowH
                c.drawText(k, x, yy, p(MUTED, 26f))
                c.drawText(v, x + colW - 60f, yy, p(TEXT, 28f, bold = true, align = Paint.Align.RIGHT))
            }
            y = panel.bottom + 40f
        } else {
            // Bang thong so 2 cot
            val panel = RectF(m, y, W - m, y + 4 * 110f + 40f)
            c.drawRoundRect(panel, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PANEL })
            LookParam.entries.forEachIndexed { i, prm ->
                val col = i / 4
                val row = i % 4
                val x = m + 40f + col * colW
                val yy = y + 70f + row * 110f
                c.drawText(prm.label, x, yy, p(MUTED, 30f))
                c.drawText(prm.format(r.value(prm)), x, yy + 46f, p(TEXT, 42f, bold = true))
            }
            y = panel.bottom + 50f
        }

        c.drawText("Cân bằng trắng", m, y + 30f, p(MUTED, 30f))
        c.drawText(r.wbText(), m, y + 80f, p(TEXT, 42f, bold = true))
        y += 120f
        val extras = listOfNotNull(
            r.dro.takeIf { it.isNotBlank() }?.let { "DRO/HDR: $it" },
            r.exposureComp.takeIf { it.isNotBlank() }?.let { "Bù sáng: $it" },
            r.camera.takeIf { it.isNotBlank() }?.let { "Máy: $it" },
        ).joinToString("   ·   ")
        if (r.source.isNotBlank()) {
            c.drawText("Nguồn: " + r.source, m, y + 30f, p(MUTED, 28f)); y += 50f
        }
        if (extras.isNotBlank()) {
            c.drawText(extras, m, y + 30f, p(MUTED, 30f)); y += 60f
        }

        // QR o goc duoi phai, ghi chu ben trai
        val qrSize = 330
        val qrTop = H - m - qrSize
        if (withQr) {
            val qr = Qr.encode(RecipeCodec.toShareCode(r), qrSize)
            val frame = RectF(W - m - qrSize - 20f, qrTop - 20f, W - m + 0f, H - m + 0f)
            c.drawRoundRect(frame, 20f, 20f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            c.drawBitmap(qr, W - m - qrSize - 10f, qrTop - 10f, null)
            c.drawText("Quét bằng LookNote để nhập", W - m - qrSize / 2f - 10f, qrTop - 40f, p(MUTED, 24f, align = Paint.Align.CENTER))
        }
        if (r.notes.isNotBlank()) {
            val np = p(TEXT, 32f)
            val noteW = (if (withQr) W - 2 * m - qrSize - 60 else W - 2 * m).toInt()
            val layout = StaticLayout.Builder.obtain(r.notes, 0, r.notes.length, np, noteW)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(((H - m - 70f - y) / 44f).toInt().coerceAtLeast(1))
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()
            c.save(); c.translate(m, y + 10f); layout.draw(c); c.restore()
        }
        c.drawText("LookNote", m, H - m + 0f, p(ACCENT, 34f, bold = true))
        return out
    }
}
