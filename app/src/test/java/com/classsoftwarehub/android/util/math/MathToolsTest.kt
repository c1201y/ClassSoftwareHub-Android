package com.classsoftwarehub.android.util.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MathToolsTest {

    private fun eval(expression: String, x: Double = 0.0, unit: AngleUnit = AngleUnit.RADIANS): Double =
        ExpressionParser.compile(expression, unit).eval(x)

    @Test
    fun parserHandlesBasicOperatorsAndImplicitMultiplication() {
        assertEquals(7.0, eval("2x + 1", 3.0), 1e-9)
        assertEquals(9.0, eval("3(x + 1)", 2.0), 1e-9)
        assertEquals(-4.0, eval("-x^2", 2.0), 1e-9)
        assertEquals(512.0, eval("2^3^2"), 1e-9)
        assertEquals(6.0, eval("2*3"), 1e-9)
    }

    @Test
    fun parserSupportsFunctionsAndAngleModes() {
        assertEquals(3.0, eval("log(8, 2)"), 1e-9)
        assertEquals(0.5, eval("sin(30)", unit = AngleUnit.DEGREES), 1e-9)
        assertEquals(30.0, eval("asin(0.5)", unit = AngleUnit.DEGREES), 1e-9)
        assertEquals(Math.E, eval("e"), 1e-9)
    }

    @Test
    fun solverFindsPolynomialRoots() {
        val result = EquationSolver.solve("x^2 - 4 = 0")
        assertTrue(result.ok)
        assertEquals(listOf("x = -2", "x = 2"), result.solutions)
    }

    @Test
    fun solverFindsSpecialTrigRoots() {
        val result = EquationSolver.solve("sin(x) = 0.5")
        assertTrue(result.ok)
        assertTrue(result.solutions.contains("x = π/6"))
        assertTrue(result.solutions.contains("x = 5π/6"))
    }

    @Test
    fun solverHandlesLinearAndNoSolution() {
        assertEquals(listOf("x = 2"), EquationSolver.solve("2x + 1 = 5").solutions)
        assertTrue(EquationSolver.solve("x^2 + 1 = 0").solutions.isEmpty())
        assertNotNull(EquationSolver.solve("没有等号").error)
    }

    @Test
    fun balancerHandlesSimpleAndNestedFormulas() {
        assertEquals("4Fe + 3O2 = 2Fe2O3", ChemistryBalancer.balance("Fe + O2 = Fe2O3").equation)
        assertEquals("2H2 + O2 = 2H2O", ChemistryBalancer.balance("H2 + O2 = H2O").equation)
        assertEquals(
            "3Ca(OH)2 + 2H3PO4 = Ca3(PO4)2 + 6H2O",
            ChemistryBalancer.balance("Ca(OH)2 + H3PO4 = Ca3(PO4)2 + H2O").equation,
        )
    }

    @Test
    fun balancerKeepsHydrateCoefficientsCorrect() {
        val result = ChemistryBalancer.balance("CuSO4·5H2O = CuSO4 + H2O")
        assertTrue(result.ok)
        assertEquals("CuSO4·5H2O = CuSO4 + 5H2O", result.equation)
        assertTrue(result.checks.all { it.left == it.right })
    }

    @Test
    fun balancerReportsUnbalancedElements() {
        assertNotNull(ChemistryBalancer.balance("H2 = O2").error)
        assertNotNull(ChemistryBalancer.balance("Fe + O2").error)
        assertNotNull(ChemistryBalancer.balance("Xx + O2 = XxO2").error)
    }
}
