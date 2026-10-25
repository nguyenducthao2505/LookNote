package com.looknote.cam.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.DngCreator
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.looknote.ai.engine.EditSettings
import com.looknote.ai.engine.Pipeline
import com.looknote.ai.util.AndroidImage
import com.looknote.ai.util.BitmapSink
import com.looknote.ai.util.BitmapSource
import com.looknote.app.model.Recipe
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SaveOptions(
    val recipe: Recipe?,
    val strength: Float,
    val keepOriginal: Boolean,
    val filteredMaxEdge: Int,
    val quality: Int,
)

data class Saved(val original: Uri?, val filtered: Uri?, val raw: Uri?, val thumb: Bitmap?)

object ShotSaver {
    private val FOLDER = Environment.DIRECTORY_DCIM + "/LookCam"

    private fun insert(context: Context, name: String, mime: String, write: (OutputStream) -> Unit): Uri {
        val cr = context.contentResolver
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, FOLDER)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: throw IllegalStateException("Không tạo được tệp")
        try {
            cr.openOutputStream(uri).use { write(requireNotNull(it)) }
        } catch (e: Exception) {
            cr.delete(uri, null, null); throw e
        }
        v.clear(); v.put(MediaStore.Images.Media.IS_PENDING, 0)
        cr.update(uri, v, null, null)
        return uri
    }

    private fun exifOrientation(deg: Int) = when (deg) {
        90 -> ExifInterface.ORIENTATION_ROTATE_90
        180 -> ExifInterface.ORIENTATION_ROTATE_180
        270 -> ExifInterface.ORIENTATION_ROTATE_270
        else -> ExifInterface.ORIENTATION_NORMAL
    }

    fun save(context: Context, shot: Shot, o: SaveOptions): Saved {
        val stamp = "LookCam_" + SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        var rawUri: Uri? = null
        // RAW (DNG)
        val raw = shot.raw
        val res = shot.result
        if (raw != null) {
            try {
                if (res != null) rawUri = insert(context, "$stamp.dng", "image/x-adobe-dng") { out ->
                    DngCreator(shot.chars, res).use { dng ->
                        dng.setOrientation(exifOrientation(shot.orientation))
                        dng.writeImage(out, raw)
                    }
                }
            } finally { raw.close() }
        }

        val r = o.recipe
        var origUri: Uri? = null
        if (o.keepOriginal || r == null) origUri = insert(context, "$stamp.jpg", "image/jpeg") { it.write(shot.jpeg) }

        var filtered: Uri? = null
        var thumbSrc: Bitmap? = null
        if (r != null) {
            // Nguon de giai ma: anh goc vua luu, hoac tep tam
            var temp: File? = null
            val src: Uri = origUri ?: File(context.cacheDir, "$stamp.jpg").also { temp = it; it.writeBytes(shot.jpeg) }.let { Uri.fromFile(it) }
            try {
                val bmp = AndroidImage.load(context, src, o.filteredMaxEdge)
                val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
                Pipeline.processTiled(BitmapSource(bmp), BitmapSink(out), emptyList(), EditSettings(r, o.strength, 0f, true))
                bmp.recycle()
                filtered = AndroidImage.saveJpeg(
                    context, out, "${stamp}_${r.look.code}.jpg", o.quality, src,
                    folder = FOLDER, description = "LookCam: ${r.name} (${r.look.code})", software = "LookCam",
                )
                thumbSrc = AndroidImage.scaled(out, 240)
                out.recycle()
            } finally { temp?.delete() }
        } else {
            thumbSrc = origUri?.let { runCatching { AndroidImage.load(context, it, 240) }.getOrNull() }
        }
        return Saved(origUri, filtered, rawUri, thumbSrc)
    }
}
