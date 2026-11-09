package app.trueride.core.parse

import app.trueride.core.model.OfferField
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * D-123 (km) si D-124 (pret), din raportul F-025: aceeasi harta l/I -> 1, O/o -> 0 ca la minute (D-122).
 * Aceleasi zavoare: doar cand citirea directa esueaza, doar pe un token facut din cifre si litere din harta,
 * nelipit de alta litera sau cifra; campul corectat are incredere 0,9.
 */
class CorectiiKmPretTest {

    private val parser = OfferParser(UBER_RO_V1)

    private fun linie(text: String, sus: Int, inaltime: Int = 45) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private fun corp() = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }
    private val pret = linie("9,47 RON", sus = 1232, inaltime = 115)
    private val preluare = linie("4 min. (1.5 km)", sus = 1700)
    private val cursa = linie("9 min. (4.5 km)", sus = 1800)

    private fun egal(asteptat: String, real: BigDecimal?) {
        requireNotNull(real) { "asteptat $asteptat, primit null" }
        assertEquals(0, BigDecimal(asteptat).compareTo(real), "asteptat $asteptat, primit $real")
    }

    private fun incredere(asteptat: String, real: BigDecimal?) =
        assertEquals(0, BigDecimal(asteptat).compareTo(requireNotNull(real)), "incredere $real")

    // --- km (D-123) ---

    @Test
    fun `km cu l dupa punct devine 1,1 cu incredere 0,9`() {
        val o = parser.parseaza(corp() + pret + preluare + linie("9 min. (1.l km)", sus = 1800))
        egal("1.1", o.tripDistance)
        incredere("0.9", o.fieldConfidence[OfferField.TRIP_DISTANCE])
    }

    @Test
    fun `km cu l in fata devine 12,5, cu O devine 10,5`() {
        // 12 min, nu 4 si nu 9: 12,5 km in 4 min ar fi 167 km/h (D-148), in 9 min 79 km/h (D-371, peste 65): km necunoscuti.
        egal("12.5", parser.parseaza(corp() + pret + linie("12 min. (l2.5 km)", sus = 1700) + cursa).pickupDistance)
        egal("10.5", parser.parseaza(corp() + pret + preluare + linie("9 min. (1O.5 km)", sus = 1800)).tripDistance)
    }

    @Test
    fun `km curati pastreaza incredere 1`() {
        val o = parser.parseaza(corp() + pret + preluare + cursa)
        egal("4.5", o.tripDistance)
        assertEquals(BigDecimal.ONE, o.fieldConfidence[OfferField.TRIP_DISTANCE])
    }

    @Test
    fun `km cu o litera din afara hartii raman necunoscuti`() {
        val linii = corp() + pret + preluare + linie("9 min. (1.x km)", sus = 1800)
        assertNull(parser.parseaza(linii).tripDistance)
        assertEquals(MotivLipsa.FARA_KM, parser.motiveLipsa(linii)[OfferField.TRIP_DISTANCE])
    }

    // D-125: pe SM-A137F, OCR-ul a citit „(l2.5 km)” ca „((2.5 km)”. Tiparul lua „(2.5 km)” de dupa a doua
    // paranteza: 2.5 citit sigur in loc de 12.5. Paranteza dubla = km necunoscuti, nu ghicim.
    @Test
    fun `paranteza dubla in fata km lasa km necunoscuti`() {
        val linii = corp() + pret + linie("4 min. ((2.5 km) distanta", sus = 1700) + cursa
        assertNull(parser.parseaza(linii).pickupDistance)
        assertEquals(MotivLipsa.FARA_KM, parser.motiveLipsa(linii)[OfferField.PICKUP_DISTANCE])
    }

    @Test
    fun `paranteza dubla cu litera din harta dupa ea ramane tot necunoscuta`() {
        assertNull(parser.parseaza(corp() + pret + linie("4 min. ((l2.5 km)", sus = 1700) + cursa).pickupDistance)
    }

    // --- pret (D-124) ---

    @Test
    fun `pret cu l in fata devine 13,76 cu incredere 0,9, nu 3,76`() {
        val o = parser.parseaza(corp() + linie("l3,76 RON", sus = 1232, inaltime = 115) + preluare + cursa)
        egal("13.76", o.price)
        incredere("0.9", o.fieldConfidence[OfferField.PRICE])
    }

    @Test
    fun `pret curat pastreaza incredere 1`() {
        val o = parser.parseaza(corp() + pret + preluare + cursa)
        egal("9.47", o.price)
        assertEquals(BigDecimal.ONE, o.fieldConfidence[OfferField.PRICE])
    }

    // Forma pretului (D-080) se verifica pe valoarea corectata: un ban pierdut ramane pret necunoscut.
    @Test
    fun `pret corectat fara doi bani ramane necunoscut`() {
        val linii = corp() + linie("l3,7 RON", sus = 1232, inaltime = 115) + preluare + cursa
        assertNull(parser.parseaza(linii).price)
        assertEquals(MotivLipsa.FORMA_PRET, parser.motiveLipsa(linii)[OfferField.PRICE])
    }

    @Test
    fun `pret lipit de alt cuvant ramane necunoscut`() {
        assertNull(parser.parseaza(corp() + linie("Tl3,76 RON", sus = 1232, inaltime = 115) + preluare + cursa).price)
    }

    // Un bonus „+l50 RON” scris mai mare decat pretul: azi „50” pierde semnul si ia locul pretului.
    @Test
    fun `bonus cu litera dupa semn ramane bonus si nu devine pret`() {
        val o = parser.parseaza(corp() + pret + linie("+l50 RON", sus = 1100, inaltime = 120) + preluare + cursa)
        egal("9.47", o.price)
    }

    @Test
    fun `fara harta pretul cu litera lipita ramane necunoscut, nu pretul fara litera (D-312)`() {
        val faraCorectii = OfferParser(UBER_RO_V1.copy(reguli = UBER_RO_V1.reguli.copy(corectiiCifre = emptyMap())))
        val o = faraCorectii.parseaza(corp() + linie("l3,76 RON", sus = 1232, inaltime = 115) + preluare + cursa)
        assertNull(o.price)
    }
}
