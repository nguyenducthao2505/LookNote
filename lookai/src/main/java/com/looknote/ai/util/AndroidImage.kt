package com.looknote.ai.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.media.FaceDetector
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.looknote.ai.engine.Face
import com.looknote.ai.engine.PixelSink
import com.looknote.ai.engine.PixelSource
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

class BitmapSource(private val bmp: Bitmap) : PixelSource {
    override val width: Int get() = bmp.width
    override val height: Int get() = bmp.height
    override fun read(x: Int, y: Int, w: Int, h: Int): IntArray {
        val a = IntArray(w * h)
        bmp.getPixels(a, 0, w, x, y, w, h)
        return a
    }
}

class BitmapSink(private val bmp: Bitmap) : PixelSink {
    override fun write(x: Int, y: Int, w: Int, h: Int, px: IntArray) = bmp.setPixels(px, 0, w, x, y, w, h)
}

object AndroidImage {

    private val EXIF_TAGS = listOf(
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, ExifInterface.TAG_LENS_MODEL, ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL, ExifInterface.TAG_ARTIST, ExifInterface.TAG_COPYRIGHT,
    )

    fun imageSize(context: Context, uri: Uri): Pair<Int, Int> {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0) throw IllegalArgumentException("Không đọc được ảnh")
        val rot = orientation(context, uri)
        val swap = rot == ExifInterface.ORIENTATION_ROTATE_90 || rot == ExifInterface.ORIENTATION_ROTATE_270 ||
            rot == ExifInterface.ORIENTATION_TRANSPOSE || rot == ExifInterface.ORIENTATION_TRANSVERSE
        return if (swap) o.outHeight to o.outWidth else o.outWidth to o.outHeight
    }

    private fun orientation(context: Context, uri: Uri): Int = runCatching {
        context.contentResolver.openInputStream(uri).use { s ->
            if (s == null) ExifInterface.ORIENTATION_NORMAL
            else ExifInterface(s).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    /** Giai ma, xoay dung chieu, thu ve canh dai <= maxEdge. Bitmap tra ve co the sua (mutable). */
    fun load(context: Context, uri: Uri, maxEdge: Int): Bitmap {
        val cr = context.contentResolver
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0) throw IllegalArgumentException("Không giải mã được ảnh")
        var sample = 1
        while (max(o.outWidth, o.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        var bmp = cr.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalArgumentException("Không giải mã được ảnh")
        val m = Matrix()
        val long = max(bmp.width, bmp.height)
        if (long > maxEdge) { val s = maxEdge.toFloat() / long; m.postScale(s, s) }
        when (orientation(context, uri)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        }
        if (!m.isIdentity) {
            val t = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (t != bmp) bmp.recycle()
            bmp = t
        }
        return if (bmp.isMutable && bmp.config == Bitmap.Config.ARGB_8888) bmp
        else bmp.copy(Bitmap.Config.ARGB_8888, true).also { if (it != bmp) bmp.recycle() }
    }

    fun scaled(src: Bitmap, maxEdge: Int): Bitmap {
        val long = max(src.width, src.height)
        if (long <= maxEdge) return src.copy(Bitmap.Config.ARGB_8888, true)
        val s = maxEdge.toFloat() / long
        return Bitmap.createScaledBitmap(src, max(1, (src.width * s).roundToInt()), max(1, (src.height * s).roundToInt()), true)
    }

    fun toJpeg(bmp: Bitmap, maxEdge: Int, quality: Int = 88): ByteArray {
        val s = scaled(bmp, maxEdge)
        return ByteArrayOutputStream().use { s.compress(Bitmap.CompressFormat.JPEG, quality, it); it.toByteArray() }
    }

    /** Phat hien khuon mat (bo phat hien co san cua Android, chay offline). */
    fun detectFaces(bmp: Bitmap, maxFaces: Int = 8): List<Face> {
        val work = scaled(bmp, 1024)
        val w = work.width - (work.width % 2)
        val src565 = Bitmap.createBitmap(work, 0, 0, w, work.height).copy(Bitmap.Config.RGB_565, false)
        val arr = arrayOfNulls<FaceDetector.Face>(maxFaces)
        val n = runCatching { FaceDetector(src565.width, src565.height, maxFaces).findFaces(src565, arr) }.getOrDefault(0)
        val scale = bmp.width.toFloat() / work.width
        val p = PointF()
        return (0 until n).mapNotNull { i ->
            val f = arr[i] ?: return@mapNotNull null
            if (f.confidence() < 0.3f) return@mapNotNull null
            f.getMidPoint(p)
            Face(p.x * scale, p.y * scale, f.eyesDistance() * scale)
        }
    }

    /** Luu JPEG vao Pictures/LookAI, chep lai thong tin chup tu anh goc. */
    fun saveJpeg(context: Context, bmp: Bitmap, name: String, quality: Int, exifFrom: Uri?): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/LookAI")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val cr = context.contentResolver
        val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw IllegalStateException("Không tạo được tệp ảnh")
        cr.openOutputStream(uri).use { out ->
            requireNotNull(out)
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        if (exifFrom != null) runCatching {
            val srcExif = cr.openInputStream(exifFrom).use { ExifInterface(requireNotNull(it)) }
            cr.openFileDescriptor(uri, "rw").use { pfd ->
                val dst = ExifInterface(requireNotNull(pfd).fileDescriptor)
                EXIF_TAGS.forEach { t -> srcExif.getAttribute(t)?.let { v -> dst.setAttribute(t, v) } }
                dst.setAttribute(ExifInterface.TAG_SOFTWARE, "LookAI")
                dst.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                dst.saveAttributes()
            }
        }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        cr.update(uri, values, null, null)
        return uri
    }

    fun shareJpeg(context: Context, bmp: Bitmap, quality: Int) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val f = File(dir, "lookai_${System.currentTimeMillis()}.jpg")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Chia sẻ").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
