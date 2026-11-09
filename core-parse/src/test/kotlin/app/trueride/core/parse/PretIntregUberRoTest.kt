package app.trueride.core.parse

import app.trueride.core.model.OfferField
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * D-305: Uber RO accepta pretul intreg de exact 2 cifre („25 RON”) ca Uber EN (D-299), DOAR pe o linie pe care
 * lantul a marcat-o recitita (`LinieOcr.recitita`, pusa de lant, nu dedusa din text). Fara recitire ramane
 * FORMA_PRET. Linii sintetice, cifre inventate (fixturile reale sunt ignorate de git).
 */
class PretIntregUberRoTest {

    private val ro = OfferParser(UBER_RO_V1)
    private val nb = "\u00A0"

    private fun linie(text: String, sus: Int, inaltime: Int = 45, recitita: Boolean = false) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime), recitita)

    private val corp = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }
    private val segmente = listOf(linie("▢ 4 min. (1.5 km) ▢", sus = 1659), linie("▢ 9 min. (4.5 km)", sus = 1788))

    private fun oferta(textPret: String, recitita: Boolean) =
        corp + segmente + linie(textPret, sus = 1232, inaltime = 115, recitita = recitita)

    private fun pret(text: String, recitita: Boolean) = ro.parseaza(oferta(text, recitita)).price

    private fun egal(asteptat: String, real: BigDecimal?) =
        assertEquals(0, BigDecimal(asteptat).compareTo(requireNotNull(real) { "asteptat $asteptat, primit null" }))

    @Test
    fun `25 RON pe o linie recitita este pret, cu spatiu obisnuit sau neintrerupt`() {
        egal("25", pret("25 RON", recitita = true))
        egal("25", pret("25${nb}RON", recitita = true))
    }

    @Test
    fun `25 RON la prima citire ramane FORMA_PRET`() {
        assertNull(pret("25 RON", recitita = false))
        assertNull(pret("25${nb}RON", recitita = false))
        assertEquals(MotivLipsa.FORMA_PRET, ro.motiveLipsa(oferta("25 RON", recitita = false))[OfferField.PRICE])
    }

    @Test
    fun `litera corectata prin harta nu da pret intreg, nici dupa recitire`() {
        assertNull(pret("2O RON", recitita = true))
        assertNull(pret("l5 RON", recitita = true))
    }

    @Test
    fun `bonusul cu semn nu e pret, nici dupa recitire`() {
        assertNull(pret("+25 RON", recitita = true))
        assertNull(pret("-25 RON", recitita = true))
    }

    @Test
    fun `doar exact 2 cifre - 1 sau 3 cifre raman FORMA_PRET chiar si dupa recitire`() {
        listOf("125 RON", "947 RON", "9 RON").forEach {
            assertNull(pret(it, recitita = true), it)
            assertEquals(MotivLipsa.FORMA_PRET, ro.motiveLipsa(oferta(it, recitita = true))[OfferField.PRICE], it)
        }
    }

    @Test
    fun `pretul cu zecimale se citeste la fel, recitit sau nu`() {
        egal("25.50", pret("25,50 RON", recitita = false))
        egal("25.50", pret("25,50 RON", recitita = true))
    }

    @Test
    fun `setul Uber RO are forma de dupa recitire, versiunea e 1_10_0`() {
        assertEquals("""\d{2}""", UBER_RO_V1.pretFormaDupaRecitirePattern)
        assertEquals("1.19.0", UBER_RO_V1.version)
        assertNull(BOLT_RO_V1.pretFormaDupaRecitirePattern)
    }
}
