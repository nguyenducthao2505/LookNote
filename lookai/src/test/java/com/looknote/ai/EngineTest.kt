package com.looknote.ai

import com.looknote.ai.engine.ColorEngine
import com.looknote.ai.engine.Face
import com.looknote.ai.engine.SkinRetouch
import com.looknote.app.model.BuiltInRecipes
import com.looknote.app.model.CreativeLook
import com.looknote.app.model.LookParam
import com.looknote.app.model.Recipe
import com.looknote.app.model.WhiteBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class EngineTest {
    private fun rgb(r: Int, g: Int, b: Int) = (255 shl 24) or (r shl 16) or (g shl 8) or b
    private fun ch(p: Int, c: Int) = (p shr (16 - 8 * c)) and 255

    private fun gradient(w: Int, h: Int) = IntArray(w * h) {
        val x = it % w; val y = it / w
        rgb(x * 255 / (w - 1), y * 255 / (h - 1), ((x + y) * 127) / (w + h - 2))
    }

    @Test
    fun standardIsNearIdentity() {
        val w = 32; val h = 32
        val src = gradient(w, h)
        val out = ColorEngine.apply(src, w, h, Recipe(look = CreativeLook.ST))
        var maxDiff = 0
        for (i in src.indices) for (c in 0..2) maxDiff = maxOf(maxDiff, abs(ch(src[i], c) - ch(out[i], c)))
        assertTrue("ST khong doi anh, lech toi da $maxDiff", maxDiff <= 2)
    }

    @Test
    fun bwIsGray() {
        val src = gradient(16, 16)
        val out = ColorEngine.apply(src, 16, 16, Recipe(look = CreativeLook.BW))
        out.forEach { assertTrue(ch(it, 0) == ch(it, 1) && ch(it, 1) == ch(it, 2)) }
    }

    @Test
    fun strengthZeroIsIdentity() {
        val src = gradient(16, 16)
        val r = BuiltInRecipes.all.first { !it.isPP && it.look == CreativeLook.FL }
        val out = ColorEngine.apply(src, 16, 16, r, 0f)
        assertTrue(src.contentEquals(out))
    }

    @Test
    fun warmKelvinAddsRed() {
        val gray = IntArray(4) { rgb(128, 128, 128) }
        val warm = ColorEngine.apply(gray, 2, 2, Recipe(look = CreativeLook.ST, wb = WhiteBalance.KELVIN, kelvin = 7500))
        val cool = ColorEngine.apply(gray, 2, 2, Recipe(look = CreativeLook.ST, wb = WhiteBalance.KELVIN, kelvin = 3500))
        assertTrue(ch(warm[0], 0) > ch(warm[0], 2))
        assertTrue(ch(cool[0], 0) < ch(cool[0], 2))
        val amber = ColorEngine.apply(gray, 2, 2, Recipe(look = CreativeLook.ST, wbAB = 3.0))
        assertTrue(ch(amber[0], 0) > ch(amber[0], 2))
    }

    @Test
    fun contrastAndFadeShapeCurve() {
        val hi = ColorEngine.buildToneCurve(Recipe(look = CreativeLook.ST).withValue(LookParam.CONTRAST, 6))
        assertTrue(hi[100] < 100 / 1023f) // toi hon
        assertTrue(hi[900] > 900 / 1023f) // sang hon
        val fade = ColorEngine.buildToneCurve(Recipe(look = CreativeLook.ST).withValue(LookParam.FADE, 5))
        assertTrue(fade[0] > 0.08f)
    }

    @Test
    fun saturationReducesChroma() {
        val red = IntArray(4) { rgb(200, 60, 60) }
        val out = ColorEngine.apply(red, 2, 2, Recipe(look = CreativeLook.ST).withValue(LookParam.SATURATION, -9))
        assertTrue(ch(out[0], 0) - ch(out[0], 1) < 140)
    }

    @Test
    fun allBuiltInsRun() {
        val w = 40; val h = 30
        val src = gradient(w, h)
        BuiltInRecipes.all.filter { !it.isPP }.forEach { r ->
            val out = ColorEngine.apply(src, w, h, r)
            assertEquals(src.size, out.size)
        }
    }

    @Test
    fun skinMaskAndSmoothing() {
        assertTrue(SkinRetouch.skinProbability(224, 172, 140) > 0.5f) // da sang
        assertTrue(SkinRetouch.skinProbability(40, 120, 200) == 0f)   // xanh duong
        // Mang da co nhieu, phai min hon sau loc
        val w = 60; val h = 60
        val rnd = java.util.Random(1)
        val src = IntArray(w * h) { val n = rnd.nextInt(21) - 10; rgb(220 + n / 2, 170 + n, 140 + n) }
        val res = SkinRetouch.apply(src, w, h, listOf(Face(30f, 25f, 14f)), 1f, faceOnly = true)
        fun variance(a: IntArray): Double {
            val v = a.map { ch(it, 1).toDouble() }; val m = v.average(); return v.sumOf { (it - m) * (it - m) } / v.size
        }
        assertTrue("phu da ${res.maskCoverage}", res.maskCoverage > 0.05f)
        assertTrue(variance(res.pixels) < variance(src) * 0.8)
        // Khong co mat + chi vung mat -> giu nguyen
        assertTrue(SkinRetouch.apply(src, w, h, emptyList(), 1f, true).pixels.contentEquals(src))
    }
}

class TileTest {
    private fun rgb(r: Int, g: Int, b: Int) = (255 shl 24) or (r shl 16) or (g shl 8) or b

    @org.junit.Test
    fun tiledMatchesWhole() {
        val w = 300; val h = 220
        val rnd = java.util.Random(3)
        val src = IntArray(w * h) {
            val x = it % w; val y = it / w
            val n = rnd.nextInt(17) - 8
            if ((x - 150) * (x - 150) + (y - 100) * (y - 100) < 60 * 60) rgb(220 + n / 2, 168 + n, 138 + n)
            else rgb((x * 255 / w), (y * 255 / h), 90 + n)
        }
        val faces = listOf(com.looknote.ai.engine.Face(150f, 85f, 40f))
        val recipe = com.looknote.app.model.BuiltInRecipes.all.first { it.id == "builtin-kelvin-movie" }
        val st = com.looknote.ai.engine.EditSettings(recipe, 1f, 0.8f, true)
        val whole = com.looknote.ai.engine.Pipeline.process(src, w, h, faces, st)
        val out = IntArray(w * h)
        val source = object : com.looknote.ai.engine.PixelSource {
            override val width = w
            override val height = h
            override fun read(x: Int, y: Int, ww: Int, hh: Int) = IntArray(ww * hh) { src[(y + it / ww) * w + x + it % ww] }
        }
        val sink = object : com.looknote.ai.engine.PixelSink {
            override fun write(x: Int, y: Int, ww: Int, hh: Int, px: IntArray) {
                for (i in px.indices) out[(y + i / ww) * w + x + i % ww] = px[i]
            }
        }
        com.looknote.ai.engine.Pipeline.processTiled(source, sink, faces, st, tile = 64)
        var maxDiff = 0
        for (i in out.indices) for (c in 0..2) {
            val d = kotlin.math.abs(((out[i] shr (8 * c)) and 255) - ((whole[i] shr (8 * c)) and 255))
            if (d > maxDiff) maxDiff = d
        }
        org.junit.Assert.assertTrue("lech $maxDiff", maxDiff <= 3)
    }
}
