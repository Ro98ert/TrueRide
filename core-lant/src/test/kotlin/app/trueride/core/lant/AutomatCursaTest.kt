package app.trueride.core.lant

import app.trueride.core.lant.Eveniment.AsteptareExpirata
import app.trueride.core.lant.Eveniment.EcranSchimbat
import app.trueride.core.lant.Eveniment.NoduriCitite
import app.trueride.core.lant.Eveniment.OcrCitit
import app.trueride.core.lant.PlatformaSofer.BOLT
import app.trueride.core.lant.PlatformaSofer.UBER
import app.trueride.core.model.Thresholds
import app.trueride.core.parse.BOLT_RO_V1
import app.trueride.core.parse.Cutie
import app.trueride.core.parse.LinieOcr
import app.trueride.core.parse.OfferParser
import app.trueride.core.parse.UBER_RO_V1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * D-396.2: o cursa se numara o singura data, iar la nesiguranta nu se numara. Primele cinci teste trec evenimente
 * reale prin [AutomatCard] (liniile din [Ecrane]); restul lucreaza direct pe [AutomatCursa]. "Drum" vine de la detector:
 * aici e un parametru, deci liniile ecranului de drum nu se inventeaza (folosim [Ecrane.faraOferta], fara pret).
 */
class AutomatCursaTest {

    private val uber = OfferParser(UBER_RO_V1)
    private val automat = AutomatCard(
        mapOf(UBER to uber, BOLT to OfferParser(BOLT_RO_V1), PlatformaSofer.NECUNOSCUTA to uber),
    )
    private val start = StareLant(setari = Setari(cost = null, praguri = Thresholds()))

    /** Alt segment decat [Ecrane.ofertaCompleta], fara pret: peste un Succes da cercul (D-303). */
    private val altSegmentFaraPret: List<LinieOcr> = Ecrane.faraOferta + listOf(
        LinieOcr("6 min. (2.5 km)", Cutie(100, 1700, 700, 1745)),
        LinieOcr("12 min. (7.5 km)", Cutie(100, 1800, 700, 1845)),
    )

    /** Lantul real + automatul de numarare, cu ceasul dat de test (automatul n-are ceas). */
    private inner class Rulare {
        var pas = Pas(start)
        var cursa = StareCursa()
        var numarate = 0
        var oferteNoi = 0

        fun da(e: Eveniment, drum: Boolean?, acumMs: Long) {
            pas = automat.trateaza(pas.stare, e)
            val r = AutomatCursa.trateaza(cursa, pas.stare.card, drum, UBER, acumMs, FEREASTRA_MS)
            cursa = r.stare
            if (r.cursaNumarata) numarate++
            if (r.ofertaNoua) oferteNoi++
        }
    }

    private fun pas(s: StareCursa, card: StareCard, drum: Boolean?, t: Long, p: PlatformaSofer = UBER) =
        AutomatCursa.trateaza(s, card, drum, p, t, FEREASTRA_MS)

    /** Cardul `Succes` pe care lantul real il da pentru [linii] (ecran schimbat, apoi noduri citite). */
    private fun card(linii: List<LinieOcr>): StareCard =
        automat.trateaza(automat.trateaza(start, EcranSchimbat(1, UBER)).stare, NoduriCitite(1, linii)).stare.card

    private val oferta = card(Ecrane.ofertaCompleta)

    @Test
    fun `secventa reala acceptare cu Succes ramas si drum din noduri numara`() {
        val r = Rulare()
        r.da(EcranSchimbat(1, UBER), null, 0)
        r.da(NoduriCitite(1, Ecrane.ofertaCompleta), false, 100)
        r.da(EcranSchimbat(2, UBER), null, 5_000)
        r.da(NoduriCitite(2, Ecrane.faraOferta), true, 5_100)

        assertIs<StareCard.Succes>(r.pas.stare.card) // D-134: cardul ramane pana la recitire
        assertEquals(1, r.oferteNoi)
        assertEquals(1, r.numarate)
    }

    @Test
    fun `secventa reala oferta, Incarcare, apoi drum numara`() {
        val r = Rulare()
        r.da(EcranSchimbat(1, UBER), null, 0)
        r.da(NoduriCitite(1, Ecrane.ofertaCompleta), false, 100)
        r.da(EcranSchimbat(2, UBER), null, 5_000)
        r.da(NoduriCitite(2, emptyList()), false, 5_050)
        r.da(OcrCitit(2, altSegmentFaraPret), false, 5_100)
        assertIs<StareCard.Incarcare>(r.pas.stare.card)
        r.da(EcranSchimbat(3, UBER), null, 6_000)
        r.da(NoduriCitite(3, Ecrane.faraOferta), true, 6_100)

        assertEquals(1, r.numarate)
    }

    @Test
    fun `secventa reala oferta, Eroare necitibila, apoi drum numara`() {
        val r = Rulare()
        r.da(EcranSchimbat(1, UBER), null, 0)
        r.da(NoduriCitite(1, Ecrane.ofertaCompleta), false, 100)
        r.da(EcranSchimbat(2, UBER), null, 5_000)
        r.da(NoduriCitite(2, emptyList()), false, 5_050)
        r.da(OcrCitit(2, altSegmentFaraPret), false, 5_100)
        r.da(AsteptareExpirata(2), null, 8_100)
        assertIs<StareCard.Eroare>(r.pas.stare.card)
        r.da(EcranSchimbat(3, UBER), null, 9_000)
        r.da(NoduriCitite(3, Ecrane.faraOferta), true, 9_100)

        assertEquals(1, r.numarate)
    }

    @Test
    fun `secventa reala refuz fara ecran de drum nu numara`() {
        val r = Rulare()
        r.da(EcranSchimbat(1, UBER), null, 0)
        r.da(NoduriCitite(1, Ecrane.ofertaCompleta), false, 100)
        r.da(EcranSchimbat(2, UBER), null, 5_000)
        r.da(NoduriCitite(2, Ecrane.faraOferta), false, 5_050)
        r.da(OcrCitit(2, Ecrane.faraOferta), false, 5_100)

        assertEquals(StareCard.Gol, r.pas.stare.card)
        assertEquals(1, r.oferteNoi)
        assertEquals(0, r.numarate)
    }

    @Test
    fun `schimbarea fara linii nici nu confirma, nici nu sterge candidata`() {
        val s = StareCursa(
            ultimaOferta = null, candidata = true, platforma = UBER, ofertaLaMs = 1_000, ultimulDrumMs = 500,
        )

        val p = pas(s, StareCard.Gol, null, 2_000)

        assertEquals(s, p.stare)
        assertFalse(p.cursaNumarata)
    }

    @Test
    fun `aceeasi oferta citita de mai multe ori numara o singura data`() {
        val altPret = card(Ecrane.ofertaAltPret) // un singur camp diferit: aceeasi oferta (D-368.5)
        val p1 = pas(StareCursa(), oferta, null, 0)
        val p2 = pas(p1.stare, altPret, null, 1_000)
        val p3 = pas(p2.stare, oferta, null, 2_000)
        val d1 = pas(p3.stare, oferta, true, 3_000)
        val d2 = pas(d1.stare, oferta, true, 4_000)

        assertEquals(listOf(true, false, false), listOf(p1, p2, p3).map { it.ofertaNoua })
        assertEquals(listOf(true, false), listOf(d1, d2).map { it.cursaNumarata })
    }

    @Test
    fun `oferta expirata nu numara`() {
        val p1 = pas(StareCursa(), oferta, null, 0)

        val d = pas(p1.stare, StareCard.Gol, true, FEREASTRA_MS + 1)

        assertFalse(d.cursaNumarata)
    }

    @Test
    fun `refuz peste un drum deja vazut nu numara`() {
        val d0 = pas(StareCursa(), StareCard.Gol, true, 0)
        val p1 = pas(d0.stare, oferta, null, 5_000)
        val d1 = pas(p1.stare, oferta, true, 6_000)

        assertTrue(p1.ofertaNoua)
        assertFalse(p1.stare.candidata) // drumul vazut acum 5 s nu dovedeste ca aceasta oferta a fost acceptata
        assertFalse(d1.cursaNumarata)
    }

    @Test
    fun `un drum vechi nu blocheaza oferta urmatoare`() {
        val d0 = pas(StareCursa(), StareCard.Gol, true, 0)
        val p1 = pas(d0.stare, oferta, null, FEREASTRA_MS + 10_000)
        val d1 = pas(p1.stare, oferta, true, FEREASTRA_MS + 11_000)

        assertTrue(p1.stare.candidata)
        assertTrue(d1.cursaNumarata)
    }

    @Test
    fun `ecranul de drum fara oferta nu numara`() {
        val d = pas(StareCursa(), StareCard.Gol, true, 1_000)

        assertFalse(d.cursaNumarata)
        assertEquals(1_000L, d.stare.ultimulDrumMs)
    }

    @Test
    fun `alta platforma nu confirma oferta`() {
        val p1 = pas(StareCursa(), oferta, null, 0, UBER)

        val d = pas(p1.stare, oferta, true, 1_000, BOLT)

        assertFalse(d.cursaNumarata)
        assertTrue(d.stare.candidata)
    }

    @Test
    fun `un card de eroare fara oferta inainte nu e candidat`() {
        val d = pas(StareCursa(), StareCard.Eroare(UBER, MotivEroare.NECITIBILA), true, 1_000)

        assertFalse(d.cursaNumarata)
        assertFalse(d.stare.candidata)
    }

    private companion object {
        /** Valoare doar pentru test; constanta reala vine din app-produs (Task 17). */
        const val FEREASTRA_MS = 30_000L
    }
}
