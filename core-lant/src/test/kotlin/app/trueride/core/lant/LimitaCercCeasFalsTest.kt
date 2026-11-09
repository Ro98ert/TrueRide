package app.trueride.core.lant

import app.trueride.core.lant.Eveniment.EcranSchimbat
import app.trueride.core.lant.Eveniment.NoduriCitite
import app.trueride.core.lant.Eveniment.OcrCitit
import app.trueride.core.lant.PlatformaSofer.UBER
import app.trueride.core.model.Thresholds
import app.trueride.core.parse.OfferParser
import app.trueride.core.parse.UBER_RO_V1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Regresie F-002 (2026-09-27): cardul ramanea blocat pe „citesc oferta…”, inclusiv pe ecranul de
 * navigare fara oferta. Cauza: starea de incarcare pornea la orice schimbare de ecran si n-avea iesire in timp.
 *
 * Oracolul e D-074, nu iesirea de azi: (1) din `Gol` citirea ruleaza in fundal, cardul nu apare; (3) cercul dintre
 * oferte sta cel mult 3 s, apoi cardul trece in `Gol`. Limita curge de la aparitia cercului, nu de la ultima
 * schimbare (pe harta ecranul se schimba continuu; o limita repornita n-ar expira niciodata — d2e460c4).
 *
 * Automatul nu tine ceas (cere [Comanda.PornesteAsteptare], primeste [Eveniment.AsteptareExpirata]); aici „lumea”
 * e un ceas fals care programeaza expirarea la `acum + LIMITA_CERC_MS`, ca `postDelayed` din ServiciuCaptura.
 * Firul real (`principal.postDelayed(..., LIMITA_CERC_MS)`) nu e acoperit de acest test.
 */
class LimitaCercCeasFalsTest {

    private val uber = OfferParser(UBER_RO_V1)
    private val automat = AutomatCard(mapOf(UBER to uber, PlatformaSofer.NECUNOSCUTA to uber))

    /** Ceas fals + coada de expirari programate: singurul loc unde trece timpul. */
    private inner class Lume(stareInitiala: StareLant) {
        var acumMs = 0L
            private set
        var stare = stareInitiala
            private set
        private val programate = mutableListOf<Pair<Long, Long>>() // (scadentaMs, n)

        fun trimite(e: Eveniment) {
            val pas = automat.trateaza(stare, e)
            stare = pas.stare
            pas.comenzi.filterIsInstance<Comanda.PornesteAsteptare>()
                .forEach { programate += (acumMs + LIMITA_CERC_MS) to it.n }
        }

        fun avanseazaLa(ms: Long) {
            while (true) {
                val urmatoarea = programate.filter { it.first <= ms }.minByOrNull { it.first } ?: break
                programate.remove(urmatoarea)
                acumMs = urmatoarea.first
                trimite(Eveniment.AsteptareExpirata(urmatoarea.second))
            }
            acumMs = ms
        }
    }

    /** Cardul arata deja un rezultat fara cifre (eroare D-303), deci o oferta urmatoare pune cercul (D-074.2). */
    private fun lumeCuRezultatPeEcran(): Lume {
        val lume = Lume(StareLant(setari = Setari(cost = null, praguri = Thresholds())))
        lume.trimite(EcranSchimbat(1, UBER))
        lume.trimite(NoduriCitite(1, emptyList()))
        lume.trimite(OcrCitit(1, Ecrane.ofertaFaraPret))
        lume.avanseazaLa(LIMITA_CERC_MS)
        assertIs<StareCard.Eroare>(lume.stare.card)
        return lume
    }

    @Test
    fun `F-002 cercul dintre oferte sta pana la 2999 ms si dispare la 3001 ms (D-074_3)`() {
        val lume = lumeCuRezultatPeEcran()
        val t0 = lume.acumMs

        lume.trimite(EcranSchimbat(2, UBER))
        lume.avanseazaLa(t0 + 2_999)
        assertEquals(StareCard.Incarcare(UBER), lume.stare.card, "la 2999 ms cercul e inca pe ecran")

        lume.avanseazaLa(t0 + 3_001)
        assertEquals(StareCard.Gol, lume.stare.card, "la 3001 ms cercul fara rezultat trebuie sa fi disparut")
    }

    @Test
    fun `F-002 pe un ecran care se tot schimba limita curge de la aparitia cercului`() {
        val lume = lumeCuRezultatPeEcran()
        val t0 = lume.acumMs

        lume.trimite(EcranSchimbat(2, UBER))
        for ((i, la) in listOf(1_000L, 2_000L, 2_900L).withIndex()) {
            lume.avanseazaLa(t0 + la)
            lume.trimite(EcranSchimbat(3L + i, UBER)) // harta in miscare, fara oferta
        }
        lume.avanseazaLa(t0 + 2_999)
        assertEquals(StareCard.Incarcare(UBER), lume.stare.card)

        lume.avanseazaLa(t0 + 3_001)
        assertEquals(StareCard.Gol, lume.stare.card, "schimbarile ulterioare nu au voie sa mute limita")
    }

    @Test
    fun `F-002 pe ecranul de navigare fara oferta cardul nu apare deloc (D-074_1)`() {
        val lume = Lume(StareLant(setari = Setari(cost = null, praguri = Thresholds())))
        val vazute = mutableListOf<StareCard>()

        lume.trimite(EcranSchimbat(1, UBER)); vazute += lume.stare.card
        lume.trimite(NoduriCitite(1, Ecrane.faraOferta)); vazute += lume.stare.card
        lume.avanseazaLa(1_500)
        lume.trimite(OcrCitit(1, Ecrane.faraOferta)); vazute += lume.stare.card
        lume.avanseazaLa(10_000); vazute += lume.stare.card

        assertEquals(List<StareCard>(4) { StareCard.Gol }, vazute)
    }
}
