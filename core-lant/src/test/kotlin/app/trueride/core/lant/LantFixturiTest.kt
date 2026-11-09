package app.trueride.core.lant

import app.trueride.core.lant.PlatformaSofer.UBER
import app.trueride.core.model.DataCompleteness
import app.trueride.core.model.Thresholds
import app.trueride.core.parse.CititorFixturi
import app.trueride.core.parse.ComparatorFixturi
import app.trueride.core.parse.OfferParser
import app.trueride.core.parse.UBER_RO_V1
import java.io.File
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Lantul complet pe capturile reale (anonimizate, D-025): exact ce se intampla
 * pe telefon cu o oferta Uber de azi — nodurile nu dau text (S7), OCR-ul da
 * liniile fixture-ului. Comparat cu etichetele M3.
 */
class LantFixturiTest {

    private val automat = AutomatCard(mapOf(UBER to OfferParser(UBER_RO_V1)))
    private val setari = Setari(cost = null, praguri = Thresholds())
    private val fixturi: List<File> = ComparatorFixturi.fixturi("uber")

    private fun cardPentru(f: File): StareCard {
        val evenimente = listOf(
            Eveniment.EcranSchimbat(1, UBER),
            Eveniment.NoduriCitite(1, emptyList()),
            Eveniment.OcrCitit(1, CititorFixturi.linii(f)),
        )
        return evenimente.fold(Pas(StareLant(setari = setari))) { p, e -> automat.trateaza(p.stare, e) }.stare.card
    }

    // D-130 (F-020): pe ecranele fara oferta, lantul nu afiseaza niciun card (inainte: card „partial”
    // cu castigul zilei ca pret).
    @Test
    fun `ecranele fara oferta nu produc niciun card`() {
        val fara = ComparatorFixturi.fixturi("uber-en-fara-oferta")
        assertTrue(fara.size >= 30, "gasite ${fara.size}")
        val cuCard = fara.filter { cardPentru(it) != StareCard.Gol }.map { it.name }
        assertEquals(emptyList(), cuCard)
    }

    @Test
    fun `setul are cel putin 8 fixture-uri`() {
        assertTrue(fixturi.size >= 8, "gasite ${fixturi.size}")
    }

    @Test
    fun `fiecare fixture ajunge pe card cu pretul din eticheta`() {
        fixturi.forEach { f ->
            val eticheta = CititorFixturi.eticheta(ComparatorFixturi.etichetaDe(f))
            val card = assertIs<StareCard.Succes>(cardPentru(f), f.name)
            val asteptat = BigDecimal(requireNotNull(eticheta["price"]) { "${f.name} fara pret" })

            assertEquals(0, asteptat.compareTo(card.oferta.price), f.name)
        }
    }

    // Regula din OfferCalculator (pasul 2): FULL doar cu pret + cele 4 campuri de segment.
    @Test
    fun `un camp lipsa din eticheta apare ca partial, nu ca zero`() {
        val campuri = listOf("price", "pickupMinutes", "tripMinutes", "pickupDistance", "tripDistance")
        fixturi.forEach { f ->
            val eticheta = CititorFixturi.eticheta(ComparatorFixturi.etichetaDe(f))
            val card = assertIs<StareCard.Succes>(cardPentru(f), f.name)
            val asteptat = if (campuri.any { eticheta[it] == null }) DataCompleteness.PARTIAL else DataCompleteness.FULL

            assertEquals(asteptat, card.evaluare.completeness, f.name)
        }
    }
}
