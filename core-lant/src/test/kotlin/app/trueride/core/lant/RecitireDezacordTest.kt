package app.trueride.core.lant

import app.trueride.core.lant.PlatformaSofer.BOLT
import app.trueride.core.lant.PlatformaSofer.UBER
import app.trueride.core.model.OfferField
import app.trueride.core.parse.Cutie
import app.trueride.core.parse.LinieOcr
import app.trueride.core.parse.OfferParser
import app.trueride.core.parse.UBER_EN_V1
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * D-368: o recitire marita a liniei de la preluare (D-288.4 km, D-367 minute) respinsa pentru ca a dat o valoare
 * cunoscuta DIFERITA fata de prima citire, la un camp cunoscut in ambele. Repararea campului necunoscut nu e dezacord.
 * Parserul real Uber EN; cifrele sunt inventate. Iese doar numele campului.
 */
class RecitireDezacordTest {

    private val automat = AutomatCard(mapOf(UBER to OfferParser(UBER_EN_V1)))

    private fun linie(text: String, sus: Int, inaltime: Int = 46) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private val corp = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }

    private fun ecran(preluare: String, pret: String = "RON12.30") =
        corp + linie(pret, sus = 1232, inaltime = 90) + linie(preluare, sus = 1700) + linie("14 mins (7.2 km) ▢", sus = 1800)

    private fun dezacord(inainte: List<LinieOcr>, dupa: List<LinieOcr>) = automat.recitireDezacord(UBER, inainte, dupa)

    private val faraMinute = ecran("mins (2.1 km) ▢")
    private val faraKm = ecran("6 mins (2.1 xm) ▢")

    @Test
    fun `recitirea minutelor care schimba km cititi in prima citire e dezacord pe km de la preluare`() {
        assertEquals(listOf(OfferField.PICKUP_DISTANCE), dezacord(faraMinute, ecran("5 mins (2.4 km) ▢")))
    }

    @Test
    fun `recitirea km care schimba minutele citite in prima citire e dezacord pe minutele de la preluare`() {
        assertEquals(listOf(OfferField.PICKUP_MINUTES), dezacord(faraKm, ecran("7 mins (2.1 km) ▢")))
    }

    @Test
    fun `repararea campului necunoscut, fara alta schimbare, nu e dezacord`() {
        assertEquals(emptyList<OfferField>(), dezacord(faraMinute, ecran("5 mins (2.1 km) ▢")))
        assertEquals(emptyList<OfferField>(), dezacord(faraKm, ecran("6 mins (2.1 km) ▢")))
    }

    @Test
    fun `un camp care ramane necunoscut dupa recitire nu e dezacord`() {
        assertEquals(emptyList<OfferField>(), dezacord(faraMinute, faraMinute))
        assertEquals(emptyList<OfferField>(), dezacord(faraKm, faraKm))
    }

    @Test
    fun `pretul cunoscut in ambele citiri si diferit e dezacord pe pret`() {
        assertEquals(listOf(OfferField.PRICE), dezacord(faraMinute, ecran("mins (2.1 km) ▢", pret = "RON12.40")))
    }

    @Test
    fun `o platforma fara reguli nu are ce compara`() {
        assertEquals(emptyList<OfferField>(), automat.recitireDezacord(BOLT, faraMinute, ecran("5 mins (2.4 km) ▢")))
    }
}
