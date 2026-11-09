package app.trueride.core.parse

import app.trueride.core.model.OfferField
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * D-315, P-sim-7 (val7-bolt-viteza-min): limita INFERIOARA de viteza pe CURSA, doar la Bolt (`minVitezaCursaKmH` = 7).
 * Pe 530 oferte Bolt reale viteza cursei are minimul 14,0 km/h (p1 16,2), deci sub 7 km/h (2x sub minim) e un km
 * cu o cifra pierduta; km devin necunoscuti (invariantul 6). Preluarea nu se judeca; minute 0 sau lipsa = nu se judeca.
 * Valorile sunt inventate (invariantul 2).
 */
class VitezaMinimaBoltTest {

    private val nb = " "
    private val bolt = OfferParser(BOLT_RO_V1)

    private fun linie(text: String, sus: Int, inaltime: Int = 45) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private fun corp() = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }

    private fun linii(preluare: String, cursa: String) =
        corp() + linie("21,40${nb}lei (▢, ▢ ▢)", sus = 1232, inaltime = 115) + linie(preluare, sus = 1700) + linie(cursa, sus = 1800)

    @Test
    fun `Bolt - cursa sub limita de viteza are km necunoscuti, restul ramane`() {
        val o = bolt.parseaza(linii("5 min$nb• 2.3 km", "20 min$nb• 1.8 km"))   // 5,4 km/h

        assertNull(o.tripDistance)
        assertNull(o.fieldConfidence[OfferField.TRIP_DISTANCE])
        assertEquals(BigDecimal("20"), o.tripMinutes)
        assertEquals(BigDecimal("2.3"), o.pickupDistance)
        assertNotNull(o.price)
    }

    @Test
    fun `Bolt - motivul lipsei km cursei sub limita e KM_IMPLAUZIBIL`() {
        val motive = bolt.motiveLipsa(linii("5 min$nb• 2.3 km", "20 min$nb• 1.8 km"))

        assertEquals(MotivLipsa.KM_IMPLAUZIBIL, motive[OfferField.TRIP_DISTANCE])
    }

    @Test
    fun `Bolt - cursa la viteza reala minima se citeste`() {
        val o = bolt.parseaza(linii("5 min$nb• 2.3 km", "30 min$nb• 7.0 km"))   // 14 km/h

        assertEquals(BigDecimal("7.0"), o.tripDistance)
    }

    @Test
    fun `Bolt - exact la limita se citeste`() {
        val o = bolt.parseaza(linii("5 min$nb• 2.3 km", "60 min$nb• 7.0 km"))   // 7 km/h

        assertEquals(BigDecimal("7.0"), o.tripDistance)
    }

    @Test
    fun `Bolt - preluarea lenta nu se judeca`() {
        val o = bolt.parseaza(linii("15 min$nb• 0.5 km", "30 min$nb• 7.0 km"))

        assertEquals(BigDecimal("0.5"), o.pickupDistance)
    }

    @Test
    fun `Bolt - 0 minute la cursa nu se judeca`() {
        val o = bolt.parseaza(linii("5 min$nb• 2.3 km", "0 min$nb• 0.3 km"))

        assertEquals(BigDecimal("0.3"), o.tripDistance)
    }

    @Test
    fun `setul Bolt are limita 7 km pe ora`() {
        assertEquals(0, BigDecimal("7").compareTo(BOLT_RO_V1.minVitezaCursaKmH))
    }
}
