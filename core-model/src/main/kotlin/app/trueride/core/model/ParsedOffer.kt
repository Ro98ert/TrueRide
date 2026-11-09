package app.trueride.core.model

import java.math.BigDecimal

/**
 * Baza pe care e afisat pretul pe ecran (spec 5.5, risc R9).
 *
 * De ce e un enum de prim rang si nu un boolean: Uber afiseaza net de comision
 * ("NET, taxe incluse"), Bolt afiseaza brut. Un profit calculat pe baza gresita
 * e fals cu ~20-25% — adica exact atat cat sa para plauzibil. `UNKNOWN` nu e o
 * valoare de umplutura: e starea in care cardul trebuie sa spuna explicit ca nu
 * stie, in loc sa ghiceasca (invariantul 6 din CLAUDE.md).
 */
enum class PriceBasis { NET, GROSS, UNKNOWN }

/**
 * Campurile unei oferte pe care parserul le poate citi, ca tip de cheie.
 *
 * Spec 5.5 scrie `fieldConfidence` ca `map<string, Decimal>`. Aici cheia e un
 * enum, nu un `String`: o cheie gresit scrisa intr-un `Map<String, _>` nu e o
 * eroare de compilare, ci o incredere lipsa citita ca "sub prag" — adica un
 * camp care dispare tacut de pe card. Cu enum, greseala nu compileaza.
 */
enum class OfferField {
    PRICE,
    PICKUP_MINUTES,
    TRIP_MINUTES,
    PICKUP_DISTANCE,
    TRIP_DISTANCE,
}

/**
 * Rezultatul parsarii unui singur cadru de oferta, inainte de orice calcul.
 *
 * Subset din entitatea completa din spec 5.5 — lipsesc `id` si `captureId`,
 * care apartin stratului de persistenta (task-ul M1 complet). Ce e aici e exact
 * ce au nevoie M3 (rezultatul asteptat al unui fixture) si M4 (iesirea
 * parserului), ca sa poata fi comparate direct.
 *
 * Fiecare camp masurabil e nullable din acelasi motiv ca in [OfferInput]:
 * `null` inseamna "necitit", niciodata "zero".
 *
 * Distantele sunt `BigDecimal?` plus un singur [distanceUnitDetected], nu doua
 * [Distance]. Motivul e fizic: ambele distante vin de pe acelasi ecran, deci
 * poarta aceeasi unitate; iar cand unitatea nu apare in text, ea lipseste pentru
 * amandoua simultan, nu pentru una.
 */
data class ParsedOffer(
    val price: BigDecimal? = null,
    /** Cod ISO 4217 (validat: trei litere mari) citit din simbolul de pe ecran. Null = de luat din profil. */
    val currency: String? = null,
    val priceBasis: PriceBasis = PriceBasis.UNKNOWN,
    val pickupMinutes: BigDecimal? = null,
    val tripMinutes: BigDecimal? = null,
    val pickupDistance: BigDecimal? = null,
    val tripDistance: BigDecimal? = null,
    /** Null = unitatea nu apare in text; se ia din profilul soferului. */
    val distanceUnitDetected: DistanceUnit? = null,
    /**
     * Multiplicatorul de tarif dinamic citit de pe ecran ("1.24x" -> 1.24).
     * Informativ — nu intra in niciun calcul. Null = neafisat sau necitit.
     *
     * Numeric, nu text (D-027, R2): un `String` aici ar fi singurul loc prin care
     * un fragment de ecran oarecare poate ajunge din parser pe card.
     */
    val surgeMultiplier: BigDecimal? = null,
    /** Incredere per camp. Un camp absent din map nu are incredere masurata. */
    val fieldConfidence: Map<OfferField, BigDecimal> = emptyMap(),
) {
    init {
        // `currency` ramane String ca sa poata purta orice cod ISO 4217, dar
        // doar un cod: trei litere mari. Orice altceva e o eroare de parser,
        // nu o valoare de afisat (D-027, R2).
        require(currency == null || ISO_4217.matches(currency)) { "currency nu e un cod ISO 4217" }
    }

    /**
     * Derivat, nu stocat: pret + cel putin un timp + cel putin o distanta.
     *
     * De ce derivat si nu un camp: un `isComplete` stocat poate intra in
     * contradictie cu campurile pe care le descrie. Derivat, nu poate.
     */
    val isComplete: Boolean
        get() = price != null &&
            (pickupMinutes != null || tripMinutes != null) &&
            (pickupDistance != null || tripDistance != null)

    private companion object {
        val ISO_4217 = Regex("[A-Z]{3}")
    }
}
