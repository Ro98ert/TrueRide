package app.trueride.core.lant

import app.trueride.core.lant.PlatformaSofer.UBER
import app.trueride.core.model.Thresholds
import app.trueride.core.parse.CititorFixturi
import app.trueride.core.parse.ComparatorFixturi
import app.trueride.core.parse.OfferParser
import app.trueride.core.parse.ParserPeLimbi
import app.trueride.core.parse.UBER_EN_V1
import app.trueride.core.parse.UBER_RO_V1
import java.io.File
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** F5: lantul cu selectia pe limbi (ca in ModulLant) pe ofertele Uber EN reale, prin OCR. */
class LantFixturiEnTest {

    private val uber = ParserPeLimbi(listOf(OfferParser(UBER_RO_V1), OfferParser(UBER_EN_V1)))
    private val automat = AutomatCard(mapOf(UBER to uber))
    private val setari = Setari(cost = null, praguri = Thresholds())

    private fun cardPentru(f: File): StareCard = listOf(
        Eveniment.EcranSchimbat(1, UBER),
        Eveniment.NoduriCitite(1, emptyList()),
        Eveniment.OcrCitit(1, CititorFixturi.linii(f)),
        // D-303: segmentul fara pret asteapta limita; pe fixturile cu pret sau fara segment evenimentul nu schimba nimic.
        Eveniment.AsteptareExpirata(1),
    ).fold(Pas(StareLant(setari = setari))) { p, e -> automat.trateaza(p.stare, e) }.stare.card

    @Test
    fun `ofertele EN cu pret citibil ajung pe card cu pretul din eticheta`() {
        val cuPret = ComparatorFixturi.fixturi("uber-en-oferte")
            .filter { CititorFixturi.eticheta(ComparatorFixturi.etichetaDe(it))["price"] != null }
        assertTrue(cuPret.size >= 4, "gasite ${cuPret.size}")
        cuPret.forEach { f ->
            val asteptat = BigDecimal(requireNotNull(CititorFixturi.eticheta(ComparatorFixturi.etichetaDe(f))["price"]))
            assertEquals(0, asteptat.compareTo(assertIs<StareCard.Succes>(cardPentru(f), f.name).oferta.price), f.name)
        }
    }

    // Pretul cu punctul pierdut de OCR: segmentele fara pret dau cardul „nu am putut citi”, nu un pret ghicit.
    @Test
    fun `ofertele EN fara pret citibil dau eroare, nu cifre`() {
        ComparatorFixturi.fixturi("uber-en-oferte")
            .filter { CititorFixturi.eticheta(ComparatorFixturi.etichetaDe(it))["price"] == null }
            .forEach { f -> assertIs<StareCard.Eroare>(cardPentru(f), f.name) }
    }

    @Test
    fun `ecranele EN fara oferta tot nu produc niciun card cu selectia pe limbi`() {
        val cuCard = ComparatorFixturi.fixturi("uber-en-fara-oferta").filter { cardPentru(it) != StareCard.Gol }.map { it.name }
        assertEquals(emptyList(), cuCard)
    }
}
