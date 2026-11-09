package app.trueride.core.parse

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

/**
 * D-315 (val9-doua-puncte-en-bolt): „:” ca separator zecimal e regula UNUI singur set, Uber RO, si doar la km
 * (vezi `KmDouaPuncteUberRoTest`). Aici se fixeaza granita: Uber EN si Bolt RO nu citesc km cu „:”, iar pretul si
 * minutele cu „:” nu devin zecimale pe niciun set. Pentru pret/minute se cere „nu e valoarea zecimala”, nu neaparat null
 * (alta regula poate lua legitim o parte din cifre). Toate cifrele sunt inventate.
 */
class DouaPuncteDoarUberRoTest {

    private val en = OfferParser(UBER_EN_V1)
    private val ro = OfferParser(UBER_RO_V1)
    private val bolt = OfferParser(BOLT_RO_V1)
    private val nb = " "

    private fun linie(text: String, sus: Int, inaltime: Int = 46) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private val corpEn = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }
    private val corpBolt = listOf(500, 700, 900).map { linie("▢ ▢", sus = it) }

    private fun oferaEn(pret: String = "RON12.47", s1: String = "6 mins (2.1 km) ▢", s2: String = "14 mins (7.2 km) ▢") =
        corpEn + listOf(linie(pret, sus = 1232, inaltime = 90), linie(s1, sus = 1700), linie(s2, sus = 1800))

    private fun ofertaRo(pret: String = "12,47 RON", s1: String = "6 min. (2.1 km) ▢", s2: String = "14 min. (7.2 km) ▢") =
        listOf(linie(pret, sus = 1232, inaltime = 90), linie(s1, sus = 1700), linie(s2, sus = 1800))

    private fun ofertaBolt(pret: String = "21,40${nb}lei (▢, ▢ ▢)", s1: String = "5 min$nb• 2.3 km", s2: String = "11 min$nb• 6.4 km") =
        corpBolt + listOf(linie(pret, sus = 1375, inaltime = 114), linie(s1, sus = 1650, inaltime = 82), linie(s2, sus = 1875, inaltime = 82))

    private fun nu(interzis: String, real: BigDecimal?) {
        if (real != null) assertNotEquals(0, BigDecimal(interzis).compareTo(real), "„:” citit ca zecimala: $real")
    }

    // ---------- baza: ofertele fara „:” se citesc (altfel testele de mai jos ar trece pe gol)

    @Test
    fun `ofertele de baza fara doua puncte se citesc pe toate cele trei seturi`() {
        assertEquals(0, BigDecimal("2.1").compareTo(en.parseaza(oferaEn()).pickupDistance))
        assertEquals(0, BigDecimal("2.1").compareTo(ro.parseaza(ofertaRo()).pickupDistance))
        assertEquals(0, BigDecimal("2.3").compareTo(bolt.parseaza(ofertaBolt()).pickupDistance))
    }

    // ---------- Uber EN

    @Test
    fun `Uber EN km de ridicare cu doua puncte raman necunoscuti`() {
        assertNull(en.parseaza(oferaEn(s1 = "6 mins (3:4 km) ▢")).pickupDistance)
    }

    @Test
    fun `Uber EN km de cursa cu doua puncte raman necunoscuti`() {
        assertNull(en.parseaza(oferaEn(s2 = "14 mins (7:2 km) ▢")).tripDistance)
    }

    @Test
    fun `Uber EN pretul cu doua puncte nu e zecimala`() {
        nu("12.47", en.parseaza(oferaEn(pret = "RON12:47")).price)
    }

    @Test
    fun `Uber EN minutele cu doua puncte nu sunt zecimala`() {
        nu("6.5", en.parseaza(oferaEn(s1 = "6:5 mins (2.1 km) ▢")).pickupMinutes)
        nu("14.5", en.parseaza(oferaEn(s2 = "14:5 mins (7.2 km) ▢")).tripMinutes)
    }

    // ---------- Bolt RO

    @Test
    fun `Bolt RO km cu doua puncte raman necunoscuti`() {
        assertNull(bolt.parseaza(ofertaBolt(s1 = "5 min$nb• 3:4 km")).pickupDistance)
        assertNull(bolt.parseaza(ofertaBolt(s2 = "11 min$nb• 6:4 km")).tripDistance)
    }

    @Test
    fun `Bolt RO pretul cu doua puncte nu e zecimala`() {
        nu("21.40", bolt.parseaza(ofertaBolt(pret = "21:40${nb}lei (▢, ▢ ▢)")).price)
    }

    @Test
    fun `Bolt RO minutele cu doua puncte nu sunt zecimala`() {
        nu("5.5", bolt.parseaza(ofertaBolt(s1 = "5:5 min$nb• 2.3 km")).pickupMinutes)
        nu("11.5", bolt.parseaza(ofertaBolt(s2 = "11:5 min$nb• 6.4 km")).tripMinutes)
    }

    // ---------- Uber RO: doar km-ul

    @Test
    fun `Uber RO pretul cu doua puncte nu e zecimala`() {
        nu("12.47", ro.parseaza(ofertaRo(pret = "12:47 RON")).price)
    }

    @Test
    fun `Uber RO minutele cu doua puncte nu sunt zecimala`() {
        nu("6.5", ro.parseaza(ofertaRo(s1 = "6:5 min. (2.1 km) ▢")).pickupMinutes)
        nu("14.5", ro.parseaza(ofertaRo(s2 = "14:5 min. (7.2 km) ▢")).tripMinutes)
    }
}
