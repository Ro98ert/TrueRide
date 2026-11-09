package app.trueride.core.parse

import app.trueride.core.model.OfferField
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * F-036, D-148, D-332: km care nu se potrivesc cu minutele aceluiasi segment (viteza cu minutele + 0,5 peste
 * 100 km/h sub 30 de minute, peste 150 km/h de la 30) devin necunoscuti — OCR-ul a pierdut punctul zecimal
 * sau a dublat o cifra. Punctul nu se ghiceste.
 * Valorile sunt sintetice, nu dintr-o oferta reala (invariantul 2).
 */
class VitezaImplauzibilaTest {

    private val en = OfferParser(UBER_EN_V1)
    private val ro = OfferParser(UBER_RO_V1)

    private fun linie(text: String, sus: Int, inaltime: Int = 45) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private fun corp() = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }

    private fun ofertaEn(preluare: String, cursa: String) =
        corp() + linie("RON20.00", sus = 1232, inaltime = 115) +
            linie(preluare, sus = 1700) + linie(cursa, sus = 1800)

    @Test
    fun `km cursei fara punct zecimal devin necunoscuti, restul campurilor raman`() {
        val oferta = en.parseaza(ofertaEn("10 mins (8.0 km) away", "6 mins (52 km) trip"))

        assertNull(oferta.tripDistance)
        assertEquals(BigDecimal("6"), oferta.tripMinutes)
        assertEquals(BigDecimal("8.0"), oferta.pickupDistance)
        assertEquals(BigDecimal("20.00"), oferta.price)
    }

    @Test
    fun `motivul lipsei e KM_IMPLAUZIBIL, nu FARA_KM`() {
        val motive = en.motiveLipsa(ofertaEn("10 mins (8.0 km) away", "6 mins (52 km) trip"))

        assertEquals(mapOf(OfferField.TRIP_DISTANCE to MotivLipsa.KM_IMPLAUZIBIL), motive)
    }

    @Test
    fun `verificarea se aplica si preluarii si setului RO`() {
        val linii = corp() + linie("20,00 RON", sus = 1232, inaltime = 115) +
            linie("La 4 min. (31 km) distanta", sus = 1700) + linie("9 min. (4.5 km)", sus = 1800)

        val oferta = ro.parseaza(linii)

        assertNull(oferta.pickupDistance)
        assertEquals(BigDecimal("4.5"), oferta.tripDistance)
    }

    @Test
    fun `sub 30 de minute, chiar sub 100 km_h km raman citi`() {
        // 2 min + 0,5 = 2,5 min; 4,1 km / 2,5 min * 60 = 98,4 km/h: sub prag. Pe cursa: la preluare D-371 (65 km/h) e mai strict.
        val oferta = en.parseaza(ofertaEn("2 mins (0.5 km) away", "2 mins (4.1 km) trip"))

        assertEquals(BigDecimal("4.1"), oferta.tripDistance)
    }

    @Test
    fun `sub 30 de minute, chiar peste 100 km_h km devin necunoscuti`() {
        // 4,2 km / 2,5 min * 60 = 100,8 km/h: peste prag.
        val oferta = en.parseaza(ofertaEn("2 mins (4.2 km) away", "9 mins (4.5 km) trip"))

        assertNull(oferta.pickupDistance)
    }

    @Test
    fun `la 29 de minute pragul e inca 100 km_h`() {
        // 50,0 km / 29,5 min * 60 = 101,7 km/h: peste 100, desi sub vechiul prag de 150.
        val oferta = en.parseaza(ofertaEn("2 mins (0.5 km) away", "29 mins (50.0 km) trip"))

        assertNull(oferta.tripDistance)
    }

    @Test
    fun `de la 30 de minute, chiar sub 150 km_h km raman citi`() {
        // 76,0 km / 30,5 min * 60 = 149,5 km/h: sub prag.
        val oferta = en.parseaza(ofertaEn("2 mins (0.5 km) away", "30 mins (76.0 km) trip"))

        assertEquals(BigDecimal("76.0"), oferta.tripDistance)
    }

    @Test
    fun `de la 30 de minute, chiar peste 150 km_h km devin necunoscuti`() {
        // 77,0 km / 30,5 min * 60 = 151,5 km/h: peste prag.
        val oferta = en.parseaza(ofertaEn("2 mins (0.5 km) away", "30 mins (77.0 km) trip"))

        assertNull(oferta.tripDistance)
    }

    @Test
    fun `segment cu 0 minute nu se verifica`() {
        val oferta = en.parseaza(ofertaEn("0 mins (1.2 km) away", "9 mins (4.5 km) trip"))

        assertEquals(BigDecimal("1.2"), oferta.pickupDistance)
    }

    @Test
    fun `km fara minute citite nu se verifica`() {
        val oferta = en.parseaza(ofertaEn("▢ mins (8.0 km) away", "9 mins (4.5 km) trip"))

        assertEquals(BigDecimal("8.0"), oferta.pickupDistance)
    }
}
