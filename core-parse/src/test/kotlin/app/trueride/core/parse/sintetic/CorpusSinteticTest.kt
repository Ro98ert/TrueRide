package app.trueride.core.parse.sintetic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Corpusul sintetic (B2, Goal v3): oferte Uber RO / Uber EN / Bolt RO generate determinist (seed fix, valori
 * inventate), perturbate dupa formele reale din lot si citite cu parserul REAL. Asteptarea fiecarei variante e scrisa
 * de mana pe tip de perturbare ([PerturbariSintetice]); aici doar se clasifica rezultatul si se scrie tabelul.
 * Sinteticul nu e dovada pentru o regula, doar pentru teste.
 */
class CorpusSinteticTest {

    private val corpus = GeneratorSintetic.corpus()

    /** Frecventele reale (D-315.2), fata de directorul modulului; ignorat de git, deci poate lipsi. */
    private companion object {
        const val FRECVENTE = "../.probe-shots/fixturi-raport/frecvente-erori.tsv"
    }

    private data class Cheie(val set: String, val familie: Familie, val perturbare: String)

    private class Numaratori {
        val pe = Clasa.entries.associateWith { 0 }.toMutableMap()
        var gresitCunoscut = 0
        var n = 0
    }

    private fun masoara(): Map<Cheie, Numaratori> {
        val tabel = linkedMapOf<Cheie, Numaratori>()
        for (v in corpus) for ((set, parser) in GeneratorSintetic.seturi(v.familie)) {
            val o = parser.parseaza(v.linii)
            val clasa = Clasificare.varianta(v.asteptat, o)
            val nume = tabel.getOrPut(Cheie(set, v.familie, v.perturbare)) { Numaratori() }
            nume.n++
            if (clasa == Clasa.GRESIT && GeneratorSintetic.cunoscutGresit(v)) {
                nume.gresitCunoscut++
            } else {
                nume.pe[clasa] = nume.pe.getValue(clasa) + 1
            }
        }
        return tabel
    }

    private fun scrieTabel(tabel: Map<Cheie, Numaratori>) {
        val f = File("build/sintetic/tabel.tsv")
        f.parentFile.mkdirs()
        val cap = "set\tfamilie\tperturbare\tvariante\tCORECT\tNECUNOSCUT_CORECT\tNECUNOSCUT_PIERDUT\tGRESIT\tGRESIT_CUNOSCUT"
        val linii = tabel.map { (k, n) ->
            listOf(k.set, k.familie, k.perturbare, n.n, n.pe.getValue(Clasa.CORECT), n.pe.getValue(Clasa.NECUNOSCUT_CORECT),
                n.pe.getValue(Clasa.NECUNOSCUT_PIERDUT), n.pe.getValue(Clasa.GRESIT), n.gresitCunoscut).joinToString("\t")
        }
        f.writeText((listOf(cap) + linii).joinToString("\n", postfix = "\n"), Charsets.UTF_8)
    }

    private fun celule(tabel: Map<Cheie, Numaratori>): List<Celula> = tabel.map { (k, n) ->
        Celula(k.set, k.familie, k.perturbare, n.pe.getValue(Clasa.CORECT), n.pe.getValue(Clasa.NECUNOSCUT_CORECT),
            n.pe.getValue(Clasa.NECUNOSCUT_PIERDUT), n.pe.getValue(Clasa.GRESIT), n.gresitCunoscut)
    }

    private fun scrieProcentPonderat(randuri: List<RandPonderat>): String {
        val f = File("build/sintetic/procent-ponderat.tsv")
        f.parentFile.mkdirs()
        return ProcentPonderat.tsv(randuri).also { f.writeText(it, Charsets.UTF_8) }
    }

    @Test
    fun `procentul ponderat dupa frecventele reale se scrie in build sintetic procent-ponderat tsv iar GRESIT necunoscut e 0`() {
        val frecvente = Frecvente.dinFisier(File(FRECVENTE))
        val inventate = PerturbariSintetice.TOATE.filter { it.inventata }.map { it.id }.toSet()

        val randuri = ProcentPonderat.calculeaza(celule(masoara()), frecvente, inventate)
        println(scrieProcentPonderat(randuri))
        frecvente?.let { f ->
            println("frecvente reale: ${f.randuri()} randuri, din care ${f.nemodelate()} cu forma nemodelata")
            println("AVERTISMENT inventate care apar in Sentry: ${inventate.intersect(f.vazute())}")
        }

        assertTrue(randuri.isNotEmpty())
        assertTrue(randuri.all { it.gresit == 0.0 }, "GRESIT necunoscut: ${randuri.filter { it.gresit != 0.0 }}")
        assertTrue(randuri.any { it.grup == "inventat" } && randuri.any { it.grup == "real" })
    }

    @Test
    fun `corpusul e determinist - doua generari dau aceleasi variante, cu nume stabile si unice`() {
        val a = GeneratorSintetic.corpus()
        val b = GeneratorSintetic.corpus()

        assertEquals(a, b)
        assertEquals(a.size, a.map { it.nume }.toSet().size)
    }

    @Test
    fun `corpusul acopera cele trei familii cu cel putin 20 de variante pe perturbare si 30 de perturbari`() {
        val pePerturbare = corpus.groupBy { it.familie to it.perturbare }

        Familie.entries.forEach { f -> assertTrue(pePerturbare.keys.count { it.first == f } >= 30, "$f are putine perturbari") }
        assertTrue(pePerturbare.values.all { it.size >= 20 })
    }

    @Test
    fun `nicio varianta nu da o valoare gresita - tabelul se scrie in build sintetic tabel tsv`() {
        val tabel = masoara()
        scrieTabel(tabel)

        val gresite = corpus.flatMap { v ->
            GeneratorSintetic.seturi(v.familie).mapNotNull { (set, parser) ->
                val o = parser.parseaza(v.linii)
                if (Clasificare.varianta(v.asteptat, o) == Clasa.GRESIT && !GeneratorSintetic.cunoscutGresit(v)) {
                    "${v.nume} [$set]: ${Clasificare.gresite(v.asteptat, o)}"
                } else {
                    null
                }
            }
        }
        assertTrue(gresite.isEmpty(), "GRESIT=${gresite.size}: ${gresite.take(5)}")
    }

    @Test
    fun `cazurile cunoscute ca gresite exista in corpus si au un P-sint`() {
        val chei = corpus.map { "${it.familie}.${it.perturbare}" }.toSet()

        GeneratorSintetic.CUNOSCUTE_GRESITE.forEach { (cheie, motiv) ->
            assertTrue(cheie in chei, "cheie necunoscuta: $cheie")
            assertTrue(motiv.startsWith("P-sint-") || motiv.startsWith("P-sim-"), motiv)
        }
    }
}
