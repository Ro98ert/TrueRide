package app.trueride.core.lant

import app.trueride.core.lant.DezacordCadre.MAX_CAMPURI_ACEEASI_OFERTA
import app.trueride.core.model.ParsedOffer

data class StareCursa(
    val ultimaOferta: ParsedOffer? = null,
    /** Oferta poate deveni cursa; false dupa numarare, expirare sau cand un drum era deja pe ecran. */
    val candidata: Boolean = false,
    val platforma: PlatformaSofer = PlatformaSofer.NECUNOSCUTA,
    val ofertaLaMs: Long = 0,
    /** Cand s-a vazut ultima oara ecranul de drum; dupa un refuz revine la fel, deci nu dovedeste nimic. */
    val ultimulDrumMs: Long? = null,
)

data class PasCursa(val stare: StareCursa, val ofertaNoua: Boolean, val cursaNumarata: Boolean)

/**
 * D-396.2: o cursa se numara o singura data, iar la nesiguranta nu se numara (o ratare costa o cursa gratuita,
 * o numarare in plus costa una platita). Functie pura: ceasul vine de la apelant ([acumMs]), ca in `AutomatCard`.
 * Nu afiseaza nimic, deci invariantul 5 nu se aplica; liniile ecranului nu intra aici, doar verdictul detectorului.
 */
object AutomatCursa {

    /** [drum]: true = drum, false = linii citite, alt ecran, null = pasul n-a avut linii (`EcranSchimbat`). */
    fun trateaza(
        s: StareCursa,
        card: StareCard,
        drum: Boolean?,
        platforma: PlatformaSofer,
        acumMs: Long,
        fereastraMs: Long,
    ): PasCursa {
        // 1. Oferta prea veche nu mai poate deveni cursa.
        var cur = if (acumMs - s.ofertaLaMs > fereastraMs) s.copy(candidata = false) else s
        var ofertaNoua = false
        if (card is StareCard.Succes) {
            val veche = cur.ultimaOferta
            // 2. Aceeasi definitie de „aceeasi oferta” ca D-368.5: cel mult doua campuri diferite, aceeasi platforma.
            val aceeasi = veche != null && card.platforma == cur.platforma &&
                DezacordCadre.campuri(veche, card.oferta).size <= MAX_CAMPURI_ACEEASI_OFERTA
            cur = if (aceeasi) {
                if (cur.candidata) cur.copy(ofertaLaMs = acumMs) else cur
            } else {
                ofertaNoua = true
                // 3. Un drum vazut chiar acum poate fi al ofertei dinainte, nu al acesteia: la nesiguranta nu numara.
                val drumRecent = s.ultimulDrumMs?.let { acumMs - it <= fereastraMs } ?: false
                StareCursa(card.oferta, !drumRecent, card.platforma, acumMs, s.ultimulDrumMs)
            }
        }
        // 4. Drumul confirma doar o candidata de pe aceeasi platforma. 5. Gol/Incarcare/Eroare si drum false/null nu sterg nimic.
        val numarata = drum == true && cur.candidata && platforma == cur.platforma
        if (drum == true) cur = cur.copy(candidata = cur.candidata && !numarata, ultimulDrumMs = acumMs)
        return PasCursa(cur, ofertaNoua, numarata)
    }
}
