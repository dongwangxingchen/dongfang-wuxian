package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [DFWX] v1.18.0 新工具纯函数用例（先写用例后实现，工具精修铁律）。
 * HSL 向量取规范值（HSL and HSV）：#3366CC=(220°,60%,50%) 等。
 */
class ToolboxLogicJvmTest {

    @Test fun rgbToHsl_canonicalVectors() {
        assertHsl(255, 0, 0, 0, 100, 50)
        assertHsl(0, 255, 0, 120, 100, 50)
        assertHsl(0, 0, 255, 240, 100, 50)
        assertHsl(255, 255, 255, 0, 0, 100)
        assertHsl(0, 0, 0, 0, 0, 0)
        assertHsl(128, 128, 128, 0, 0, 50)
        assertHsl(255, 128, 0, 30, 100, 50)
        assertHsl(255, 255, 0, 60, 100, 50)
        assertHsl(51, 102, 204, 220, 60, 50) // #3366CC
    }

    private fun assertHsl(r: Int, g: Int, b: Int, h: Int, s: Int, l: Int) {
        val v = Toolbox.rgbToHsl(r, g, b)
        assertEquals("h($r,$g,$b)", h, v[0])
        assertEquals("s($r,$g,$b)", s, v[1])
        assertEquals("l($r,$g,$b)", l, v[2])
    }

    @Test fun colorInfo_containsAllThreeFormats() {
        val t = Toolbox.colorInfo(0xFF3366CC.toInt())
        assertTrue(t, t.contains("#3366CC"))
        assertTrue(t, t.contains("RGB(51, 102, 204)"))
        assertTrue(t, t.contains("HSL(220°, 60%, 50%)"))
    }

    @Test fun rmsToDb_referencePoints() {
        assertEquals(0.0, Toolbox.rmsToDb(32768.0), 0.01)
        assertEquals(-20.0, Toolbox.rmsToDb(3276.8), 0.05)
        assertEquals(-999.0, Toolbox.rmsToDb(0.0), 0.0001)
        assertTrue(Toolbox.rmsToDb(327.68) < -39 && Toolbox.rmsToDb(327.68) > -41)
    }
}
