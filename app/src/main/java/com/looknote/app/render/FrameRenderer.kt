package com.looknote.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.looknote.app.exif.PhotoInfo

enum class FrameStyle(val label: String) {
    WHITE_BAR("Thanh trắng"),
    BLACK_BAR("Thanh đen"),
    BORDER("Viền trắng"),
    OVERLAY("Huy hiệu trên ảnh"),
}

data class FrameOptions(
    val style: FrameStyle = FrameStyle.WHITE_BAR,
    val showLens: Boolean = true,
    val showDate: Boolean = false,
    val badgeText: String = "",      // vi du "FL" hoac "FL · C+2"
    val signature: String = "",      // chu ky/tac gia
)

/** Ve khung va thong tin chup len anh. */
object FrameRenderer {

    fun render(photo: Bitmap, info: PhotoInfo?, opt: FrameOptions): Bitmap = when (opt.style) {
        FrameStyle.WHITE_BAR -> bar(photo, info, opt, Color.WHITE, Color.rgb(20, 20, 20), Color.rgb(110, 110, 110))
        FrameStyle.BLACK_BAR -> bar(photo, info, opt, Color.rgb(14, 14, 14), Color.WHITE, Color.rgb(170, 170, 170))
        FrameStyle.BORDER -> border(photo, info, opt)
        FrameStyle.OVERLAY -> overlay(photo, info, opt)
    }

    private fun paint(color: Int, size: Float, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
            textAlign = align
        }

    private fun topLine(info: PhotoInfo?, opt: FrameOptions): String {
        val cam = info?.cameraName().orEmpty()
        return if (opt.signature.isNotBlank()) {
            if (cam.isBlank()) opt.signature else "$cam  ·  ${opt.signature}"
        } else cam.ifBlank { "LookNote" }
    }

    private fun subLeft(info: PhotoInfo?, opt: FrameOptions): String =
        if (opt.showLens) info?.lens.orEmpty() else ""

    private fun subRight(info: PhotoInfo?, opt: FrameOptions): String =
        if (opt.showDate) info?.dateTime?.take(16)?.replaceFirst(":", ".")?.replaceFirst(":", ".").orEmpty() else ""

    private fun fit(p: Paint, text: String, maxWidth: Float) {
        while (p.textSize > 8f && p.measureText(text) > maxWidth) p.textSize *= 0.95f
    }

    private fun drawBadge(c: Canvas, text: String, rightX: Float, centerY: Float, h: Float, fg: Int, bg: Int) {
        if (text.isBlank()) return
        val p = paint(fg, h * 0.52f, bold = true)
        val padX = h * 0.45f
        val w = p.measureText(text) + padX * 2
        val r = RectF(rightX - w, centerY - h / 2, rightX, centerY + h / 2)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        c.drawRoundRect(r, h * 0.22f, h * 0.22f, bgPaint)
        val fm = p.fontMetrics
        c.drawText(text, r.left + padX, centerY - (fm.ascent + fm.descent) / 2, p)
    }

    private fun bar(photo: Bitmap, info: PhotoInfo?, opt: FrameOptions, bg: Int, fg: Int, sub: Int): Bitmap {
        val w = photo.width
        val barH = (minOf(photo.width, photo.height) * 0.13f).coerceAtLeast(120f)
        val out = Bitmap.createBitmap(w, (photo.height + barH).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(bg)
        c.drawBitmap(photo, 0f, 0f, null)

        val pad = barH * 0.42f
        val top = photo.height.toFloat()
        val main = paint(fg, barH * 0.24f, bold = true)
        val small = paint(sub, barH * 0.17f)
        val mainR = paint(fg, barH * 0.22f, bold = true, align = Paint.Align.RIGHT)
        val smallR = paint(sub, barH * 0.17f, align = Paint.Align.RIGHT)

        val half = w / 2f - pad
        val l1 = topLine(info, opt)
        val l2 = subLeft(info, opt)
        fit(main, l1, half); fit(small, l2, half)

        val settings = info?.settingsLine().orEmpty()
        val r2 = subRight(info, opt)

        // Huy hieu Creative Look nam ben phai, thong so dat ben trai huy hieu
        var rightEdge = w - pad
        if (opt.badgeText.isNotBlank()) {
            val bh = barH * 0.38f
            drawBadge(c, opt.badgeText, rightEdge, top + barH / 2, bh, if (bg == Color.WHITE) Color.WHITE else Color.BLACK, fg)
            val bp = paint(fg, bh * 0.52f, bold = true)
            rightEdge -= bp.measureText(opt.badgeText) + bh * 0.9f + pad * 0.5f
        }
        val rightWidth = rightEdge - w / 2f
        fit(mainR, settings, rightWidth); fit(smallR, r2, rightWidth)

        val hasSubL = l2.isNotBlank()
        val hasSubR = r2.isNotBlank()
        c.drawText(l1, pad, top + if (hasSubL) barH * 0.46f else barH * 0.58f, main)
        if (hasSubL) c.drawText(l2, pad, top + barH * 0.74f, small)
        c.drawText(settings, rightEdge, top + if (hasSubR) barH * 0.46f else barH * 0.58f, mainR)
        if (hasSubR) c.drawText(r2, rightEdge, top + barH * 0.74f, smallR)
        return out
    }

    private fun border(photo: Bitmap, info: PhotoInfo?, opt: FrameOptions): Bitmap {
        val base = minOf(photo.width, photo.height).toFloat()
        val side = base * 0.045f
        val bottom = base * 0.17f
        val out = Bitmap.createBitmap((photo.width + side * 2).toInt(), (photo.height + side + bottom).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawBitmap(photo, side, side, null)
        val cx = out.width / 2f
        val top = side + photo.height
        val main = paint(Color.rgb(25, 25, 25), bottom * 0.2f, bold = true, align = Paint.Align.CENTER)
        val small = paint(Color.rgb(120, 120, 120), bottom * 0.14f, align = Paint.Align.CENTER)
        val l1 = topLine(info, opt)
        val parts = listOfNotNull(
            info?.settingsLine()?.takeIf { it.isNotBlank() },
            subLeft(info, opt).takeIf { it.isNotBlank() },
            opt.badgeText.takeIf { it.isNotBlank() },
        )
        val l2 = parts.joinToString("   |   ")
        fit(main, l1, out.width - side * 4); fit(small, l2, out.width - side * 4)
        c.drawText(l1, cx, top + bottom * 0.45f, main)
        c.drawText(l2, cx, top + bottom * 0.72f, small)
        return out
    }

    private fun overlay(photo: Bitmap, info: PhotoInfo?, opt: FrameOptions): Bitmap {
        val out = photo.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val base = minOf(out.width, out.height).toFloat()
        val pad = base * 0.04f
        val h = base * 0.075f
        val text = listOfNotNull(
            opt.badgeText.takeIf { it.isNotBlank() },
            info?.cameraName()?.takeIf { it.isNotBlank() },
            info?.settingsLine()?.takeIf { it.isNotBlank() },
            opt.signature.takeIf { it.isNotBlank() },
        ).joinToString("  ·  ").ifBlank { "LookNote" }
        val p = paint(Color.WHITE, h * 0.42f, bold = true)
        fit(p, text, out.width - pad * 4)
        val tw = p.measureText(text)
        val r = RectF(pad, out.height - pad - h, pad + tw + h * 0.8f, out.height - pad)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }
        c.drawRoundRect(r, h / 2, h / 2, bg)
        val fm = p.fontMetrics
        c.drawText(text, r.left + h * 0.4f, r.centerY() - (fm.ascent + fm.descent) / 2, p)
        return out
    }
}
