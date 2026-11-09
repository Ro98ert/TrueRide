package app.trueride.core.parse

import app.trueride.core.model.OfferField
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * F-038: harta l/I -> 1, O/o -> 0 (D-124) trebuie sa ajunga si la pretul Uber EN, unde moneda e lipita IN FATA
 * („RON1l.50”). Pe telefonul de test 2 forma „RON<cifra><litera>.<doi bani>” dadea FORMA_PRET. Pretul fara bani
 * ramane necunoscut (F-011, D-080), corectat sau nu. Toate cifrele sunt inventate.
 */
class CorectiiPretUberEnTest {

    private val en = OfferParser(UBER_EN_V1)

    private fun linie(text: String, sus: Int, inaltime: Int = 46) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private val corp = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }
    private val segmente = listOf(linie("6 mins (2.1 km) ▢", sus = 1700), linie("14 mins (7.2 km) ▢", sus = 1800))

    private fun cuPret(text: String) = corp + segmente + linie(text, sus = 1232, inaltime = 90)

    private fun egal(asteptat: String, real: BigDecimal?) =
        assertEquals(0, BigDecimal(asteptat).compareTo(requireNotNull(real) { "asteptat $asteptat, primit null" }))

    @Test
    fun `litera din harta dupa prima cifra se corecteaza, cu incredere 0,9`() {
        val o = en.parseaza(cuPret("RON1l.50"))
        egal("11.50", o.price)
        egal("0.9", o.fieldConfidence[OfferField.PRICE])
    }

    @Test
    fun `litera din harta lipita de RON se corecteaza`() {
        egal("10.50", en.parseaza(cuPret("RONl0.50")).price)
    }

    @Test
    fun `O in bani se corecteaza`() {
        egal("12.05", en.parseaza(cuPret("RON12.O5")).price)
    }

    @Test
    fun `pretul curat pastreaza incredere 1`() {
        val o = en.parseaza(cuPret("RON12.30"))
        egal("12.30", o.price)
        assertEquals(BigDecimal.ONE, o.fieldConfidence[OfferField.PRICE])
    }

    @Test
    fun `litera din afara hartii lasa pretul necunoscut`() {
        assertNull(en.parseaza(cuPret("RON1x.50")).price)
    }

    // F-011: banii lipsa nu se ghicesc, nici dupa corectie.
    @Test
    fun `pret fara bani ramane FORMA_PRET`() {
        val linii = cuPret("RON15")
        assertNull(en.parseaza(linii).price)
        assertEquals(MotivLipsa.FORMA_PRET, en.motiveLipsa(linii)[OfferField.PRICE])
    }

    @Test
    fun `litera in locul punctului ramane FORMA_PRET`() {
        val linii = cuPret("RON12l50")
        assertNull(en.parseaza(linii).price)
        assertEquals(MotivLipsa.FORMA_PRET, en.motiveLipsa(linii)[OfferField.PRICE])
    }
}
