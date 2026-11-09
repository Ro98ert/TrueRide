package app.trueride.core.calc

import app.trueride.core.model.CostInput
import app.trueride.core.model.DataCompleteness
import app.trueride.core.model.Distance
import app.trueride.core.model.DistanceUnit
import app.trueride.core.model.Evaluation
import app.trueride.core.model.OfferInput
import app.trueride.core.model.Thresholds
import app.trueride.core.model.Verdict
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Motorul de calcul: cele sapte formule din spec 4.4. Functie pura —
 * aceleasi intrari dau intotdeauna aceleasi iesiri, fara stare si fara efecte.
 */
object OfferCalculator {

    /**
     * Precizia de lucru a aritmeticii. NU e rotunjire de afisare.
     *
     * `BigDecimal.divide` arunca exceptie daca rezultatul are zecimale
     * infinite (33,07 ÷ 14,7 e periodic), deci impartirea are nevoie de o
     * precizie declarata. 16 cifre semnificative sunt cu multe ordine de
     * marime peste ce poate distinge afisarea cu 2 zecimale.
     *
     * Analogia: e rezolutia convertorului analog-digital, nu numarul de
     * zecimale de pe display.
     */
    val WORKING_PRECISION: MathContext = MathContext(16, RoundingMode.HALF_UP)

    private val MINUTES_PER_HOUR = BigDecimal("60")

    /**
     * Treptele verdictului, de la cea mai slaba la cea mai buna (D-082). Lista
     * explicita, nu `Verdict.ordinal`: ordinea din enum e de afisare si se poate
     * schimba fara ca cineva sa se gandeasca la regula de aici.
     */
    private val ORDINE_TREPTE = listOf(Verdict.WEAK, Verdict.GOOD, Verdict.EXCELLENT)

    fun evaluate(
        offer: OfferInput,
        cost: CostInput,
        thresholds: Thresholds,
    ): Evaluation {
        // Pasul 1: curatarea intrarilor. Orice valoare imposibila fizic
        // (negativa) devine "necitita". Vezi `usable`.
        val price = offer.price.usable()
        val pickupMinutes = offer.pickupMinutes.usable()
        val tripMinutes = offer.tripMinutes.usable()
        val pickupDistance = offer.pickupDistance.usableIn(cost.unit)
        val tripDistance = offer.tripDistance.usableIn(cost.unit)
        val costPerDistance = cost.costPerDistance.usable()

        // Pasul 2: cat de completa e oferta. Se stabileste pe intrari, inainte
        // de orice calcul — o valoare aruncata la pasul 1 conteaza ca lipsa.
        val completeness = if (
            price != null && pickupMinutes != null && tripMinutes != null &&
            pickupDistance != null && tripDistance != null
        ) DataCompleteness.FULL else DataCompleteness.PARTIAL

        // Pasul 3: totalurile. Exista doar cu ambele segmente citite; daca lipseste
        // oricare, totalul e necunoscut — nici zero, nici segmentul ramas (D-072).
        val totalMinutes = bothOrNull(pickupMinutes, tripMinutes)
        val totalDistanceAmount = bothOrNull(pickupDistance, tripDistance)
        val totalDistance = totalDistanceAmount?.let { Distance(it, cost.unit) }

        // Pasul 4: cele sapte formule. Nimic nu se rotunjeste aici.
        val perHour = divideOrNull(price?.multiply(MINUTES_PER_HOUR), totalMinutes)
        val perDistance = divideOrNull(price, totalDistanceAmount)
        val perMinute = divideOrNull(price, totalMinutes)
        val estimatedCost = if (costPerDistance != null && totalDistanceAmount != null) {
            costPerDistance.multiply(totalDistanceAmount, WORKING_PRECISION)
        } else {
            null
        }
        val estimatedProfit = if (price != null && estimatedCost != null) {
            price.subtract(estimatedCost, WORKING_PRECISION)
        } else {
            null
        }

        return Evaluation(
            totalMinutes = totalMinutes,
            totalDistance = totalDistance,
            perHour = perHour,
            perDistance = perDistance,
            perMinute = perMinute,
            estimatedCost = estimatedCost,
            estimatedProfit = estimatedProfit,
            verdict = verdict(thresholds, perHour, perDistance, perMinute),
            completeness = completeness,
        )
    }

    /**
     * Verdictul, dupa D-082 (inlocuieste regula de 85 % din spec 4.4 punctul 5).
     *
     * Fiecare indicator cu prag Bun activ si valoare calculabila primeste o
     * treapta; verdictul cardului e cea mai slaba dintre ele, ca inainte de D-082:
     * o oferta e Excelenta doar daca e Excelenta pe tot ce a cerut soferul. Un prag
     * setat pentru care indicatorul nu s-a putut calcula nu participa.
     */
    private fun verdict(
        thresholds: Thresholds,
        perHour: BigDecimal?,
        perDistance: BigDecimal?,
        perMinute: BigDecimal?,
    ): Verdict {
        val trepte = listOfNotNull(
            treapta(thresholds.minPerHour, thresholds.excellentPerHour, perHour),
            treapta(thresholds.minPerDistance, thresholds.excellentPerDistance, perDistance),
            treapta(thresholds.minPerMinute, thresholds.excellentPerMinute, perMinute),
        )

        // Fara praguri active, sau fara niciun indicator comparabil, verdictul
        // nu se poate calcula. Nu e o eroare — e o stare de prim rang.
        if (trepte.isEmpty()) return Verdict.INSUFFICIENT_DATA
        return trepte.minBy { ORDINE_TREPTE.indexOf(it) }
    }

    /**
     * Treapta unui singur indicator, sau null daca nu participa. `>=` pe ambele
     * praguri: la egalitate, verdictul mai bun (D-038, D-082.3). Un Excelent care
     * nu e peste Bun e ignorat, nu inversat (D-082.7): verdictul merge doar cu Bun.
     */
    private fun treapta(bun: BigDecimal?, excelent: BigDecimal?, valoare: BigDecimal?): Verdict? {
        if (bun == null || valoare == null) return null
        val excelentValid = excelent?.takeIf { it > bun }
        return when {
            excelentValid != null && valoare >= excelentValid -> Verdict.EXCELLENT
            valoare >= bun -> Verdict.GOOD
            else -> Verdict.WEAK
        }
    }

    /**
     * O valoare negativa nu exista fizic in acest domeniu (pret, durata,
     * distanta, cost). Daca apare, e o citire gresita a parserului, nu un
     * numar. Se propaga ca lipsa, pentru ca un total micsorat de un termen
     * negativ ar produce un venit-pe-km fals de optimist — exact genul de
     * cifra care te face sa accepti o cursa proasta.
     *
     * Zero e o valoare valida: se lasa sa treaca si e tratat de `divideOrNull`.
     */
    private fun BigDecimal?.usable(): BigDecimal? = this?.takeIf { it.signum() >= 0 }

    /** Idem, plus normalizarea la unitatea in care e exprimat costul. */
    private fun Distance?.usableIn(target: DistanceUnit): BigDecimal? =
        this?.takeIf { it.amount.signum() >= 0 }
            ?.convertTo(target, WORKING_PRECISION)
            ?.amount

    /**
     * Suma celor doua segmente, sau null daca lipseste oricare (D-072, F-006).
     *
     * Nu "suma termenilor prezenti": pe teren, cu distanta cursei necitita,
     * lei/km impartit doar la distanta preluarii a iesit de ~35x prea mare,
     * iar marcajul PARTIAL nu l-a facut mai putin fals. Un numitor incomplet
     * se propaga ca necunoscut (invariantul 6).
     */
    private fun bothOrNull(a: BigDecimal?, b: BigDecimal?): BigDecimal? =
        if (a != null && b != null) a.add(b) else null

    /**
     * Impartirea la zero e o stare, nu o exceptie (spec 4.4, regula 3).
     * Numitor zero sau lipsa -> indicatorul e indisponibil, adica `null`.
     * Niciodata `∞`, niciodata `0`.
     */
    private fun divideOrNull(dividend: BigDecimal?, divisor: BigDecimal?): BigDecimal? {
        if (dividend == null || divisor == null || divisor.signum() == 0) return null
        return dividend.divide(divisor, WORKING_PRECISION)
    }
}
