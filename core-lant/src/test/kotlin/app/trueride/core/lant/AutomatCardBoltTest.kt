package app.trueride.core.lant

import app.trueride.core.lant.Eveniment.EcranSchimbat
import app.trueride.core.lant.Eveniment.NoduriCitite
import app.trueride.core.lant.PlatformaSofer.BOLT
import app.trueride.core.model.PriceBasis
import app.trueride.core.model.Thresholds
import app.trueride.core.parse.BOLT_RO_V1
import app.trueride.core.parse.Cutie
import app.trueride.core.parse.LinieOcr
import app.trueride.core.parse.OfferParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** F3: pe Bolt nodurile dau toata oferta, deci cardul vine fara OCR (planul 2026-10-05, faptul 2). */
class AutomatCardBoltTest {

    private val automat = AutomatCard(mapOf(BOLT to OfferParser(BOLT_RO_V1)))
    private val start = StareLant(setari = Setari(cost = null, praguri = Thresholds()))

    private fun linie(text: String, sus: Int, inaltime: Int = 82) = LinieOcr(text, Cutie(152, sus, 962, sus + inaltime))

    /** Cifre inventate, in forma nodurilor Bolt RO (U+00A0 intre numar si unitate). */
    private val ofertaBolt = listOf(500, 700, 900).map { linie("▢ ▢", it) } + listOf(
        linie("21,40\u00A0lei (▢, ▢ ▢)", 1375, 114),
        linie("5 min\u00A0• 2.3 km", 1650),
        linie("11 min\u00A0• 6.4 km", 1875),
    )

    @Test
    fun `pe Bolt cu reguli oferta din noduri ajunge pe card fara OCR`() {
        val pas = listOf(EcranSchimbat(1, BOLT), NoduriCitite(1, ofertaBolt))
            .fold(Pas(start)) { p, e -> automat.trateaza(p.stare, e) }

        val card = assertIs<StareCard.Succes>(pas.stare.card)
        assertEquals(BOLT, card.platforma)
        assertEquals(PriceBasis.GROSS, card.oferta.priceBasis)
        assertTrue(pas.comenzi.none { it is Comanda.FaOcr })
    }
}
