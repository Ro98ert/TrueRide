package app.trueride.core.parse

import app.trueride.core.model.ParsedOffer
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag

/** Ce a facut o rulare: numaratori pe director de primul nivel (invariantul 2: niciun continut de fixture). */
data class RezultatLinieDeBaza(val generate: Int = 0, val existau: Int = 0, val faraLinii: Int = 0)

/**
 * D-353.4: scrie `*.asteptat.json` din ce citeste parserul ACUM, doar pentru fixture-urile care nu au deja unul.
 * E o linie de baza, nu adevar de teren: prinde schimbarile de acum incolo, nu spune ca citirea de azi e corecta.
 * Nu suprascrie niciodata o eticheta existenta (nici cele scrise de om) si nu atinge alte fisiere. Un fixture fara nicio linie
 * citibila nu primeste eticheta (n-a citit nimeni nimic). Fiecare director care primeste etichete noi noteaza commit-ul
 * parserului intr-un `linie-de-baza.txt` (adaugat la coada, nu rescris).
 */
object LinieDeBazaFixturi {

    fun genereaza(radacina: File, commit: String): Map<String, RezultatLinieDeBaza> =
        radacina.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }.associate { d ->
            var r = RezultatLinieDeBaza()
            for (f in d.walkTopDown().filter(::esteFixture)) {
                val eticheta = ComparatorFixturi.etichetaDe(f)
                val linii = ReplayFixturi.linii(f)
                r = when {
                    eticheta.exists() -> r.copy(existau = r.existau + 1)
                    linii.isEmpty() -> r.copy(faraLinii = r.faraLinii + 1)
                    scrieNou(eticheta, ReplayFixturi.parserPentru(ReplayFixturi.aplicatieDin(f)).parseaza(linii)) ->
                        r.copy(generate = r.generate + 1)
                    else -> r.copy(existau = r.existau + 1) // aparuta intre timp: tot nu o atingem
                }
            }
            if (r.generate > 0) File(d, "linie-de-baza.txt").appendText("commit=$commit generate=${r.generate}\n", Charsets.UTF_8)
            d.name to r
        }

    private fun esteFixture(f: File) = f.isFile && f.name.endsWith(".json") && !f.name.endsWith(".asteptat.json")

    /** `createNewFile` e atomic: daca eticheta exista deja, nu scriem nimic. */
    private fun scrieNou(eticheta: File, o: ParsedOffer): Boolean {
        if (!eticheta.createNewFile()) return false
        eticheta.writeText(continut(o), Charsets.UTF_8)
        return true
    }

    // Formatul din `fixtures/` (CititorFixturi.eticheta); campul lipsa e null, nu "0" (invariantul 6).
    private fun continut(o: ParsedOffer): String {
        fun s(v: String?) = if (v == null) "null" else "\"$v\""
        return """{
  "price": ${s(o.price?.toPlainString())},
  "currency": ${s(o.currency)},
  "priceBasis": ${s(o.priceBasis.name)},
  "pickupMinutes": ${s(o.pickupMinutes?.toPlainString())},
  "tripMinutes": ${s(o.tripMinutes?.toPlainString())},
  "pickupDistance": ${s(o.pickupDistance?.toPlainString())},
  "tripDistance": ${s(o.tripDistance?.toPlainString())},
  "distanceUnitDetected": ${s(o.distanceUnitDetected?.name)},
  "surgeMultiplier": ${s(o.surgeMultiplier?.toPlainString())}
}
"""
    }
}

/**
 * Pornire explicita, niciodata din noaptea normala: cere variabila de mediu `TRUERIDE_LINIE_DE_BAZA=<commit>` (commit-ul
 * parserului) SI `-Ptrueride.noapte.fixturi=<director>`; fara una dintre ele testul e sarit. Comanda:
 *   TRUERIDE_LINIE_DE_BAZA=<commit> ./gradlew :core-parse:noapte -Ptrueride.noapte.fixturi=<director> --tests '*LinieDeBazaFixturiNoapteTest*'
 * (variabila de mediu, nu proprietate Gradle: `build.gradle.kts` nu trebuie sa o transmita, iar faza de noapte n-o seteaza).
 */
@Tag("noapte")
class LinieDeBazaFixturiNoapteTest {

    private fun tmp(): File = createTempDirectory("linie-de-baza").toFile().also { it.deleteOnExit() }

    private fun copiaza(dest: File, nume: String? = null, eticheta: Boolean = false): File {
        val f = ComparatorFixturi.fixturi("uber").first()
        dest.mkdirs()
        val copie = File(dest, nume ?: f.name)
        f.copyTo(copie)
        if (eticheta) ComparatorFixturi.etichetaDe(f).copyTo(ComparatorFixturi.etichetaDe(copie))
        return copie
    }

    @Test
    fun `genereaza eticheta pentru fixture-ul fara eticheta si parserul o gaseste corecta`() {
        val r = tmp()
        copiaza(File(r, "d"))

        val rez = LinieDeBazaFixturi.genereaza(r, "abc1234")

        assertEquals(RezultatLinieDeBaza(generate = 1), rez["d"])
        assertEquals(NumaratoriFixturi(corect = 1), NumarareFixturi.numara(r)["d"])
        assertTrue(File(r, "d/linie-de-baza.txt").readText().contains("commit=abc1234 generate=1"))
    }

    @Test
    fun `nu suprascrie o eticheta existenta nici cand difera de ce citeste parserul`() {
        val r = tmp()
        val f = copiaza(File(r, "d"), eticheta = true)
        val eticheta = ComparatorFixturi.etichetaDe(f)
        eticheta.writeText(eticheta.readText().replace("\"price\": \"", "\"price\": \"9"))
        val inainte = eticheta.readText()

        val rez = LinieDeBazaFixturi.genereaza(r, "abc1234")

        assertEquals(RezultatLinieDeBaza(existau = 1), rez["d"])
        assertEquals(inainte, eticheta.readText())
        assertTrue(!File(r, "d/linie-de-baza.txt").exists())
        assertEquals(NumaratoriFixturi(gresit = 1), NumarareFixturi.numara(r)["d"])
    }

    @Test
    fun `a doua rulare genereaza zero si nu schimba nimic`() {
        val r = tmp()
        copiaza(File(r, "d"))
        LinieDeBazaFixturi.genereaza(r, "abc1234")
        val eticheta = r.walkTopDown().first { it.name.endsWith(".asteptat.json") }
        val prima = eticheta.readText()
        val nota = File(r, "d/linie-de-baza.txt").readText()

        val rez = LinieDeBazaFixturi.genereaza(r, "def5678")

        assertEquals(RezultatLinieDeBaza(existau = 1), rez["d"])
        assertEquals(prima, eticheta.readText())
        assertEquals(nota, File(r, "d/linie-de-baza.txt").readText())
        assertEquals(NumaratoriFixturi(corect = 1), NumarareFixturi.numara(r)["d"])
    }

    @Test
    fun `fixture fara nicio linie citibila nu primeste eticheta`() {
        val r = tmp()
        copiaza(File(r, "d")).writeText("{}")

        val rez = LinieDeBazaFixturi.genereaza(r, "abc1234")

        assertEquals(RezultatLinieDeBaza(faraLinii = 1), rez["d"])
        assertEquals(NumaratoriFixturi(faraAsteptat = 1), NumarareFixturi.numara(r)["d"])
    }

    @Test
    fun `nu atinge fisierele care nu sunt fixture-uri`() {
        val r = tmp()
        copiaza(File(r, "d"))
        val md = File(r, "d/etichete-propuse.md").also { it.writeText("propuneri") }

        LinieDeBazaFixturi.genereaza(r, "abc1234")

        assertEquals("propuneri", md.readText())
    }

    @Test
    fun `genereaza linia de baza pentru fixture-urile de pe disc cand e cerut explicit`() {
        val commit = System.getenv("TRUERIDE_LINIE_DE_BAZA")
        val radacina = System.getProperty("trueride.noapte.fixturi")?.let(::File)
        assumeTrue(!commit.isNullOrBlank() && radacina != null && radacina.isDirectory, "fara TRUERIDE_LINIE_DE_BAZA si -Ptrueride.noapte.fixturi")

        val rez = LinieDeBazaFixturi.genereaza(radacina!!, commit)

        // Doar numaratori (invariantul 2), pe director.
        rez.forEach { (d, r) -> println("linie de baza: $d generate=${r.generate} existau=${r.existau} fara_linii=${r.faraLinii}") }
        println("linie de baza total: generate=${rez.values.sumOf { it.generate }} existau=${rez.values.sumOf { it.existau }} fara_linii=${rez.values.sumOf { it.faraLinii }}")
    }
}
