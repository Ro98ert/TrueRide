package app.trueride.core.parse

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * D-315 val13 (val13-prag-pret-uber-ro): pe arhiva reala un pret Uber RO real a iesit cu raportul inaltimii fata de mediana
 * putin sub 1,8 si se pierdea (SUB_PRAG). Pragul coboara la 1,6; o suma fara semn sub prag ramane necunoscuta.
 * Doar rapoarte de inaltime inventate (invariantul 2): mediana 40 px.
 */
class PragPretUberRoTest {

    private val uberRo = OfferParser(UBER_RO_V1)

    private fun linie(text: String, sus: Int, inaltime: Int = 40) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private fun corp() = listOf(1400, 1500, 1600, 1900, 2000).map { linie("▢ ▢ ▢", sus = it) }

    private fun segmente() = listOf(linie("4 min. (1.5 km)", sus = 1700), linie("20 min. (5.5 km)", sus = 1800))

    @Test
    fun `Uber RO - pret la 1,7 x mediana se citeste`() {
        val o = uberRo.parseaza(corp() + segmente() + linie("21,40 RON", sus = 1232, inaltime = 68))

        assertEquals(BigDecimal("21.40"), o.price)
    }

    @Test
    fun `Uber RO - suma fara semn la 1,55 x mediana ramane necunoscuta`() {
        val o = uberRo.parseaza(corp() + segmente() + linie("21,40 RON", sus = 1232, inaltime = 62))

        assertNull(o.price)
    }

    @Test
    fun `Uber RO - pretul la 1,7 x nu e luat de o a doua suma fara semn mai joasa`() {
        val o = uberRo.parseaza(
            corp() + segmente() + linie("21,40 RON", sus = 1232, inaltime = 68) + linie("5,00 RON", sus = 1300, inaltime = 62),
        )

        assertEquals(BigDecimal("21.40"), o.price)
    }

    @Test
    fun `Uber RO 1,16,0 - pragul inaltimii pretului e 1,6`() {
        assertEquals("1.19.0", UBER_RO_V1.version)
        assertEquals(BigDecimal("1.6"), UBER_RO_V1.reguli.pretInaltimeMinimaRelativa)
    }
}
