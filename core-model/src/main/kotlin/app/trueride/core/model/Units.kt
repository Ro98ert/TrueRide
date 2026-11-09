package app.trueride.core.model

import java.math.BigDecimal
import java.math.MathContext

/**
 * Unitatea de distanta. Soferul o alege; nu se deduce din piata
 * (spec 5.1 — `distanceUnit` e independent de `market`).
 */
enum class DistanceUnit { KM, MI }

/**
 * O distanta poarta intotdeauna unitatea cu ea.
 *
 * De ce nu un simplu `BigDecimal`: parserul poate citi de pe ecran "3.5 mi"
 * in timp ce profilul soferului e in km. Un numar fara unitate face
 * adunarea `5.2 + 9.5` sa para corecta si cand cei doi termeni sunt in
 * unitati diferite — exact bug-ul pe care testul de "unitati mixte" din
 * criteriul M2 il cauta.
 */
data class Distance(
    val amount: BigDecimal,
    val unit: DistanceUnit,
) {
    /** Conversie exacta intre unitati. 1 mi = 1.609344 km, prin definitie. */
    fun convertTo(target: DistanceUnit, mc: MathContext): Distance = when {
        unit == target -> this
        unit == DistanceUnit.MI && target == DistanceUnit.KM ->
            Distance(amount.multiply(MILES_TO_KM, mc), target)
        else ->
            Distance(amount.divide(MILES_TO_KM, mc), target)
    }

    companion object {
        /** Factor exact, definit international. Nu e o aproximare de rotunjit. */
        val MILES_TO_KM: BigDecimal = BigDecimal("1.609344")
    }
}
