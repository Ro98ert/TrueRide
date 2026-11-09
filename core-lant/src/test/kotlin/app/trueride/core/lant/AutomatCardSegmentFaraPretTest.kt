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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * D-303: un cadru cu segment, dar fara pret, nu pune imediat „Nu am putut citi”. Aceeasi oferta
 * lasa cardul pe loc; oferta noua da cercul (sau Gol) si asteapta [LIMITA_CERC_MS]; eroarea apare
 * doar daca pretul lipseste si dupa limita. Cifre sintetice (invariantul 2).
 */
class AutomatCardSegmentFaraPretTest {

    private val uber = OfferParser(UBER_RO_V1)
    private val automat = AutomatCard(
        mapOf(UBER to uber, BOLT to OfferParser(BOLT_RO_V1), PlatformaSofer.NECUNOSCUTA to uber),
    )
    private val start = StareLant(setari = Setari(cost = null, praguri = Thresholds()))

    private fun dupa(pas: Pas, vararg evenimente: Eveniment): Pas =
        evenimente.fold(pas) { p, e -> automat.trateaza(p.stare, e) }

    /** Cardul arata oferta n=1 cu cifre (segmentele 4 min/1.5 km si 9 min/4.5 km). */
    private val cuCifre = dupa(Pas(start), EcranSchimbat(1, UBER), NoduriCitite(1, Ecrane.ofertaCompleta))

    /** Alt segment decat Ecrane.ofertaCompleta, fara pret. */
    private val altSegmentFaraPret: List<LinieOcr> = Ecrane.faraOferta + listOf(
        LinieOcr("6 min. (2.5 km)", Cutie(100, 1700, 700, 1745)),
        LinieOcr("12 min. (7.5 km)", Cutie(100, 1800, 700, 1845)),
    )

    private fun ocrFaraPret(pas: Pas, n: Long, linii: List<LinieOcr>): Pas =
        dupa(pas, EcranSchimbat(n, UBER), NoduriCitite(n, emptyList()), OcrCitit(n, linii))

    @Test
    fun `acelasi segment fara pret peste un card cu cifre lasa cardul exact pe loc`() {
        val pas = ocrFaraPret(cuCifre, 2, Ecrane.ofertaFaraPret)

        assertSame(cuCifre.stare.card, pas.stare.card)
        assertEquals(Etapa.GATA, pas.stare.etapa)
        assertEquals(emptyList<Comanda>(), pas.comenzi)
    }

    @Test
    fun `alt segment fara pret peste un card cu cifre da cercul fara cifre vechi`() {
        val pas = ocrFaraPret(cuCifre, 2, altSegmentFaraPret)

        assertEquals(StareCard.Incarcare(UBER), pas.stare.card)
        assertFalse(pas.stare.card.toString().any { it.isDigit() }, pas.stare.card.toString())
        assertEquals(Etapa.GATA, pas.stare.etapa)
    }

    @Test
    fun `alt segment fara pret peste un card cu cifre porneste limita`() {
        // Peste un Succes de aceeasi platforma schimbarea de ecran nu pune cerc (D-134), deci limita porneste din OCR.
        val schimbat = dupa(cuCifre, EcranSchimbat(2, UBER), NoduriCitite(2, emptyList()))
        assertEquals(emptyList<Comanda>(), schimbat.comenzi.filterIsInstance<Comanda.PornesteAsteptare>())

        val pas = dupa(schimbat, OcrCitit(2, altSegmentFaraPret))

        assertEquals(listOf<Comanda>(Comanda.PornesteAsteptare(2)), pas.comenzi)
        assertEquals(2, pas.stare.nCerc)
    }

    @Test
    fun `daca limita acestui n ruleaza deja nu se porneste a doua`() {
        val eroare = dupa(ocrFaraPret(Pas(start), 1, Ecrane.ofertaFaraPret), AsteptareExpirata(1))
        assertEquals(StareCard.Eroare(UBER, MotivEroare.NECITIBILA), eroare.stare.card)
        val inCerc = dupa(eroare, EcranSchimbat(2, UBER), NoduriCitite(2, emptyList()))
        assertEquals(StareCard.Incarcare(UBER), inCerc.stare.card)

        val pas = dupa(inCerc, OcrCitit(2, Ecrane.ofertaFaraPret))

        assertEquals(StareCard.Incarcare(UBER), pas.stare.card)
        assertEquals(emptyList<Comanda>(), pas.comenzi)
        assertEquals(StareCard.Eroare(UBER, MotivEroare.NECITIBILA), dupa(pas, AsteptareExpirata(2)).stare.card)
    }

    @Test
    fun `din Gol un segment fara pret ramane Gol si porneste limita`() {
        val pas = ocrFaraPret(Pas(start), 1, Ecrane.ofertaFaraPret)

        assertEquals(StareCard.Gol, pas.stare.card)
        assertEquals(listOf<Comanda>(Comanda.PornesteAsteptare(1)), pas.comenzi)
        assertEquals(1, pas.stare.nCerc)
    }

    @Test
    fun `dupa limita, cu pretul tot lipsa, apare eroarea necitibila`() {
        val pas = dupa(ocrFaraPret(Pas(start), 1, Ecrane.ofertaFaraPret), AsteptareExpirata(1))

        assertEquals(StareCard.Eroare(UBER, MotivEroare.NECITIBILA), pas.stare.card)
        assertEquals(Etapa.GATA, pas.stare.etapa)
    }

    @Test
    fun `dupa limita, peste cerc, apare eroarea necitibila`() {
        val pas = dupa(ocrFaraPret(cuCifre, 2, altSegmentFaraPret), AsteptareExpirata(2))

        assertEquals(StareCard.Eroare(UBER, MotivEroare.NECITIBILA), pas.stare.card)
    }

    @Test
    fun `un cadru bun inainte de limita da cardul, iar limita de dupa nu schimba nimic`() {
        val inAsteptare = ocrFaraPret(Pas(start), 1, Ecrane.ofertaFaraPret)
        val bun = dupa(inAsteptare, EcranSchimbat(2, UBER), NoduriCitite(2, Ecrane.ofertaCompleta))
        val card = assertIs<StareCard.Succes>(bun.stare.card)

        val pas = dupa(bun, AsteptareExpirata(1), AsteptareExpirata(2))

        assertSame(card, pas.stare.card)
    }

    @Test
    fun `un cadru bun din OCR dupa asteptare da cardul, iar limita nu mai are efect`() {
        val inAsteptare = ocrFaraPret(Pas(start), 1, Ecrane.ofertaFaraPret)
        val bun = dupa(inAsteptare, EcranSchimbat(2, UBER), NoduriCitite(2, emptyList()), OcrCitit(2, Ecrane.ofertaCompleta))
        assertIs<StareCard.Succes>(bun.stare.card)

        assertSame(bun.stare.card, dupa(bun, AsteptareExpirata(1)).stare.card)
    }

    @Test
    fun `o schimbare de ecran, un ecran gol sau inchiderea sterg pendinta`() {
        val inAsteptare = ocrFaraPret(Pas(start), 1, Ecrane.ofertaFaraPret)

        val schimbat = dupa(inAsteptare, EcranSchimbat(2, UBER))
        assertEquals(StareCard.Gol, dupa(schimbat, AsteptareExpirata(1)).stare.card)

        val gol = dupa(inAsteptare, EcranSchimbat(2, UBER), NoduriCitite(2, emptyList()), OcrCitit(2, Ecrane.faraOferta))
        assertEquals(StareCard.Gol, dupa(gol, AsteptareExpirata(1)).stare.card)

        val inchis = dupa(inAsteptare, Eveniment.Inchis)
        assertEquals(StareCard.Gol, dupa(inchis, AsteptareExpirata(1)).stare.card)
    }

    @Test
    fun `un rezultat al unei schimbari vechi nu atinge asteptarea`() {
        val inAsteptare = ocrFaraPret(Pas(start), 1, Ecrane.ofertaFaraPret)
        val pas = dupa(inAsteptare, EcranSchimbat(2, UBER), OcrCitit(1, Ecrane.ofertaCompleta))

        assertEquals(StareCard.Gol, pas.stare.card)
    }

    @Test
    fun `cerc de la un n mai vechi, apoi segment fara pret, limita noua, cea veche nu face nimic`() {
        val eroare = dupa(ocrFaraPret(Pas(start), 1, Ecrane.ofertaFaraPret), AsteptareExpirata(1))
        val inCerc = dupa(eroare, EcranSchimbat(2, UBER), EcranSchimbat(3, UBER), NoduriCitite(3, emptyList()))
        assertEquals(StareCard.Incarcare(UBER), inCerc.stare.card)
        assertEquals(2, inCerc.stare.nCerc)

        val pas = dupa(inCerc, OcrCitit(3, Ecrane.ofertaFaraPret))

        assertEquals(listOf<Comanda>(Comanda.PornesteAsteptare(3)), pas.comenzi)
        assertEquals(3, pas.stare.nCerc)
        assertEquals(pas.stare, dupa(pas, AsteptareExpirata(2)).stare)
        assertEquals(StareCard.Eroare(UBER, MotivEroare.NECITIBILA), dupa(pas, AsteptareExpirata(3)).stare.card)
    }

    @Test
    fun `segment partial fara pret nu e aceeasi oferta cu cea afisata`() {
        // Preluarea se citeste, cursa e mascata (fara minute): mai putine campuri decat cardul afisat.
        val partial = Ecrane.faraOferta + listOf(
            LinieOcr("4 min. (1.5 km)", Cutie(100, 1700, 700, 1745)),
            LinieOcr("▢ min. (▢ km)", Cutie(100, 1800, 700, 1845)),
        )
        val citit = uber.parseaza(partial)
        assertNull(citit.price, "arrange: fara pret")
        assertNotNull(citit.pickupMinutes, "arrange: segment citit, altfel ar fi Gol")
        assertNull(citit.tripMinutes, "arrange: mai putine campuri decat cardul")

        val pas = ocrFaraPret(cuCifre, 2, partial)

        assertEquals(StareCard.Incarcare(UBER), pas.stare.card)
        assertEquals(listOf<Comanda>(Comanda.PornesteAsteptare(2)), pas.comenzi)
    }

    @Test
    fun `pe Bolt segmentul identic lasa cardul, iar altul porneste asteptarea`() {
        val bolt = AutomatCard(mapOf(BOLT to OfferParser(BOLT_RO_V1)))
        fun l(text: String, sus: Int, h: Int = 82) = LinieOcr(text, Cutie(152, sus, 962, sus + h))
        val corp = listOf(500, 700, 900).map { l("▢ ▢", it) }
        val pret = l("21,40 lei (▢, ▢ ▢)", 1375, 114)
        val s1 = l("5 min • 2.3 km", 1650)
        val s2 = l("11 min • 6.4 km", 1875)
        val altul = listOf(l("8 min • 3.1 km", 1650), l("14 min • 9.2 km", 1875))
        fun treci(p: Pas, vararg e: Eveniment) = e.fold(p) { x, ev -> bolt.trateaza(x.stare, ev) }

        val cu = treci(Pas(start), EcranSchimbat(1, BOLT), NoduriCitite(1, corp + pret + s1 + s2))
        assertIs<StareCard.Succes>(cu.stare.card)

        val aceeasi = treci(cu, EcranSchimbat(2, BOLT), NoduriCitite(2, emptyList()), OcrCitit(2, corp + s1 + s2))
        assertSame(cu.stare.card, aceeasi.stare.card)

        val nou = treci(cu, EcranSchimbat(2, BOLT), NoduriCitite(2, emptyList()), OcrCitit(2, corp + altul))
        assertEquals(StareCard.Incarcare(BOLT), nou.stare.card)
        assertEquals(listOf<Comanda>(Comanda.PornesteAsteptare(2)), nou.comenzi)
        assertEquals(StareCard.Eroare(BOLT, MotivEroare.NECITIBILA), treci(nou, AsteptareExpirata(2)).stare.card)
    }
}
