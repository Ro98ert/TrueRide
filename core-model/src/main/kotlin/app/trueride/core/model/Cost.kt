package app.trueride.core.model

import java.math.BigDecimal

/**
 * Costul masinii, redus la ce foloseste efectiv motorul de calcul.
 *
 * `costPerDistance` e valoarea *derivata* `effectiveCostPerDistance` din
 * spec 5.2 — indiferent daca profilul e in mod AUTO (derivata din consum si
 * pret energie) sau MANUAL (introdusa direct). Derivarea e treaba ecranului
 * E3, nu a motorului; motorul primeste rezultatul.
 *
 * `unit` spune in ce unitate e exprimat costul: 0,63 *pe km* si 0,63 *pe mila*
 * sunt doua lucruri diferite. Toate distantele se normalizeaza la unitatea
 * asta inainte de inmultire.
 */
data class CostInput(
    val costPerDistance: BigDecimal?,
    val unit: DistanceUnit,
)
