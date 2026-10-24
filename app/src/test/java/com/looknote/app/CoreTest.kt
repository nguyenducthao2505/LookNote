package com.looknote.app

import com.looknote.app.exif.ExifReader
import com.looknote.app.model.CreativeLook
import com.looknote.app.model.LookParam
import com.looknote.app.model.Recipe
import com.looknote.app.model.RecipeCodec
import com.looknote.app.model.WbShift
import com.looknote.app.model.WhiteBalance
import com.looknote.app.ptp.DeviceInfo
import com.looknote.app.ptp.PtpCodes
import com.looknote.app.ptp.PtpWriter
import com.looknote.app.ptp.SonyPropParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class CoreTest {

    @Test
    fun shareCodeRoundTrip() {
        val r = Recipe(name = "Thử nghiệm", look = CreativeLook.FL)
            .withValue(LookParam.CONTRAST, 3)
            .withValue(LookParam.FADE, 12) // bi gioi han ve 9
            .copy(wb = WhiteBalance.KELVIN, kelvin = 5200, wbAB = 1.5, wbGM = -0.75, notes = "Ghi chú")
        val code = RecipeCodec.toShareCode(r)
        assertTrue(code.startsWith(RecipeCodec.SHARE_PREFIX))
        val back = RecipeCodec.fromShareCode(code)
        assertNotNull(back)
        back!!
        assertEquals("Thử nghiệm", back.name)
        assertEquals(CreativeLook.FL, back.look)
        assertEquals(3, back.value(LookParam.CONTRAST))
        assertEquals(9, back.value(LookParam.FADE))
        assertEquals(0, back.value(LookParam.SHARPNESS_RANGE))
        assertEquals(5200, back.kelvin)
        assertEquals(1.5, back.wbAB, 0.0001)
        assertEquals(-0.75, back.wbGM, 0.0001)
        assertEquals("Ghi chú", back.notes)
    }

    @Test
    fun pictureProfileRoundTrip() {
        val r = com.looknote.app.model.BuiltInRecipes.all.first { it.isPP }
        val back = RecipeCodec.fromShareCode(RecipeCodec.toShareCode(r))!!
        assertTrue(back.isPP)
        assertEquals(r.pp, back.pp)
        assertEquals(r.source, back.source)
        assertEquals("PP", back.badge())
    }

    @Test
    fun builtInsAreValid() {
        val all = com.looknote.app.model.BuiltInRecipes.all
        assertEquals(all.size, all.map { it.id }.toSet().size)
        all.forEach { r ->
            LookParam.entries.forEach { p -> assertEquals(r.params[p] ?: p.default, r.value(p)) }
            assertEquals(WbShift.snap(r.wbAB, WbShift.STEP_AB), r.wbAB, 0.0)
            assertEquals(WbShift.snap(r.wbGM, WbShift.STEP_GM), r.wbGM, 0.0)
        }
        assertEquals(32, all.size)
    }

    @Test
    fun listRoundTrip() {
        val list = listOf(Recipe(name = "A"), Recipe(name = "B", favorite = true))
        val back = RecipeCodec.listFromString(RecipeCodec.listToString(list))
        assertEquals(2, back.size)
        assertEquals(list[1].id, back[1].id)
        assertTrue(back[1].favorite)
    }

    @Test
    fun wbFormatting() {
        assertEquals("A2", WbShift.formatAB(2.0))
        assertEquals("B0.5", WbShift.formatAB(-0.5))
        assertEquals("M1.25", WbShift.formatGM(-1.25))
        assertEquals(7.0, WbShift.snap(9.3, WbShift.STEP_AB), 0.0)
        assertEquals(0.25, WbShift.snap(0.3, WbShift.STEP_GM), 0.0)
    }

    // ---------- EXIF tong hop ----------

    private class Le {
        val o = ByteArrayOutputStream()
        fun u16(v: Int) { o.write(v and 0xFF); o.write((v shr 8) and 0xFF) }
        fun u32(v: Long) { for (i in 0 until 4) o.write(((v shr (8 * i)) and 0xFF).toInt()) }
        fun bytes(b: ByteArray) { o.write(b) }
        fun size() = o.size()
        fun toByteArray(): ByteArray = o.toByteArray()
    }

    private fun buildSonyJpeg(): ByteArray {
        // Bo cuc TIFF (offset tinh tu dau TIFF):
        // 0: header(8) | 8: IFD0 (3 the) | 50: Exif IFD (4 the) | 104: chuoi/du lieu | MakerNote
        val make = "SONY\u0000".toByteArray()
        val model = "ILCE-7CM2\u0000".toByteArray()
        val cs = "FL\u0000".toByteArray()

        val ifd0 = 8
        val ifd0Size = 2 + 3 * 12 + 4         // 42
        val exifIfd = ifd0 + ifd0Size           // 50
        val exifSize = 2 + 4 * 12 + 4           // 54
        val dataStart = exifIfd + exifSize      // 104
        val makeOff = dataStart
        val modelOff = makeOff + make.size
        val ratOff = modelOff + model.size      // 2 rational: exposure, fnumber
        val mnOff = ratOff + 16
        // MakerNote: "SONY DSC \0\0\0" + IFD (3 the) + chuoi CreativeStyle
        val mnIfd = mnOff + 12
        val mnIfdSize = 2 + 3 * 12 + 4
        val csOff = mnIfd + mnIfdSize
        val mnLen = 12 + mnIfdSize + cs.size

        val t = Le()
        t.bytes("II".toByteArray()); t.u16(42); t.u32(ifd0.toLong())
        // IFD0
        t.u16(3)
        t.u16(0x010F); t.u16(2); t.u32(make.size.toLong()); t.u32(makeOff.toLong())
        t.u16(0x0110); t.u16(2); t.u32(model.size.toLong()); t.u32(modelOff.toLong())
        t.u16(0x8769); t.u16(4); t.u32(1); t.u32(exifIfd.toLong())
        t.u32(0)
        // Exif IFD
        t.u16(4)
        t.u16(0x829A); t.u16(5); t.u32(1); t.u32(ratOff.toLong())
        t.u16(0x829D); t.u16(5); t.u32(1); t.u32((ratOff + 8).toLong())
        t.u16(0x8827); t.u16(3); t.u32(1); t.u32(400)
        t.u16(0x927C); t.u16(7); t.u32(mnLen.toLong()); t.u32(mnOff.toLong())
        t.u32(0)
        check(t.size() == dataStart)
        t.bytes(make); t.bytes(model)
        t.u32(1); t.u32(250)   // 1/250
        t.u32(28); t.u32(10)   // f/2.8
        // MakerNote
        t.bytes("SONY DSC ".toByteArray()); t.bytes(byteArrayOf(0, 0, 0))
        t.u16(3)
        t.u16(0x2004); t.u16(9); t.u32(1); t.u32(2L)                 // Contrast +2
        t.u16(0x2005); t.u16(9); t.u32(1); t.u32((-1L) and 0xFFFFFFFFL) // Saturation -1
        // Chuoi <= 4 byte duoc luu ngay trong the (inline)
        t.u16(0xB020); t.u16(2); t.u32(cs.size.toLong()); t.bytes(cs); t.o.write(0)
        t.u32(0)
        check(t.size() == csOff)
        t.bytes(cs)
        val tiff = t.toByteArray()

        val jpg = ByteArrayOutputStream()
        jpg.write(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        val app1Len = 2 + 6 + tiff.size
        jpg.write(byteArrayOf(0xFF.toByte(), 0xE1.toByte(), (app1Len shr 8).toByte(), (app1Len and 0xFF).toByte()))
        jpg.write("Exif".toByteArray()); jpg.write(byteArrayOf(0, 0))
        jpg.write(tiff)
        jpg.write(byteArrayOf(0xFF.toByte(), 0xD9.toByte()))
        return jpg.toByteArray()
    }

    @Test
    fun parseSyntheticSonyJpeg() {
        val info = ExifReader.parse(buildSonyJpeg())
        assertEquals("SONY", info.make)
        assertEquals("ILCE-7CM2", info.model)
        assertEquals(400, info.iso)
        assertEquals("1/250", info.shutterText())
        assertEquals("f/2.8", info.apertureText())
        val s = info.sony
        assertNotNull(s)
        assertEquals("FL", s!!.creativeStyleRaw)
        assertEquals(CreativeLook.FL, s.look)
        assertEquals(2, s.contrast)
        assertEquals(-1, s.saturation)
    }

    // ---------- PTP ----------

    @Test
    fun ptpContainer() {
        val c = PtpWriter.container(PtpCodes.TYPE_COMMAND, 0x1002, 1, listOf(1L))
        assertEquals(16, c.size)
        assertEquals(16, c[0].toInt())
        assertEquals(0x02, c[6].toInt())
        assertEquals(0x10, c[7].toInt())
    }

    private fun ptpString(s: String, out: Le) {
        if (s.isEmpty()) { out.o.write(0); return }
        out.o.write(s.length + 1)
        s.forEach { out.u16(it.code) }
        out.u16(0)
    }

    @Test
    fun deviceInfoParse() {
        val b = Le()
        b.u16(100); b.u32(0x11); b.u16(100); ptpString("Sony PTP Extensions", b); b.u16(0)
        b.u32(2); b.u16(0x1001); b.u16(0x9201)
        b.u32(0); b.u32(1); b.u16(0x5007); b.u32(0); b.u32(0)
        ptpString("Sony Corporation", b); ptpString("ILCE-7CM2", b); ptpString("1.00", b); ptpString("0123", b)
        val di = DeviceInfo.parse(b.toByteArray())
        assertEquals("ILCE-7CM2", di.model)
        assertEquals(listOf(0x1001, 0x9201), di.operations)
        assertEquals("0123", di.serialNumber)
    }

    @Test
    fun sonyPropsSingleAndDoubleEnum() {
        fun build(double: Boolean): ByteArray {
            val b = Le()
            b.u32(2); b.u32(0) // count u64
            // prop 1: range UINT16
            b.u16(0xD2AA); b.u16(0x0004); b.o.write(1); b.o.write(1)
            b.u16(5); b.u16(7); b.o.write(1); b.u16(0); b.u16(10); b.u16(1)
            // prop 2: enum INT8
            b.u16(0xD2BB); b.u16(0x0001); b.o.write(1); b.o.write(1)
            b.o.write(0); b.o.write(3); b.o.write(2)
            b.u16(3); b.o.write(1); b.o.write(2); b.o.write(3)
            if (double) { b.u16(2); b.o.write(2); b.o.write(3) }
            return b.toByteArray()
        }
        for (d in listOf(false, true)) {
            val props = SonyPropParser.parse(build(d), preferDoubleEnum = !d)
            assertEquals(2, props.size)
            assertEquals(0xD2AA, props[0].code)
            assertEquals(7L, props[0].current)
            assertEquals(3L, props[1].current)
            assertNotNull(props[1].enumValues)
        }
    }
}
