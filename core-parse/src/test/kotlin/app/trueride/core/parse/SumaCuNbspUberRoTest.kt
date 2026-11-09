package app.trueride.core.parse

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * D-299.4: Uber RO accepta si spatiul neintrerupt (U+00A0) inainte de „RON”, ca Bolt (`\s` din regex-ul Java nu-l
 * prinde). Formele fara virgula sau cu separator de mii raman necunoscute / nu sunt pret (matricea 15-18 din plan).
 * Cifrele sunt inventate.
 */
class SumaCuNbspUberRoTest {

    private val ro = OfferParser(UBER_RO_V1)
    private val bolt = OfferParser(BOLT_RO_V1)
    private val nb = "\u00A0"

    private fun linie(text: String, sus: Int, inaltime: Int = 45) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private val corp = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }

    private fun ofertaRo(textPret: String) = corp + listOf(
        linie(textPret, sus = 1232, inaltime = 115),
        linie("▢ 4 min. (1.5 km) ▢", sus = 1659),
        linie("▢ 9 min. (4.5 km)", sus = 1788),
    )

    private fun ofertaBolt(textPret: String) = corp + listOf(
        linie(textPret, sus = 1232, inaltime = 115),
        linie("5 min$nb• 2.3 km", sus = 1659),
        linie("11 min$nb• 6.4 km", sus = 1788),
    )

    private fun pretRo(text: String): BigDecimal? = ro.parseaza(ofertaRo(text)).price

    @Test
    fun `25,50 NBSP RON se citeste ca 25,50 (matricea 15)`() {
        assertEquals(0, BigDecimal("25.50").compareTo(pretRo("25,50${nb}RON")))
    }

    @Test
    fun `spatiul obisnuit inainte de RON ramane valid`() {
        assertEquals(0, BigDecimal("25.50").compareTo(pretRo("25,50 RON")))
    }

    @Test
    fun `NBSP nu relaxeaza granitele - virgula pierduta ramane necunoscuta (matricea 16)`() {
        assertNull(pretRo("2550${nb}RON"))
        assertNull(pretRo("2550 RON"))
        assertNull(pretRo("25${nb}RON"))
    }

    @Test
    fun `sumele cu separator de mii (ecrane de castiguri) nu sunt pret (matricea 17)`() {
        assertNull(pretRo("1.234,56${nb}RON"))
        assertNull(pretRo("1.234,56 RON"))
        // Separatorul de mii poate fi si el spatiu neintrerupt: „1 234,56” nu e 234,56.
        assertNull(pretRo("1${nb}234,56 RON"))
        assertNull(pretRo("1${nb}234,56${nb}RON"))
    }

    @Test
    fun `Bolt ramane neschimbat - fara virgula sau cu litera nu e pret (matricea 18)`() {
        listOf("2550${nb}lei", "255${nb}lei", "25,5▢${nb}lei", "1.234,56${nb}lei").forEach {
            assertNull(bolt.parseaza(ofertaBolt(it)).price, it)
        }
    }
}
