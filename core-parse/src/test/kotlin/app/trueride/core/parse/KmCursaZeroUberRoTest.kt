package app.trueride.core.parse

import app.trueride.core.model.OfferField
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-315, val8-uber-ro-km-cursa-zero: la Uber RO o cursa cu minute > 0 si 0 km nu exista; 0.0 vine din OCR gresit, deci km
 * cursei sunt necunoscuti (invariantul 6), nu zero. Preluarea nu se judeca; Uber EN si Bolt raman neschimbate (parametru oprit).
 * Valorile sunt inventate (invariantul 2).
 */
class KmCursaZeroUberRoTest {

    private val uberRo = OfferParser(UBER_RO_V1)

    private fun linie(text: String, sus: Int, inaltime: Int = 45) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private fun corp() = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }

    private fun linii(preluare: String, cursa: String) =
        corp() + linie("21,40 RON", sus = 1232, inaltime = 115) + linie(preluare, sus = 1700) + linie(cursa, sus = 1800)

    @Test
    fun `Uber RO - km cursa 0 cu minute pozitive sunt necunoscuti, restul ramane`() {
        val o = uberRo.parseaza(linii("4 min. (1.5 km)", "20 min. (0.0 km)"))

        assertNull(o.tripDistance)
        assertNull(o.fieldConfidence[OfferField.TRIP_DISTANCE])
        assertEquals(BigDecimal("20"), o.tripMinutes)
        assertEquals(BigDecimal("1.5"), o.pickupDistance)
        assertNotNull(o.price)
    }

    @Test
    fun `Uber RO - km cursa 0 intreg e tratat la fel ca 0 cu zecimala`() {
        assertNull(uberRo.parseaza(linii("4 min. (1.5 km)", "20 min. (0 km)")).tripDistance)
    }

    @Test
    fun `Uber RO - motivul lipsei km cursei zero e KM_IMPLAUZIBIL`() {
        val motive = uberRo.motiveLipsa(linii("4 min. (1.5 km)", "20 min. (0.0 km)"))

        assertEquals(MotivLipsa.KM_IMPLAUZIBIL, motive[OfferField.TRIP_DISTANCE])
    }

    @Test
    fun `Uber RO - km cursa 0,1 se pastreaza`() {
        assertEquals(BigDecimal("0.1"), uberRo.parseaza(linii("4 min. (1.5 km)", "1 min. (0.1 km)")).tripDistance)
    }

    @Test
    fun `Uber RO - preluarea cu 0 km se pastreaza`() {
        val o = uberRo.parseaza(linii("4 min. (0.0 km)", "20 min. (5.5 km)"))

        assertEquals(0, BigDecimal.ZERO.compareTo(o.pickupDistance))
        assertEquals(BigDecimal("5.5"), o.tripDistance)
    }

    @Test
    fun `Uber RO - cursa cu 0 minute si 0 km nu se judeca`() {
        val o = uberRo.parseaza(linii("4 min. (1.5 km)", "0 min. (0.0 km)"))

        assertEquals(0, BigDecimal.ZERO.compareTo(o.tripDistance))
    }

    @Test
    fun `Uber EN si Bolt - garda oprita`() {
        assertFalse(UBER_EN_V1.respingeKmCursaZero)
        assertFalse(BOLT_RO_V1.respingeKmCursaZero)
    }

    @Test
    fun `doar setul Uber RO are garda si versiunea 1,14,0`() {
        assertTrue(UBER_RO_V1.respingeKmCursaZero)
        assertEquals("1.19.0", UBER_RO_V1.version)
    }
}
