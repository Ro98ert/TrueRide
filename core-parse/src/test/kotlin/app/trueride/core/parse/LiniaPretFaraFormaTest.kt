package app.trueride.core.parse

import app.trueride.core.model.OfferField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * D-173: linia pe care lantul o reciteste marita e exact linia aleasa ca pret si respinsa de forma (FORMA_PRET).
 * In orice alt caz nu se reciteste nimic: o recitire fara pret ales nu are ce repara. Cifrele sunt inventate.
 */
class LiniaPretFaraFormaTest {

    private val en = OfferParser(UBER_EN_V1)
    private val ro = OfferParser(UBER_RO_V1)

    private fun linie(text: String, sus: Int, inaltime: Int = 46) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private val corp = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }
    private val segmenteEn = listOf(linie("6 mins (2.1 km) ▢", sus = 1700), linie("14 mins (7.2 km) ▢", sus = 1800))
    private val segmenteRo = listOf(linie("▢ 4 min. (1.5 km) ▢", sus = 1700), linie("▢ 9 min. (4.5 km)", sus = 1800))

    /** Ce se reciteste e exact ce explica motivul: cele doua nu pot diverge (D-109). */
    private fun coerent(p: ParserOferta, linii: List<LinieOcr>) {
        val reciteste = p.liniaPretFaraForma(linii) != null
        assertEquals(p.motiveLipsa(linii)[OfferField.PRICE] == MotivLipsa.FORMA_PRET, reciteste)
    }

    @Test
    fun `Uber EN - pretul cu punctul pierdut e linia de recitit`() {
        val pret = linie("RON2540", sus = 1232, inaltime = 90)
        val linii = corp + segmenteEn + pret

        assertSame(pret, en.liniaPretFaraForma(linii))
        coerent(en, linii)
    }

    @Test
    fun `Uber RO - pretul cu virgula pierduta e linia de recitit`() {
        val pret = linie("2540 RON", sus = 1232, inaltime = 115)
        val linii = corp + segmenteRo + pret

        assertSame(pret, ro.liniaPretFaraForma(linii))
        coerent(ro, linii)
    }

    @Test
    fun `pretul cu forma buna nu se reciteste`() {
        val linii = corp + segmenteEn + linie("RON25.40", sus = 1232, inaltime = 90)

        assertNull(en.liniaPretFaraForma(linii))
        coerent(en, linii)
    }

    @Test
    fun `o suma scrisa ca textul obisnuit nu se reciteste (SUB_PRAG)`() {
        val linii = corp + segmenteEn + linie("RON2540", sus = 1232, inaltime = 46)

        assertNull(en.liniaPretFaraForma(linii))
        coerent(en, linii)
    }

    @Test
    fun `doar un bonus cu semn nu se reciteste`() {
        val linii = corp + segmenteEn + linie("+RON2540", sus = 1232, inaltime = 90)

        assertNull(en.liniaPretFaraForma(linii))
        coerent(en, linii)
    }

    @Test
    fun `pe mai multe limbi se reciteste linia setului castigator`() {
        val pret = linie("RON2540", sus = 1232, inaltime = 90)
        val linii = corp + segmenteEn + pret
        val pe = ParserPeLimbi(listOf(ro, en))

        assertSame(pret, pe.liniaPretFaraForma(linii))
        coerent(pe, linii)
    }
}
