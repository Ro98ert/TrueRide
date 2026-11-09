package app.trueride.core.model

import java.math.BigDecimal

/**
 * Verdictul afisat pe card (spec 5.6, D-082).
 *
 * EXCELLENT = pragul Excelent atins, GOOD = pragul Bun atins, WEAK = sub pragul
 * Bun. Ordinea din enum nu e o regula: OfferCalculator are ordinea treptelor scrisa
 * explicit, ca o reordonare de afisare sa nu schimbe verdictul.
 *
 * `INSUFFICIENT_DATA` e o stare de prim rang, nu o eroare: se ajunge la ea
 * cand oferta nu s-a putut citi indeajuns, sau cand niciun prag activ nu are
 * un indicator calculabil cu care sa fie comparat.
 */
enum class Verdict { EXCELLENT, GOOD, WEAK, INSUFFICIENT_DATA }

/** Cat de completa a fost oferta citita. Se afiseaza pe card (spec E6). */
enum class DataCompleteness { FULL, PARTIAL }

/**
 * Rezultatul motorului de calcul.
 *
 * Toate valorile numerice sunt **nerotunjite** — rotunjirea se face o singura
 * data, la afisare (spec 4.4, regula 2). `null` pe un indicator inseamna
 * "indisponibil": fie o intrare lipsea, fie numitorul era zero. In ambele
 * cazuri cardul il arata ca indisponibil, niciodata ca `0` si niciodata ca `∞`
 * (spec 4.4, regulile 3 si 4).
 */
data class Evaluation(
    val totalMinutes: BigDecimal? = null,
    val totalDistance: Distance? = null,
    val perHour: BigDecimal? = null,
    val perDistance: BigDecimal? = null,
    val perMinute: BigDecimal? = null,
    val estimatedCost: BigDecimal? = null,
    val estimatedProfit: BigDecimal? = null,
    val verdict: Verdict = Verdict.INSUFFICIENT_DATA,
    val completeness: DataCompleteness = DataCompleteness.PARTIAL,
)
