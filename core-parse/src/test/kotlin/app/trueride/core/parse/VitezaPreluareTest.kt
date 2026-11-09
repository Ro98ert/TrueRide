package app.trueride.core.parse

import app.trueride.core.model.OfferField
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * D-371: la PRELUARE, minutele si km merg impreuna. OCR-ul pierde uneori orele din „2 h 22 min.” si citeste 22 in loc
 * de 142; viteza implicita iese din orice masuratoare reala (max 62,64 km/h) si tradeaza citirea. Deci:
 * 1. cand verificarea D-332 respinge km de la preluare, minutele de la preluare devin necunoscute si ele;
 * 2. o preluare cu viteza medie peste 65 km/h (minute + 0,5, ca la D-332) are minute SI km necunoscuti.
 * Cursa ramane neschimbata. Valorile sunt sintetice, nu dintr-o oferta reala (invariantul 2).
 */
class VitezaPreluareTest {

    private val en = OfferParser(UBER_EN_V1)
    private val ro = OfferParser(UBER_RO_V1)

    private fun linie(text: String, sus: Int, inaltime: Int = 45) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private fun corp() = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }

    private fun ofertaEn(preluare: String, cursa: String = "9 mins (4.5 km) trip") =
        corp() + linie("RON20.00", sus = 1232, inaltime = 115) + linie(preluare, sus = 1700) + linie(cursa, sus = 1800)

    private fun ofertaRo(preluare: String, cursa: String = "9 min. (4.5 km)") =
        corp() + linie("20,00 RON", sus = 1232, inaltime = 115) + linie(preluare, sus = 1700) + linie(cursa, sus = 1800)

    @Test
    fun `km respinsi de D-332 la preluare iau cu ei si minutele (Uber RO, ore pierdute)`() {
        // „2 h 22 min.” citit „22 min.”: 40,0 km / 22,5 min * 60 = 106,7 km/h, peste pragul D-332 de 100.
        val oferta = ro.parseaza(ofertaRo("22 min. (40.0 km)"))

        assertNull(oferta.pickupMinutes)
        assertNull(oferta.pickupDistance)
        assertEquals(BigDecimal("9"), oferta.tripMinutes)
        assertEquals(BigDecimal("4.5"), oferta.tripDistance)
        assertEquals(BigDecimal("20.00"), oferta.price)
    }

    @Test
    fun `punct zecimal pierdut la preluare (D-332) anuleaza si minutele`() {
        val oferta = en.parseaza(ofertaEn("3 mins (95 km) away"))

        assertNull(oferta.pickupMinutes)
        assertNull(oferta.pickupDistance)
    }

    @Test
    fun `preluare la 72 km_h peste 29 de minute - minute si km necunoscuti, sub pragul D-332`() {
        // 34,8 km / 29,5 min * 60 = 70,8 km/h: D-332 (100) nu o respinge, D-371 da.
        val oferta = en.parseaza(ofertaEn("29 mins (34.8 km) away"))

        assertNull(oferta.pickupMinutes)
        assertNull(oferta.pickupDistance)
        assertEquals(BigDecimal("4.5"), oferta.tripDistance)
    }

    @Test
    fun `preluare la 60 km_h - minute si km raman`() {
        val oferta = en.parseaza(ofertaEn("29 mins (29.0 km) away"))

        assertEquals(BigDecimal("29"), oferta.pickupMinutes)
        assertEquals(BigDecimal("29.0"), oferta.pickupDistance)
    }

    @Test
    fun `limita preluarii e 65 km_h cu minutele + 0,5 - exact la prag ramane, peste prag se anuleaza`() {
        // 29 min -> 29,5 min * 65 / 60 = 31,958 km. 31,5 km = 65,2 km/h pe minutele afisate, dar sub prag cu toleranta.
        val sub = en.parseaza(ofertaEn("29 mins (31.9 km) away"))
        val toleranta = en.parseaza(ofertaEn("29 mins (31.5 km) away"))
        val peste = en.parseaza(ofertaEn("29 mins (32.0 km) away"))

        assertEquals(BigDecimal("31.9"), sub.pickupDistance)
        assertEquals(BigDecimal("29"), sub.pickupMinutes)
        assertEquals(BigDecimal("31.5"), toleranta.pickupDistance)
        assertEquals(BigDecimal("29"), toleranta.pickupMinutes)
        assertNull(peste.pickupDistance)
        assertNull(peste.pickupMinutes)
    }

    @Test
    fun `la 1 minut limita e 1,625 km - 1,6 trece, 1,7 nu`() {
        // 1 min + 0,5 = 1,5 min * 65 / 60 = 1,625 km: 1,6 trece, 1,7 nu.
        assertEquals(BigDecimal("1.6"), en.parseaza(ofertaEn("1 mins (1.6 km) away")).pickupDistance)
        val peste = en.parseaza(ofertaEn("1 mins (1.7 km) away"))
        assertNull(peste.pickupDistance)
        assertNull(peste.pickupMinutes)
    }

    @Test
    fun `cursa la 80 km_h ramane neschimbata - D-371 se aplica doar preluarii`() {
        // 27,0 km / 20,5 min * 60 = 79 km/h: sub pragul D-332 (100), peste 65.
        val oferta = en.parseaza(ofertaEn("10 mins (4.0 km) away", "20 mins (27.0 km) trip"))

        assertEquals(BigDecimal("20"), oferta.tripMinutes)
        assertEquals(BigDecimal("27.0"), oferta.tripDistance)
        assertEquals(BigDecimal("10"), oferta.pickupMinutes)
        assertEquals(BigDecimal("4.0"), oferta.pickupDistance)
    }

    @Test
    fun `km cursei respinsi de D-332 nu ating minutele preluarii si nici minutele cursei`() {
        val oferta = en.parseaza(ofertaEn("10 mins (4.0 km) away", "6 mins (52 km) trip"))

        assertNull(oferta.tripDistance)
        assertEquals(BigDecimal("6"), oferta.tripMinutes)
        assertEquals(BigDecimal("10"), oferta.pickupMinutes)
    }

    @Test
    fun `preluare cu 0 minute sau fara minute nu se verifica (impartirea la zero e stare)`() {
        val zero = en.parseaza(ofertaEn("0 mins (1.2 km) away"))
        val fara = en.parseaza(ofertaEn("▢ mins (8.0 km) away"))

        assertEquals(BigDecimal("1.2"), zero.pickupDistance)
        assertEquals(BigDecimal("0"), zero.pickupMinutes)
        assertEquals(BigDecimal("8.0"), fara.pickupDistance)
        assertNull(fara.pickupMinutes)
    }

    @Test
    fun `motivul lipsei e KM_IMPLAUZIBIL pentru ambele campuri ale preluarii, nu FARA_MINUTE`() {
        val motive = en.motiveLipsa(ofertaEn("29 mins (34.8 km) away"))

        assertEquals(
            mapOf(OfferField.PICKUP_MINUTES to MotivLipsa.KM_IMPLAUZIBIL, OfferField.PICKUP_DISTANCE to MotivLipsa.KM_IMPLAUZIBIL),
            motive,
        )
    }

    @Test
    fun `linia km se reciteste si dupa ce minutele au fost anulate, linia minutelor nu (D-288_4, D-367)`() {
        val preluare = linie("3 mins (95 km) away", sus = 1700)
        val linii = corp() + linie("RON20.00", sus = 1232, inaltime = 115) + preluare + linie("9 mins (4.5 km) trip", sus = 1800)

        assertSame(preluare, en.liniaKmPreluareDeRecitit(linii))
        assertNull(en.liniaMinutePreluareDeRecitit(linii))
    }

    @Test
    fun `minutele citite inainte de viteza se pot cere, ca recitirea sa le compare (D-371)`() {
        val linii = ofertaEn("3 mins (95 km) away")

        assertEquals(BigDecimal("3"), en.minutePreluareInainteDeViteza(linii))
        assertEquals(BigDecimal("10"), en.minutePreluareInainteDeViteza(ofertaEn("10 mins (4.0 km) away")))
        assertNull(en.minutePreluareInainteDeViteza(ofertaEn("▢ mins (8.0 km) away")))
        assertNotNull(ParserPeLimbi(listOf(ro, en)).minutePreluareInainteDeViteza(linii))
    }
}
