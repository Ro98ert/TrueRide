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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * D-367: recitirea marita a MINUTELOR de la preluare (cifra scapata de OCR: „mins (2.1 km)”). Se accepta doar daca
 * singura schimbare e minutele de la preluare, necunoscute -> citite DIRECT (nu prin harta de litere, D-122), cu km
 * de la preluare, pretul si cursa identice. Parserul real Uber EN; cifrele sunt inventate.
 */
class RecitireMinuteAcceptataTest {

    private val parser = OfferParser(UBER_EN_V1)
    private val automat = AutomatCard(mapOf(UBER to parser))

    private fun linie(text: String, sus: Int, inaltime: Int = 46) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private val corp = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }

    private fun ecran(preluare: String, pret: String = "RON12.30", cursa: String = "14 mins (7.2 km) ▢") =
        corp + linie(pret, sus = 1232, inaltime = 90) + linie(preluare, sus = 1700) + linie(cursa, sus = 1800)

    private val faraMinute = ecran("mins (2.1 km) ▢")

    private fun accepta(inainte: List<LinieOcr>, dupa: List<LinieOcr>) = automat.recitireKmAcceptata(UBER, inainte, dupa)

    @Test
    fun `prima citire are pret, km si cursa dar nu minutele preluarii (baza testelor)`() {
        val o = parser.parseaza(faraMinute)

        assertNotNull(o.price)
        assertNull(o.pickupMinutes)
        assertNotNull(o.pickupDistance)
        assertNotNull(o.tripMinutes)
        assertNotNull(o.tripDistance)
    }

    @Test
    fun `linia minutelor de recitit vine de la parserul platformei`() {
        assertSame(faraMinute[faraMinute.size - 2], automat.liniaMinuteDeRecitit(UBER, faraMinute))
        assertNull(automat.liniaKmDeRecitit(UBER, faraMinute))
        assertNull(automat.liniaMinuteDeRecitit(BOLT, faraMinute))
    }

    @Test
    fun `minutele devin cititi direct, restul identic - se accepta`() {
        assertTrue(accepta(faraMinute, ecran("5 mins (2.1 km) ▢")))
    }

    @Test
    fun `minutele corectate din litera (l, I, O) se resping - litera nu e o cifra citita`() {
        // 1,0 km: cu 1 minut (l, I) viteza ramane sub 65 km/h (D-371); 2,1 km ar fi anulat minutele si baza ar pica.
        val faraMinuteKmMici = ecran("mins (1.0 km) ▢")
        listOf("l", "I", "O").forEach {
            val dupa = ecran("$it mins (1.0 km) ▢")
            assertNotNull(parser.parseaza(dupa).pickupMinutes, "baza: $it da minute")
            assertFalse(accepta(faraMinuteKmMici, dupa), it)
        }
    }

    @Test
    fun `pretul sau cursa schimbate odata cu minutele se resping`() {
        assertFalse(accepta(faraMinute, ecran("5 mins (2.1 km) ▢", pret = "RON12.40")))
        assertFalse(accepta(faraMinute, ecran("5 mins (2.1 km) ▢", cursa = "15 mins (7.2 km) ▢")))
        assertFalse(accepta(faraMinute, ecran("5 mins (2.1 km) ▢", cursa = "14 mins (7.3 km) ▢")))
    }

    @Test
    fun `km de la preluare schimbati se resping`() {
        assertFalse(accepta(faraMinute, ecran("5 mins (2.7 km) ▢")))
    }

    @Test
    fun `minutele recitite fac km imposibili (D-148) - parserul anuleaza km din dupa si recitirea se respinge`() {
        val inainte = ecran("mins (9.9 km) ▢")
        val dupa = ecran("1 mins (9.9 km) ▢")

        // Prima citire: minute lipsa, deci viteza nu se judeca si km raman cititi; abia cu minutele citite D-148 ii anuleaza.
        // D-371: o data cu km se anuleaza si minutele preluarii.
        assertNotNull(parser.parseaza(inainte).pickupDistance)
        assertNull(parser.parseaza(dupa).pickupMinutes)
        assertNull(parser.parseaza(dupa).pickupDistance)
        assertFalse(accepta(inainte, dupa))
    }

    @Test
    fun `peste 99 de minute se resping (D-315)`() {
        assertFalse(accepta(faraMinute, ecran("100 mins (2.1 km) ▢")))
        assertFalse(accepta(faraMinute, ecran("477 mins (2.1 km) ▢")))
    }

    @Test
    fun `recitirea care tot nu citeste minutele, sau care nu avea ce repara, se respinge`() {
        assertFalse(accepta(faraMinute, faraMinute))
        assertFalse(accepta(ecran("5 mins (2.1 km) ▢"), ecran("5 mins (2.1 km) ▢")))
        assertFalse(accepta(ecran("5 mins (2.1 km) ▢"), ecran("6 mins (2.1 km) ▢")))
    }

    @Test
    fun `minute si km necitite deodata nu se accepta intr-o singura recitire`() {
        assertFalse(accepta(ecran("mins (▢ km) ▢"), ecran("5 mins (2.1 km) ▢")))
        // Km necititi si dupa recitire: „null egal cu null” nu ajunge, km trebuie sa fi fost cititi dinainte.
        assertFalse(accepta(ecran("mins (▢ km) ▢"), ecran("5 mins (▢ km) ▢")))
    }

    @Test
    fun `campurile spun ce a fost citit - baza pentru incredere`() {
        val o = parser.parseaza(ecran("l mins (1.0 km) ▢"))

        assertEquals("0.9", o.fieldConfidence[OfferField.PICKUP_MINUTES]?.toPlainString())
    }

    // D-391.2 (F-058): cuvant de litere inaintea ancorei („Lal mins”) = cifra citita ca litere; se reciteste, nu se deduce.
    private val litere = ecran("Lal mins (2.1 km) ▢")

    @Test
    fun `D-391 - cuvantul de litere cere recitire si minutele recitite direct devin cititi`() {
        assertNull(parser.parseaza(litere).pickupMinutes)
        assertSame(litere[litere.size - 2], automat.liniaMinuteDeRecitit(UBER, litere))
        assertTrue(accepta(litere, ecran("5 mins (2.1 km) ▢")))
    }

    @Test
    fun `D-391 - recitirea care tot da litere, sau alt km, sau minute prin harta, ramane necunoscuta`() {
        assertFalse(accepta(litere, litere))
        assertFalse(accepta(litere, ecran("5 mins (2.7 km) ▢")))
        assertFalse(accepta(litere, ecran("l mins (1.0 km) ▢")))
        assertNull(automat.liniaMinuteDeRecitit(UBER, ecran("La5 mins (2.1 km) ▢")))
    }
}
