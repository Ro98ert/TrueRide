package app.trueride.core.parse

import app.trueride.core.model.OfferField
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * D-329.1: lei/km pe km totali (preluare + cursa) in afara benzii setului [minLeiPeKm; maxLeiPeKm] = pret necunoscut
 * (`PRET_IMPLAUZIBIL`), restul campurilor raman. Banda e data in reguli, nu in motor. Fara km (unul lipsa sau suma 0)
 * nu se judeca. Valorile sunt inventate (invariantul 2).
 */
class BandaLeiPeKmTest {

    private val nb = " "
    private val ro = OfferParser(UBER_RO_V1)
    private val en = OfferParser(UBER_EN_V1)
    private val bolt = OfferParser(BOLT_RO_V1)

    private fun linie(text: String, sus: Int, inaltime: Int = 45) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private fun linii(pret: String, preluare: String, cursa: String) =
        listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) } +
            linie(pret, sus = 1232, inaltime = 115) + linie(preluare, sus = 1700) + linie(cursa, sus = 1800)

    private fun uberRo(pret: String) = linii(pret, "4 min. (1.5 km)", "15 min. (5.0 km)")   // 6,5 km totali

    @Test
    fun `Uber RO - pret prea mare pe km e necunoscut, segmentele raman`() {
        val o = ro.parseaza(uberRo("210,40 RON"))   // 32,4 lei/km

        assertNull(o.price)
        assertNull(o.fieldConfidence[OfferField.PRICE])
        assertEquals(BigDecimal("5.0"), o.tripDistance)
        assertEquals(BigDecimal("15"), o.tripMinutes)
        assertEquals(MotivLipsa.PRET_IMPLAUZIBIL, ro.motiveLipsa(uberRo("210,40 RON"))[OfferField.PRICE])
    }

    @Test
    fun `Uber RO - pret prea mic pe km e necunoscut`() {
        assertNull(ro.parseaza(uberRo("1,40 RON")).price)   // 0,22 lei/km
    }

    @Test
    fun `Uber RO - pret in banda se citeste`() {
        assertEquals(BigDecimal("21.40"), ro.parseaza(uberRo("21,40 RON")).price)
        assertNull(ro.motiveLipsa(uberRo("21,40 RON"))[OfferField.PRICE])
    }

    @Test
    fun `Uber EN - pret prea mare pe km e necunoscut`() {
        val l = linii("RON210.40", "4 mins (1.5 km)", "15 mins (5.0 km)")
        assertNull(en.parseaza(l).price)
        assertEquals(BigDecimal("21.40"), en.parseaza(linii("RON21.40", "4 mins (1.5 km)", "15 mins (5.0 km)")).price)
    }

    @Test
    fun `Bolt - pret prea mare pe km e necunoscut`() {
        val l = linii("214,00${nb}lei (▢, ▢ ▢)", "5 min$nb• 2.3 km", "15 min$nb• 5.0 km")   // 29,3 lei/km
        assertNull(bolt.parseaza(l).price)
        assertEquals(
            BigDecimal("21.40"),
            bolt.parseaza(linii("21,40${nb}lei (▢, ▢ ▢)", "5 min$nb• 2.3 km", "15 min$nb• 5.0 km")).price,
        )
    }

    @Test
    fun `fara km pe un segment pretul nu se judeca`() {
        val l = linii("210,40 RON", "4 min. (▢ km)", "15 min. (5.0 km)")
        assertNotNull(ro.parseaza(l).price)
    }

    @Test
    fun `banda e data in fiecare set de reguli`() {
        listOf(UBER_RO_V1, UBER_EN_V1, BOLT_RO_V1).forEach {
            assertNotNull(it.minLeiPeKm, it.version)
            assertNotNull(it.maxLeiPeKm, it.version)
        }
    }
}
