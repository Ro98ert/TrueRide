package app.trueride.core.parse

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * D-315 (pret_separator_alternativ): Uber EN afiseaza uneori virgula in locul punctului zecimal („RON12,47”).
 * Exact doua cifre dupa virgula = bani, nu separator de mii (mii ar avea trei). Orice alta forma ramane necunoscuta.
 * Toate cifrele sunt inventate.
 */
class PretSeparatorAlternativUberEnTest {

    private val en = OfferParser(UBER_EN_V1)

    private fun linie(text: String, sus: Int, inaltime: Int = 46) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private val corp = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }
    private val segmente = listOf(linie("6 mins (2.1 km) ▢", sus = 1700), linie("14 mins (7.2 km) ▢", sus = 1800))

    private fun cuPret(text: String) = corp + segmente + linie(text, sus = 1232, inaltime = 90)

    private fun egal(asteptat: String, real: BigDecimal?) =
        assertEquals(0, BigDecimal(asteptat).compareTo(requireNotNull(real) { "asteptat $asteptat, primit null" }))

    @Test
    fun `virgula cu doi bani se citeste ca pret zecimal`() {
        egal("13.62", en.parseaza(cuPret("RON13,62")).price)
    }

    @Test
    fun `virgula cu un singur ban ramane necunoscuta`() {
        assertNull(en.parseaza(cuPret("RON13,6")).price)
    }

    @Test
    fun `virgula cu trei cifre (mii) ramane necunoscuta`() {
        assertNull(en.parseaza(cuPret("RON1,362")).price)
    }

    @Test
    fun `punctul si virgula amestecate raman necunoscute`() {
        assertNull(en.parseaza(cuPret("RON1,3.62")).price)
    }

    @Test
    fun `pretul cu punct ramane neschimbat`() {
        egal("13.62", en.parseaza(cuPret("RON13.62")).price)
    }
}
