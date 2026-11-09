package app.trueride.core.calc

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Rotunjirea de afisare. Testele confirma si numarul de zecimale per indicator,
 * asa cum reiese din tabelul de iesiri din spec 4.5.
 */
class DisplayRoundingTest {

    @Test
    fun `sumele de bani se afiseaza cu doua zecimale`() {
        assertEquals(BigDecimal("9.26"), DisplayRounding.money(BigDecimal("9.261")))
    }

    @Test
    fun `venitul pe ora se afiseaza fara zecimale`() {
        assertEquals(BigDecimal("66"), DisplayRounding.perHour(BigDecimal("66.14")))
    }

    @Test
    fun `distantele se afiseaza cu doua zecimale`() {
        assertEquals(BigDecimal("14.70"), DisplayRounding.distance(BigDecimal("14.7")))
    }

    @Test
    fun `duratele se afiseaza fara zecimale`() {
        assertEquals(BigDecimal("30"), DisplayRounding.minutes(BigDecimal("30")))
    }

    @Test
    fun `rotunjirea la jumatate merge in sus`() {
        assertEquals(BigDecimal("2.25"), DisplayRounding.money(BigDecimal("2.245")))
    }

    @Test
    fun `o valoare indisponibila ramane indisponibila dupa rotunjire`() {
        // Rotunjirea nu inventeaza niciodata un zero dintr-un camp necunoscut.
        assertNull(DisplayRounding.money(null))
        assertNull(DisplayRounding.perHour(null))
        assertNull(DisplayRounding.distance(null))
        assertNull(DisplayRounding.minutes(null))
    }
}
