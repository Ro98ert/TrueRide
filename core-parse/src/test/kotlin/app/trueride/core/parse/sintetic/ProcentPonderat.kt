package app.trueride.core.parse.sintetic

import java.io.File
import java.util.Locale

/** Numaratorile unei celule (set de reguli, familie, perturbare) din corpus; [necCorect] = necunoscut corect. */
data class Celula(
    val set: String,
    val familie: Familie,
    val perturbare: String,
    val corect: Int,
    val necCorect: Int,
    val necPierdut: Int,
    val gresit: Int,
    val gresitCunoscut: Int,
) {
    val n: Int get() = corect + necCorect + necPierdut + gresit + gresitCunoscut
}

/**
 * Frecventele reale ale tipurilor de eroare (D-315.2), din `.probe-shots/fixturi-raport/frecvente-erori.tsv`
 * (generat de un script privat): `platforma<TAB>limba<TAB>perturbare<TAB>numar`, cu un rand `total` pe grup.
 * Fisierul e ignorat de git, deci poate lipsi: [dinFisier] da atunci null si ponderile devin egale.
 */
class Frecvente private constructor(private val numar: Map<Triple<String, String, String>, Int>, private val total: Map<Pair<String, String>, Int>) {

    /** Pondere = cate randuri reale au tipul acesta pe (platforma, limba) a familiei; o perturbare inventata primeste [POND_INVENTATA]. */
    fun pondere(familie: Familie, perturbare: String, inventata: Boolean): Double {
        val (platforma, limba) = grup(familie)
        if (inventata) return POND_INVENTATA * (total[platforma to limba] ?: 0)
        val tip = if (perturbare == "baza") "curat" else perturbare
        return (numar[Triple(platforma, limba, tip)] ?: 0).toDouble()
    }

    /** Ponderea totala a randurilor reale `nemodelat` (forme care nu au inca perturbare in generator), pentru raport. */
    fun nemodelate(): Int = numar.filterKeys { it.third == "nemodelat" }.values.sum()

    fun randuri(): Int = total.values.sum()

    /** Tipurile cu cel putin un rand real pe vreo (platforma, limba), ca raportul sa semnaleze o perturbare marcata inventata care totusi apare. */
    fun vazute(): Set<String> = numar.filterValues { it > 0 }.keys.map { it.third }.toSet()

    companion object {
        /** Ponderea fixa a unei perturbari inventate: 1% din randurile reale ale (platformei, limbii) ei (D-315.3). */
        const val POND_INVENTATA = 0.01

        fun grup(f: Familie): Pair<String, String> = when (f) {
            Familie.UBER_RO -> "uber" to "ro"
            Familie.UBER_EN -> "uber" to "en"
            Familie.BOLT_RO -> "bolt" to "ro"
        }

        fun dinText(text: String): Frecvente {
            val numar = mutableMapOf<Triple<String, String, String>, Int>()
            val total = mutableMapOf<Pair<String, String>, Int>()
            text.lineSequence().drop(1).map { it.split("\t") }.filter { it.size == 4 }.forEach { (plat, limba, tip, n) ->
                if (tip == "total") total[plat to limba] = n.toInt() else numar[Triple(plat, limba, tip)] = n.toInt()
            }
            return Frecvente(numar, total)
        }

        fun dinFisier(f: File): Frecvente? = f.takeIf { it.isFile }?.let { dinText(it.readText(Charsets.UTF_8)) }

        /** Fara frecvente (null) fiecare perturbare cantareste la fel. */
        fun pondere(f: Frecvente?, familie: Familie, perturbare: String, inventata: Boolean): Double =
            f?.pondere(familie, perturbare, inventata) ?: 1.0
    }
}

/** Un rand al tabelului ponderat: procente pe (set de reguli, grup real|inventat). */
data class RandPonderat(
    val set: String,
    val grup: String,
    val mod: String,
    val perturbari: Int,
    val gresit: Double,
    val necPierdut: Double,
    val necCorect: Double,
    val corect: Double,
    val gresitCunoscut: Double,
)

object ProcentPonderat {
    const val ANTET = "set\tgrup\tmod\tperturbari\tGRESIT\tNEC_PIERDUT\tNEC_CORECT\tCORECT\tGRESIT_CUNOSCUT"

    /** [inventate] = id-urile perturbarilor inventate; restul sunt reale. Fara [frecvente] ponderile sunt egale. */
    fun calculeaza(celule: List<Celula>, frecvente: Frecvente?, inventate: Set<String>): List<RandPonderat> =
        celule.groupBy { it.set }.flatMap { (set, ale) ->
            listOf("real" to false, "inventat" to true).mapNotNull { (grup, inv) ->
                val grupul = ale.filter { (it.perturbare in inventate) == inv && it.n > 0 }
                if (grupul.isEmpty()) null else rand(set, grup, grupul, frecvente, inv)
            }
        }

    private fun rand(set: String, grup: String, celule: List<Celula>, frecvente: Frecvente?, inventata: Boolean): RandPonderat {
        val pond = celule.map { Frecvente.pondere(frecvente, it.familie, it.perturbare, inventata) }
        val total = pond.sum()
        fun procent(parte: (Celula) -> Int): Double =
            if (total == 0.0) 0.0 else celule.indices.sumOf { pond[it] * parte(celule[it]) / celule[it].n } / total * 100
        return RandPonderat(
            set, grup, if (frecvente == null) "egal" else "ponderat", pond.count { it > 0 },
            procent { it.gresit }, procent { it.necPierdut }, procent { it.necCorect }, procent { it.corect }, procent { it.gresitCunoscut },
        )
    }

    fun tsv(randuri: List<RandPonderat>): String = (listOf(ANTET) + randuri.map { r ->
        val procente = listOf(r.gresit, r.necPierdut, r.necCorect, r.corect, r.gresitCunoscut).map { String.format(Locale.ROOT, "%.2f", it) }
        (listOf(r.set, r.grup, r.mod, r.perturbari.toString()) + procente).joinToString("\t")
    }).joinToString("\n", postfix = "\n")
}
