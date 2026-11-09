package app.trueride.core.parse

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * D-367: linia pe care lantul o reciteste marita pentru MINUTELE de la preluare = linia primului segment cand km
 * sunt cititi, dar tokenul minutelor lipseste cu totul (OCR-ul a scapat cifra: „mins (2.1 km)”). Nu se reciteste cand
 * minutele au fost anulate de o garda (D-312 lipit de litera / „h ” / alt numar, D-315 peste 99) sau cand in fata
 * ancorei e o cifra ori o litera din harta: acolo cifra nu lipseste, e citita altfel. Cifrele sunt inventate.
 */
class LiniaMinutePreluareTest {

    private val en = OfferParser(UBER_EN_V1)
    private val ro = OfferParser(UBER_RO_V1)
    private val bolt = OfferParser(BOLT_RO_V1)
    private val ambele = ParserPeLimbi(listOf(ro, en))

    private fun linie(text: String, sus: Int, inaltime: Int = 46) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private val corp = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }
    private val pret = linie("RON12.30", sus = 1232, inaltime = 90)
    private val cursa = linie("14 mins (7.2 km) ▢", sus = 1800)

    private fun oferta(preluare: LinieOcr) = corp + pret + preluare + cursa

    @Test
    fun `Uber EN - tokenul minutelor lipseste, km cititi - linia preluarii e de recitit`() {
        val preluare = linie("mins (2.1 km) ▢", sus = 1700)
        val linii = oferta(preluare)

        assertSame(preluare, en.liniaMinutePreluareDeRecitit(linii))
        assertNull(en.liniaKmPreluareDeRecitit(linii))
    }

    @Test
    fun `Uber RO - aceeasi regula, forma min punct`() {
        val preluare = linie("min. (1.5 km)", sus = 1700)
        val linii = corp + linie("12,30 RON", sus = 1232, inaltime = 115) + preluare + linie("9 min. (4.5 km)", sus = 1800)

        assertSame(preluare, ro.liniaMinutePreluareDeRecitit(linii))
        assertNull(ro.liniaKmPreluareDeRecitit(linii))
    }

    @Test
    fun `Bolt - aceeasi regula, forma min punct-mijloc`() {
        val nb = " "
        val preluare = linie("min$nb• 2.3 km", sus = 1650)
        val linii = corp + linie("21,40${nb}lei", sus = 1375, inaltime = 114) + preluare + linie("11 min$nb• 6.4 km", sus = 1875)

        assertSame(preluare, bolt.liniaMinutePreluareDeRecitit(linii))
    }

    @Test
    fun `cifra in fata ancorei (D-312, garda a anulat minutele) nu cere recitire`() {
        listOf("a5 mins (2.1 km)", "3 h 5 mins (2.1 km)", "1h 5 mins (2.1 km)").forEach {
            assertNull(en.liniaMinutePreluareDeRecitit(oferta(linie(it, sus = 1700))), it)
        }
    }

    @Test
    fun `peste 99 de minute (D-315) nu cere recitire`() {
        assertNull(en.liniaMinutePreluareDeRecitit(oferta(linie("477 mins (2.1 km)", sus = 1700))))
    }

    @Test
    fun `litera din harta in fata ancorei (D-315 umflata la margine) nu cere recitire`() {
        assertNull(en.liniaMinutePreluareDeRecitit(oferta(linie("l55 mins (2.1 km)", sus = 1700))))
        assertNull(en.liniaMinutePreluareDeRecitit(oferta(linie("O mins (2.1 km)", sus = 1700))))
        // „lOl” (umflat la margine, D-315): minutele s-au citit prin harta, deci nu lipsesc (D-391.2 nu-l atinge).
        assertNull(en.liniaMinutePreluareDeRecitit(oferta(linie("lOl mins (2.1 km) ▢", sus = 1700))))
    }

    @Test
    fun `minute citite sau corectate din harta nu cer recitire`() {
        assertNull(en.liniaMinutePreluareDeRecitit(oferta(linie("6 mins (2.1 km) ▢", sus = 1700))))
        assertNull(en.liniaMinutePreluareDeRecitit(oferta(linie("l mins (2.1 km) ▢", sus = 1700))))
    }

    @Test
    fun `minute si km lipsa - nu se reciteste pentru minute, doar km se repara cand minutele exista`() {
        val linii = oferta(linie("mins (2.1 xm) ▢", sus = 1700))

        assertNull(en.liniaMinutePreluareDeRecitit(linii))
        assertNull(en.liniaKmPreluareDeRecitit(linii))
    }

    @Test
    fun `lipsa minutelor la cursa nu cere recitire - doar preluarea`() {
        val linii = corp + pret + linie("6 mins (2.1 km) ▢", sus = 1700) + linie("mins (7.2 km) ▢", sus = 1800)

        assertNull(en.liniaMinutePreluareDeRecitit(linii))
    }

    @Test
    fun `un singur segment in cadru (ANCORE_NUMAR) si ecran fara oferta nu recitesc nimic`() {
        assertNull(en.liniaMinutePreluareDeRecitit(corp + pret + linie("mins (2.1 km) ▢", sus = 1700)))
        assertNull(en.liniaMinutePreluareDeRecitit(corp))
    }

    @Test
    fun `ParserPeLimbi cere linia setului castigator`() {
        val preluare = linie("mins (2.1 km) ▢", sus = 1700)

        assertSame(preluare, ambele.liniaMinutePreluareDeRecitit(oferta(preluare)))
        assertNull(ambele.liniaMinutePreluareDeRecitit(oferta(linie("6 mins (2.1 km) ▢", sus = 1700))))
    }

    @Test
    fun `h in fata ancorei (ore + minute cu cifrele pierdute) nu cere recitire - o recitire ar da doar minutele din „1 h 5 min”`() {
        listOf("h mins (2.1 km)", "H mins (2.1 km)").forEach {
            assertNull(en.liniaMinutePreluareDeRecitit(oferta(linie(it, sus = 1700))), it)
        }
        val linii = corp + linie("21,40 RON", sus = 1232, inaltime = 115) + linie("h min. (1.5 km)", sus = 1700) + linie("9 min. (4.5 km)", sus = 1800)
        assertNull(ro.liniaMinutePreluareDeRecitit(linii))
    }

    @Test
    fun `Bolt - h in fata ancorei nu cere recitire, o alta litera singura da`() {
        val nb = "\u00A0"
        fun linii(preluare: LinieOcr) = corp + linie("21,40${nb}lei", sus = 1375, inaltime = 114) + preluare + linie("11 min$nb• 6.4 km", sus = 1875)
        val hOre = linie("h min$nb• 2.3 km", sus = 1650)
        val litera = linie("S min$nb• 2.3 km", sus = 1650)

        assertNull(bolt.liniaMinutePreluareDeRecitit(linii(hOre)))
        assertSame(litera, bolt.liniaMinutePreluareDeRecitit(linii(litera)))
    }

    @Test
    fun `o litera singura care nu e din harta si nu e h (cifra citita ca litera) cere recitire`() {
        val preluare = linie("S mins (2.1 km) ▢", sus = 1700)

        assertSame(preluare, en.liniaMinutePreluareDeRecitit(oferta(preluare)))
    }

    @Test
    fun `Uber RO - un cuvant fara litere din harta inaintea ancorei cere recitire, unul cu „o” nu`() {
        fun linii(preluare: LinieOcr) = corp + linie("12,30 RON", sus = 1232, inaltime = 115) + preluare + linie("9 min. (4.5 km)", sus = 1800)
        val cuvant = linie("La min. (1.5 km) ▢", sus = 1700)

        assertSame(cuvant, ro.liniaMinutePreluareDeRecitit(linii(cuvant)))
        // „Pornire” are „o” (din harta, ca „0”): textul de dinaintea ancorei nu e gol de cifre posibile.
        assertNull(ro.liniaMinutePreluareDeRecitit(linii(linie("Pornire min. (1.5 km) ▢", sus = 1700))))
    }

    // D-391.2 (F-058, modifica D-367.1): un cuvant doar din litere, 1-3 caractere, lipit de ancora = cifra citita ca litere.
    // Litere si cifre inventate; forma „Lal min” vine din investigatia D-122.

    @Test
    fun `D-391 - cuvant de 1-3 litere inaintea ancorei, chiar cu litere din harta, cere recitire`() {
        listOf("Lal", "xO", "La", "Z").forEach {
            val preluare = linie("$it mins (2.1 km) ▢", sus = 1700)
            assertSame(preluare, en.liniaMinutePreluareDeRecitit(oferta(preluare)), it)
        }
        val ro = ro
        val linieRo = linie("Lal min. (1.5 km) ▢", sus = 1700)
        val linii = corp + linie("12,30 RON", sus = 1232, inaltime = 115) + linieRo + linie("9 min. (4.5 km)", sus = 1800)
        assertSame(linieRo, ro.liniaMinutePreluareDeRecitit(linii))
    }

    @Test
    fun `D-391 - cifra lipita de litera, h, cuvant lung sau minute citite prin harta raman fara recitire`() {
        listOf("La5 mins (2.1 km)", "L5 mins (2.1 km)", "Lalo mins (2.1 km)", "Pornire mins (2.1 km)", "hl mins (2.1 km)", "Lh mins (2.1 km)", "lOl mins (2.1 km)")
            .forEach { assertNull(en.liniaMinutePreluareDeRecitit(oferta(linie(it, sus = 1700))), it) }
    }
}
