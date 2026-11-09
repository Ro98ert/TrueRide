package app.trueride.core.lant

import app.trueride.core.model.OfferField
import app.trueride.core.model.ParsedOffer
import app.trueride.core.parse.BOLT_RO_OCR_VERIFICARE
import app.trueride.core.parse.Cutie
import app.trueride.core.parse.LinieOcr
import app.trueride.core.parse.OfferParser
import app.trueride.core.parse.ParserOferta
import app.trueride.core.parse.ParserPeLimbi
import app.trueride.core.parse.UBER_EN_V1
import app.trueride.core.parse.UBER_RO_V1
import java.math.BigDecimal

enum class RezultatVerificare { POTRIVIT, DIFERIT, NEVERIFICABIL }

enum class MotivNeverificabil { CAMP_NECITIT, POZITIE, OFERTA_SCHIMBATA, OCR_OCUPAT, NEGRU, ESEC }

/**
 * D-394: rezultatul comparatiei. NU e data class: `toString` generat ar scrie [perechi], adica cifre de oferta
 * (invariantul 2). Aici iese doar rezultatul, motivul si numele campurilor.
 */
class Comparatie(
    val rezultat: RezultatVerificare,
    val motiv: MotivNeverificabil?,
    val diferite: List<OfferField>,
    /** Doar campurile diferite: valoarea din prima citire, apoi din a doua. */
    val perechi: Map<OfferField, Pair<BigDecimal, BigDecimal>>,
) {
    override fun toString() = "Comparatie($rezultat, $motiv, $diferite)"
}

/**
 * D-394: compara prima citire (cardul) cu a doua citire OCR a aceleiasi zone. Textul nou intra in CUTIILE vechi ale
 * liniilor cardului, ca parserul sa vada aceleasi inaltimi (mediana liniilor, `pretInaltimeMinimaRelativa`) si sa
 * difere doar cifrele. Functie pura; nu corecteaza cardul, doar spune daca cele doua citiri se potrivesc.
 */
class ComparaCitiri(private val parsere: Map<PlatformaSofer, ParserOferta>) {

    fun compara(
        platforma: PlatformaSofer,
        linii: List<LinieOcr>,
        liniiCard: List<LinieOcr>,
        prima: ParsedOffer,
        recitite: List<LinieOcr>,
    ): Comparatie {
        val parser = parsere[platforma] ?: return neverificabil(MotivNeverificabil.ESEC)
        val potriviri = liniiCard.associateWith { liniiDinCutie(it.cutie, recitite) }
        if (potriviri.any { (card, gasite) -> gasite.isNotEmpty() && deplasata(card.cutie, gasite) }) {
            return neverificabil(MotivNeverificabil.POZITIE)
        }
        val inlocuiri = potriviri.filterValues { it.isNotEmpty() }
            .mapValues { (card, gasite) -> LinieOcr(gasite.joinToString(" ") { it.text }, card.cutie) }
        val aDoua = parser.parseaza(linii.map { inlocuiri[it] ?: it })
        return rezultat(prima, aDoua, nerecitita = potriviri.values.any { it.isEmpty() })
    }

    private fun rezultat(prima: ParsedOffer, aDoua: ParsedOffer, nerecitita: Boolean): Comparatie {
        val diferite = DezacordCadre.campuri(prima, aDoua)
        if (diferite.isNotEmpty()) {
            val perechi = diferite.associateWith { requireNotNull(valoare(prima, it)) to requireNotNull(valoare(aDoua, it)) }
            return Comparatie(RezultatVerificare.DIFERIT, null, diferite, perechi)
        }
        val necitit = nerecitita || OfferField.entries.any { valoare(aDoua, it) == null }
        return if (necitit) neverificabil(MotivNeverificabil.CAMP_NECITIT) else Comparatie(RezultatVerificare.POTRIVIT, null, emptyList(), emptyMap())
    }

    /** Liniile recitite cu centrul vertical in [cutie] si suprapunere orizontala, de sus in jos, apoi de la stanga. */
    private fun liniiDinCutie(cutie: Cutie, recitite: List<LinieOcr>): List<LinieOcr> =
        recitite.filter { r ->
            val centru = (r.cutie.sus + r.cutie.jos) / 2.0
            centru >= cutie.sus && centru <= cutie.jos &&
                minOf(r.cutie.dreapta, cutie.dreapta) > maxOf(r.cutie.stanga, cutie.stanga)
        }.sortedWith(compareBy({ it.cutie.sus }, { it.cutie.stanga }))

    /** Centrul, nu marginea de sus: la Bolt cutia e a nodului, mai inalta decat textul. Prag: un sfert din inaltimea liniei. */
    private fun deplasata(cutie: Cutie, gasite: List<LinieOcr>): Boolean {
        val centruNou = (gasite.minOf { it.cutie.sus } + gasite.maxOf { it.cutie.jos }) / 2.0
        val centruVechi = (cutie.sus + cutie.jos) / 2.0
        return Math.abs(centruNou - centruVechi) > cutie.inaltime / 4.0
    }

    private fun valoare(o: ParsedOffer, camp: OfferField): BigDecimal? = when (camp) {
        OfferField.PRICE -> o.price
        OfferField.PICKUP_MINUTES -> o.pickupMinutes
        OfferField.TRIP_MINUTES -> o.tripMinutes
        OfferField.PICKUP_DISTANCE -> o.pickupDistance
        OfferField.TRIP_DISTANCE -> o.tripDistance
    }

    companion object {
        /** Ca `di/ModulLant` pentru Uber si NECUNOSCUTA; Bolt cu setul de reguli al citirii OCR. */
        val PRODUS: ComparaCitiri by lazy {
            val uber = ParserPeLimbi(listOf(OfferParser(UBER_RO_V1), OfferParser(UBER_EN_V1)))
            ComparaCitiri(
                mapOf(
                    PlatformaSofer.UBER to uber,
                    PlatformaSofer.NECUNOSCUTA to uber,
                    PlatformaSofer.BOLT to OfferParser(BOLT_RO_OCR_VERIFICARE),
                ),
            )
        }

        fun neverificabil(motiv: MotivNeverificabil) = Comparatie(RezultatVerificare.NEVERIFICABIL, motiv, emptyList(), emptyMap())
    }
}
