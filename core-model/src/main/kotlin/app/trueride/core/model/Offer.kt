package app.trueride.core.model

import java.math.BigDecimal

/**
 * Oferta asa cum a fost citita de pe ecran, inainte de orice calcul.
 *
 * Fiecare camp e nullable pentru ca fiecare camp poate lipsi de pe ecran.
 * `null` inseamna "necitit", niciodata "zero" — invariantul 6 din CLAUDE.md
 * si regula 4 din spec 4.4. Kotlin face distinctia asta obligatorie: nu poti
 * folosi un `BigDecimal?` ca numar fara sa tratezi explicit cazul lipsa,
 * altfel nu compileaza.
 *
 * Subset din `ParsedOffer` (spec 5.5) — doar campurile de care are nevoie
 * motorul de calcul. Entitatea completa e task-ul M1, din Faza 1.
 */
data class OfferInput(
    val price: BigDecimal? = null,
    val pickupMinutes: BigDecimal? = null,
    val tripMinutes: BigDecimal? = null,
    val pickupDistance: Distance? = null,
    val tripDistance: Distance? = null,
)
