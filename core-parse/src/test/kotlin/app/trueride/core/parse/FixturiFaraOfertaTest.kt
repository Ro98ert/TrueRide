package app.trueride.core.parse

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-130, D-131: ecranele fara oferta din rapoartele telefonului de test 2 (en-RO). Pe 32 dintre ele singura suma, sus,
 * e probabil castigul zilei, care inainte de D-130 devenea pret pe un card „partial” (F-020).
 */
class FixturiFaraOfertaTest {

    private val fixturi: List<File> = ComparatorFixturi.fixturi("uber-en-fara-oferta")

    private fun linii(f: File) = CititorFixturi.linii(f)

    @Test
    fun `setul are cel putin 30 de ecrane fara oferta`() {
        assertTrue(fixturi.size >= 30, "gasite ${fixturi.size} in ${CititorFixturi.director("uber-en-fara-oferta")}")
    }

    @Test
    fun `niciun ecran fara oferta nu da pret`() {
        val cuPret = fixturi.filter { OfferParser(UBER_RO_V1).parseaza(linii(it)).price != null }.map { it.name }
        assertEquals(emptyList(), cuPret)
    }

    @Test
    fun `niciun ecran fara oferta nu da vreun camp de oferta`() {
        fixturi.forEach { f ->
            val o = OfferParser(UBER_RO_V1).parseaza(linii(f))
            assertNull(o.pickupMinutes, f.name)
            assertNull(o.tripMinutes, f.name)
            assertNull(o.pickupDistance, f.name)
            assertNull(o.tripDistance, f.name)
        }
    }

    // Controlul: fara regula D-130 cele mai multe dau pret. Dovada ca regula le opreste, nu forma sumei.
    @Test
    fun `fara regula D-130 cele mai multe ar da pret`() {
        val faraRegula = OfferParser(UBER_RO_V1.copy(reguli = UBER_RO_V1.reguli.copy(pretCereSegment = false)))
        val cuPret = fixturi.count { faraRegula.parseaza(linii(it)).price != null }
        assertTrue(cuPret >= 25, "doar $cuPret din ${fixturi.size} ar da pret fara regula")
    }

    // O oferta strecurata aici ar trece testele de mai sus fara sa fie vazuta: eticheta e toata null.
    @Test
    fun `fiecare eticheta are cele 9 chei, toate null`() {
        val chei = setOf(
            "price", "currency", "priceBasis", "pickupMinutes", "tripMinutes",
            "pickupDistance", "tripDistance", "distanceUnitDetected", "surgeMultiplier",
        )
        fixturi.forEach { f ->
            val e = CititorFixturi.eticheta(ComparatorFixturi.etichetaDe(f))
            assertEquals(chei, e.keys, f.name)
            assertTrue(e.values.all { it == null }, f.name)
        }
    }
}
