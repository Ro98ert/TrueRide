package app.trueride.core.calc

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Rotunjirea de afisare — **singurul** loc din tot lantul unde se rotunjeste
 * (spec 4.4, regula 2). Valorile din [app.trueride.core.model.Evaluation] raman
 * nerotunjite; UI-ul trece prin functiile de aici chiar inainte sa scrie pe
 * ecran.
 *
 * De ce conteaza ca e un singur loc: daca s-ar rotunji si in lantul de calcul,
 * profitul (pret − cost) s-ar calcula pe un cost deja rotunjit, iar eroarea s-ar
 * propaga. Vectorul din spec 4.5 prinde exact asta: costul real e 9,261 si doar
 * afisarea il face 9,26.
 *
 * Numarul de zecimale per indicator e derivat din tabelul de iesiri asteptate
 * din spec 4.5: `venit pe ora` apare ca `66` (0 zecimale) desi valoarea
 * calculata e 66,14; restul apar cu 2 zecimale.
 */
object DisplayRounding {

    private const val MONEY_SCALE = 2
    private const val PER_HOUR_SCALE = 0
    private const val DISTANCE_SCALE = 2
    private const val MINUTES_SCALE = 0

    /** Sume de bani si indicatori per distanta / per minut: 2 zecimale. */
    fun money(value: BigDecimal?): BigDecimal? = value?.setScale(MONEY_SCALE, RoundingMode.HALF_UP)

    /** Venit pe ora: 0 zecimale. */
    fun perHour(value: BigDecimal?): BigDecimal? = value?.setScale(PER_HOUR_SCALE, RoundingMode.HALF_UP)

    /** Distante: 2 zecimale. */
    fun distance(value: BigDecimal?): BigDecimal? = value?.setScale(DISTANCE_SCALE, RoundingMode.HALF_UP)

    /** Durate in minute: 0 zecimale. */
    fun minutes(value: BigDecimal?): BigDecimal? = value?.setScale(MINUTES_SCALE, RoundingMode.HALF_UP)
}
