package app.trueride.core.parse

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F-031, D-135.1: ofertele Uber RO din raportul de proba (nivel 3, D-111). Setul e mic, deci
 * M4 cere 100%, nu 90%: o singura oferta gresita inseamna ca regula nu acopera forma.
 */
class FixturiUberRoRaportTest {

    private val fixturi: List<File> = ComparatorFixturi.fixturi("uber-ro")

    @Test
    fun `setul uber-ro are cel putin un fixture si fiecare are eticheta`() {
        assertTrue(fixturi.isNotEmpty(), "niciun fixture in ${CititorFixturi.director("uber-ro")}")
        fixturi.forEach { f -> assertTrue(ComparatorFixturi.etichetaDe(f).exists(), "lipseste eticheta ${f.name}") }
    }

    @Test
    fun `M4 pe uber-ro - toate ofertele au toate campurile corecte`() {
        val gresite = ComparatorFixturi.gresite(fixturi, OfferParser(UBER_RO_V1))
        println("M4 uber-ro: ${fixturi.size - gresite.size} / ${fixturi.size}")

        assertEquals(emptyList(), gresite)
    }

    // Controlul negativ: metrica trebuie sa poata pica.
    @Test
    fun `M4 pe uber-ro pica pe reguli pentru alta moneda`() {
        val gresit = UBER_RO_V1.copy(
            reguli = UBER_RO_V1.reguli.copy(sumaPattern = """(\+)?(\d+(?:[.,]\d{1,2})?) EUR"""),
        )

        assertEquals(fixturi.size, ComparatorFixturi.gresite(fixturi, OfferParser(gresit)).size)
    }
}
