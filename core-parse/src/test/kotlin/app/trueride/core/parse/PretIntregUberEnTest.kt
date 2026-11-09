package app.trueride.core.parse

import app.trueride.core.model.OfferField
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * D-299: Uber EN accepta pretul intreg de exact 2 cifre, DOAR pe o linie pe care lantul a marcat-o recitita
 * (`LinieOcr.recitita`, pusa de lant, nu dedusa din text). Prima trecere ramane ca inainte: `RON25` = FORMA_PRET.
 * Matricea 1-13 din planul 2026-10-20-d299 (cazul 5, garda de margine, e in `RecitirePretIntregTest`). Cifrele sunt inventate.
 */
class PretIntregUberEnTest {

    private val en = OfferParser(UBER_EN_V1)
    private val nb = "\u00A0"

    private fun linie(text: String, sus: Int, inaltime: Int = 46, recitita: Boolean = false) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime), recitita)

    private val corp = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }
    private val segmente = listOf(linie("6 mins (2.1 km) ▢", sus = 1700), linie("14 mins (7.2 km) ▢", sus = 1800))

    private fun cuPret(text: String, recitita: Boolean = false) =
        corp + segmente + linie(text, sus = 1232, inaltime = 90, recitita = recitita)

    private fun pret(text: String, recitita: Boolean) = en.parseaza(cuPret(text, recitita)).price

    private fun egal(asteptat: String, real: BigDecimal?) =
        assertEquals(0, BigDecimal(asteptat).compareTo(requireNotNull(real) { "asteptat $asteptat, primit null" }))

    // ---------- #1: forma obisnuita, neschimbata

    @Test
    fun `pretul cu zecimale se citeste la fel, recitit sau nu`() {
        egal("25.50", pret("RON25.50", recitita = false))
        egal("25.50", pret("RON25.50", recitita = true))
    }

    // ---------- #2, #3: intregul de 2 cifre doar pe linia recitita

    @Test
    fun `RON25 pe o linie recitita este pret`() {
        egal("25", pret("RON25", recitita = true))
        assertEquals(BigDecimal.ONE, en.parseaza(cuPret("RON25", true)).fieldConfidence[OfferField.PRICE])
    }

    @Test
    fun `RON25 la prima citire ramane FORMA_PRET si linie de recitit`() {
        val linii = cuPret("RON25")

        assertNull(en.parseaza(linii).price)
        assertEquals(MotivLipsa.FORMA_PRET, en.motiveLipsa(linii)[OfferField.PRICE])
        assertNotNull(en.liniaPretFaraForma(linii))
    }

    @Test
    fun `dupa o recitire acceptata linia nu mai e de recitit si intra in liniile cardului`() {
        val linii = cuPret("RON25", recitita = true)

        assertNull(en.liniaPretFaraForma(linii))
        assertEquals(emptyMap(), en.motiveLipsa(linii).filterKeys { it == OfferField.PRICE })
        assertSame(linii.last(), en.liniiFolosite(linii).first())
    }

    // ---------- #4: punct pierdut (x100) si alte numere de cifre

    @Test
    fun `numerele de 1 sau 3-4 cifre raman necunoscute chiar si dupa recitire`() {
        listOf("RON947", "RON9", "RON1234").forEach {
            assertNull(pret(it, recitita = true), it)
        }
    }

    // ---------- #6, #9, #12: forme care nu sunt intreg de 2 cifre

    @Test
    fun `ultima cifra pierduta, virgula si separatorul de mii raman necunoscute dupa recitire`() {
        listOf("RON25.5", "RON1,234.56").forEach { // D-315: „RON25,50” (virgula + 2 bani) se citeste acum: PretSeparatorAlternativUberEnTest
            assertNull(pret(it, recitita = true), it)
        }
    }

    // ---------- #7: litera corectata (D-288.2) nu da pret intreg (D-299.6)

    @Test
    fun `un pret intreg obtinut dintr-o litera corectata ramane necunoscut dupa recitire`() {
        listOf("RON2O", "RONl5", "RONO5").forEach {
            val linii = cuPret(it, recitita = true)
            assertNull(en.parseaza(linii).price, it)
        }
    }

    // ---------- #8: spatiu dupa RON

    @Test
    fun `RON si 25 despartite de spatiu sau de spatiu neintrerupt`() {
        egal("25", pret("RON 25", recitita = true))
        egal("25", pret("RON${nb}25", recitita = true))
        assertNull(pret("RON 25", recitita = false))
        assertNull(pret("RON${nb}25", recitita = false))
        assertEquals(MotivLipsa.FORMA_PRET, en.motiveLipsa(cuPret("RON${nb}25"))[OfferField.PRICE])
    }

    @Test
    fun `spatiul neintrerupt dupa RON nu strica pretul cu zecimale`() {
        egal("25.50", pret("RON${nb}25.50", recitita = false))
    }

    // ---------- #10, #11: bonus si text de promotie

    @Test
    fun `bonusul cu semn nu e pret, nici dupa recitire`() {
        listOf("+RON25", "-RON25", "+ RON25").forEach {
            assertNull(pret(it, recitita = true), it)
        }
    }

    @Test
    fun `textul de promotie cu doua sume nu e pret, nici dupa recitire`() {
        assertNull(pret("RON20-RON30", recitita = true))
        assertNull(pret("RON20-RON30", recitita = false))
    }

    @Test
    fun `text scris dupa suma pe aceeasi linie nu da pret intreg, nici dupa recitire`() {
        listOf("RON25 ▢", "RON25 mins").forEach {
            assertNull(pret(it, recitita = true), it)
        }
    }

    // ---------- #13: litera din afara listei

    @Test
    fun `litera din afara listei lasa pretul necunoscut, recitit sau nu`() {
        assertNull(pret("RON2▢.50", recitita = false))
        assertNull(pret("RON2▢.50", recitita = true))
        assertNull(pret("RON2▢", recitita = true))
    }

    // ---------- mecanismul e date: doar Uber EN il are

    @Test
    fun `seturile Uber EN si Uber RO (D-305) au forma de dupa recitire, Bolt nu, versiunile sunt ridicate`() {
        assertEquals("""\d{2}""", UBER_EN_V1.pretFormaDupaRecitirePattern)
        assertEquals("""\d{2}""", UBER_RO_V1.pretFormaDupaRecitirePattern)
        assertNull(BOLT_RO_V1.pretFormaDupaRecitirePattern)
        assertEquals("1.11.0", UBER_EN_V1.version)
        assertEquals("1.19.0", UBER_RO_V1.version)
    }

    @Test
    fun `fara forma de dupa recitire linia marcata ramane pe FORMA_PRET`() {
        // O aplicatie care nu cunoaste campul (D-299.9): acelasi set, campul nul.
        val fara = OfferParser(UBER_EN_V1.copy(pretFormaDupaRecitirePattern = null))

        assertNull(fara.parseaza(cuPret("RON25", recitita = true)).price)
    }
}
