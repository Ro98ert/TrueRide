package app.trueride.core.parse

import app.trueride.core.model.OfferField
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * D-288.4: tiparul km al Uber (RO si EN) citeste si „(N km” fara paranteza de inchidere, fara spatiu inainte
 * de unitate si unitatea citita gresit de OCR (lista mica, explicita: kn, krn, Km, „k m”). Nu relaxeaza ce ar
 * putea lua o cifra din alta linie: paranteza de deschidere ramane obligatorie (si singura), iar dupa unitate
 * nu urmeaza litera. Toate cifrele sunt inventate.
 */
class TiparKmTolerantTest {

    private val en = OfferParser(UBER_EN_V1)
    private val ro = OfferParser(UBER_RO_V1)

    private fun linie(text: String, sus: Int, inaltime: Int = 46) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private val corp = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }

    private fun ofertaEn(preluare: String) =
        corp + linie("RON12.30", sus = 1232, inaltime = 90) + linie(preluare, sus = 1700) + linie("14 mins (7.2 km) ▢", sus = 1800)

    private fun ofertaRo(preluare: String) =
        corp + linie("12,30 RON", sus = 1232, inaltime = 90) + linie(preluare, sus = 1700) + linie("14 min. (7.2 km) ▢", sus = 1800)

    private fun egal(asteptat: String, real: BigDecimal?) =
        assertEquals(0, BigDecimal(asteptat).compareTo(requireNotNull(real) { "asteptat $asteptat, primit null" }))

    private fun kmEn(segment: String) = en.parseaza(ofertaEn(segment)).pickupDistance
    private fun kmRo(segment: String) = ro.parseaza(ofertaRo(segment)).pickupDistance

    // ---------- pozitive: fiecare varianta noua, pe EN si pe RO

    @Test
    fun `fara spatiu intre numar si unitate`() {
        egal("2.1", kmEn("6 mins (2.1km) ▢"))
        egal("2.1", kmRo("6 min. (2.1km) ▢"))
    }

    @Test
    fun `fara paranteza de inchidere`() {
        egal("2.1", kmEn("6 mins (2.1 km ▢"))
        egal("2.1", kmRo("6 min. (2.1 km ▢"))
    }

    @Test
    fun `fara spatiu si fara paranteza de inchidere`() {
        egal("2.1", kmEn("6 mins (2.1km"))
        egal("2.1", kmRo("6 min. (2.1km"))
    }

    @Test
    fun `unitatea Km cu majuscula`() {
        egal("2.1", kmEn("6 mins (2.1 Km) ▢"))
        egal("2.1", kmRo("6 min. (2.1 Km) ▢"))
    }

    @Test
    fun `unitatea kn`() {
        egal("2.1", kmEn("6 mins (2.1 kn) ▢"))
        egal("2.1", kmRo("6 min. (2.1 kn) ▢"))
    }

    @Test
    fun `unitatea krn`() {
        egal("2.1", kmEn("6 mins (2.1 krn) ▢"))
        egal("2.1", kmRo("6 min. (2.1 krn) ▢"))
    }

    @Test
    fun `unitatea k m cu spatiu`() {
        egal("2.1", kmEn("6 mins (2.1 k m) ▢"))
        egal("2.1", kmRo("6 min. (2.1 k m) ▢"))
    }

    @Test
    fun `unitatea gresita combinata cu lipsa spatiului si a parantezei`() {
        egal("2.1", kmEn("6 mins (2.1krn"))
    }

    @Test
    fun `litera din harta in numar se corecteaza si cu unitatea tolerata, cu incredere 0,9`() {
        val o = en.parseaza(ofertaEn("6 mins (l.1 krn) ▢"))
        egal("1.1", o.pickupDistance)
        egal("0.9", o.fieldConfidence[OfferField.PICKUP_DISTANCE])
    }

    @Test
    fun `forma curata ramane incredere 1 si segmentul de cursa nu se schimba`() {
        val o = en.parseaza(ofertaEn("6 mins (2.1 km) ▢"))
        egal("2.1", o.pickupDistance)
        assertEquals(BigDecimal.ONE, o.fieldConfidence[OfferField.PICKUP_DISTANCE])
        egal("7.2", o.tripDistance)
    }

    // ---------- negative: ce NU se citeste

    @Test
    fun `fara unitate nu e km`() {
        assertNull(kmEn("6 mins (2.1) ▢"))
        assertEquals(MotivLipsa.FARA_KM, en.motiveLipsa(ofertaEn("6 mins (2.1) ▢"))[OfferField.PICKUP_DISTANCE])
    }

    @Test
    fun `unitati straine nu sunt km`() {
        listOf("(2.1 mi)", "(2.1 kg)", "(2.1 m)", "(2.1 kmh)", "(2.1 knots)", "(2.1 kms)", "(2.1 k)").forEach {
            assertNull(kmEn("6 mins $it ▢"), it)
            assertNull(kmRo("6 min. $it ▢"), it)
        }
    }

    @Test
    fun `unitate in afara listei explicite nu se ghiceste`() {
        assertNull(kmEn("6 mins (2.1 lm) ▢"))
        assertNull(kmEn("6 mins (2.1 kr) ▢"))
    }

    @Test
    fun `fara paranteza de deschidere nu se ia nimic`() {
        assertNull(kmEn("6 mins 2.1 km) ▢"))
        assertNull(kmEn("6 mins 2.1 km ▢"))
    }

    @Test
    fun `paranteza dubla ramane necunoscuta (D-125)`() {
        assertNull(kmEn("6 mins ((2.1 km) ▢"))
    }

    @Test
    fun `km de pe alta linie nu se muta pe linia segmentului`() {
        val linii = corp + linie("RON12.30", sus = 1232, inaltime = 90) +
            linie("6 mins (", sus = 1700) + linie("2.1 km)", sus = 1750) + linie("14 mins (7.2 km) ▢", sus = 1800)
        val o = en.parseaza(linii)
        assertNull(o.pickupDistance)
        egal("7.2", o.tripDistance)
    }

    @Test
    fun `numar fara cifre inainte de unitate nu da km`() {
        assertNull(kmEn("6 mins ( km) ▢"))
        assertNull(kmEn("6 mins ( k m) ▢"))
    }

    @Test
    fun `viteza implauzibila cu unitatea tolerata ramane necunoscuta, nu zero (D-148)`() {
        val oferta = ofertaEn("3 mins (95 krn")
        assertNull(en.parseaza(oferta).pickupDistance)
        assertEquals(MotivLipsa.KM_IMPLAUZIBIL, en.motiveLipsa(oferta)[OfferField.PICKUP_DISTANCE])
    }

    @Test
    fun `versiunile seturilor au crescut odata cu tiparul`() {
        assertEquals("1.19.0", UBER_RO_V1.version)
        assertEquals("1.11.0", UBER_EN_V1.version)
    }
}
