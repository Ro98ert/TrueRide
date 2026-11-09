package app.trueride.core.parse.sintetic

import app.trueride.core.model.ParsedOffer
import app.trueride.core.parse.Cutie
import app.trueride.core.parse.LinieOcr
import java.math.BigDecimal

/** Spatiul neintrerupt (U+00A0), pe care `\s` din regex-ul Java nu-l prinde. */
const val NB = " "

enum class Familie { UBER_RO, UBER_EN, BOLT_RO }

enum class Camp { PRET, MIN_PRELUARE, KM_PRELUARE, MIN_CURSA, KM_CURSA }

/** Ce trebuie sa iasa pe un camp, scris de mana pe tip de perturbare (nu dedus din rezultatul parserului). */
sealed interface Asteptare {
    /** Valoarea exacta; null din parser = pierdut (nu e eroare), alta valoare = GRESIT. */
    data class Exact(val v: BigDecimal) : Asteptare

    /** Invariantul 6: informatia lipseste sau e ambigua; orice valoare e GRESIT. */
    object Necunoscut : Asteptare

    /** Ambiguu: necunoscut sau una dintre aceste valori; nicio alta valoare. */
    data class Oricare(val valori: List<BigDecimal>) : Asteptare
}

enum class Clasa { CORECT, NECUNOSCUT_CORECT, NECUNOSCUT_PIERDUT, GRESIT }

/** Valorile inventate ale unei oferte (adevarul din care se construiesc liniile). */
data class Valori(val pret: BigDecimal, val minPre: Int, val kmPre: BigDecimal, val minCursa: Int, val kmCursa: BigDecimal)

enum class Taiere { NICIUNA, LA_ANCORA, FARA_ANCORA }

/** Un rand de segment (preluare/cursa) ca piese de text, ca perturbarile sa schimbe cate una. */
data class Seg(
    val min: String,
    val km: String,
    val minTxt: String,
    val sepAncora: String,
    val unit: String = "km",
    val spMin: String = " ",
    val spKm: String = " ",
    val inchide: String = ")",
    val eticheta: String = "",
    val coda: String = "",
    val taiere: Taiere = Taiere.NICIUNA,
    val bulina: String = "•",
) {
    fun text(f: Familie): String {
        val capat = "$min$spMin$minTxt"
        return when {
            taiere == Taiere.FARA_ANCORA -> "$eticheta$capat"
            f == Familie.BOLT_RO -> if (taiere == Taiere.LA_ANCORA) "$capat$sepAncora$bulina" else "$capat$sepAncora$bulina $km$spKm$unit"
            taiere == Taiere.LA_ANCORA -> "$eticheta$capat$sepAncora("
            else -> "$eticheta$capat$sepAncora($km$spKm$unit$inchide$coda"
        }
    }

    companion object {
        fun baza(f: Familie, min: Int, km: BigDecimal, prima: Boolean): Seg = when (f) {
            Familie.UBER_RO -> Seg(min.toString(), km.toPlainString(), "min.", " ",
                eticheta = if (prima) "▢: " else "▢ ", coda = if (prima) "" else " ▢")
            Familie.UBER_EN -> Seg(min.toString(), km.toPlainString(), "mins", " ", coda = if (prima) "" else " ▢")
            Familie.BOLT_RO -> Seg(min.toString(), km.toPlainString(), "min", NB)
        }
    }
}

/**
 * O oferta planificata: textele liniilor + ce se asteapta. [secunda] = a doua oferta din acelasi cadru.
 * [pretNum] = textul numeric al pretului (fara moneda), pe care il schimba perturbarile de pret.
 */
data class Plan(
    val familie: Familie,
    val v: Valori,
    val pretNum: String,
    val pret: String,
    val pre: Seg,
    val cu: Seg,
    val asteptat: Map<Camp, Asteptare>,
    val inainte: List<String> = listOf("▢ ▢ ▢", "▢ ▢", "▢ ▢ ▢"),
    val bonus: String? = null,
    val dupaPret: List<String> = emptyList(),
    val bonusMare: Boolean = false,
    val dupaCursa: List<String> = emptyList(),
    /** Randuri puse intre preluare si cursa (D-315.3: ancora in plus, randuri straine). */
    val intreSegmente: List<String> = emptyList(),
    val recitita: Boolean = false,
    val pretSub: Boolean = false,
    val amesteca: Boolean = false,
    val secunda: Plan? = null,
    /** Geometrie dupa randare (casete sparte, dublate, mutate pe alte coloane); identitate = linii neschimbate. */
    val post: (List<LinieOcr>) -> List<LinieOcr> = { it },
)

data class Varianta(
    val nume: String,
    val familie: Familie,
    val perturbare: String,
    val linii: List<LinieOcr>,
    val asteptat: Map<Camp, Asteptare>,
)

/** Pozitiile pe ecran: inaltimi ca in testele existente (mediana liniilor vs pretul, D-143). */
object Randare {
    private fun inaltimi(f: Familie): Pair<Int, Int> = when (f) {
        Familie.UBER_RO -> 45 to 115
        Familie.UBER_EN -> 46 to 90
        Familie.BOLT_RO -> 82 to 114
    }

    fun linii(p: Plan): List<LinieOcr> = liniiOferta(p, 300) + (p.secunda?.let { liniiOferta(it, 2500) } ?: emptyList())

    private fun liniiOferta(p: Plan, y0: Int): List<LinieOcr> {
        val (h, hp) = inaltimi(p.familie)
        val out = mutableListOf<LinieOcr>()
        var y = y0
        fun adauga(text: String, inaltime: Int = h, recitita: Boolean = false) {
            out += LinieOcr(text, Cutie(100, y, minOf(1000, 100 + 20 * text.length), y + inaltime), recitita)
            y += inaltime + 55
        }
        fun blocPret() {
            adauga(p.pret, hp, p.recitita)
            p.bonus?.let { adauga(it, if (p.bonusMare) hp + 10 else h) }
            p.dupaPret.forEach { adauga(it) }
        }
        p.inainte.forEach { adauga(it) }
        if (!p.pretSub) blocPret()
        adauga(p.pre.text(p.familie))
        p.intreSegmente.forEach { adauga(it) }
        adauga(p.cu.text(p.familie))
        p.dupaCursa.forEach { adauga(it) }
        if (p.pretSub) blocPret()
        return out
    }
}

object Clasificare {
    fun valori(o: ParsedOffer): Map<Camp, BigDecimal?> = mapOf(
        Camp.PRET to o.price, Camp.MIN_PRELUARE to o.pickupMinutes, Camp.KM_PRELUARE to o.pickupDistance,
        Camp.MIN_CURSA to o.tripMinutes, Camp.KM_CURSA to o.tripDistance,
    )

    fun camp(a: Asteptare, got: BigDecimal?): Clasa = when (a) {
        is Asteptare.Exact -> when {
            got == null -> Clasa.NECUNOSCUT_PIERDUT
            got.compareTo(a.v) == 0 -> Clasa.CORECT
            else -> Clasa.GRESIT
        }
        Asteptare.Necunoscut -> if (got == null) Clasa.NECUNOSCUT_CORECT else Clasa.GRESIT
        is Asteptare.Oricare -> when {
            got == null -> Clasa.NECUNOSCUT_CORECT
            a.valori.any { it.compareTo(got) == 0 } -> Clasa.CORECT
            else -> Clasa.GRESIT
        }
    }

    /** GRESIT bate PIERDUT bate NECUNOSCUT_CORECT bate CORECT: o varianta e atat de buna cat cel mai rau camp al ei. */
    fun varianta(asteptat: Map<Camp, Asteptare>, o: ParsedOffer): Clasa {
        val v = valori(o)
        val pe = Camp.entries.map { camp(asteptat.getValue(it), v[it]) }
        return listOf(Clasa.GRESIT, Clasa.NECUNOSCUT_PIERDUT, Clasa.NECUNOSCUT_CORECT).firstOrNull { it in pe } ?: Clasa.CORECT
    }

    /** Pentru mesaje de eroare: doar campurile gresite, cu valorile inventate. */
    fun gresite(asteptat: Map<Camp, Asteptare>, o: ParsedOffer): String =
        Camp.entries.filter { camp(asteptat.getValue(it), valori(o)[it]) == Clasa.GRESIT }
            .joinToString { "$it asteptat=${asteptat[it]} primit=${valori(o)[it]}" }
}
