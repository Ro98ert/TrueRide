package app.trueride.core.parse

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag

/** Numaratorile unui director de fixture-uri; `faraAsteptat` = fixture fara `.asteptat.json`, deci nejudecat. */
data class NumaratoriFixturi(val corect: Int = 0, val gresit: Int = 0, val necunoscut: Int = 0, val faraAsteptat: Int = 0)

object NumarareFixturi {

    private enum class Clasa { CORECT, GRESIT, NECUNOSCUT, FARA_ASTEPTAT }

    /** Cate o intrare per director de primul nivel (si cele fara fixture-uri: zero e o stare, nu o lipsa). */
    fun numara(radacina: File): Map<String, NumaratoriFixturi> =
        radacina.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }.associate { d ->
            val pe = d.walkTopDown().filter(::esteFixture).groupingBy(::clasifica).eachCount()
            d.name to NumaratoriFixturi(
                pe[Clasa.CORECT] ?: 0, pe[Clasa.GRESIT] ?: 0, pe[Clasa.NECUNOSCUT] ?: 0, pe[Clasa.FARA_ASTEPTAT] ?: 0,
            )
        }

    private fun esteFixture(f: File) = f.isFile && f.name.endsWith(".json") && !f.name.endsWith(".asteptat.json")

    /**
     * Fara eticheta nu judecam (n-avem adevar de teren): nici nu parsam, deci 35 000 de fisiere raman ieftine.
     * Cu eticheta dar fara nicio linie citibila nu putem spune corect sau gresit: `necunoscut`. Parserul si cititorul
     * sunt cei din `ReplayFixturi`, adica exact ce ruleaza replay-ul de pe teren (inclusiv campul `recitita`).
     */
    private fun clasifica(f: File): Clasa {
        val eticheta = ComparatorFixturi.etichetaDe(f)
        if (!eticheta.isFile) return Clasa.FARA_ASTEPTAT
        val linii = ReplayFixturi.linii(f)
        if (linii.isEmpty()) return Clasa.NECUNOSCUT
        val oferta = ReplayFixturi.parserPentru(ReplayFixturi.aplicatieDin(f)).parseaza(linii)
        return if (ComparatorFixturi.diferente(oferta, CititorFixturi.eticheta(eticheta)).isEmpty()) Clasa.CORECT else Clasa.GRESIT
    }
}

/**
 * D-350.5: fixture-urile istorice de pe disc (`.probe-shots/fixturi-raport/` (recursiv), din checkout-ul principal), judecate
 * noaptea cu parserul real. Ruleaza doar prin `:core-parse:noapte` (faza `noapte_jvm`); fara
 * `-Ptrueride.noapte.fixturi` testul pe date reale e sarit, deci suita de zi nu il vede niciodata.
 * Iesirea sunt numaratori pe director de primul nivel (TSV in `-Ptrueride.noapte.fixturi.iesire`); un fixture fara
 * rezultat asteptat se numara `fara_asteptat` si nu se evalueaza (nu inventam un adevar de teren). Mesajele de picare
 * poarta doar nume de directoare si numere (invariantul 2): continutul fixture-urilor nu iese din test.
 */
@Tag("noapte")
class FixturiIstoriceNoapteTest {

    private fun tmp(): File = createTempDirectory("fixturi-noapte").toFile().also { it.deleteOnExit() }

    /** Copiaza un fixture real din `fixtures/` (cu eticheta lui) sub [dest] ca sa avem un caz cunoscut corect. */
    private fun copiaza(dest: File, cuEticheta: Boolean = true): File {
        val f = ComparatorFixturi.fixturi("uber").first()
        dest.mkdirs()
        val copie = File(dest, f.name)
        f.copyTo(copie)
        if (cuEticheta) ComparatorFixturi.etichetaDe(f).copyTo(ComparatorFixturi.etichetaDe(copie))
        return copie
    }

    @Test
    fun `fixture cu eticheta potrivita e corect, fara eticheta e fara_asteptat`() {
        val r = tmp()
        copiaza(File(r, "cu-eticheta"))
        copiaza(File(r, "fara-eticheta"), cuEticheta = false)

        val n = NumarareFixturi.numara(r)

        assertEquals(NumaratoriFixturi(corect = 1), n["cu-eticheta"])
        assertEquals(NumaratoriFixturi(faraAsteptat = 1), n["fara-eticheta"])
    }

    @Test
    fun `eticheta care nu se potriveste da gresit`() {
        val r = tmp()
        val f = copiaza(File(r, "d"))
        val eticheta = ComparatorFixturi.etichetaDe(f)
        eticheta.writeText(eticheta.readText().replace("\"price\": \"", "\"price\": \"9"))

        assertEquals(NumaratoriFixturi(gresit = 1), NumarareFixturi.numara(r)["d"])
    }

    @Test
    fun `fixture cu eticheta dar fara nicio linie citibila e necunoscut, nu corect`() {
        val r = tmp()
        val f = copiaza(File(r, "d"))
        f.writeText("{}")

        assertEquals(NumaratoriFixturi(necunoscut = 1), NumarareFixturi.numara(r)["d"])
    }

    @Test
    fun `directorul fara fixture-uri apare cu zero si fisierele asteptat nu se numara ca fixture-uri`() {
        val r = tmp()
        File(r, "gol").mkdirs()
        File(r, "radacina.txt").writeText("nu e director")
        copiaza(File(r, "unul"))

        val n = NumarareFixturi.numara(r)

        assertEquals(setOf("gol", "unul"), n.keys)
        assertEquals(NumaratoriFixturi(), n["gol"])
        assertEquals(1, n["unul"]!!.let { it.corect + it.gresit + it.necunoscut + it.faraAsteptat })
    }

    @Test
    fun `fixture-urile istorice de pe disc se numara pe director si se scriu in TSV`() {
        val radacina = System.getProperty("trueride.noapte.fixturi")?.let(::File)
        assumeTrue(radacina != null && radacina.isDirectory, "fara -Ptrueride.noapte.fixturi")
        val iesire = File(requireNotNull(System.getProperty("trueride.noapte.fixturi.iesire")) { "lipseste iesirea TSV" })

        val n = NumarareFixturi.numara(radacina!!)
        iesire.parentFile.mkdirs()
        iesire.writeText(
            n.toSortedMap().entries.joinToString("") { (d, c) ->
                "$d\t${c.corect}\t${c.gresit}\t${c.necunoscut}\t${c.faraAsteptat}\n"
            },
            Charsets.UTF_8,
        )

        assertTrue(n.isNotEmpty(), "niciun director sub radacina de fixture-uri")
        println("fixturi istorice: ${n.size} directoare, ${n.values.sumOf { it.corect + it.gresit + it.necunoscut + it.faraAsteptat }} fixture-uri")
    }
}
