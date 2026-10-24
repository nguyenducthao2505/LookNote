package com.looknote.app.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

object Qr {
    fun encode(text: String, size: Int, dark: Int = Color.BLACK, light: Int = Color.WHITE): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        )
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) pixels[row + x] = if (matrix.get(x, y)) dark else light
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /** Doc ma QR tu mot anh (vi du anh chup man hinh the cong thuc). */
    fun decode(source: Bitmap): String? {
        val longEdge = maxOf(source.width, source.height)
        val bmp = if (longEdge > 1600) {
            val s = 1600f / longEdge
            Bitmap.createScaledBitmap(source, (source.width * s).toInt(), (source.height * s).toInt(), true)
        } else source
        val pixels = IntArray(bmp.width * bmp.height)
        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        val lum = RGBLuminanceSource(bmp.width, bmp.height, pixels)
        val hints = mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        )
        return try {
            MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(lum)), hints).text
        } catch (e: Exception) {
            null
        }
    }
}
