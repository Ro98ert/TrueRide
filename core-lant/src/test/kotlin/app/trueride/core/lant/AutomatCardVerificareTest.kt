package app.trueride.core.lant

import app.trueride.core.lant.Eveniment.EcranSchimbat
import app.trueride.core.lant.Eveniment.NoduriCitite
import app.trueride.core.lant.Eveniment.VerificareCitita
import app.trueride.core.lant.PlatformaSofer.UBER
import app.trueride.core.model.Thresholds
import app.trueride.core.parse.OfferParser
import app.trueride.core.parse.UBER_RO_V1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** D-394: a doua citire doar numara; ajunge in automat ca eveniment, dar nu atinge cardul si nu cere nimic. */
class AutomatCardVerificareTest {

    private val automat = AutomatCard(mapOf(UBER to OfferParser(UBER_RO_V1)))
    private val start = StareLant(setari = Setari(cost = null, praguri = Thresholds()))

    @Test
    fun `verificareCitita nuSchimbaStarea`() {
        val cuCifre = listOf(EcranSchimbat(1, UBER), NoduriCitite(1, Ecrane.ofertaCompleta))
            .fold(Pas(start)) { p, e -> automat.trateaza(p.stare, e) }
        assertTrue(cuCifre.stare.card is StareCard.Succes)

        val pas = automat.trateaza(cuCifre.stare, VerificareCitita(idCard = 1, linii = Ecrane.ofertaCompleta))

        assertEquals(cuCifre.stare, pas.stare)
        assertTrue(pas.comenzi.isEmpty())
        assertTrue(pas.dezacord.isEmpty())
    }
}
