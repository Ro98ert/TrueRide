package app.trueride.core.parse

import app.trueride.core.model.OfferField
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * D-122 (F-019, F-015 H2): o litera in locul unei cifre la minute, corectata din regulile Uber RO.
 * Trei zavoare: doar pe linia-ancora, doar cand `minutePattern` nu gaseste nimic, doar pe un token facut
 * exclusiv din cifre si literele din harta. Campul corectat are incredere sub 1 (invariantul 6: nu e zero,
 * dar nu e nici o citire sigura).
 */
class CorectiiMinuteTest {

    private val parser = OfferParser(UBER_RO_V1)

    private fun linie(text: String, sus: Int, inaltime: Int = 45) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private fun corp() = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }
    private val pret = linie("9,47 RON", sus = 1232, inaltime = 115)
    private val preluare = linie("4 min. (1.5 km)", sus = 1700)

    private fun egal(asteptat: String, real: BigDecimal?) {
        requireNotNull(real) { "asteptat $asteptat, primit null" }
        assertEquals(0, BigDecimal(asteptat).compareTo(real), "asteptat $asteptat, primit $real")
    }

    @Test
    fun `ll in fata lui min devine 11 cu incredere 0,9`() {
        val o = parser.parseaza(corp() + pret + preluare + linie("ll min. (4.5 km)", sus = 1800))
        egal("11", o.tripMinutes)
        assertEquals(0, BigDecimal("0.9").compareTo(o.fieldConfidence.getValue(OfferField.TRIP_MINUTES)))
        egal("4", o.pickupMinutes)
        assertEquals(BigDecimal.ONE, o.fieldConfidence.getValue(OfferField.PICKUP_MINUTES))
    }

    @Test
    fun `I mare si l mic dau amandoua 1, O mare si o mic dau 0`() {
        egal("11", parser.parseaza(corp() + pret + preluare + linie("Il min. (4.5 km)", sus = 1800)).tripMinutes)
        egal("10", parser.parseaza(corp() + pret + preluare + linie("lO min. (4.5 km)", sus = 1800)).tripMinutes)
        egal("20", parser.parseaza(corp() + pret + preluare + linie("2o min. (4.5 km)", sus = 1800)).tripMinutes)
    }

    @Test
    fun `cifrele curate nu trec prin corectie si pastreaza incredere 1`() {
        val o = parser.parseaza(corp() + pret + preluare + linie("9 min. (4.5 km)", sus = 1800))
        egal("9", o.tripMinutes)
        assertEquals(BigDecimal.ONE, o.fieldConfidence.getValue(OfferField.TRIP_MINUTES))
    }

    @Test
    fun `o litera din afara hartii lasa minutele necunoscute`() {
        val o = parser.parseaza(corp() + pret + preluare + linie("lx min. (4.5 km)", sus = 1800))
        assertNull(o.tripMinutes)
        assertEquals(MotivLipsa.FARA_MINUTE, parser.motiveLipsa(corp() + pret + preluare + linie("lx min. (4.5 km)", sus = 1800))[OfferField.TRIP_MINUTES])
    }

    // „l h 5 min.”: ora amestecata cu minutele e necunoscuta (I3, OfferParserTest); corectia are aceleasi
    // granite („h ” in fata), deci nu inventeaza nici 5, nici 15: campul ramane necunoscut, fara incredere.
    @Test
    fun `cu ora in fata minutele raman necunoscute si cu harta`() {
        val o = parser.parseaza(corp() + pret + preluare + linie("l h 5 min. (40 km)", sus = 1800))
        assertNull(o.tripMinutes)
        assertNull(o.fieldConfidence[OfferField.TRIP_MINUTES])
    }

    @Test
    fun `corectia nu atinge o linie care nu e ancora`() {
        // „Admin” nu are paranteza distantei → nu e segment → nu e citit deloc (I2, I3).
        val o = parser.parseaza(corp() + pret + preluare + linie("ll min. (4.5 km)", sus = 1800) + linie("Admin 4", sus = 1900))
        egal("11", o.tripMinutes)
        egal("4", o.pickupMinutes)
    }

    // Controlul negativ: fara harta, comportamentul de ieri (necunoscut). Regula e in date, nu in motor.
    @Test
    fun `fara harta de corectii literele raman necunoscute`() {
        val faraCorectii = OfferParser(UBER_RO_V1.copy(reguli = UBER_RO_V1.reguli.copy(corectiiCifre = emptyMap())))
        assertNull(faraCorectii.parseaza(corp() + pret + preluare + linie("ll min. (4.5 km)", sus = 1800)).tripMinutes)
    }

    // D-123 (F-025): o litera din harta lipita in fata cifrelor nu mai lasa tiparul sa ia doar ultima cifra.
    @Test
    fun `l0 in fata lui min devine 10 cu incredere 0,9, nu 0`() {
        val o = parser.parseaza(corp() + pret + preluare + linie("l0 min. (4.5 km)", sus = 1800))
        egal("10", o.tripMinutes)
        assertEquals(0, BigDecimal("0.9").compareTo(o.fieldConfidence.getValue(OfferField.TRIP_MINUTES)))
    }

    @Test
    fun `l2 la preluare devine 12, nu 2`() {
        val o = parser.parseaza(corp() + pret + linie("l2 min. (1.5 km)", sus = 1700) + linie("9 min. (4.5 km)", sus = 1800))
        egal("12", o.pickupMinutes)
        assertEquals(0, BigDecimal("0.9").compareTo(o.fieldConfidence.getValue(OfferField.PICKUP_MINUTES)))
    }

    // Forma „La5 min.” (3 oferte in raportul F-025): „a” nu e in harta, deci cifra se citeste direct, sigur.
    @Test
    fun `La lipit de cifra ramane citire directa cu incredere 1`() {
        val o = parser.parseaza(corp() + pret + linie("La5 min. (1.5 km)", sus = 1700) + linie("9 min. (4.5 km)", sus = 1800))
        egal("5", o.pickupMinutes)
        assertEquals(BigDecimal.ONE, o.fieldConfidence.getValue(OfferField.PICKUP_MINUTES))
    }

    // Token lipit de o alta litera: nu se stie unde incepe numarul, deci minutele raman necunoscute.
    @Test
    fun `litera din harta lipita de alt cuvant lasa minutele necunoscute`() {
        assertNull(parser.parseaza(corp() + pret + preluare + linie("Tl0 min. (4.5 km)", sus = 1800)).tripMinutes)
    }

    @Test
    fun `setul Uber RO pastreaza corectiile din 1 5 0`() {
        // 1.6.0 (D-130) adauga pretCereSegment, 1.7.0 (F-031) virgula dupa min; harta ramane cea din 1.5.0.
        assertEquals("1.19.0", UBER_RO_V1.version)
        assertEquals(mapOf("l" to "1", "I" to "1", "O" to "0", "o" to "0"), UBER_RO_V1.reguli.corectiiCifre)
    }

    // D-315, val5-l-margine: un `l` de zgomot lipit de minute, la marginea tokenului, nu are voie sa-l faca de 3 cifre.
    @Test
    fun `litera corectata la margine care face minutele de 3 cifre le lasa necunoscute`() {
        for (forma in listOf("l37", "37l", "I42", "O55", "73o")) {
            val o = parser.parseaza(corp() + pret + preluare + linie("$forma min. (4.5 km)", sus = 1800))
            assertNull(o.tripMinutes, forma)
            assertNull(o.fieldConfidence[OfferField.TRIP_MINUTES], forma)
            egal("4", o.pickupMinutes)
        }
    }

    @Test
    fun `litera la margine pe doua cifre ramane corectie legitima`() {
        egal("17", parser.parseaza(corp() + pret + preluare + linie("l7 min. (4.5 km)", sus = 1800)).tripMinutes)
        egal("71", parser.parseaza(corp() + pret + preluare + linie("7l min. (4.5 km)", sus = 1800)).tripMinutes)
    }

    @Test
    fun `litera in interiorul tokenului nu cade sub garda de margine`() {
        egal("101", parser.parseaza(corp() + pret + preluare + linie("1O1 min. (4.5 km)", sus = 1800)).tripMinutes)
    }

    @Test
    fun `garda de margine nu atinge kilometrii`() {
        val o = parser.parseaza(corp() + pret + preluare + linie("9 min. (l37 km)", sus = 1800))
        egal("9", o.tripMinutes)
        assertNull(o.tripDistance, "km pe 3 cifre fata de 9 min raman judecati de D-148")
    }
}
