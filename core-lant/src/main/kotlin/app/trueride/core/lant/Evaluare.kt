package app.trueride.core.lant

import app.trueride.core.calc.OfferCalculator
import app.trueride.core.model.CostInput
import app.trueride.core.model.Distance
import app.trueride.core.model.Evaluation
import app.trueride.core.model.OfferInput
import app.trueride.core.model.ParsedOffer

/**
 * Puntea G2 -> motor: `ParsedOffer` are distantele ca numere plus o singura
 * unitate; motorul vrea `Distance`. Unitatea lipsa se ia din profil (spec §5.5).
 * Profil de cost lipsa -> cost necunoscut, nu zero: profitul iese null.
 */
object Evaluare {
    fun calculeaza(oferta: ParsedOffer, setari: Setari): Evaluation {
        val unitate = oferta.distanceUnitDetected ?: setari.unitateImplicita
        val intrare = OfferInput(
            price = oferta.price,
            pickupMinutes = oferta.pickupMinutes,
            tripMinutes = oferta.tripMinutes,
            pickupDistance = oferta.pickupDistance?.let { Distance(it, unitate) },
            tripDistance = oferta.tripDistance?.let { Distance(it, unitate) },
        )
        val cost = setari.cost ?: CostInput(costPerDistance = null, unit = setari.unitateImplicita)
        return OfferCalculator.evaluate(intrare, cost, setari.praguri)
    }
}
