package app.trueride.core.lant

import app.trueride.core.lant.Eveniment.CadruNegru
import app.trueride.core.lant.Eveniment.EcranSchimbat
import app.trueride.core.lant.Eveniment.NoduriCitite
import app.trueride.core.lant.Eveniment.OcrCitit
import app.trueride.core.lant.PlatformaSofer.UBER
import app.trueride.core.model.Thresholds
import app.trueride.core.parse.OfferParser
import app.trueride.core.parse.UBER_RO_V1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * D-134 (F-030): valul albastru al ofertei Uber exclusive schimba pixelii la cateva secunde.
 * Cardul cu cifre ramane pe loc pana la citirea noua; aceleasi cifre nu schimba cardul,
 * alte cifre il inlocuiesc imediat, iar fara oferta sau peste alta aplicatie cardul dispare.
 */
class AutomatCardPulsTest {

    private val uber = OfferParser(UBER_RO_V1)
    private val automat = AutomatCard(mapOf(UBER to uber, PlatformaSofer.NECUNOSCUTA to uber))
    private val start = StareLant(setari = Setari(cost = null, praguri = Thresholds()))

    private fun dupa(pas: Pas, vararg evenimente: Eveniment): Pas =
        evenimente.fold(pas) { p, e -> automat.trateaza(p.stare, e) }

    /** Cardul arata deja oferta n=1, cu cifre. */
    private val cuCifre = dupa(Pas(start), EcranSchimbat(1, UBER), NoduriCitite(1, Ecrane.ofertaCompleta))

    @Test
    fun `pulsul peste un card cu cifre pastreaza cifrele, fara cerc si fara limita`() {
        val pas = dupa(cuCifre, EcranSchimbat(2, UBER))

        assertEquals(cuCifre.stare.card, pas.stare.card)
        assertEquals(2, pas.stare.ultimulN)
        assertEquals(Etapa.NODURI, pas.stare.etapa)
        assertEquals(listOf<Comanda>(Comanda.CitesteNoduri(2)), pas.comenzi)
    }

    @Test
    fun `recitirea din noduri cu aceleasi cifre lasa cardul neschimbat`() {
        val pas = dupa(cuCifre, EcranSchimbat(2, UBER), NoduriCitite(2, Ecrane.ofertaCompleta))

        assertEquals(cuCifre.stare.card, pas.stare.card)
        assertEquals(Etapa.GATA, pas.stare.etapa)
    }

    @Test
    fun `recitirea prin OCR cu aceleasi cifre lasa cardul neschimbat`() {
        val cerutOcr = dupa(cuCifre, EcranSchimbat(2, UBER), NoduriCitite(2, Ecrane.faraOferta))
        assertEquals(cuCifre.stare.card, cerutOcr.stare.card)

        val pas = dupa(cerutOcr, OcrCitit(2, Ecrane.ofertaCompleta))

        assertEquals(cuCifre.stare.card, pas.stare.card)
    }

    // D-134.2 si D-135.3: un camp lipsa in recitire e tot „alte valori”, deci inlocuieste cardul.
    @Test
    fun `o recitire cu alte valori inlocuieste cardul imediat`() {
        val pas = dupa(cuCifre, EcranSchimbat(2, UBER), NoduriCitite(2, Ecrane.ofertaFaraTimpCursa))

        val card = assertIs<StareCard.Succes>(pas.stare.card)
        assertNotEquals(cuCifre.stare.card, card)
        assertNull(card.oferta.tripMinutes)
    }

    @Test
    fun `recitirea fara oferta ascunde cardul`() {
        val pas = dupa(
            cuCifre,
            EcranSchimbat(2, UBER), NoduriCitite(2, Ecrane.faraOferta), OcrCitit(2, Ecrane.faraOferta),
        )

        assertEquals(StareCard.Gol, pas.stare.card)
    }

    @Test
    fun `alta aplicatie sau ecran negru in timpul recitirii ascund cifrele`() {
        val inRecitire = dupa(cuCifre, EcranSchimbat(2, UBER))

        assertEquals(StareCard.Gol, dupa(inRecitire, Eveniment.AltaAplicatie).stare.card)
        assertEquals(
            StareCard.Eroare(UBER, MotivEroare.ECRAN_PROTEJAT),
            dupa(inRecitire, CadruNegru(2)).stare.card,
        )
    }

    // D-134.1 cere aceeasi platforma: cifrele Uber nu raman peste un ecran nerecunoscut.
    @Test
    fun `la schimbarea platformei cardul cu cifre devine cerc fara cifre`() {
        val pas = dupa(cuCifre, EcranSchimbat(2, PlatformaSofer.NECUNOSCUTA))

        assertEquals(StareCard.Incarcare(PlatformaSofer.NECUNOSCUTA), pas.stare.card)
        assertFalse(pas.stare.card.toString().any { it.isDigit() }, pas.stare.card.toString())
    }

    // Invariantul 5 ramane pentru ofertele diferite: rezultatul unei schimbari vechi nu ajunge pe card.
    @Test
    fun `in recitire un rezultat al schimbarii vechi nu inlocuieste cardul`() {
        val pas = dupa(
            cuCifre,
            EcranSchimbat(2, UBER), NoduriCitite(2, Ecrane.faraOferta),
            EcranSchimbat(3, UBER), OcrCitit(2, Ecrane.ofertaFaraTimpCursa),
        )

        assertEquals(cuCifre.stare.card, pas.stare.card)
    }
}
