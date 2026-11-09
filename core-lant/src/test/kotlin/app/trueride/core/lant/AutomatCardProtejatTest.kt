package app.trueride.core.lant

import app.trueride.core.lant.Eveniment.CadruNegru
import app.trueride.core.lant.Eveniment.EcranSchimbat
import app.trueride.core.lant.Eveniment.NoduriCitite
import app.trueride.core.lant.PlatformaSofer.BOLT
import app.trueride.core.lant.PlatformaSofer.UBER
import app.trueride.core.model.Thresholds
import app.trueride.core.parse.OfferParser
import app.trueride.core.parse.UBER_RO_V1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * D-225 (F-039):cat ecranul ramane protejat, rafalele Accessibility emit schimbari noi.
 * Mesajul „ecran protejat” ramane pe loc, fara cerc intre ele; un cadru normal il inlocuieste.
 */
class AutomatCardProtejatTest {

    private val uber = OfferParser(UBER_RO_V1)
    private val automat = AutomatCard(mapOf(UBER to uber))
    private val start = StareLant(setari = Setari(cost = null, praguri = Thresholds()))
    private val protejat = StareCard.Eroare(UBER, MotivEroare.ECRAN_PROTEJAT)

    private fun dupa(pas: Pas, vararg evenimente: Eveniment): Pas =
        evenimente.fold(pas) { p, e -> automat.trateaza(p.stare, e) }

    /** Cardul arata deja mesajul pentru schimbarea n=1 (intrarea in negru). */
    private val peProtejat = dupa(Pas(start), EcranSchimbat(1, UBER, negru = true), CadruNegru(1))

    @Test
    fun `o schimbare cat ecranul ramane negru pastreaza mesajul, fara cerc`() {
        val pas = dupa(peProtejat, EcranSchimbat(2, UBER, negru = true))

        assertEquals(protejat, pas.stare.card)
        assertEquals(listOf<Comanda>(Comanda.CitesteNoduri(2)), pas.comenzi)
    }

    @Test
    fun `zece rafale pe acelasi ecran protejat nu schimba cardul niciodata`() {
        // Cardul dupa fiecare eveniment, nu doar la capatul rafalei: clipirea e cercul dintre ele.
        val evenimente = (2L..11L).flatMap { n -> listOf(EcranSchimbat(n, UBER, negru = true), CadruNegru(n)) }
        val carduri = evenimente.runningFold(peProtejat) { p, e -> dupa(p, e) }.map { it.stare.card }

        assertEquals(setOf<StareCard>(protejat), carduri.toSet())
    }

    @Test
    fun `un cadru normal dupa negru pune cercul, iar oferta citita inlocuieste mesajul`() {
        val cerc = dupa(peProtejat, EcranSchimbat(2, UBER))
        assertEquals(StareCard.Incarcare(UBER), cerc.stare.card)

        val pas = dupa(cerc, NoduriCitite(2, Ecrane.ofertaCompleta))

        assertIs<StareCard.Succes>(pas.stare.card)
    }

    @Test
    fun `alta platforma pe ecran negru nu pastreaza mesajul platformei vechi`() {
        val pas = dupa(peProtejat, EcranSchimbat(2, BOLT, negru = true))

        assertEquals(StareCard.Incarcare(BOLT), pas.stare.card)
    }
}
