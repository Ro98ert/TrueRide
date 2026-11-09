package app.trueride.core.lant

import app.trueride.core.lant.PlatformaSofer.UBER
import app.trueride.core.parse.Cutie
import app.trueride.core.parse.LinieOcr
import app.trueride.core.parse.OfferParser
import app.trueride.core.parse.UBER_EN_V1
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * D-371 x D-288.4 / D-367: cand viteza preluarii e implauzibila, parserul anuleaza MINUTELE ca si km. Recitirea marita
 * a liniei km trebuie sa mearga mai departe: minutele de dinainte (cele citite, nu cele anulate) se compara cu cele de
 * dupa recitire. Parserul real Uber EN; cifrele sunt inventate.
 */
class RecitireKmDupaD371Test {

    private val parser = OfferParser(UBER_EN_V1)
    private val automat = AutomatCard(mapOf(UBER to parser))

    private fun linie(text: String, sus: Int, inaltime: Int = 46) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private val corp = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }

    private fun ecran(preluare: String, pret: String = "RON12.30", cursa: String = "14 mins (7.2 km) ▢") =
        corp + linie(pret, sus = 1232, inaltime = 90) + linie(preluare, sus = 1700) + linie(cursa, sus = 1800)

    private fun accepta(inainte: List<LinieOcr>, dupa: List<LinieOcr>) = automat.recitireKmAcceptata(UBER, inainte, dupa)

    // 95 km in 3 min: D-332 anuleaza km, iar D-371 anuleaza si minutele.
    private val kmDubleCuD332 = ecran("3 mins (95 km) ▢")

    // 4,0 km in 3 min: 4,0 * 60 = 240 > 65 * 3,5 = 227,5 -> peste 65 km/h (D-371), sub pragul D-332 (350).
    private val doarD371 = ecran("3 mins (4.0 km) ▢")

    private val bun = ecran("3 mins (1.5 km) ▢")

    @Test
    fun `baza - preluarea implauzibila are minutele si km necunoscuti (D-371)`() {
        listOf(kmDubleCuD332, doarD371).forEach {
            val o = parser.parseaza(it)
            assertNull(o.pickupMinutes)
            assertNull(o.pickupDistance)
        }
    }

    @Test
    fun `linia km se reciteste si cand minutele au fost anulate de viteza (D-288_4)`() {
        assertSame(kmDubleCuD332[kmDubleCuD332.size - 2], automat.liniaKmDeRecitit(UBER, kmDubleCuD332))
        assertSame(doarD371[doarD371.size - 2], automat.liniaKmDeRecitit(UBER, doarD371))
        assertNull(automat.liniaMinuteDeRecitit(UBER, kmDubleCuD332))
    }

    @Test
    fun `recitirea km care repara km cu aceleasi minute se accepta, desi minutele erau anulate (D-371)`() {
        assertTrue(accepta(kmDubleCuD332, bun))
        assertTrue(accepta(doarD371, bun))
    }

    @Test
    fun `recitirea care schimba si minutele, sau care lasa viteza implauzibila, se respinge`() {
        assertFalse(accepta(kmDubleCuD332, ecran("4 mins (1.5 km) ▢")))
        assertFalse(accepta(kmDubleCuD332, kmDubleCuD332))
        assertFalse(accepta(kmDubleCuD332, doarD371))
    }

    @Test
    fun `recitirea care mai schimba pretul sau cursa se respinge si cu minutele anulate`() {
        assertFalse(accepta(kmDubleCuD332, ecran("3 mins (1.5 km) ▢", pret = "RON12.40")))
        assertFalse(accepta(kmDubleCuD332, ecran("3 mins (1.5 km) ▢", cursa = "15 mins (7.2 km) ▢")))
    }

    @Test
    fun `minutele lipsa cu totul nu se potrivesc cu o recitire care aduce si km si minute (un singur camp odata)`() {
        val faraAmbele = ecran("mins (▢ km) ▢")

        assertFalse(accepta(faraAmbele, bun))
    }

    @Test
    fun `D-367 ramane - minutele recitite care fac viteza implauzibila se resping`() {
        val faraMinute = ecran("mins (2.1 km) ▢")

        assertTrue(accepta(faraMinute, ecran("5 mins (2.1 km) ▢")))
        // 1 min + 0,5 -> 2,1 km depasesc 65 km/h: parserul anuleaza ambele campuri, nu mai e „doar minutele”.
        assertFalse(accepta(faraMinute, ecran("1 mins (2.1 km) ▢")))
        assertNull(parser.parseaza(ecran("1 mins (2.1 km) ▢")).pickupMinutes)
    }
}
