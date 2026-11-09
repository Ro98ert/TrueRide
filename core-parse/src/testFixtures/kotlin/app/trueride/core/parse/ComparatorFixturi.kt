package app.trueride.core.parse

import app.trueride.core.model.ParsedOffer
import java.io.File
import java.math.BigDecimal

/**
 * Ce au in comun testele pe fixture-uri (M4 Uber, M4 Bolt, lantul din core-lant):
 * lista fixture-urilor unei aplicatii, eticheta fiecaruia si diferentele fata de
 * ce a citit parserul. O singura copie, ca doua teste sa nu judece diferit.
 */
object ComparatorFixturi {

    fun fixturi(aplicatie: String): List<File> = CititorFixturi.director(aplicatie)
        .listFiles { f -> f.name.endsWith(".json") && !f.name.endsWith(".asteptat.json") }
        .orEmpty()
        .sortedBy { it.name }

    fun etichetaDe(f: File): File = File(f.parentFile, f.name.removeSuffix(".json") + ".asteptat.json")

    /** Numele campurilor care difera. BigDecimal prin compareTo: "4" si "4.0" sunt aceeasi valoare. */
    fun diferente(o: ParsedOffer, e: Map<String, String?>): List<String> = buildList {
        fun numar(camp: String, v: BigDecimal?) {
            val asteptat = e[camp]?.let(::BigDecimal)
            val egal = if (v == null || asteptat == null) v == asteptat else v.compareTo(asteptat) == 0
            if (!egal) add(camp)
        }
        numar("price", o.price)
        numar("pickupMinutes", o.pickupMinutes)
        numar("tripMinutes", o.tripMinutes)
        numar("pickupDistance", o.pickupDistance)
        numar("tripDistance", o.tripDistance)
        numar("surgeMultiplier", o.surgeMultiplier)
        if (o.currency != e["currency"]) add("currency")
        if (o.priceBasis.name != e["priceBasis"]) add("priceBasis")
        if (o.distanceUnitDetected?.name != e["distanceUnitDetected"]) add("distanceUnitDetected")
    }

    /** "nume: [campuri]" pentru fiecare fixture citit gresit de [parser]. */
    fun gresite(fixturi: List<File>, parser: ParserOferta): List<String> = fixturi.mapNotNull { f ->
        val d = diferente(parser.parseaza(CititorFixturi.linii(f)), CititorFixturi.eticheta(etichetaDe(f)))
        if (d.isEmpty()) null else "${f.name}: $d"
    }
}
