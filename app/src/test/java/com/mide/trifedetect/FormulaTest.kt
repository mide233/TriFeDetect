package com.mide.trifedetect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormulaTest {

    private fun eval(expr: String, cal: Double = 0.0, v: Double = 0.0) =
        Formula.eval(expr, cal, v)

    @Test
    fun basicArithmeticAndPrecedence() {
        assertEquals(14.0, eval("2 + 3 * 4"), 1e-9)
        assertEquals(20.0, eval("(2 + 3) * 4"), 1e-9)
        assertEquals(2.0, eval("8 / 4"), 1e-9)
        assertEquals(8.0, eval("2 ^ 3"), 1e-9)
        assertEquals(2.0, eval("2 ^ 3 ^ 0"), 1e-9) // 右结合: 2^(3^0)
        assertEquals(4.0, eval("-2 + 6"), 1e-9)
    }

    @Test
    fun unaryMinusAndNegativeResults() {
        assertEquals(-5.0, eval("-5"), 1e-9)
        assertEquals(-3.0, eval("2 - 5"), 1e-9)
    }

    @Test
    fun variables() {
        assertEquals(7.0, eval("val - cal", cal = 3.0, v = 10.0), 1e-9)
        assertEquals(30.0, eval("cal * val", cal = 3.0, v = 10.0), 1e-9)
        assertEquals(0.0, eval("val - cal", cal = 0.0, v = 0.0), 1e-9)
    }

    @Test
    fun functions() {
        assertEquals(4.0, eval("sqrt(16)"), 1e-9)
        assertEquals(5.0, eval("abs(-5)"), 1e-9)
        assertEquals(1.0, eval("ln(e)"), 1e-9)
        assertEquals(2.0, eval("log(100)"), 1e-9)
        assertEquals(3.0, eval("max(1, 3)"), 1e-9)
        assertEquals(1.0, eval("min(1, 3)"), 1e-9)
        assertEquals(0.0, eval("sin(0)"), 1e-9)
        assertEquals(2.0, eval("round(2.4)"), 1e-9)
    }

    @Test
    fun toExpressionRoundTrips() {
        val e = "val - cal * 2"
        val node = Formula.parse(e)
        val rendered = Formula.toExpression(node)
        assertEquals(Formula.eval(e, 3.0, 10.0), Formula.eval(rendered, 3.0, 10.0), 1e-9)
    }

    @Test
    fun invalidExpressionsThrow() {
        val bad = listOf("", "2 +", "foo(1)", "2 $ 3", "sin(1, 2)", "(1 + 2")
        for (b in bad) {
            var threw = false
            try {
                Formula.parse(b)
            } catch (e: FormulaException) {
                threw = true
            }
            assertTrue("expected FormulaException for '$b'", threw)
        }
    }
}
