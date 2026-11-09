package app.trueride.core.parse

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F3 (D-135.9): ofertele Bolt RO citite din noduri, raportul Sofer 1 din 2026-10-05. Etichetele sunt scrise
 * din fisierul mascat si verificate independent (D-032). Set mic: M4 cere 100%.
 */
class FixturiBoltRoTest {

    private val fixturi: List<File> = ComparatorFixturi.fixturi("bolt-ro")
    private val fara: List<File> = ComparatorFixturi.fixturi("bolt-ro-fara-oferta")

    @Test
    fun `setul are cel putin 4 oferte si fiecare are eticheta`() {
        assertTrue(fixturi.size >= 4, "gasite ${fixturi.size} in ${CititorFixturi.director("bolt-ro")}")
        fixturi.forEach { assertTrue(ComparatorFixturi.etichetaDe(it).exists(), "lipseste eticheta ${it.name}") }
    }

    @Test
    fun `cititorul nu pierde nicio linie`() {
        (fixturi + fara).forEach { f ->
            val asteptate = f.readText(Charsets.UTF_8).split("\"text\":").size - 1
            assertEquals(asteptate, CititorFixturi.linii(f).size, f.name)
        }
    }

    @Test
    fun `M4 Bolt RO - toate ofertele au toate campurile corecte`() {
        val gresite = ComparatorFixturi.gresite(fixturi, OfferParser(BOLT_RO_V1))
        println("M4 Bolt RO: ${fixturi.size - gresite.size} / ${fixturi.size}")

        assertEquals(emptyList(), gresite)
    }

    @Test
    fun `M4 Bolt RO pica pe reguli pentru alta moneda`() {
        val gresit = BOLT_RO_V1.copy(reguli = BOLT_RO_V1.reguli.copy(sumaPattern = """(\+)?(\d+(?:[.,]\d{1,2})?) EUR"""))

        assertEquals(fixturi.size, ComparatorFixturi.gresite(fixturi, OfferParser(gresit)).size)
    }

    @Test
    fun `niciun ecran Bolt fara oferta nu da pret sau segment`() {
        assertTrue(fara.size >= 2)
        fara.forEach { f ->
            val o = OfferParser(BOLT_RO_V1).parseaza(CititorFixturi.linii(f))
            assertEquals(
                listOf(null, null, null, null, null),
                listOf(o.price, o.pickupMinutes, o.tripMinutes, o.pickupDistance, o.tripDistance),
                f.name,
            )
        }
    }

    @Test
    fun `regulile Uber RO nu citesc ofertele Bolt`() {
        assertEquals(fixturi.size, ComparatorFixturi.gresite(fixturi, OfferParser(UBER_RO_V1)).size)
    }
}
