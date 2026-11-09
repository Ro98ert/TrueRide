package app.trueride.core.parse

import app.trueride.core.model.OfferField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * D-288.4: linia pe care lantul o reciteste marita pentru km la preluare = linia primului segment cand are
 * minute citite, dar km lipsesc (FARA_KM) sau sunt imposibili (KM_IMPLAUZIBIL, D-148). In orice alt caz nu se
 * reciteste nimic. Cifrele sunt inventate.
 */
class LiniaKmPreluareTest {

    private val en = OfferParser(UBER_EN_V1)
    private val ro = OfferParser(UBER_RO_V1)
    private val ambele = ParserPeLimbi(listOf(ro, en))

    private fun linie(text: String, sus: Int, inaltime: Int = 46) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private val corp = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }
    private val pret = linie("RON12.30", sus = 1232, inaltime = 90)
    private val cursa = linie("14 mins (7.2 km) ▢", sus = 1800)

    private fun oferta(preluare: LinieOcr) = corp + pret + preluare + cursa

    /** Ce se reciteste e exact ce explica motivul: cele doua nu pot diverge (D-109). */
    private fun coerent(p: ParserOferta, linii: List<LinieOcr>) {
        val motiv = p.motiveLipsa(linii)[OfferField.PICKUP_DISTANCE]
        val reciteste = p.liniaKmPreluareDeRecitit(linii) != null
        assertEquals(motiv == MotivLipsa.FARA_KM || motiv == MotivLipsa.KM_IMPLAUZIBIL, reciteste)
    }

    @Test
    fun `unitate necitita - linia preluarii e de recitit`() {
        val preluare = linie("6 mins (2.1 xm) ▢", sus = 1700)
        val linii = oferta(preluare)

        assertSame(preluare, en.liniaKmPreluareDeRecitit(linii))
        coerent(en, linii)
    }

    @Test
    fun `numar necitit - linia preluarii e de recitit`() {
        val preluare = linie("6 mins (▢ km) ▢", sus = 1700)

        assertSame(preluare, en.liniaKmPreluareDeRecitit(oferta(preluare)))
    }

    @Test
    fun `punct zecimal pierdut (KM_IMPLAUZIBIL) - linia preluarii e de recitit`() {
        val preluare = linie("3 mins (95 km) ▢", sus = 1700)
        val linii = oferta(preluare)

        assertEquals(MotivLipsa.KM_IMPLAUZIBIL, en.motiveLipsa(linii)[OfferField.PICKUP_DISTANCE])
        assertSame(preluare, en.liniaKmPreluareDeRecitit(linii))
        coerent(en, linii)
    }

    @Test
    fun `Uber RO - aceeasi regula`() {
        val preluare = linie("▢ 4 min. (1.5 xm)", sus = 1700)
        val linii = corp + linie("12,30 RON", sus = 1232, inaltime = 115) + preluare + linie("9 min. (4.5 km)", sus = 1800)

        assertSame(preluare, ro.liniaKmPreluareDeRecitit(linii))
        coerent(ro, linii)
    }

    @Test
    fun `km cititi nu se recitesc`() {
        val linii = oferta(linie("6 mins (2.1 km) ▢", sus = 1700))

        assertNull(en.liniaKmPreluareDeRecitit(linii))
        coerent(en, linii)
    }

    @Test
    fun `lipsa km la cursa nu cere recitire - doar preluarea`() {
        val linii = corp + pret + linie("6 mins (2.1 km) ▢", sus = 1700) + linie("14 mins (7.2 xm) ▢", sus = 1800)

        assertNull(en.liniaKmPreluareDeRecitit(linii))
    }

    @Test
    fun `fara minute citite nu se reciteste (se repara doar km)`() {
        val linii = oferta(linie("▢ mins (2.1 xm) ▢", sus = 1700))

        assertNull(en.liniaKmPreluareDeRecitit(linii))
    }

    @Test
    fun `un singur segment in cadru (ANCORE_NUMAR) nu se reciteste`() {
        val linii = corp + pret + linie("6 mins (2.1 xm) ▢", sus = 1700)

        assertNull(en.liniaKmPreluareDeRecitit(linii))
        coerent(en, linii)
    }

    @Test
    fun `fara nicio oferta pe ecran nu se reciteste nimic`() {
        assertNull(en.liniaKmPreluareDeRecitit(corp))
    }

    @Test
    fun `ParserPeLimbi cere linia setului castigator`() {
        val preluare = linie("6 mins (2.1 xm) ▢", sus = 1700)

        assertSame(preluare, ambele.liniaKmPreluareDeRecitit(oferta(preluare)))
        assertNull(ambele.liniaKmPreluareDeRecitit(oferta(linie("6 mins (2.1 km) ▢", sus = 1700))))
    }
}
