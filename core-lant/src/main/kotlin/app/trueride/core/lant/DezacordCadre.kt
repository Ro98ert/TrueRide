package app.trueride.core.lant

import app.trueride.core.model.OfferField
import app.trueride.core.model.ParsedOffer
import java.math.BigDecimal

/**
 * D-368: dezacordul dintre doua citiri ale ACELEIASI oferte. Un camp e in dezacord doar daca ambele citiri
 * au o valoare CUNOSCUTA si valorile difera numeric. Necunoscut -> cunoscut (sau invers) e completitudine,
 * masurata deja (invariantul 6), nu un dezacord. Iese doar numele campului, niciodata valoarea (invariantul 2).
 */
object DezacordCadre {

    /**
     * D-368.5: cel mult atatea campuri diferite intre doua citiri ale aceleiasi oferte. Mai multe deodata nu sunt cifre
     * citite gresit, ci o oferta noua venita direct peste cardul vechi (D-134), asa ca nu se numara nimic.
     */
    const val MAX_CAMPURI_ACEEASI_OFERTA = 2

    /** [campuri] pentru doua citiri succesive ale cardului: gol cand diferenta e prea mare ca sa fie aceeasi oferta. */
    fun dintreCadre(vechi: ParsedOffer, noua: ParsedOffer): List<OfferField> =
        campuri(vechi, noua).takeIf { it.size <= MAX_CAMPURI_ACEEASI_OFERTA }.orEmpty()

    /** Campurile cunoscute in ambele oferte si diferite, in ordinea [OfferField]. */
    fun campuri(vechi: ParsedOffer, noua: ParsedOffer): List<OfferField> = buildList {
        if (difera(vechi.price, noua.price)) add(OfferField.PRICE)
        if (difera(vechi.pickupMinutes, noua.pickupMinutes)) add(OfferField.PICKUP_MINUTES)
        if (difera(vechi.tripMinutes, noua.tripMinutes)) add(OfferField.TRIP_MINUTES)
        if (difera(vechi.pickupDistance, noua.pickupDistance)) add(OfferField.PICKUP_DISTANCE)
        if (difera(vechi.tripDistance, noua.tripDistance)) add(OfferField.TRIP_DISTANCE)
    }

    /** Numeric (1.5 si 1.50 sunt aceeasi valoare); un null nu difera de nimic. */
    private fun difera(a: BigDecimal?, b: BigDecimal?) = a != null && b != null && a.compareTo(b) != 0
}
