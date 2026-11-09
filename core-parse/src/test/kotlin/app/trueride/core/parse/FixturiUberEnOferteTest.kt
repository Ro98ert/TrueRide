package app.trueride.core.parse

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F5 (D-131.3): ofertele Uber EN reale (en-GB Sofer 1, en-RO telefonul de test 2, 2026-10-05), etichete scrise din
 * fisierul mascat si verificate independent (D-032). Set mic: M4 cere 100%.
 */
class FixturiUberEnOferteTest {

    private val fixturi: List<File> = ComparatorFixturi.fixturi("uber-en-oferte")
    private val uberRo: List<File> = ComparatorFixturi.fixturi("uber")
    private val selectie = ParserPeLimbi(listOf(OfferParser(UBER_RO_V1), OfferParser(UBER_EN_V1)))

    @Test
    fun `setul are cel putin 5 oferte si fiecare are eticheta`() {
        assertTrue(fixturi.size >= 5, "gasite ${fixturi.size}")
        fixturi.forEach { assertTrue(ComparatorFixturi.etichetaDe(it).exists(), "lipseste eticheta ${it.name}") }
    }

    @Test
    fun `M4 Uber EN - toate ofertele corecte cu setul EN`() {
        val gresite = ComparatorFixturi.gresite(fixturi, OfferParser(UBER_EN_V1))
        println("M4 Uber EN: ${fixturi.size - gresite.size} / ${fixturi.size}")

        assertEquals(emptyList(), gresite)
    }

    @Test
    fun `selectia pe limbi da acelasi rezultat pe EN si nu strica RO`() {
        assertEquals(emptyList(), ComparatorFixturi.gresite(fixturi, selectie))
        assertEquals(
            ComparatorFixturi.gresite(uberRo, OfferParser(UBER_RO_V1)),
            ComparatorFixturi.gresite(uberRo, selectie),
        )
    }

    @Test
    fun `M4 Uber EN pica pe reguli pentru alta moneda`() {
        val gresit = UBER_EN_V1.copy(reguli = UBER_EN_V1.reguli.copy(sumaPattern = """([+\-−]\s*)?EUR\s?(\d+(?:[.,]\d{1,2})?)"""))
        val cuPret = fixturi.filter { CititorFixturi.eticheta(ComparatorFixturi.etichetaDe(it))["price"] != null }

        assertEquals(cuPret.size, ComparatorFixturi.gresite(cuPret, OfferParser(gresit)).size)
    }
}
