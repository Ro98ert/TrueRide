package app.trueride.core.parse

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FixturiUberTest {

    private val fixturi: List<File> = ComparatorFixturi.fixturi("uber")

    @Test
    fun `setul are cel putin 8 fixture-uri si fiecare are eticheta`() {
        assertTrue(fixturi.size >= 8, "gasite ${fixturi.size} in ${CititorFixturi.director()}")
        fixturi.forEach { f ->
            val e = ComparatorFixturi.etichetaDe(f)
            assertTrue(e.exists(), "lipseste ${e.name}")
        }
    }

    // Un cititor care pierde linii ar face M4 sa treaca sau sa pice din motive false.
    @Test
    fun `cititorul nu pierde nicio linie`() {
        fixturi.forEach { f ->
            val asteptate = f.readText(Charsets.UTF_8).split("\"text\":").size - 1
            assertEquals(asteptate, CititorFixturi.linii(f).size, f.name)
        }
    }

    @Test
    fun `M4 - cel putin 90 la suta din oferte au toate campurile corecte`() {
        val gresite = ComparatorFixturi.gresite(fixturi, OfferParser(UBER_RO_V1))
        val corecte = fixturi.size - gresite.size
        println("M4: $corecte / ${fixturi.size} oferte corecte")
        gresite.forEach(::println)

        assertTrue(fixturi.isNotEmpty(), "niciun fixture")
        assertTrue(corecte * 10 >= fixturi.size * 9, "M4 sub 90%: $gresite")
    }

    // Controlul negativ: metrica trebuie sa poata pica. Reguli pentru alta moneda
    // nu gasesc niciun pret -> nicio oferta corecta.
    @Test
    fun `M4 pica pe reguli gresite`() {
        val gresit = UBER_RO_V1.copy(
            reguli = UBER_RO_V1.reguli.copy(sumaPattern = """(\+)?(\d+(?:[.,]\d{1,2})?) EUR"""),
        )

        assertEquals(fixturi.size, ComparatorFixturi.gresite(fixturi, OfferParser(gresit)).size)
    }

    // M6 — o eticheta cu o cheie in plus sau in minus ar trece testele de mai sus
    // fara sa fie observata: garda explicita pe forma etichetei.
    @Test
    fun `fiecare eticheta are exact cele 9 chei asteptate`() {
        val cheileAsteptate = setOf(
            "price", "currency", "priceBasis", "pickupMinutes", "tripMinutes",
            "pickupDistance", "tripDistance", "distanceUnitDetected", "surgeMultiplier",
        )

        fixturi.forEach { f ->
            assertEquals(cheileAsteptate, CititorFixturi.eticheta(ComparatorFixturi.etichetaDe(f)).keys, f.name)
        }
    }

    // M7 — al doilea control negativ: distantaPattern stricat (mi in loc de km).
    // Doar fixture-urile cu o distanta pe eticheta pot pica pe regula distantei.
    @Test
    fun `M4 pica pe reguli de distanta gresite`() {
        val gresit = UBER_RO_V1.copy(
            reguli = UBER_RO_V1.reguli.copy(distantaPattern = """\((\d+(?:[.,]\d+)?) mi\)"""),
        )
        val cuDistanta = fixturi.filter { f ->
            val e = CititorFixturi.eticheta(ComparatorFixturi.etichetaDe(f))
            e["pickupDistance"] != null || e["tripDistance"] != null
        }

        assertTrue(cuDistanta.isNotEmpty(), "niciun fixture cu distanta")
        assertEquals(cuDistanta.size, ComparatorFixturi.gresite(cuDistanta, OfferParser(gresit)).size)
    }
}
