package app.trueride.core.parse

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * F5: aplicatia Uber vorbeste limba telefonului, deci acelasi ecran poate fi RO sau EN. ParserPeLimbi
 * ruleaza seturile si pastreaza rezultatul INTREG al celui care citeste mai multe campuri (inv. 5, 6):
 * niciun camp dintr-un set amestecat cu altul din celalalt.
 */
class ParserPeLimbiTest {

    private val ro = OfferParser(UBER_RO_V1)
    private val en = OfferParser(UBER_EN_V1)
    private val ambele = ParserPeLimbi(listOf(ro, en))

    private fun linie(text: String, sus: Int, inaltime: Int = 45) = LinieOcr(text, Cutie(100, sus, 700, sus + inaltime))
    private val corp = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", it) }

    /** Cifre inventate, in formele observate: RO „9,47 RON / 4 min. (1.5 km)”, EN „RON9.47 / 4 mins (1.5 km)”. */
    private val ecranRo = corp + listOf(linie("9,47 RON", 1232, 115), linie("4 min. (1.5 km)", 1700), linie("9 min. (4.5 km)", 1800))
    private val ecranEn = corp + listOf(linie("RON12.30", 1232, 115), linie("6 mins (2.1 km) ▢", 1700), linie("14 mins (7.2 km) ▢", 1800))

    private fun egal(a: String, v: BigDecimal?) = assertEquals(0, BigDecimal(a).compareTo(requireNotNull(v)))

    @Test
    fun `pe un ecran EN castiga setul EN, cu toate campurile`() {
        val o = ambele.parseaza(ecranEn)

        egal("12.30", o.price)
        egal("6", o.pickupMinutes)
        egal("7.2", o.tripDistance)
    }

    @Test
    fun `pe un ecran RO rezultatul e exact cel al setului RO`() {
        assertEquals(ro.parseaza(ecranRo), ambele.parseaza(ecranRo))
    }

    @Test
    fun `seturile nu se citesc unul pe altul`() {
        assertNull(ro.parseaza(ecranEn).price)
        assertNull(ro.parseaza(ecranEn).pickupMinutes)
        assertNull(en.parseaza(ecranRo).price)
        assertNull(en.parseaza(ecranRo).pickupMinutes)
    }

    @Test
    fun `liniile folosite si motivele vin din setul castigator`() {
        assertEquals(en.liniiFolosite(ecranEn), ambele.liniiFolosite(ecranEn))
        assertEquals(en.motiveLipsa(ecranEn), ambele.motiveLipsa(ecranEn))
        assertEquals(ro.motiveLipsa(ecranRo), ambele.motiveLipsa(ecranRo))
    }

    @Test
    fun `la egalitate castiga primul set`() {
        assertEquals(ro.motiveLipsa(corp), ambele.motiveLipsa(corp))
    }

    @Test
    fun `pretul EN fara punct zecimal e necunoscut, nu ghicit`() {
        val o = en.parseaza(corp + listOf(linie("RON1230", 1232, 115), linie("6 mins (2.1 km)", 1700), linie("14 mins (7.2 km)", 1800)))

        assertNull(o.price)
        egal("6", o.pickupMinutes)
    }

    // Fable (verificarea etichetelor): pe n186 OCR-ul a pus o cifra straina dupa pret; pretul e ancorat pe „RON”.
    @Test
    fun `o cifra straina dupa pretul EN nu schimba pretul`() {
        val o = en.parseaza(corp + listOf(linie("RON12.30 9", 1232, 115), linie("6 mins (2.1 km)", 1700), linie("14 mins (7.2 km)", 1800)))

        egal("12.30", o.price)
    }

    @Test
    fun `setul EN e versiunea 1_1_0 si are pragul de inaltime propriu, nu pe cel RO`() {
        assertEquals("1.11.0", UBER_EN_V1.version)
        assertEquals("en", UBER_EN_V1.locale)
        // D-143: valoarea e a setului EN; daca RO se recalibreaza, EN nu se misca.
        assertEquals(0, BigDecimal("1.5").compareTo(UBER_EN_V1.reguli.pretInaltimeMinimaRelativa))
    }
}
