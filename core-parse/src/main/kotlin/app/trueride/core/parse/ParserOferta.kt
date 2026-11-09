package app.trueride.core.parse

import app.trueride.core.model.OfferField
import app.trueride.core.model.ParsedOffer
import java.math.BigDecimal

/** Ce cere lantul de la un parser de oferta: un singur set de reguli ([OfferParser]) sau mai multe ([ParserPeLimbi]). */
interface ParserOferta {
    fun parseaza(linii: List<LinieOcr>): ParsedOffer
    fun liniiFolosite(linii: List<LinieOcr>): List<LinieOcr>
    fun motiveLipsa(linii: List<LinieOcr>): Map<OfferField, MotivLipsa>

    /** D-173: linia aleasa ca pret, dar respinsa de forma (FORMA_PRET); null = nimic de recitit. */
    fun liniaPretFaraForma(linii: List<LinieOcr>): LinieOcr?

    /**
     * D-288.4: linia primului segment (preluarea) cand are minute citite, dar km lipsesc sau sunt imposibili
     * (FARA_KM, KM_IMPLAUZIBIL); null = nimic de recitit. Minutele lipsa le acopera [liniaMinutePreluareDeRecitit] (D-367).
     */
    fun liniaKmPreluareDeRecitit(linii: List<LinieOcr>): LinieOcr?

    /**
     * D-367: linia primului segment (preluarea) cand km sunt cititi, dar tokenul minutelor lipseste cu totul (OCR-ul
     * scapa cifra singura: „mins (2.1 km)”); null = nimic de recitit. Niciodata odata cu [liniaKmPreluareDeRecitit]:
     * una cere minute citite fara km, cealalta km cititi fara minute.
     */
    fun liniaMinutePreluareDeRecitit(linii: List<LinieOcr>): LinieOcr?

    /**
     * D-371: minutele preluarii asa cum le-a citit parserul, dupa garzile de forma, DAR inainte ca viteza preluarii sa le
     * anuleze. Recitirea km (D-288.4) le compara cu cele de dupa recitire: `parseaza` nu le mai arata cand km sunt
     * implauzibili, iar fara ele recitirea care repara km n-ar mai avea cu ce se compara. Null = nu s-au citit.
     */
    fun minutePreluareInainteDeViteza(linii: List<LinieOcr>): BigDecimal?

    /**
     * D-301: liniile pe care parserul le numara ca ancore de segment, de sus in jos, oricate sunt. Jurnalul
     * le scrie (doar cutia, fara text) cand numarul nu e cel asteptat (`ANCORE_NUMAR`).
     */
    fun ancoreSegment(linii: List<LinieOcr>): List<LinieOcr>
}

/**
 * F5: aplicatia de sofer vorbeste limba telefonului, deci acelasi ecran poate fi in oricare dintre [seturi].
 * Se pastreaza rezultatul INTREG al setului care citeste cele mai multe campuri, niciodata campuri amestecate
 * intre seturi (inv. 5, 6); la egalitate castiga primul set (RO, D-024). Un set care citeste tot opreste cautarea.
 */
class ParserPeLimbi(private val seturi: List<ParserOferta>) : ParserOferta {
    init {
        require(seturi.isNotEmpty()) { "cel putin un set de reguli" }
    }

    override fun parseaza(linii: List<LinieOcr>): ParsedOffer = castigator(linii).second

    override fun liniiFolosite(linii: List<LinieOcr>): List<LinieOcr> = castigator(linii).first.liniiFolosite(linii)

    override fun motiveLipsa(linii: List<LinieOcr>): Map<OfferField, MotivLipsa> = castigator(linii).first.motiveLipsa(linii)

    override fun liniaPretFaraForma(linii: List<LinieOcr>): LinieOcr? = castigator(linii).first.liniaPretFaraForma(linii)

    override fun liniaKmPreluareDeRecitit(linii: List<LinieOcr>): LinieOcr? =
        castigator(linii).first.liniaKmPreluareDeRecitit(linii)

    override fun liniaMinutePreluareDeRecitit(linii: List<LinieOcr>): LinieOcr? =
        castigator(linii).first.liniaMinutePreluareDeRecitit(linii)

    override fun minutePreluareInainteDeViteza(linii: List<LinieOcr>): BigDecimal? =
        castigator(linii).first.minutePreluareInainteDeViteza(linii)

    override fun ancoreSegment(linii: List<LinieOcr>): List<LinieOcr> = castigator(linii).first.ancoreSegment(linii)

    private fun castigator(linii: List<LinieOcr>): Pair<ParserOferta, ParsedOffer> {
        var best: Pair<ParserOferta, ParsedOffer>? = null
        for (set in seturi) {
            val o = set.parseaza(linii)
            if (best == null || campuri(o) > campuri(best.second)) best = set to o
            if (campuri(o) == CAMPURI) break
        }
        return checkNotNull(best)
    }

    private fun campuri(o: ParsedOffer) =
        listOf(o.price, o.pickupMinutes, o.tripMinutes, o.pickupDistance, o.tripDistance).count { it != null }

    private companion object {
        const val CAMPURI = 5
    }
}
