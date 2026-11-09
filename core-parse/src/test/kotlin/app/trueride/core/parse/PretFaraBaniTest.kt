package app.trueride.core.parse

import app.trueride.core.model.OfferField
import app.trueride.core.model.PriceBasis
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * F-011: pe telefonul soferului, pretul Uber RO a aparut de ~100 de ori mai mare,
 * cu cardul marcat "estimat" (complet), nu "partial". Fixture-ul importat din captura
 * (D-076) da pretul corect, deci defectul e in textul OCR de pe telefon: separatorul
 * dintre lei si bani s-a pierdut sau s-a citit gresit, iar `sumaPattern` accepta
 * suma fara zecimale ca pret intreg.
 *
 * Toate textele sunt sintetice (invariantii 2 si 4): doar cifre si unitati.
 * Comportamentul asteptat e acelasi pentru orice varianta de reparatie: pretul devine
 * necunoscut (null, nu 0 — invariantul 6), iar segmentele citite corect raman citite.
 */
class PretFaraBaniTest {

    private val parser = OfferParser(UBER_RO_V1)

    private fun linie(text: String, sus: Int, inaltime: Int = 45) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    // Aceeasi forma ca in OfferParserTest: text obisnuit ~45 px, pretul ~115 px.
    private fun corp() = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }

    // O oferta completa cu pretul scris asa cum l-a dat OCR-ul. 6 km si 13 minute in total.
    private fun oferta(textPret: String) = parser.parseaza(
        corp() + listOf(
            linie(textPret, sus = 1232, inaltime = 115),
            linie("▢ 4 min. (1.5 km) ▢", sus = 1659),
            linie("▢ 9 min. (4.5 km)", sus = 1788),
        ),
    )

    private fun egal(asteptat: String, real: BigDecimal?) {
        requireNotNull(real) { "asteptat $asteptat, primit null" }
        assertEquals(0, BigDecimal(asteptat).compareTo(real), "asteptat $asteptat, primit $real")
    }

    /** Pretul e necunoscut, iar restul ofertei ramane citit (nu dispare odata cu pretul). */
    private fun pretNecunoscutSegmenteCitite(textPret: String) {
        val o = oferta(textPret)

        assertNull(o.price, "\"$textPret\" a fost citit ca pret ${o.price?.toPlainString()} lei")
        assertNull(o.currency)
        assertEquals(PriceBasis.UNKNOWN, o.priceBasis)
        assertFalse(OfferField.PRICE in o.fieldConfidence)
        egal("4", o.pickupMinutes)
        egal("1.5", o.pickupDistance)
        egal("9", o.tripMinutes)
        egal("4.5", o.tripDistance)
    }

    // Control pozitiv: aceeasi oferta, cu virgula citita, da pretul exact. Fara el,
    // un test "pret null" ar trece si daca linia pretului nu ar fi citita deloc.
    @Test
    fun `control - pretul cu virgula si doi bani e citit exact`() {
        egal("12.34", oferta("12,34 RON").price)
    }

    // Forma 1 (F-011, cazul de pe teren): "12,34 RON" -> OCR "1234 RON" -> 1234 lei.
    @Test
    fun `pretul cu virgula pierduta intre lei si bani e necunoscut, nu de 100 de ori mai mare`() {
        pretNecunoscutSegmenteCitite("1234 RON")
    }

    // Forma 1b: leii dintr-o singura cifra. "9,47 RON" -> "947 RON" -> 947 lei.
    @Test
    fun `pretul sub 10 lei cu virgula pierduta e necunoscut, nu 947 de lei`() {
        pretNecunoscutSegmenteCitite("947 RON")
    }

    // Forma 2: virgula citita ca alt semn (apostrof, punct si virgula, doua puncte).
    // Granita din stanga a `sumaPattern` opreste doar cifra, virgula si punctul, deci
    // match-ul incepe la bani: "12'34 RON" -> 34 lei. Nu x100, dar tot o cifra falsa
    // afisata ca "estimat" (invariantul 5/6).
    @Test
    fun `virgula citita ca apostrof nu face din bani pretul`() {
        pretNecunoscutSegmenteCitite("12'34 RON")
    }

    @Test
    fun `virgula citita ca punct si virgula nu face din bani pretul`() {
        pretNecunoscutSegmenteCitite("12;34 RON")
    }

    @Test
    fun `virgula citita ca doua puncte nu face din bani pretul`() {
        pretNecunoscutSegmenteCitite("12:34 RON")
    }

    /**
     * Forma 3 (D-080): virgula citita ca cifra ("," -> "1"): "12,34 RON" -> "12134 RON".
     * Dupa ce virgula devine cifra nu mai ramane niciun separator, deci nici doi bani —
     * regula celor doua zecimale o face necunoscuta, ca pe celelalte forme.
     */
    @Test
    fun `virgula citita ca cifra nu da un pret de o mie de ori mai mare`() {
        pretNecunoscutSegmenteCitite("12134 RON")
    }

    // Limita de jos a lui "exact doi": un ban pierdut nu mai e forma pretului Uber RO.
    @Test
    fun `pretul cu un singur ban e necunoscut`() {
        pretNecunoscutSegmenteCitite("12,3 RON")
    }

    /**
     * Forma se verifica pe suma CASTIGATOARE, dupa alegere (D-080), nu ca filtru inainte.
     * Linia pretului stricata de OCR (cea mai inalta) face pretul necunoscut; o alta suma
     * fara semn, mai mica dar peste pragul de inaltime, nu are voie sa-i ia locul — ar fi
     * o cifra care nu e pretul ofertei, afisata ca "estimat".
     */
    @Test
    fun `pretul stricat nu lasa alta suma mai mica sa-i ia locul`() {
        val o = parser.parseaza(
            corp() + listOf(
                linie("1234 RON", sus = 1232, inaltime = 115),
                linie("12,34 RON", sus = 1400 - 100, inaltime = 90),
                linie("▢ 4 min. (1.5 km) ▢", sus = 1659),
                linie("▢ 9 min. (4.5 km)", sus = 1788),
            ),
        )

        assertNull(o.price, "a fost promovata alta suma: ${o.price?.toPlainString()}")
        assertFalse(OfferField.PRICE in o.fieldConfidence)
    }

    /**
     * Limita cunoscuta (D-080): regula verifica FORMA pretului, nu cifrele lui. O cifra
     * confundata intr-un pret cu virgula si doi bani ("12,34" -> "72,34", 1 <-> 7) are forma
     * valida si trece. D-080 a respins limita lei/km care ar fi putut prinde unele asemenea
     * cazuri (ar fi fost ghicita pe 8-9 capturi). Testul fixeaza comportamentul de azi, ca o
     * schimbare a lui sa fie vizibila.
     */
    @Test
    fun `limita cunoscuta D-080 - o cifra confundata intr-un pret cu doi bani trece`() {
        egal("72.34", oferta("72,34 RON").price)
    }
}
