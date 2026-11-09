package app.trueride.core.parse.sintetic

import kotlin.random.Random

/**
 * D-315.3, erori inventate pe CAMPURI (pret, minute, km): confuziile OCR din D-288.2 (S/5, B/8, Z/2, G/6, q/9, i/1),
 * cifra dublata sau pierduta, textul taiat la marginea din stanga, unitatea minutelor sau a km confundata, zecimala
 * mutata cu o pozitie. Asteptarea e scrisa de mana, din adevarul inventat: un camp ale carui cifre nu se mai pot citi
 * sigur trebuie sa iasa „necunoscut” (invariantul 6), iar celelalte patru raman exacte. Daca parserul da pe un camp
 * stricat o valoare plauzibila dar DIFERITA, varianta e GRESIT si ajunge in `CUNOSCUTE_GRESITE` (P-sim-N), nu in parser.
 */
internal object PerturbariInventateCampuri {
    private const val CONFUZII = "SBZGqi"

    private fun String.inloc(i: Int, c: Char) = substring(0, i) + c + substring(i + 1)
    private fun String.fara(i: Int) = substring(0, i) + substring(i + 1)
    private fun String.dublat(i: Int) = substring(0, i + 1) + this[i] + substring(i + 1)
    private fun String.cifre(): List<Int> = indices.filter { this[it].isDigit() }

    /** Segmentul [prima] cu minute 10-45 (doua cifre) si km din viteza plauzibila; km nu mai pot fi 1 cifra. */
    private fun cuMinuteDeDoua(pl: Plan, prima: Boolean, r: Random): Plan =
        GeneratorSintetic.bazaDin(pl.familie, pl.v.cuMinute(prima, r.nextInt(10, 46), r))

    /** Segmentul [prima] cu km de cel putin 10.0 si minute care il fac plauzibil (D-148). */
    private fun cuKmMari(pl: Plan, prima: Boolean, r: Random): Plan = PerturbariValori.peSegment(pl, prima, r.nextInt(20, 46), r.nextInt(100, 250))

    private fun campPret(id: String, schimba: (String, Random) -> String) =
        p(id, inventata = true) { pl, r -> pl.cuPret(schimba(pl.pretNum, r), NECUNOSCUT) }

    private fun campMinute(id: String, schimba: (String, Random) -> String) = p(id, inventata = true) { pl, r ->
        val prima = r.nextBoolean()
        val n = cuMinuteDeDoua(pl, prima, r)
        n.pe(prima) { it.copy(min = schimba(it.min, r)) }.seteaza(campMin(prima) to NECUNOSCUT)
    }

    private fun campKm(id: String, schimba: (String, Random) -> String) = p(id, inventata = true) { pl, r ->
        val prima = r.nextBoolean()
        val n = if (r.nextBoolean()) cuKmMari(pl, prima, r) else pl
        n.pe(prima) { it.copy(km = schimba(it.km, r)) }.seteaza(campKm(prima) to NECUNOSCUT)
    }

    private fun confuzie(s: String, r: Random): String = s.inloc(s.cifre().random(r), CONFUZII.random(r))

    /**
     * Un 0 din fata (inainte de orice cifra nenula, ex. „0.7”) dublat nu schimba valoarea („00.7” = 0.7): nu e o eroare de
     * citire, deci nu se alege. Un singur `random(r)`, ca secventa aleatoare a restului variantelor sa ramana aceeasi.
     */
    private fun dubla(s: String, r: Random): String {
        val toate = s.cifre()
        val schimba = toate.filter { i -> s[i] != '0' || s.substring(0, i).any { it in '1'..'9' } }
        return s.dublat((schimba.ifEmpty { toate }).random(r))
    }

    private fun pierduta(s: String, r: Random): String = s.fara(s.cifre().random(r))

    /** „12,50” -> „1,250” / „125,0”: cifrele raman, separatorul sare cu o pozitie sau mai multe (nu la locul lui, nici disparut). */
    private fun zecimalaPret(pl: Plan, r: Random): Plan {
        val sep = Formate.separator(pl.familie)
        val d = pl.pretNum.filter(Char::isDigit)
        val p = (1 until d.length).filter { it != d.length - 2 }.random(r)
        return pl.cuPret(d.substring(0, p) + sep + d.substring(p), NECUNOSCUT)
    }

    /** „12.5” -> „1.25” (cu 3+ cifre) sau „4.5” -> „.45” / „0.45”: punctul se muta la stanga cu o pozitie. */
    private fun zecimalaKm(pl: Plan, r: Random): Plan {
        val prima = r.nextBoolean()
        val n = if (r.nextBoolean()) cuKmMari(pl, prima, r) else pl
        return n.pe(prima) { s ->
            val d = s.km.filter(Char::isDigit)
            val nou = if (d.length >= 3) d.substring(0, d.length - 2) + "." + d.substring(d.length - 2)
            else listOf(".", "0.").random(r) + d
            s.copy(km = nou)
        }.seteaza(campKm(prima) to NECUNOSCUT)
    }

    private fun unitateMinute(id: String, minTxt: (Familie) -> String) = p(id, inventata = true) { pl, r ->
        val altul = minTxt(pl.familie)
        if (r.nextBoolean()) {
            pl.pe(r.nextBoolean()) { it.copy(minTxt = altul) }.segmenteOricare()
        } else {
            pl.cuSegmente { it.copy(minTxt = altul) }.seteaza(*Camp.entries.map { it to NECUNOSCUT }.toTypedArray())
        }
    }

    private fun taiatCuvant(id: String, seg: (Seg, Random) -> Seg) =
        p(id, inventata = true) { pl, r -> pl.pe(r.nextBoolean()) { seg(it, r) }.segmenteOricare() }

    val toate: List<Perturbare> = listOf(
        campPret("confuzie_ocr_pret", ::confuzie), campMinute("confuzie_ocr_minute") { s, r ->
            if (r.nextInt(4) == 0) CONFUZII.random(r).toString() else confuzie(s, r)
        },
        campKm("confuzie_ocr_km", ::confuzie),
        campPret("cifra_dublata_pret", ::dubla), campMinute("cifra_dublata_minute", ::dubla), campKm("cifra_dublata_km", ::dubla),
        campPret("cifra_pierduta_pret", ::pierduta), campMinute("cifra_pierduta_minute", ::pierduta), campKm("cifra_pierduta_km", ::pierduta),
        // Marginea din stanga taie prima cifra a pretului (sau, la EN/Bolt, literele monedei) si prima cifra a minutelor.
        p("taiat_stanga_pret", inventata = true) { pl, r -> pl.copy(pret = pl.pret.drop(r.nextInt(1, 3))).seteaza(Camp.PRET to NECUNOSCUT) },
        campMinute("taiat_stanga_minute") { s, _ -> s.drop(1) },
        p("zecimala_mutata_pret", inventata = true) { pl, r -> zecimalaPret(pl, r) },
        p("zecimala_mutata_km", inventata = true) { pl, r -> zecimalaKm(pl, r) },
        // OCR-ul scrie „mi”/„m” in loc de „min”: ancora segmentului se pierde (D-130: fara nicio ancora nu exista oferta).
        unitateMinute("unitate_minute_mi") { if (it == Familie.UBER_RO) "mi." else "mi" },
        unitateMinute("unitate_minute_m") { if (it == Familie.UBER_RO) "m." else "m" },
        p("unitate_km_m", inventata = true) { pl, r ->
            val prima = r.nextBoolean()
            pl.pe(prima) { it.copy(unit = "m") }.seteaza(campKm(prima) to NECUNOSCUT)
        },
        // Marginea din stanga taie prima litera a cuvantului „min” / marginea din dreapta taie randul dupa „m” sau „mi”.
        taiatCuvant("taiat_stanga_in_cuvant_minute") { s, _ -> s.copy(minTxt = s.minTxt.drop(1)) },
        taiatCuvant("taiat_dreapta_in_cuvant_minute") { s, r -> s.copy(minTxt = listOf("m", "mi").random(r), taiere = Taiere.FARA_ANCORA) },
    )
}
