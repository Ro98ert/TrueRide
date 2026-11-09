package app.trueride.core.parse

import app.trueride.core.model.OfferField
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * D-315 (val10-uber-viteza-min): limita INFERIOARA de viteza pe CURSA la Uber RO si EN (`minVitezaCursaKmH` = 5).
 * Pe oferte reale viteza cursei are minimul 11,3 km/h (RO) si 14,7 (EN); sub 5 km/h e un km cu o cifra pierduta,
 * deci km devin necunoscuti (invariantul 6). Preluarea nu se judeca; minute 0 sau lipsa = nu se judeca.
 * Valorile sunt inventate (invariantul 2).
 */
class VitezaMinimaUberTest {

    private val ro = OfferParser(UBER_RO_V1)
    private val en = OfferParser(UBER_EN_V1)

    private fun linie(text: String, sus: Int, inaltime: Int = 45) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private fun corp() = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }

    private fun linii(preluare: String, cursa: String, pret: String = "21,40 RON") =
        corp() + linie(pret, sus = 1232, inaltime = 115) + linie(preluare, sus = 1700) + linie(cursa, sus = 1800)

    @Test
    fun `Uber RO - cursa sub limita de viteza are km necunoscuti, restul ramane`() {
        val o = ro.parseaza(linii("4 min. (1.5 km)", "20 min. (1.5 km)"))   // 4,5 km/h

        assertNull(o.tripDistance)
        assertNull(o.fieldConfidence[OfferField.TRIP_DISTANCE])
        assertEquals(BigDecimal("20"), o.tripMinutes)
        assertEquals(BigDecimal("1.5"), o.pickupDistance)
        assertNotNull(o.price)
    }

    @Test
    fun `Uber EN - cursa sub limita de viteza are km necunoscuti, restul ramane`() {
        val o = en.parseaza(linii("4 mins (1.5 km)", "20 mins (1.5 km)"))

        assertNull(o.tripDistance)
        assertEquals(BigDecimal("20"), o.tripMinutes)
        assertEquals(BigDecimal("1.5"), o.pickupDistance)
    }

    @Test
    fun `motivul lipsei km cursei sub limita e KM_IMPLAUZIBIL pe ambele seturi`() {
        assertEquals(
            MotivLipsa.KM_IMPLAUZIBIL,
            ro.motiveLipsa(linii("4 min. (1.5 km)", "20 min. (1.5 km)"))[OfferField.TRIP_DISTANCE],
        )
        assertEquals(
            MotivLipsa.KM_IMPLAUZIBIL,
            en.motiveLipsa(linii("4 mins (1.5 km)", "20 mins (1.5 km)"))[OfferField.TRIP_DISTANCE],
        )
    }

    @Test
    fun `exact la limita si peste ea km se citesc`() {
        assertEquals(BigDecimal("5.0"), ro.parseaza(linii("4 min. (1.5 km)", "60 min. (5.0 km)")).tripDistance)
        assertEquals(BigDecimal("5.0"), en.parseaza(linii("4 mins (1.5 km)", "60 mins (5.0 km)")).tripDistance)
        assertEquals(BigDecimal("6.0"), ro.parseaza(linii("4 min. (1.5 km)", "30 min. (6.0 km)")).tripDistance)
        assertEquals(BigDecimal("6.0"), en.parseaza(linii("4 mins (1.5 km)", "30 mins (6.0 km)")).tripDistance)
    }

    @Test
    fun `preluarea lenta nu se judeca`() {
        assertEquals(BigDecimal("0.5"), ro.parseaza(linii("15 min. (0.5 km)", "30 min. (7.0 km)")).pickupDistance)
        assertEquals(BigDecimal("0.5"), en.parseaza(linii("15 mins (0.5 km)", "30 mins (7.0 km)")).pickupDistance)
    }

    @Test
    fun `Uber EN - 0 minute la cursa nu se judeca`() {
        assertEquals(BigDecimal("0.3"), en.parseaza(linii("4 mins (1.5 km)", "0 mins (0.3 km)")).tripDistance)
    }

    @Test
    fun `seturile Uber au limita 5 km pe ora`() {
        assertEquals(0, BigDecimal("5").compareTo(UBER_RO_V1.minVitezaCursaKmH))
        assertEquals(0, BigDecimal("5").compareTo(UBER_EN_V1.minVitezaCursaKmH))
    }
}
