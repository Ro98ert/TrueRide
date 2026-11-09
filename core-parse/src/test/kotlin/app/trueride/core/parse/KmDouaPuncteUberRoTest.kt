package app.trueride.core.parse

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * D-315 (val B, km_doua_puncte): Uber RO a afisat o data „DD min. (D:D km)” — doua puncte in loc de punct zecimal.
 * `:` se accepta doar intre cifre, in paranteza si urmat de unitatea km, cu cel mult o zecimala (P-sim-10).
 * Nu se citeste „:” din ora sau din eticheta din fata. Toate cifrele sunt inventate.
 */
class KmDouaPuncteUberRoTest {

    private val ro = OfferParser(UBER_RO_V1)

    private fun linie(text: String, sus: Int, inaltime: Int = 46) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private val pret = linie("12,47 RON", sus = 1232, inaltime = 90)

    private fun oferta(segment1: String, segment2: String = "14 min. (7.2 km) ▢") =
        listOf(pret, linie(segment1, sus = 1700), linie(segment2, sus = 1800))

    private fun egal(asteptat: String, real: BigDecimal?) =
        assertEquals(0, BigDecimal(asteptat).compareTo(requireNotNull(real) { "asteptat $asteptat, primit null" }))

    @Test
    fun `km cu doua puncte intre cifre se citesc ca zecimala`() {
        egal("3.4", ro.parseaza(oferta("6 min. (3:4 km) ▢")).pickupDistance)
    }

    @Test
    fun `eticheta cu doua puncte din fata nu schimba citirea`() {
        egal("3.4", ro.parseaza(oferta("▢: 6 min. (3:4 km)")).pickupDistance)
    }

    @Test
    fun `doua puncte cu doua zecimale raman necunoscute`() {
        assertNull(ro.parseaza(oferta("6 min. (3:45 km) ▢")).pickupDistance)
    }

    @Test
    fun `ora fara km nu devine km`() {
        assertNull(ro.parseaza(oferta("6 min. (12:30) ▢")).pickupDistance)
        assertNull(ro.parseaza(oferta("6 min. (12:3 min) ▢")).pickupDistance)
    }

    @Test
    fun `punctul si virgula zecimala raman neschimbate`() {
        egal("3.4", ro.parseaza(oferta("6 min. (3.4 km) ▢")).pickupDistance)
        egal("3.4", ro.parseaza(oferta("6 min. (3,4 km) ▢")).pickupDistance)
    }
}
