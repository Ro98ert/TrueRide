package app.trueride.core.parse

/** Recunoaste ecranul de drum spre pasager. Cauta linie cu linie, fara regex cu grupuri repetate peste tot ecranul. */
class ClasificatorEcranCursa(seturi: List<ReguliEcranCursa>) {

    private class Compilat(val semnaturi: List<Regex>, val excluderi: List<Regex>)

    private val compilate = seturi.filter { it.semnaturi.isNotEmpty() }.map { set ->
        Compilat(set.semnaturi.map(::Regex), set.excluderi.map(::Regex))
    }

    fun esteDrumSprePasager(linii: List<LinieOcr>): Boolean = compilate.any { set ->
        set.semnaturi.all { apare(it, linii) } && set.excluderi.none { apare(it, linii) }
    }

    private fun apare(tipar: Regex, linii: List<LinieOcr>) = linii.any { tipar.containsMatchIn(it.text) }
}
