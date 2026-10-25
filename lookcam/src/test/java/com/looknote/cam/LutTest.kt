package com.looknote.cam

import com.looknote.ai.engine.ColorEngine
import com.looknote.app.model.BuiltInRecipes
import com.looknote.cam.lut.LutBuilder
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LutTest {
    @Test
    fun lutMatchesCpuEngine() {
        val rnd = java.util.Random(7)
        val px = IntArray(4000) { (255 shl 24) or rnd.nextInt(1 shl 24) }
        var worst = 0
        var sum = 0L
        for (r in BuiltInRecipes.all.filter { !it.isPP }) {
            val lut = LutBuilder.build(r)
            val cpu = ColorEngine.apply(px, px.size, 1, LutBuilder.colorOnly(r))
            for (i in px.indices) {
                val l = LutBuilder.lookup(lut, LutBuilder.SIZE, px[i])
                for (s in intArrayOf(0, 8, 16)) {
                    val d = abs(((l shr s) and 255) - ((cpu[i] shr s) and 255))
                    if (d > worst) worst = d
                    sum += d
                }
            }
        }
        val n = px.size * 3L * BuiltInRecipes.all.count { !it.isPP }
        assertTrue("sai lech TB ${sum.toDouble() / n}, toi da $worst", sum.toDouble() / n < 1.5 && worst <= 24)
    }

    @Test
    fun identityLutIsIdentity() {
        val lut = LutBuilder.build(null)
        val c = (255 shl 24) or (123 shl 16) or (45 shl 8) or 210
        val o = LutBuilder.lookup(lut, LutBuilder.SIZE, c)
        for (s in intArrayOf(0, 8, 16)) assertTrue(abs(((o shr s) and 255) - ((c shr s) and 255)) <= 1)
    }
}
