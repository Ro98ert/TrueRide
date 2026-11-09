package app.trueride.core.parse.sintetic

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.random.Random

/**
 * Perturbarile corpusului. Pentru fiecare, asteptarea e scrisa de mana din adevarul inventat si din decizii, nu din
 * ce da parserul: valoare exacta cand informatia se pastreaza (NBSP, litere din `corectiiCifre`, randuri in plus),
 * `Necunoscut` cand se pierde (separator zecimal, litera nemapata, taiere), `Oricare` cand decizia nu spune
 * (doua oferte, interval de minute). Invariantele 5 si 6: nicio alta valoare nu e acceptata.
 */
object PerturbariSintetice {
    val TOATE: List<Perturbare> = PerturbariPret.toate + PerturbariSegment.toate + PerturbariRanduri.toate + PerturbariTaiere.toate +
        PerturbariValori.toate + PerturbariGeometrie.toate + PerturbariLitere.toate + PerturbariBolt.toate +
        PerturbariInventateCampuri.toate + PerturbariInventateOferta.toate
}

internal object PerturbariPret {
    private fun intreg(r: Random, de: Int, pana: Int) = r.nextInt(de, pana).toString()

    val toate: List<Perturbare> = listOf(
        p("baza") { pl, _ -> pl },
        // D-299.4 (RO: NBSP inainte de RON), D-299.9 (EN: dupa RON); Bolt: spatiu obisnuit in loc de NBSP.
        p("nbsp_pret") { pl, _ ->
            pl.copy(pret = when (pl.familie) {
                Familie.UBER_RO -> pl.pret.replace(" RON", "${NB}RON")
                Familie.UBER_EN -> pl.pret.replace("RON", "RON$NB")
                Familie.BOLT_RO -> pl.pret.replace(NB, " ")
            })
        },
        // D-080: separatorul pierdut/dublat/taiat -> pret necunoscut; punctul nu se ghiceste.
        p("pret_fara_separator") { pl, _ -> pl.cuPret(pl.pretNum.filter(Char::isDigit), NECUNOSCUT) },
        p("pret_fara_separator_recitit", UBER) { pl, _ -> pl.cuPret(pl.pretNum.filter(Char::isDigit), NECUNOSCUT, recitita = true) },
        p("pret_separator_dublat") { pl, _ ->
            val s = Formate.separator(pl.familie).toString()
            pl.cuPret(pl.pretNum.replace(s, s + s), NECUNOSCUT)
        },
        p("pret_separator_alternativ") { pl, _ ->
            pl.cuPret(pl.pretNum.replace(Formate.separator(pl.familie), Formate.alternativ(pl.familie)), exact(pl.v.pret))
        },
        p("pret_un_ban") { pl, _ -> pl.cuPret(pl.pretNum.dropLast(1), NECUNOSCUT) },
        p("pret_cifra_in_coada", UBER) { pl, r -> pl.copy(pret = pl.pret + " ${r.nextInt(2, 9)}") },
        // D-305 (RO) / D-299 (EN): pret intreg de exact 2 cifre, doar pe linia recitita.
        p("pret_intreg_nerecitit") { pl, r -> intreg(r, 10, 100).let { pl.cuPret(it, NECUNOSCUT) } },
        p("pret_intreg_recitit", UBER) { pl, r -> intreg(r, 10, 100).let { pl.cuPret(it, exact(it), recitita = true) } },
        p("pret_intreg_1_cifra_recitit", UBER) { pl, r -> pl.cuPret(intreg(r, 1, 10), NECUNOSCUT, recitita = true) },
        p("pret_intreg_3_cifre_recitit", UBER) { pl, r -> pl.cuPret(intreg(r, 100, 1000), NECUNOSCUT, recitita = true) },
        // D-299.6: un intreg obtinut prin corectarea unei litere ramane necunoscut chiar recitit.
        p("pret_intreg_corectat_recitit", UBER) { pl, r ->
            val num = if (r.nextBoolean()) "${r.nextInt(1, 10)}${listOf('O', 'o').random(r)}" else "${listOf('l', 'I').random(r)}${r.nextInt(0, 10)}"
            pl.cuPret(num, NECUNOSCUT, recitita = true)
        },
        // D-124: litera din harta se corecteaza; litera din afara hartii lasa pretul necunoscut (cifra lipsa nu se ghiceste).
        p("litera_mapata_pret", UBER) { pl, r -> Litere.mapata(pl.pretNum, r).let { (adev, pert) -> pl.cuPret(pert, exact(adev)) } },
        p("litera_nemapata_pret_prima_cifra", UBER) { pl, r -> pl.cuPret(Litere.nemapata(pl.pretNum, r, prima = true), NECUNOSCUT) },
        p("litera_nemapata_pret_alta_cifra", UBER) { pl, r -> pl.cuPret(Litere.nemapata(pl.pretNum, r, prima = false), NECUNOSCUT) },
        p("litera_in_loc_de_separator", UBER) { pl, r ->
            val litera = (Litere.MAPATE.values.flatten() + Litere.NEMAPATE.toList()).random(r)
            pl.cuPret(pl.pretNum.replace(Formate.separator(pl.familie), litera), NECUNOSCUT)
        },
    )
}

internal object PerturbariSegment {
    private val UNITATI = listOf("Km" to "Km", "kn" to "kn", "krn" to "krn", "k_m" to "k m", "Km_punct" to "Km.")

    /** Bolt citeste textul exact din noduri: unitatea gresita nu apare acolo, deci nu se cere citire (necunoscut e ok). */
    private fun unitate(id: String, unit: String) = p("unitate_$id") { pl, _ ->
        val schimbat = pl.cuSegmente { it.copy(unit = unit) }
        if (pl.familie == Familie.BOLT_RO) {
            schimbat.seteaza(Camp.KM_PRELUARE to oricare(pl.v.kmPre), Camp.KM_CURSA to oricare(pl.v.kmCursa))
        } else {
            schimbat
        }
    }

    /** Un `D.D` fara punct: ex. 71 in loc de 7.1; zecimi, fara multipli de 10 (cifra zecimala nenula). */
    private fun zecimi(r: Random, de: Int): Int = (de..29).filter { it % 10 != 0 }.random(r)

    val toate: List<Perturbare> = listOf(
        p("nbsp_segment") { pl, _ ->
            pl.cuSegmente { if (pl.familie == Familie.BOLT_RO) it.copy(spMin = NB, spKm = NB, sepAncora = " ") else it.copy(spMin = NB, spKm = NB) }
        },
        p("km_cu_virgula") { pl, _ -> pl.cuSegmente { it.copy(km = it.km.replace('.', ',')) } },
        p("km_intreg") { pl, _ ->
            fun rotund(k: BigDecimal) = k.setScale(0, RoundingMode.HALF_UP).max(BigDecimal.ONE)
            GeneratorSintetic.bazaDin(pl.familie, pl.v.copy(kmPre = rotund(pl.v.kmPre), kmCursa = rotund(pl.v.kmCursa)))
        },
        p("km_punct_dublat") { pl, r ->
            val prima = r.nextBoolean()
            pl.pe(prima) { it.copy(km = it.km.replace(".", "..")) }.seteaza(campKm(prima) to NECUNOSCUT)
        },
        // D-148: punct pierdut cu viteza imposibila -> km necunoscuti.
        p("km_punct_pierdut_imposibil") { pl, r ->
            val t = zecimi(r, 21)
            val m = r.nextInt(3, 6)
            GeneratorSintetic.bazaDin(pl.familie, pl.v.copy(minPre = m, kmPre = BigDecimal.valueOf(t.toLong(), 1)))
                .pe(true) { it.copy(km = t.toString()) }.seteaza(Camp.KM_PRELUARE to NECUNOSCUT)
        },
        // Aceeasi pierdere, dar viteza ramane sub 150 km/h: D-148 nu o poate prinde (limita cunoscuta a regulii).
        p("km_punct_pierdut_plauzibil") { pl, r ->
            val t = zecimi(r, 12)
            val m = (2 * t + 4) / 5 + r.nextInt(0, 3)
            GeneratorSintetic.bazaDin(pl.familie, pl.v.copy(minPre = m, kmPre = BigDecimal.valueOf(t.toLong(), 1)))
                .pe(true) { it.copy(km = t.toString()) }.seteaza(Camp.KM_PRELUARE to NECUNOSCUT)
        },
        p("unitate_mi") { pl, _ ->
            pl.cuSegmente { it.copy(unit = "mi") }.seteaza(Camp.KM_PRELUARE to NECUNOSCUT, Camp.KM_CURSA to NECUNOSCUT)
        },
        p("min_fara_spatiu") { pl, _ -> pl.cuSegmente { it.copy(spMin = "") } },
        p("min_fara_punct", UBER) { pl, _ -> pl.cuSegmente { it.copy(minTxt = "min") } },
        p("min_cu_virgula", setOf(Familie.UBER_RO)) { pl, _ -> pl.cuSegmente { it.copy(minTxt = "min,") } },
        // D-122/D-123: litera din harta lipita de cifrele de minute / km se corecteaza.
        p("litera_mapata_minute", UBER) { pl, r ->
            val prima = r.nextBoolean()
            val (adev, pert) = Litere.mapata(pl.minAdevarat(prima).toPlainString(), r)
            GeneratorSintetic.bazaDin(pl.familie, pl.v.cuMinute(prima, adev.toInt(), r)).pe(prima) { it.copy(min = pert) }
        },
        p("litera_mapata_km", UBER) { pl, r ->
            val prima = r.nextBoolean()
            val (adev, pert) = Litere.mapata(pl.kmAdevarat(prima).toPlainString(), r)
            pl.pe(prima) { it.copy(km = pert) }.seteaza(campKm(prima) to exact(adev))
        },
        // O litera din afara hartii pe prima cifra a unui numar de minute de 2 cifre: cifra pierduta nu se ghiceste.
        p("litera_nemapata_minute_prima_cifra", UBER) { pl, r -> literaNemapataMinute(pl, r, prima = true) },
        p("litera_nemapata_minute_alta_cifra", UBER) { pl, r -> literaNemapataMinute(pl, r, prima = false) },
        p("litera_nemapata_km", UBER) { pl, r ->
            val pr = r.nextBoolean()
            pl.pe(pr) { it.copy(km = Litere.nemapata(it.km, r, prima = r.nextBoolean())) }.seteaza(campKm(pr) to NECUNOSCUT)
        },
        p("interval_minute_in_segment", UBER) { pl, r ->
            val lo = r.nextInt(2, 10)
            val hi = lo + r.nextInt(1, 5)
            GeneratorSintetic.bazaDin(pl.familie, pl.v.cuMinute(true, lo, r)).pe(true) { it.copy(min = "$lo-$hi") }
                .seteaza(Camp.MIN_PRELUARE to oricare(BigDecimal(lo), BigDecimal(hi)))
        },
    ) + UNITATI.map { (id, u) -> unitate(id, u) }

    private fun literaNemapataMinute(pl: Plan, r: Random, prima: Boolean): Plan {
        val pr = r.nextBoolean()
        val m = r.nextInt(10, 46)
        return GeneratorSintetic.bazaDin(pl.familie, pl.v.cuMinute(pr, m, r))
            .pe(pr) { it.copy(min = Litere.nemapata(m.toString(), r, prima)) }.seteaza(campMin(pr) to NECUNOSCUT)
    }
}

internal object PerturbariRanduri {
    private fun bonus(pl: Plan, r: Random): String {
        val b = Formate.numarPret(pl.familie, BigDecimal.valueOf(r.nextInt(200, 3000).toLong(), 2))
        return when (pl.familie) {
            Familie.UBER_RO -> "+$b RON ▢"
            Familie.UBER_EN -> "+RON$b"
            Familie.BOLT_RO -> "+$b${NB}lei"
        }
    }

    private fun altPret(pl: Plan, r: Random): BigDecimal = BigDecimal.valueOf(r.nextInt(300, 3000).toLong(), 2)

    private fun segmenteNecunoscute(pl: Plan): Plan = pl.seteaza(
        Camp.MIN_PRELUARE to NECUNOSCUT, Camp.KM_PRELUARE to NECUNOSCUT, Camp.MIN_CURSA to NECUNOSCUT, Camp.KM_CURSA to NECUNOSCUT,
    )

    val toate: List<Perturbare> = listOf(
        p("rand_masca") { pl, _ -> pl.copy(dupaPret = listOf("▢ ▢"), dupaCursa = listOf("▢ ▢ ▢")) },
        p("rand_ora") { pl, _ -> pl.copy(dupaPret = listOf("Oră"), dupaCursa = listOf("Oră")) },
        p("rand_numar_fara_unitate") { pl, r -> pl.copy(dupaCursa = listOf("${r.nextInt(10, 99)} ▢")) },
        // Tinta 1 (F-044): un rand „DD min <text>” sub cursa NU e ancora (segmentAncoraPattern cere „(”).
        p("rand_DD_min_text_sub_cursa") { pl, r -> pl.copy(dupaCursa = listOf("${r.nextInt(2, 30)} min ▢ ▢")) },
        p("rand_interval_minute", UBER) { pl, r ->
            val lo = r.nextInt(2, 10)
            pl.copy(dupaCursa = listOf("$lo-${lo + r.nextInt(1, 5)} min ▢"))
        },
        p("suma_mica_in_plus") { pl, r -> pl.copy(dupaCursa = listOf(Formate.pret(pl.familie, Formate.numarPret(pl.familie, altPret(pl, r))))) },
        // O a treia ancora: motorul nu ghiceste care e care (I4), deci toate campurile de segment sunt necunoscute.
        p("a_treia_ancora") { pl, r ->
            val m = r.nextInt(2, 20)
            val a = Seg.baza(pl.familie, m, GeneratorSintetic.km(m, r), prima = false).text(pl.familie)
            pl.copy(dupaCursa = listOf(a)).seteaza(
                Camp.MIN_PRELUARE to oricare(BigDecimal(pl.v.minPre)), Camp.KM_PRELUARE to oricare(pl.v.kmPre),
                Camp.MIN_CURSA to oricare(BigDecimal(pl.v.minCursa)), Camp.KM_CURSA to oricare(pl.v.kmCursa),
            )
        },
        p("ordine_amestecata") { pl, _ -> pl.copy(amesteca = true) },
        p("pret_sub_segmente") { pl, _ -> pl.copy(pretSub = true) },
        p("bonus_mai_mic") { pl, r -> pl.copy(bonus = bonus(pl, r)) },
        p("bonus_mai_inalt") { pl, r -> pl.copy(bonus = bonus(pl, r), bonusMare = true) },
        p("bonus_en_per_unitate", setOf(Familie.UBER_EN)) { pl, r -> pl.copy(bonus = "+RON ${r.nextInt(5, 40)}/▢") },
        // Invariantul 5: doua oferte -> 4 ancore -> niciun segment; pretul e al uneia dintre ele sau necunoscut.
        p("doua_oferte") { pl, r ->
            val a = GeneratorSintetic.baza(pl.familie, r)
            segmenteNecunoscute(pl.copy(secunda = a)).seteaza(Camp.PRET to oricare(pl.v.pret, a.v.pret))
        },
        p("doua_sume_pe_o_linie") { pl, r ->
            val alta = altPret(pl, r)
            val a = Formate.numarPret(pl.familie, alta)
            val linie = when (pl.familie) {
                Familie.UBER_RO -> "* $a ▢ ${pl.pret}"
                Familie.UBER_EN -> "$a ${pl.pret}"
                Familie.BOLT_RO -> "$a ▢ ${pl.pret}"
            }
            pl.copy(pret = linie).seteaza(Camp.PRET to oricare(pl.v.pret, alta))
        },
    )
}

internal object PerturbariTaiere {
    /** Linia pretului taiata la marginea din dreapta: nimic din ce ramane nu e un pret complet. */
    private fun pretTaiat(pl: Plan, r: Random): String {
        val n = pl.pretNum
        return when (pl.familie) {
            Familie.UBER_RO -> listOf(n, "$n R", "$n RO", n.dropLast(1)).random(r)
            Familie.UBER_EN -> listOf("RON", "RON${n.take(1)}", "RON${n.dropLast(1)}", "RON${n.dropLast(2)}").random(r)
            Familie.BOLT_RO -> listOf(n, "$n${NB}l", "$n${NB}le", n.dropLast(1)).random(r)
        }
    }

    private fun seg(id: String, fam: Set<Familie> = TOATE_FAM, f: (Seg) -> Seg, km: (Plan, Boolean) -> Asteptare) =
        p(id, fam) { pl, r ->
            val prima = r.nextBoolean()
            pl.pe(prima, f).seteaza(campKm(prima) to km(pl, prima))
        }

    val toate: List<Perturbare> = listOf(
        p("taiat_pret") { pl, r -> pl.copy(pret = pretTaiat(pl, r)).seteaza(Camp.PRET to NECUNOSCUT) },
        // D-288.4: „(N km” fara „)” se citeste.
        seg("taiat_segment_dupa_unitate", UBER, { it.copy(inchide = "", coda = "") }) { pl, p -> exact(pl.kmAdevarat(p)) },
        // Numarul e intreg si urmat de „k”: citire permisa, dar nu ceruta.
        seg("taiat_segment_in_unitate", f = { it.copy(unit = "k", inchide = "", coda = "") }) { pl, p -> oricare(pl.kmAdevarat(p)) },
        // Fara unitate nu se stie nici unde se termina numarul: necunoscut.
        seg("taiat_segment_dupa_numar", f = { it.copy(unit = "", spKm = "", inchide = "", coda = "") }) { _, _ -> NECUNOSCUT },
        seg("taiat_segment_la_ancora", f = { it.copy(taiere = Taiere.LA_ANCORA) }) { _, _ -> NECUNOSCUT },
        // „(D▢)”: cifra urmata de masca; km necunoscuti, minutele raman.
        seg("paranteza_trunchiata_masca", UBER, { it.copy(km = "${it.km.first()}▢", unit = "", spKm = "", coda = "") }) { _, _ -> NECUNOSCUT },
        // Ancora pierduta (fara „(”): raman o singura ancora, motorul nu ghiceste -> segmentele pot fi doar necunoscute.
        p("taiat_segment_fara_ancora") { pl, r ->
            pl.pe(r.nextBoolean()) { it.copy(taiere = Taiere.FARA_ANCORA) }.seteaza(
                Camp.MIN_PRELUARE to oricare(BigDecimal(pl.v.minPre)), Camp.KM_PRELUARE to oricare(pl.v.kmPre),
                Camp.MIN_CURSA to oricare(BigDecimal(pl.v.minCursa)), Camp.KM_CURSA to oricare(pl.v.kmCursa),
            )
        },
    )
}
