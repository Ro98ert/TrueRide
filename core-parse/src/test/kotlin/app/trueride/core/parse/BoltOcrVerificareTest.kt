package app.trueride.core.parse

import app.trueride.core.model.ParsedOffer
import java.io.File
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-394: setul de reguli al celei de-a doua citiri Bolt (OCR). Textul OCR nu are bulina dintre minute si km, iar
 * `BOLT_RO_V1` o cere; setul nou o face optionala fara sa slabeasca garda de litera lipita (D-315). Regulile
 * cardului raman neatinse. Linii sintetice cu cifre inventate si fixture-urile Bolt reale (doar numaratori la esec).
 */
class BoltOcrVerificareTest {

    private val card = OfferParser(BOLT_RO_V1)
    private val ocr = OfferParser(BOLT_RO_OCR_VERIFICARE)
    private val nb = " "

    private fun linie(text: String, sus: Int, inaltime: Int) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private val corp = listOf(1000, 1100, 1200).map { linie("▢ ▢ ▢", it, 45) }

    private fun bolt(pre: String, cursa: String) =
        corp + linie("14,20${nb}lei (▢, ▢ ▢)", 1300, 114) + linie(pre, 1700, 82) + linie(cursa, 1800, 82)

    private fun campuri(o: ParsedOffer) = listOf(o.price, o.pickupMinutes, o.tripMinutes, o.pickupDistance, o.tripDistance)

    private fun egale(a: ParsedOffer, b: ParsedOffer) =
        campuri(a).zip(campuri(b)).all { (x, y) -> if (x == null || y == null) x == y else x.compareTo(y) == 0 }

    private fun fixturi(nume: String): List<File> = ComparatorFixturi.fixturi(nume)

    @Test
    fun `litera lipita de km nu se citeste`() {
        val o = ocr.parseaza(bolt(pre = "6 min 2.1 km", cursa = "11 min • S5.4 km"))

        assertEquals(0, BigDecimal("2.1").compareTo(o.pickupDistance))
        assertNull(o.tripDistance)
    }

    // Esec sigur: fara bulina, litera dupa „min” strica ancora, deci nimic din segmente nu se ghiceste (2 din 63 de linii masurate sunt „altceva”).
    @Test
    fun `litera fara bulina segmentul necitit niciun camp ghicit`() {
        val o = ocr.parseaza(bolt(pre = "6 min 2.1 km", cursa = "11 min S5.4 km"))

        assertNull(o.pickupMinutes)
        assertNull(o.tripMinutes)
        assertNull(o.pickupDistance)
        assertNull(o.tripDistance)
    }

    @Test
    fun `fixturile Bolt cu bulina scoasa dau aceleasi valori`() {
        val lista = fixturi("bolt-ro")
        assertTrue(lista.size >= 4, "gasite ${lista.size}")
        val diferite = lista.filter { f ->
            val original = CititorFixturi.linii(f)
            val fara = original.map { it.copy(text = it.text.replace("•", "")) }
            !egale(card.parseaza(original), ocr.parseaza(fara))
        }
        assertEquals(0, diferite.size, "fixture-uri diferite: ${diferite.size} din ${lista.size}")
    }

    @Test
    fun `fixturile Bolt cu bulina pastrata dau aceleasi valori`() {
        val diferite = fixturi("bolt-ro").filter { f ->
            val linii = CititorFixturi.linii(f)
            !egale(card.parseaza(linii), ocr.parseaza(linii))
        }
        assertEquals(0, diferite.size, "fixture-uri diferite: ${diferite.size}")
    }

    @Test
    fun `ecranele fara oferta nu dau pret`() {
        val lista = fixturi("bolt-ro-fara-oferta")
        assertTrue(lista.isNotEmpty())
        val cuPret = lista.filter { ocr.parseaza(CititorFixturi.linii(it)).price != null }
        assertEquals(0, cuPret.size, "cu pret: ${cuPret.size} din ${lista.size}")
    }

    @Test
    fun `regulile cardului raman 1_6_0`() {
        assertEquals("1.6.0", BOLT_RO_V1.version)
        assertTrue(BOLT_RO_V1.reguli.segmentAncoraPattern.contains("[•·]"))
        assertTrue(BOLT_RO_V1.reguli.distantaPattern.startsWith("[•·]"))
        assertTrue(BOLT_RO_V1.reguli.corectiiCifre.isEmpty())
        assertTrue(BOLT_RO_OCR_VERIFICARE.reguli.corectiiCifre.isEmpty())
    }
}
