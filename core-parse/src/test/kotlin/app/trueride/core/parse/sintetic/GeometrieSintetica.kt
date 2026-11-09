package app.trueride.core.parse.sintetic

import app.trueride.core.parse.Cutie
import app.trueride.core.parse.LinieOcr

/**
 * Ajutoare pentru perturbarile de geometrie (val 2): casete sparte, dublate, mutate pe alte coloane. Liniile se gasesc
 * dupa text (pretul si fiecare segment au text unic in oferta), nu dupa pozitie, ca sa nu depinda de randare.
 */
internal fun LinieOcr.deplasata(dx: Int, dy: Int): LinieOcr =
    copy(cutie = Cutie(cutie.stanga + dx, cutie.sus + dy, cutie.dreapta + dx, cutie.jos + dy))

/**
 * Imparte linia in doua casete pe acelasi rand: prima ia [taietura] caractere si latimea proportionala, a doua incepe
 * la [gol] px dupa ea. Textele se taie la margini (OCR-ul nu pune spatiul la capatul unei casete).
 */
internal fun LinieOcr.spartaLa(taietura: Int, gol: Int = 6): List<LinieOcr> {
    val latime = cutie.dreapta - cutie.stanga
    val mijloc = cutie.stanga + latime * taietura / text.length
    return listOf(
        copy(text = text.substring(0, taietura).trim(), cutie = cutie.copy(dreapta = mijloc)),
        copy(text = text.substring(taietura).trim(), cutie = cutie.copy(stanga = mijloc + gol)),
    )
}

internal fun <T> List<T>.inlocuitLa(i: Int, noi: List<T>): List<T> = take(i) + noi + drop(i + 1)

internal fun List<LinieOcr>.indicePret(pl: Plan): Int = indexOfFirst { it.text == pl.pret }

/** Preluarea e prima aparitie a textului ei, cursa ultima (pe Bolt cele doua pot avea acelasi text). */
internal fun List<LinieOcr>.indiceSegment(pl: Plan, prima: Boolean): Int =
    if (prima) indexOfFirst { it.text == pl.pre.text(pl.familie) } else indexOfLast { it.text == pl.cu.text(pl.familie) }

/** Toate campurile de segment: „exact sau necunoscut” (motorul nu poate decide care segment e care, sau nu vede ancora). */
internal fun Plan.segmenteOricare(): Plan = seteaza(
    Camp.MIN_PRELUARE to oricare(minAdevarat(true)), Camp.KM_PRELUARE to oricare(kmAdevarat(true)),
    Camp.MIN_CURSA to oricare(minAdevarat(false)), Camp.KM_CURSA to oricare(kmAdevarat(false)),
)

/** Aplica [f] liniei segmentului [prima]; restul listei ramane neschimbat. */
internal fun List<LinieOcr>.cuSegmentul(pl: Plan, prima: Boolean, f: (LinieOcr) -> List<LinieOcr>): List<LinieOcr> =
    indiceSegment(pl, prima).let { i -> inlocuitLa(i, f(this[i])) }
