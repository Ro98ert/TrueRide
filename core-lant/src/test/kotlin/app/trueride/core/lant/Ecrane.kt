package app.trueride.core.lant

import app.trueride.core.parse.Cutie
import app.trueride.core.parse.LinieOcr

/**
 * Ecrane sintetice in formatul Uber RO (vezi OfferParserTest din core-parse):
 * pretul ~115 px, restul ~45 px. Cifrele sunt inventate, nu vin de pe un
 * telefon — nu e text de oferta real (invariantul 2).
 */
object Ecrane {
    private fun linie(text: String, sus: Int, inaltime: Int = 45) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private val corp = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }

    /** 9,47 RON; preluare 4 min (1.5 km); cursa 9 min (4.5 km). */
    val ofertaCompleta: List<LinieOcr> = corp + listOf(
        linie("9,47 RON", sus = 1232, inaltime = 115),
        linie("4 min. (1.5 km)", sus = 1700),
        linie("9 min. (4.5 km)", sus = 1800),
    )

    /** Ca mai sus, dar timpul cursei e mascat, ca in 2 din cele 8 fixture-uri. */
    val ofertaFaraTimpCursa: List<LinieOcr> = corp + listOf(
        linie("9,47 RON", sus = 1232, inaltime = 115),
        linie("4 min. (1.5 km)", sus = 1700),
        linie("▢ min. (4.5 km)", sus = 1800),
    )

    /** D-368: aceeasi oferta ca [ofertaCompleta], citita cu alt pret (9,97 in loc de 9,47); restul identic. */
    val ofertaAltPret: List<LinieOcr> = corp + listOf(
        linie("9,97 RON", sus = 1232, inaltime = 115),
        linie("4 min. (1.5 km)", sus = 1700),
        linie("9 min. (4.5 km)", sus = 1800),
    )

    /** D-368: ca [ofertaCompleta], dar pretul si km cursei difera (doua campuri cunoscute in ambele citiri). */
    val ofertaAltPretSiKmCursa: List<LinieOcr> = corp + listOf(
        linie("9,97 RON", sus = 1232, inaltime = 115),
        linie("4 min. (1.5 km)", sus = 1700),
        linie("9 min. (4.9 km)", sus = 1800),
    )

    /** D-368.5: ca [ofertaCompleta], dar pretul, minutele de la preluare si km cursei difera (3 campuri = oferta noua). */
    val ofertaAltTreiCampuri: List<LinieOcr> = corp + listOf(
        linie("9,97 RON", sus = 1232, inaltime = 115),
        linie("6 min. (1.5 km)", sus = 1700),
        linie("9 min. (4.9 km)", sus = 1800),
    )

    /** Segmentele se citesc, pretul nu. */
    val ofertaFaraPret: List<LinieOcr> = corp + listOf(
        linie("4 min. (1.5 km)", sus = 1700),
        linie("9 min. (4.5 km)", sus = 1800),
    )

    /** D-130 (F-020): ecranul principal Uber cu castigul zilei sus, singura suma mare; fara segmente. */
    val ecranPrincipal: List<LinieOcr> = corp + linie("123,45 RON", sus = 150, inaltime = 100)

    /** Harta, meniu: nimic de oferta. */
    val faraOferta: List<LinieOcr> = corp
}
