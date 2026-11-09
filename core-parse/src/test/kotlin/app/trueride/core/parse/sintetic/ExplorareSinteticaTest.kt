package app.trueride.core.parse.sintetic

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Tag
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Explorarea de noapte (D-347.2): aceleasi perturbari ca [CorpusSinteticTest], dar cu alt seed si mai multe variante
 * pe pereche, ca sa gaseasca ce corpusul fix de azi nu a vazut. Cele doua teste de determinism ruleaza in suita de zi;
 * explorarea propriu-zisa e `@Tag("explorare")` si ruleaza doar prin `:core-parse:explorare`.
 */
class ExplorareSinteticaTest {

    private val perechi = PerturbariSintetice.TOATE.flatMap { p -> p.familii.map { it to p } }

    @Test
    fun `genereaza cu acelasi seed da aceeasi varianta si cu alt seed alta`() {
        val (f, p) = perechi.first()

        assertEquals(GeneratorSintetic.genereaza(f, p, 3, 7), GeneratorSintetic.genereaza(f, p, 3, 7))
        val diferite = perechi.any { (fam, pert) ->
            (0 until GeneratorSintetic.PE_PERTURBARE).any {
                GeneratorSintetic.genereaza(fam, pert, it, 7) != GeneratorSintetic.genereaza(fam, pert, it, 8)
            }
        }
        assertTrue(diferite, "seed 7 si seed 8 dau aceleasi variante pe toate perechile")
    }

    @Test
    fun `seedul implicit reproduce corpusul de azi`() {
        for ((f, p) in perechi) for (i in 0 until GeneratorSintetic.PE_PERTURBARE) {
            assertEquals(
                GeneratorSintetic.genereaza(f, p, i),
                GeneratorSintetic.genereaza(f, p, i, GeneratorSintetic.SEED),
                "${f.name}.${p.id}.$i",
            )
        }
    }

    /** Proprietatile vin din `-Ptrueride.explorare.*` prin task-ul Gradle; lipsa lor = testul nu e al suitei de zi. */
    private fun proprietate(nume: String): String =
        System.getProperty("trueride.explorare.$nume").also { Assumptions.assumeTrue(it != null, "lipseste $nume") }

    @Test
    @Tag("explorare")
    fun `exploreaza si scrie gresitele`() {
        val seed = proprietate("seed").toInt()
        val variante = proprietate("variante").toInt()
        val iesire = File(proprietate("iesire"))

        // Un rand pe varianta cu valoare GRESITA pe cel putin un set de parsere. Nu se aserteaza zero: scopul e sa gaseasca.
        val randuri = perechi.flatMap { (f, p) ->
            (0 until variante).mapNotNull { i ->
                val v = GeneratorSintetic.genereaza(f, p, i, seed)
                val gresit = GeneratorSintetic.seturi(f).any { (_, parser) ->
                    Clasificare.varianta(v.asteptat, parser.parseaza(v.linii)) == Clasa.GRESIT
                }
                if (gresit && !GeneratorSintetic.cunoscutGresit(v)) "${f.name}\t${p.id}\t$seed\t$i\tGRESIT" else null
            }
        }
        iesire.absoluteFile.parentFile.mkdirs()
        val antet = "familie\tperturbare\tseed\tindex\tclasificare"
        iesire.writeText((listOf(antet) + randuri).joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        assertTrue(iesire.isFile)
    }
}
