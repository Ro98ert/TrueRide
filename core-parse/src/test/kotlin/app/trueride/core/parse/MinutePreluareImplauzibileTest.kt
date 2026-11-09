package app.trueride.core.parse

import app.trueride.core.model.OfferField
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * D-315, P-sim-3: minutele PRELUARII au in realitate cel mult 2 cifre (pe 963 oferte reale complete maximul e 25), deci o
 * preluare de 3 cifre e o cifra dublata de OCR; ea nu se ghiceste, minutele ei devin necunoscute (invariantul 6), restul
 * campurilor raman. Garda NU se aplica cursei: exista curse reale de peste 100 min. Valorile sunt inventate (invariantul 2).
 */
class MinutePreluareImplauzibileTest {

    private val nb = " "
    private val bolt = OfferParser(BOLT_RO_V1)
    private val uberEn = OfferParser(UBER_EN_V1)
    private val uberRo = OfferParser(UBER_RO_V1)

    private fun linie(text: String, sus: Int, inaltime: Int = 45) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private fun corp() = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }

    private fun linii(preluare: String, cursa: String) =
        corp() + linie("21,40${nb}lei (▢, ▢ ▢)", sus = 1232, inaltime = 115) + linie(preluare, sus = 1700) + linie(cursa, sus = 1800)

    @Test
    fun `Bolt - preluare de 100 min are minutele necunoscute, restul ramane`() {
        val o = bolt.parseaza(linii("100 min$nb• 2.3 km", "11 min$nb• 6.4 km"))

        assertNull(o.pickupMinutes)
        assertNull(o.fieldConfidence[OfferField.PICKUP_MINUTES])
        assertEquals(BigDecimal("2.3"), o.pickupDistance)
        assertEquals(BigDecimal("11"), o.tripMinutes)
        assertEquals(BigDecimal("6.4"), o.tripDistance)
        assertNotNull(o.price)
    }

    @Test
    fun `Bolt - preluare de 99 min se citeste`() {
        val o = bolt.parseaza(linii("99 min$nb• 2.3 km", "11 min$nb• 6.4 km"))

        assertEquals(BigDecimal("99"), o.pickupMinutes)
    }

    @Test
    fun `Bolt - cursa de 111 min se citeste, garda e doar pe preluare`() {
        val o = bolt.parseaza(linii("5 min$nb• 2.3 km", "111 min$nb• 60.4 km"))

        assertEquals(BigDecimal("111"), o.tripMinutes)
        assertEquals(BigDecimal("5"), o.pickupMinutes)
    }

    @Test
    fun `Bolt - motivul lipsei pentru preluarea de 3 cifre e FARA_MINUTE`() {
        val motive = bolt.motiveLipsa(linii("100 min$nb• 2.3 km", "11 min$nb• 6.4 km"))

        assertEquals(MotivLipsa.FARA_MINUTE, motive[OfferField.PICKUP_MINUTES])
    }

    @Test
    fun `Uber EN - preluare de 100 mins necunoscuta, 99 citita`() {
        fun oferta(preluare: String) = uberEn.parseaza(
            corp() + linie("RON20.00", sus = 1232, inaltime = 115) + linie(preluare, sus = 1700) + linie("6 mins (4.5 km) trip", sus = 1800),
        )

        assertNull(oferta("100 mins (8.0 km) away").pickupMinutes)
        assertEquals(BigDecimal("99"), oferta("99 mins (8.0 km) away").pickupMinutes)
    }

    @Test
    fun `Uber RO - preluare de 100 min necunoscuta, 99 citita, cursa de 111 citita`() {
        fun oferta(preluare: String, cursa: String) = uberRo.parseaza(
            corp() + linie("20,00 RON", sus = 1232, inaltime = 115) + linie(preluare, sus = 1700) + linie(cursa, sus = 1800),
        )

        assertNull(oferta("La 100 min. (4.0 km) distanta", "9 min. (4.5 km)").pickupMinutes)
        assertEquals(BigDecimal("99"), oferta("La 99 min. (4.0 km) distanta", "9 min. (4.5 km)").pickupMinutes)
        assertEquals(BigDecimal("111"), oferta("La 5 min. (4.0 km) distanta", "111 min. (60.5 km)").tripMinutes)
    }
}
