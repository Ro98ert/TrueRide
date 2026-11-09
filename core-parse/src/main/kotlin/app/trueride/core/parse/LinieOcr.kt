package app.trueride.core.parse

/** Cutia unei linii OCR, in pixelii ecranului. `sus` creste in jos, ca pe Android. */
data class Cutie(val stanga: Int, val sus: Int, val dreapta: Int, val jos: Int) {
    val inaltime: Int get() = jos - sus
}

/**
 * O linie OCR cu pozitia ei. Intrarea parserului: pe telefon vine din OCR,
 * in teste din fixture-urile anonimizate (D-025).
 *
 * [recitita] (D-299): lantul a citit linia inca o data, marita, cu aceleasi cifre, si a pus-o in locul primei citiri.
 * Il pune DOAR lantul (nu se deduce din text); doar pe aceasta cale parserul poate accepta forma
 * `pretFormaDupaRecitirePattern`.
 */
data class LinieOcr(val text: String, val cutie: Cutie, val recitita: Boolean = false)
