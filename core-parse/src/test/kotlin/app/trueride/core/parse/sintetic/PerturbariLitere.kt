package app.trueride.core.parse.sintetic

import kotlin.random.Random

/**
 * Val 2, litere din `corectiiCifre` (l/I -> 1, O/o -> 0) pe pozitii pe care valul 1 nu le acoperea sistematic: dupa
 * ultima cifra a pretului, ca ultima cifra a minutelor, in mijlocul minutelor, in partea intreaga a km, doua litere
 * una langa alta. Asteptarea: valoarea adevarata (D-122..D-124, literele din harta se corecteaza). Doar Uber (OCR).
 */
internal object PerturbariLitere {

    /** Litera cu care OCR-ul poate inlocui cifra [cifra] (doar 0 si 1 au litere in harta). */
    private fun litera(cifra: Char, r: Random): Char = Litere.MAPATE.getValue(cifra).random(r)

    private fun pretUltimaCifra(pl: Plan, r: Random): Plan {
        val n = PerturbariValori.pretNou(pl, r.nextInt(100, 700) * 10 + r.nextInt(2))
        return n.cuPret(n.pretNum.dropLast(1) + litera(n.pretNum.last(), r), exact(n.v.pret))
    }

    private fun minuteUltimaCifra(pl: Plan, r: Random): Plan {
        val prima = r.nextBoolean()
        val m = r.nextInt(1, 5) * 10 + r.nextInt(2)
        val n = GeneratorSintetic.bazaDin(pl.familie, pl.v.cuMinute(prima, m, r))
        return n.pe(prima) { it.copy(min = "${m / 10}${litera(m.toString().last(), r)}") }
    }

    /**
     * Minute de 3 cifre cu litera in mijloc: doar pe CURSA. Preluarea reala are cel mult 25 min si de la 100 in sus e necunoscuta
     * (D-315, P-sim-3), deci „1l5” pe preluare nu are citire corecta de cerut. `nextBoolean` ramane consumat, ca restul secventei sa fie identic.
     */
    private fun minuteMijloc(pl: Plan, r: Random): Plan {
        r.nextBoolean()
        val prima = false
        val mijloc = r.nextInt(2)
        val m = 100 + mijloc * 10 + r.nextInt(10)
        val n = GeneratorSintetic.bazaDin(pl.familie, pl.v.cuMinute(prima, m, r))
        return n.pe(prima) { it.copy(min = "1${litera(('0' + mijloc), r)}${m % 10}") }
    }

    /** „(l.5 km)”, „(O.8 km)”: litera ia locul cifrei intregi 1 sau 0. */
    private fun kmIntreg(pl: Plan, r: Random): Plan {
        val prima = r.nextBoolean()
        val unu = r.nextBoolean()
        val n = PerturbariValori.peSegment(pl, prima, r.nextInt(2, 12), if (unu) r.nextInt(10, 20) else r.nextInt(1, 10))
        return n.pe(prima) { it.copy(km = litera(if (unu) '1' else '0', r) + it.km.drop(1)) }
    }

    /**
     * F-015 (D-122): minutul unic „1” e citit de OCR ca litera izolata („l min.”, „I min.”), tokenul intreg e litera.
     * `litera_mapata_minute` ajunge aici doar cand nimereste un segment de o cifra; aici modul de defect e la fiecare
     * varianta. Asteptarea: 1 minut exact (harta D-122 accepta un token format numai din litere din harta).
     */
    private fun minutIzolat(pl: Plan, r: Random): Plan {
        val prima = r.nextBoolean()
        val n = GeneratorSintetic.bazaDin(pl.familie, pl.v.cuMinute(prima, 1, r))
        return n.pe(prima) { it.copy(min = litera('1', r).toString()) }
    }

    private fun dublatePret(pl: Plan, r: Random): Plan {
        val n = PerturbariValori.pretNou(pl, r.nextInt(1000, 1200))
        val dubla = "" + litera(n.pretNum[0], r) + litera(n.pretNum[1], r)
        return n.cuPret(dubla + n.pretNum.drop(2), exact(n.v.pret))
    }

    private fun dublateMinute(pl: Plan, r: Random): Plan {
        val prima = r.nextBoolean()
        val m = 10 + r.nextInt(2)
        val n = GeneratorSintetic.bazaDin(pl.familie, pl.v.cuMinute(prima, m, r))
        return n.pe(prima) { it.copy(min = "" + litera('1', r) + litera(m.toString()[1], r)) }
    }

    private fun dublateKm(pl: Plan, r: Random): Plan {
        val prima = r.nextBoolean()
        val n = PerturbariValori.peSegment(pl, prima, r.nextInt(20, 46), r.nextInt(100, 120))
        return n.pe(prima) { it.copy(km = "" + litera(it.km[0], r) + litera(it.km[1], r) + it.km.drop(2)) }
    }

    val toate: List<Perturbare> = listOf(
        p("litera_pret_ultima_cifra", UBER) { pl, r -> pretUltimaCifra(pl, r) },
        p("litera_minute_ultima_cifra", UBER) { pl, r -> minuteUltimaCifra(pl, r) },
        p("litera_minute_mijloc", UBER) { pl, r -> minuteMijloc(pl, r) },
        p("litera_minut_izolat", UBER) { pl, r -> minutIzolat(pl, r) },
        p("litera_km_intreg", UBER) { pl, r -> kmIntreg(pl, r) },
        p("litere_dublate", UBER) { pl, r ->
            when (r.nextInt(3)) {
                0 -> dublatePret(pl, r)
                1 -> dublateMinute(pl, r)
                else -> dublateKm(pl, r)
            }
        },
    )
}
