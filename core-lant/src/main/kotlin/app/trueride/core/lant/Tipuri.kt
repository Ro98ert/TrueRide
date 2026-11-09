package app.trueride.core.lant

import app.trueride.core.model.CostInput
import app.trueride.core.model.DistanceUnit
import app.trueride.core.model.Evaluation
import app.trueride.core.model.OfferField
import app.trueride.core.model.ParsedOffer
import app.trueride.core.model.Thresholds
import app.trueride.core.parse.LinieOcr

/** Din pachetul aplicatiei din prim-plan. NECUNOSCUTA = serviciul Accessibility nu e conectat. */
enum class PlatformaSofer { UBER, BOLT, NECUNOSCUTA }

enum class MotivEroare { NECITIBILA, ECRAN_PROTEJAT }

/**
 * Cele 4 stari din E6 (D-035, D-074): ce e pe ecran, nu ce face lantul.
 * `Gol` = cardul e scos de pe ecran, nu golit — si in timpul citirii unei oferte
 * cand pe ecran nu era niciun rezultat (lantul lucreaza in fundal, vezi [Etapa]).
 */
sealed interface StareCard {
    data object Gol : StareCard

    /** D-074: doar cercul, fara text si fara cifre, intre doua oferte inlantuite. */
    data class Incarcare(val platforma: PlatformaSofer) : StareCard
    data class Eroare(val platforma: PlatformaSofer, val motiv: MotivEroare) : StareCard
    data class Succes(
        val platforma: PlatformaSofer,
        val oferta: ParsedOffer,
        val evaluare: Evaluation,
    ) : StareCard
}

/** Ce are nevoie automatul din E3/E4. `cost` null = profil necompletat (necunoscut, nu zero). */
data class Setari(
    val cost: CostInput?,
    val praguri: Thresholds,
    val unitateImplicita: DistanceUnit = DistanceUnit.KM,
)

/**
 * Tot ce poate schimba cardul. `n` = numarul de ordine al schimbarii de ecran
 * care a produs evenimentul; un rezultat cu `n` depasit se ignora (invariantul 5).
 */
sealed interface Eveniment {
    /** `negru`: cadrul care a produs schimbarea e negru (FLAG_SECURE, D-048); D-225 pastreaza atunci mesajul. */
    data class EcranSchimbat(val n: Long, val platforma: PlatformaSofer, val negru: Boolean = false) : Eveniment

    /** Nu e `data class`: un `toString` generat ar scrie textul liniilor in orice log. */
    class NoduriCitite(val n: Long, val linii: List<LinieOcr>) : Eveniment {
        override fun toString() = "NoduriCitite(n=$n, linii=${linii.size})"
    }

    /** Idem [NoduriCitite]. */
    class OcrCitit(val n: Long, val linii: List<LinieOcr>) : Eveniment {
        override fun toString() = "OcrCitit(n=$n, linii=${linii.size})"
    }

    data class OcrEsuat(val n: Long) : Eveniment

    /**
     * D-394: a doua citire a cardului [idCard] s-a terminat ([linii] null = esec). Cardul nu are cum sa se schimbe din ea;
     * evenimentul doar duce rezultatul in coada, unde se ia verdictul. Nu e `data class`, ca [NoduriCitite]: `toString`
     * generat ar scrie textul liniilor.
     */
    class VerificareCitita(val idCard: Long, val linii: List<LinieOcr>?) : Eveniment {
        override fun toString() = "VerificareCitita(idCard=$idCard, linii=${linii?.size})"
    }
    data class CadruNegru(val n: Long) : Eveniment
    data object Inchis : Eveniment

    /**
     * Accessibility e conectat, dar in prim-plan nu e o aplicatie de sofer
     * (D-069). Cifrele unei oferte anterioare nu raman peste alta aplicatie
     * (invariantul 5). Diferit de [Inchis]: acolo soferul a inchis cardul.
     */
    data object AltaAplicatie : Eveniment

    data class SetariSchimbate(val setari: Setari) : Eveniment
    data object Oprit : Eveniment

    /**
     * Pixelii nu mai vin: ecran stins sau proiectie oprita de sistem la blocare (F-008, D-084).
     * Ca [Oprit], cardul devine Gol, iar o citire in curs nu mai reafiseaza cifre cand se
     * termina (invariantul 5). Diferit de [Oprit]: tura continua.
     */
    data object CapturaIntrerupta : Eveniment

    /**
     * Au trecut [LIMITA_CERC_MS] de cand a pornit cercul schimbarii [n] (D-074).
     * Emis de lumea din afara la cererea [Comanda.PornesteAsteptare]: automatul
     * nu tine ceas.
     */
    data class AsteptareExpirata(val n: Long) : Eveniment
}

/** Ce cere automatul lumii din afara. */
sealed interface Comanda {
    data class CitesteNoduri(val n: Long) : Comanda
    data class FaOcr(val n: Long) : Comanda

    /** Trimite [Eveniment.AsteptareExpirata] cu acelasi [n] peste [LIMITA_CERC_MS] (D-074). */
    data class PornesteAsteptare(val n: Long) : Comanda
}

/** D-074.3: cat sta cercul de incarcare pe ecran fara rezultatul urmator. */
const val LIMITA_CERC_MS = 3_000L

/** Ce rezultat asteapta automatul pentru `ultimulN`. GATA = niciunul. */
enum class Etapa { NODURI, OCR, GATA }

data class StareLant(
    val setari: Setari,
    val card: StareCard = StareCard.Gol,
    val ultimulN: Long = 0,
    val etapa: Etapa = Etapa.GATA,
    val platforma: PlatformaSofer = PlatformaSofer.NECUNOSCUTA,
    /**
     * `n`-ul schimbarii care a pus cercul pe ecran (D-074). Are sens doar cat
     * cardul e [StareCard.Incarcare]; schimbarile urmatoare nu il muta, deci
     * limita curge de la aparitia cercului, nu de la ultima schimbare.
     */
    val nCerc: Long = 0,
    /**
     * D-303: ultimul rezultat OCR a avut segment fara pret, pe o oferta noua; limita [nCerc]
     * da atunci [MotivEroare.NECITIBILA]. Se sterge la orice rezultat acceptat sau schimbare
     * de ecran (`incheie`, `EcranSchimbat`); `SetariSchimbate` o pastreaza.
     */
    val eroarePendinta: Boolean = false,
)

/**
 * [dezacord] (D-368): campurile cu valori cunoscute diferite intre citirea acceptata acum si cardul cu cifre pe care
 * a ramas pana la ea (aceeasi oferta, D-134). Doar nume de campuri; gol = nicio citire sau nicio diferenta.
 */
data class Pas(
    val stare: StareLant,
    val comenzi: List<Comanda> = emptyList(),
    val dezacord: List<OfferField> = emptyList(),
)
