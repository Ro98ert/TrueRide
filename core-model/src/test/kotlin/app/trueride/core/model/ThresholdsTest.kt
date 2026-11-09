package app.trueride.core.model

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** D-082: pragul Bun decide daca un indicator participa; un Excelent singur nu da verdict. */
class ThresholdsTest {

    @Test
    fun `doar pragurile Excelent nu activeaza verdictul`() {
        val t = Thresholds(excellentPerHour = BigDecimal("90"), excellentPerDistance = BigDecimal("3.00"))

        assertFalse(t.isAnyActive)
    }

    @Test
    fun `un prag Bun activeaza verdictul, cu sau fara Excelent`() {
        assertTrue(Thresholds(minPerDistance = BigDecimal("2.50")).isAnyActive)
        assertTrue(Thresholds(minPerDistance = BigDecimal("2.50"), excellentPerDistance = BigDecimal("3.00")).isAnyActive)
    }
}
