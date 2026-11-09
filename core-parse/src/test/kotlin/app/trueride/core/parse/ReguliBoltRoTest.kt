package app.trueride.core.parse

import app.trueride.core.model.DistanceUnit
import app.trueride.core.model.PriceBasis
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Linii sintetice (cifre inventate) in forma nodurilor Bolt RO, cu spatiul neintrerupt U+00A0 din noduri. */
class ReguliBoltRoTest {

    private val parser = OfferParser(BOLT_RO_V1)
    private val nb = "\u00A0"

    private fun linie(text: String, sus: Int, inaltime: Int = 82) =
        LinieOcr(text, Cutie(152, sus, 962, sus + inaltime))

    private val corp = listOf(500, 700, 900).map { linie("▢ ▢", it) }
    private fun oferta(pret: String = "21,40${nb}lei (▢, ▢ ▢)") = corp + listOf(
        linie(pret, sus = 1375, inaltime = 114),
        linie("5 min$nb• 2.3 km", sus = 1650),
        linie("11 min$nb• 6.4 km", sus = 1875),
    )

    private fun egal(asteptat: String, real: BigDecimal?) =
        assertEquals(0, BigDecimal(asteptat).compareTo(requireNotNull(real) { "asteptat $asteptat, primit null" }))

    @Test
    fun `pretul cu virgula si lei dupa numar se citeste brut, in RON`() {
        val o = parser.parseaza(oferta())

        egal("21.40", o.price)
        assertEquals("RON", o.currency)
        assertEquals(PriceBasis.GROSS, o.priceBasis)
    }

    @Test
    fun `segmentele cu punct-mijloc si spatiu neintrerupt dau minute si km in ordine`() {
        val o = parser.parseaza(oferta())

        egal("5", o.pickupMinutes)
        egal("2.3", o.pickupDistance)
        egal("11", o.tripMinutes)
        egal("6.4", o.tripDistance)
        assertEquals(DistanceUnit.KM, o.distanceUnitDetected)
    }

    @Test
    fun `km fara zecimale se citesc`() {
        val o = parser.parseaza(corp + listOf(linie("9,00${nb}lei", 1375, 114), linie("2 min$nb• 1 km", 1650), linie("4 min$nb• 3 km", 1875)))

        egal("1", o.pickupDistance)
        egal("3", o.tripDistance)
    }

    @Test
    fun `o suma fara segmente pe ecran nu e pret`() {
        assertNull(parser.parseaza(corp + linie("104,73${nb}lei", sus = 162, inaltime = 74)).price)
    }

    @Test
    fun `o suma fara bani nu e pret`() {
        val o = parser.parseaza(oferta(pret = "▢ 400${nb}lei"))

        assertNull(o.price)
    }

    @Test
    fun `un singur segment lasa campurile segmentelor necunoscute`() {
        val o = parser.parseaza(corp + listOf(linie("21,40${nb}lei", 1375, 114), linie("5 min$nb• 2.3 km", 1650)))

        assertNull(o.pickupMinutes)
        assertNull(o.tripDistance)
    }

    @Test
    fun `versiunea si limba setului`() {
        assertEquals("1.6.0", BOLT_RO_V1.version)
        assertEquals("ro-RO", BOLT_RO_V1.locale)
    }
}
