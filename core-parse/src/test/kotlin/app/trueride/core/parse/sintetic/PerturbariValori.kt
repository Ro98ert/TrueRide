package app.trueride.core.parse.sintetic

import java.math.BigDecimal
import kotlin.random.Random

/**
 * Val 2, valori la margini si forme de scriere: pret de 3-4 cifre (cu / fara separator de mii), minute peste 60 si
 * forma cu ore, km peste 100 sau sub 1, spatii in interiorul numarului, punct dupa unitate, liniute Unicode in
 * interval. Asteptarea se scrie din adevarul inventat: exact cand informatia se pastreaza, „exact sau necunoscut”
 * cand citirea e ambigua (separator de mii, spatiu in numar, ore), niciodata alta valoare (invariantele 5 si 6).
 */
internal object PerturbariValori {

    /** Aceleasi plan, cu pretul schimbat (textul si asteptarea se reconstruiesc din valori). */
    internal fun pretNou(pl: Plan, centi: Int): Plan =
        GeneratorSintetic.bazaDin(pl.familie, pl.v.copy(pret = BigDecimal.valueOf(centi.toLong(), 2)))

    /** Segmentul [prima] cu minute si km (in zecimi) date; km nu mai sunt derivati din minute, pretul urmeaza km (D-329.1). */
    internal fun peSegment(pl: Plan, prima: Boolean, min: Int, kmZecimi: Int): Plan {
        val km = BigDecimal.valueOf(kmZecimi.toLong(), 1)
        val v = if (prima) pl.v.copy(minPre = min, kmPre = km) else pl.v.copy(minCursa = min, kmCursa = km)
        return GeneratorSintetic.bazaDin(pl.familie, with(GeneratorSintetic) { v.inPlaja() })
    }

    /**
     * Un pret de 3 cifre cere o cursa lunga (D-329.1): 40-100 km la 35-55 km/h (44-172 min, sub cele 180 ale lui
     * `minute_peste_60`), apoi pretul de cel putin 100 lei din plaja lei/km a km totali.
     */
    private fun pretTreiCifre(pl: Plan, r: Random): Plan {
        val kmZecimi = r.nextInt(400, 1001)
        val viteza = r.nextInt(35, 56)
        val n = peSegment(pl, false, (kmZecimi * 6 + viteza - 1) / viteza, kmZecimi)
        val totalZecimi = (n.v.kmPre + n.v.kmCursa).movePointRight(1).toInt()
        return pretNou(n, r.nextInt(maxOf(10000, totalZecimi * 14), totalZecimi * 53 + 1))
    }

    private fun cuMinute(pl: Plan, prima: Boolean, min: Int, r: Random): Plan =
        GeneratorSintetic.bazaDin(pl.familie, pl.v.cuMinute(prima, min, r))

    /** Pret 1000.00-9999.99 cu [sepMii] intre grupe („1.234,56”, „1,234.56”, „1 234,56”): citire ambigua, deci exact sau necunoscut. */
    private fun cuMii(pl: Plan, r: Random, sepMii: String): Plan {
        val n = pretNou(pl, r.nextInt(100000, 1000000))
        val sep = Formate.separator(n.familie).toString()
        val (intreg, zec) = n.pretNum.split(sep)
        return n.cuPret(intreg.reversed().chunked(3).joinToString(sepMii).reversed() + sep + zec, oricare(n.v.pret))
    }

    /** „D h DD min”: regulile cunosc doar „h ” inaintea minutelor (lookbehind), nu si calculul orelor. */
    private fun oreSiMinute(pl: Plan, r: Random, minute: IntRange): Plan {
        val prima = r.nextBoolean()
        val ore = r.nextInt(1, 3)
        val total = 60 * ore + r.nextInt(minute.first, minute.last + 1)
        return cuMinute(pl, prima, total, r).pe(prima) { it.copy(min = "$ore h ${total - 60 * ore}") }
            .seteaza(campMin(prima) to oricare(BigDecimal(total)))
    }

    private fun kmSubUnu(pl: Plan, r: Random): Plan {
        val prima = r.nextBoolean()
        // Viteza din plaja ofertei de baza (14-55 km/h, ca GeneratorSintetic.km), plafonata sub 1 km (D-329.4).
        val min = r.nextInt(1, 4)
        val n = peSegment(pl, prima, min, minOf(9, maxOf(2, min * r.nextInt(14, 56) / 6)))
        return if (r.nextBoolean()) n.pe(prima) { it.copy(km = it.km.replace('.', ',')) } else n
    }

    private fun spatiuInKm(pl: Plan, r: Random): Plan {
        val prima = r.nextBoolean()
        val m = r.nextInt(20, 46)
        val n = peSegment(pl, prima, m, r.nextInt(100, m * 55 / 6 + 1))
        return n.pe(prima) { it.copy(km = it.km.take(1) + " " + it.km.drop(1)) }
            .seteaza(campKm(prima) to oricare(n.kmAdevarat(prima)))
    }

    private fun spatiuInMinute(pl: Plan, r: Random): Plan {
        val prima = r.nextBoolean()
        val m = r.nextInt(10, 60)
        return cuMinute(pl, prima, m, r).pe(prima) { it.copy(min = m.toString().take(1) + " " + m.toString().drop(1)) }
            .seteaza(campMin(prima) to oricare(BigDecimal(m)))
    }

    private fun intervalUnicode(pl: Plan, r: Random): Plan {
        val lo = r.nextInt(2, 10)
        val hi = lo + r.nextInt(1, 5)
        val liniuta = listOf("–", "—", "−", " – ", " — ", " − ").random(r)
        return cuMinute(pl, true, lo, r).pe(true) { it.copy(min = "$lo$liniuta$hi") }
            .seteaza(Camp.MIN_PRELUARE to oricare(BigDecimal(lo), BigDecimal(hi)))
    }

    /** Punctul vine dupa unitate („km.”) sau dupa paranteza („km).”). */
    private fun punctDupaUnitate(pl: Plan, r: Random): Plan =
        if (r.nextBoolean()) pl.cuSegmente { it.copy(unit = "km.") } else pl.cuSegmente { it.copy(inchide = ").") }

    val toate: List<Perturbare> = listOf(
        p("pret_trei_cifre") { pl, r -> pretTreiCifre(pl, r) },
        // D-329.1: niciun drum real nu face 1000+ lei in plaja lei/km, deci banda poate face pretul necunoscut.
        p("pret_mii_fara_separator") { pl, r -> pretNou(pl, r.nextInt(100000, 1000000)).let { it.seteaza(Camp.PRET to oricare(it.v.pret)) } },
        p("pret_mii_separator") { pl, r -> cuMii(pl, r, if (pl.familie == Familie.UBER_EN) "," else ".") },
        p("pret_mii_spatiu") { pl, r -> cuMii(pl, r, listOf(" ", NB).random(r)) },
        // D-080: o singura zecimala la un pret de 3 cifre („123,4”): forma pretului cere doi bani, deci necunoscut.
        p("pret_o_zecimala_mare") { pl, r -> pretNou(pl, r.nextInt(10000, 100000)).let { n -> n.cuPret(n.pretNum.dropLast(1), NECUNOSCUT) } },
        // D-080 / D-305: un intreg de 3-4 cifre fara recitire ramane necunoscut (forma pretului cere doi bani).
        p("pret_intreg_mare_nerecitit") { pl, r -> pl.cuPret(r.nextInt(100, 10000).toString(), NECUNOSCUT) },
        // Preluarea reala are cel mult 25 min (D-315, P-sim-3): pe preluare minutele sunt 60-99, 100+ doar pe cursa (111-112 min reale).
        p("minute_peste_60") { pl, r -> r.nextBoolean().let { prima -> cuMinute(pl, prima, r.nextInt(60, if (prima) 100 else 181), r) } },
        p("ore_si_minute") { pl, r -> oreSiMinute(pl, r, 10..59) },
        p("ore_si_minute_1_cifra") { pl, r -> oreSiMinute(pl, r, 1..9) },
        // Viteza ramane sub 150 km/h (D-148): minute 80-180, km 100.0-199.9 (la 80 min limita e 201 km). Pe preluare minutele
        // sunt 80-99, fiindca preluarea reala are cel mult 25 min (D-315, P-sim-3); 100+ doar pe cursa.
        p("km_peste_100") { pl, r -> r.nextBoolean().let { prima -> peSegment(pl, prima, r.nextInt(80, if (prima) 100 else 181), r.nextInt(1000, 2000)) } },
        p("km_sub_unu") { pl, r -> kmSubUnu(pl, r) },
        // Spatii in interiorul numarului (OCR): doar pe Uber, Bolt vine din noduri cu text exact.
        p("spatiu_in_pret", UBER) { pl, r ->
            val n = pretNou(pl, r.nextInt(1000, 10000))
            n.cuPret(n.pretNum.take(1) + listOf(" ", NB).random(r) + n.pretNum.drop(1), oricare(n.v.pret))
        },
        p("spatiu_in_minute", UBER) { pl, r -> spatiuInMinute(pl, r) },
        p("spatiu_in_km", UBER) { pl, r -> spatiuInKm(pl, r) },
        p("punct_final_pret", UBER) { pl, _ -> pl.copy(pret = pl.pret + ".") },
        p("punct_dupa_unitate", UBER) { pl, r -> punctDupaUnitate(pl, r) },
        p("interval_minute_liniuta_unicode", UBER) { pl, r -> intervalUnicode(pl, r) },
    )
}
