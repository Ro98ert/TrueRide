package app.trueride.core.parse.sintetic

import app.trueride.core.parse.UBER_RO_V1
import java.math.BigDecimal
import kotlin.random.Random

internal val TOATE_FAM: Set<Familie> = Familie.entries.toSet()
internal val UBER: Set<Familie> = setOf(Familie.UBER_RO, Familie.UBER_EN)

/** Fara [inventata] explicit, o perturbare e „inventata” daca nu are corespondent real (vezi [PerturbariReale]). */
internal fun p(id: String, fam: Set<Familie> = TOATE_FAM, inventata: Boolean = id !in PerturbariReale.IDS, aplica: (Plan, Random) -> Plan) =
    Perturbare(id, fam, inventata, aplica)

internal fun dec(s: String) = BigDecimal(s.replace(',', '.'))
internal fun exact(n: BigDecimal): Asteptare = Asteptare.Exact(n)
internal fun exact(s: String): Asteptare = Asteptare.Exact(dec(s))
internal fun oricare(vararg n: BigDecimal): Asteptare = Asteptare.Oricare(n.toList())
internal val NECUNOSCUT: Asteptare = Asteptare.Necunoscut

internal fun Plan.seteaza(vararg a: Pair<Camp, Asteptare>): Plan = copy(asteptat = asteptat + a)

internal fun Plan.cuPret(num: String, a: Asteptare, recitita: Boolean = false): Plan =
    copy(pretNum = num, pret = Formate.pret(familie, num), recitita = recitita).seteaza(Camp.PRET to a)

internal fun Plan.cuSegmente(f: (Seg) -> Seg): Plan = copy(pre = f(pre), cu = f(cu))

/** Schimba doar segmentul [prima] (preluare) sau al doilea (cursa). */
internal fun Plan.pe(prima: Boolean, f: (Seg) -> Seg): Plan = if (prima) copy(pre = f(pre)) else copy(cu = f(cu))

internal fun campMin(prima: Boolean) = if (prima) Camp.MIN_PRELUARE else Camp.MIN_CURSA
internal fun campKm(prima: Boolean) = if (prima) Camp.KM_PRELUARE else Camp.KM_CURSA
internal fun Plan.kmAdevarat(prima: Boolean): BigDecimal = if (prima) v.kmPre else v.kmCursa
internal fun Plan.minAdevarat(prima: Boolean): BigDecimal = BigDecimal(if (prima) v.minPre else v.minCursa)

/** Valori noi pentru un segment; km se recalculeaza din minute ca sa ramana plauzibili (D-148). */
internal fun Valori.cuMinute(prima: Boolean, min: Int, r: Random): Valori = with(GeneratorSintetic) {
    (if (prima) copy(minPre = min, kmPre = km(min, r)) else copy(minCursa = min, kmCursa = km(min, r))).inPlaja()
}

private fun String.inloc(i: Int, c: Char) = substring(0, i) + c + substring(i + 1)

/** Confuziile OCR sunt cele din `corectiiCifre` (D-122..D-124), citite din reguli, nu copiate. */
internal object Litere {
    val MAPATE: Map<Char, List<Char>> =
        UBER_RO_V1.reguli.corectiiCifre.entries.groupBy({ it.value.single() }, { it.key.single() })

    /** Litere pe care harta NU le corecteaza (S, B, Z...). */
    const val NEMAPATE = "SBZGqAE"

    /** (numarul cu o cifra pusa pe adevar, numarul cu litera mapata in locul ei); prima cifra nu devine 0. */
    fun mapata(num: String, r: Random): Pair<String, String> {
        val cifre = num.indices.filter { num[it].isDigit() }
        val i = cifre.random(r)
        val tinta = if (i == cifre.first()) '1' else listOf('0', '1').random(r)
        return num.inloc(i, tinta) to num.inloc(i, MAPATE.getValue(tinta).random(r))
    }

    /** O cifra inlocuita cu litera nemapata: prima cifra sau una dintre celelalte. */
    fun nemapata(num: String, r: Random, prima: Boolean): String {
        val cifre = num.indices.filter { num[it].isDigit() }
        val i = if (prima || cifre.size == 1) cifre.first() else cifre.drop(1).random(r)
        return num.inloc(i, NEMAPATE.random(r))
    }
}
