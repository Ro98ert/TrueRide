package app.trueride.core.parse.sintetic

import app.trueride.core.parse.Cutie
import app.trueride.core.parse.LinieOcr
import kotlin.random.Random

/**
 * Val 2, geometria casetelor: linii sparte in doua casete pe acelasi rand, linii sau oferte citite de doua ori in
 * acelasi cadru, pret sub segmente, segmente pe coloane. Parserul lucreaza pe linii, deci o linie sparta nu se
 * reuneste: pretul sau segmentul respectiv e „exact sau necunoscut” (nicio alta valoare, invariantul 6).
 */
internal object PerturbariGeometrie {
    private val FILLERE = listOf("▢ ▢ ▢", "▢ ▢", "▢ ▢ ▢")

    /** Locuri unde OCR-ul poate taia pretul: dupa separator, inaintea lui, la granita numar/moneda. */
    private fun taieturiPret(pl: Plan): List<Int> {
        val sep = pl.pret.indexOf(Formate.separator(pl.familie))
        val moneda = if (pl.familie == Familie.UBER_EN) 3 else pl.pretNum.length
        return listOf(sep + 1, sep, moneda)
    }

    private fun textSegment(pl: Plan, prima: Boolean): String = (if (prima) pl.pre else pl.cu).text(pl.familie)

    /** (indice, ancora ramane in prima casuta): inainte sau dupa „(” (Uber) / punctul-mijloc (Bolt). */
    private fun taieturaSegment(pl: Plan, prima: Boolean, r: Random): Pair<Int, Boolean> {
        val t = textSegment(pl, prima)
        val semn = t.indexOf(if (pl.familie == Familie.BOLT_RO) pl.pre.bulina else "(")
        return if (r.nextBoolean()) semn to false else semn + 1 to true
    }

    private fun segmentSpart(pl: Plan, r: Random): Plan {
        val care = r.nextInt(3)
        val taiate = listOf(true, false).filter { care == 2 || (care == 0) == it }.associateWith { taieturaSegment(pl, it, r) }
        val ancoreRamase = 2 - taiate.count { !it.value.second }
        val post = { l: List<LinieOcr> ->
            taiate.entries.fold(l) { acc, (prima, t) -> acc.cuSegmentul(pl, prima) { s -> s.spartaLa(t.first) } }
        }
        // Cu cel putin o ancora ramasa pretul se pastreaza (D-130); fara niciuna nu e oferta, deci „exact sau necunoscut”.
        val cuPret = if (ancoreRamase > 0) pl else pl.seteaza(Camp.PRET to oricare(pl.v.pret))
        return cuPret.segmenteOricare().copy(post = post)
    }

    /** O a doua citire a aceleiasi casete: aproape suprapusa (cativa pixeli), dupa original. */
    private fun dublura(l: LinieOcr, r: Random): LinieOcr = l.deplasata(r.nextInt(-6, 7), r.nextInt(2, 9))

    /** Segmentele pe doua coloane pe acelasi rand: stanga (preluarea) mult mai inalta, dreapta aliniata sus / la mijloc / jos. */
    private fun peColoane(l: List<LinieOcr>, pl: Plan, aliniere: Int): List<LinieOcr> {
        val ip = l.indiceSegment(pl, true)
        val ic = l.indiceSegment(pl, false)
        val a = l[ip]
        val b = l[ic]
        val h = b.cutie.inaltime
        val mare = h * 5 / 2
        val susB = a.cutie.sus + listOf(0, (mare - h) / 2, mare - h)[aliniere]
        val stanga = a.copy(cutie = Cutie(100, a.cutie.sus, 540, a.cutie.sus + mare))
        val dreapta = b.copy(cutie = Cutie(560, susB, 560 + minOf(440, 20 * b.text.length), susB + h))
        return l.toMutableList().also { it[ip] = stanga; it[ic] = dreapta }
    }

    val toate: List<Perturbare> = listOf(
        // Pretul in doua casete („12,” + „50 RON”): fara reunire nu exista un pret complet; o jumatate nu devine pret.
        p("pret_spart_doua_casete") { pl, r ->
            val t = taieturiPret(pl).random(r)
            pl.copy(post = { l -> l.indicePret(pl).let { i -> l.inlocuitLa(i, l[i].spartaLa(t)) } })
                .seteaza(Camp.PRET to oricare(pl.v.pret))
        },
        p("segment_spart_doua_casete") { pl, r -> segmentSpart(pl, r) },
        // Aceeasi linie de pret de doua ori: aceeasi valoare, deci pretul ramane exact.
        p("pret_linie_dublata") { pl, r ->
            pl.copy(post = { l -> l.indicePret(pl).let { i -> l.inlocuitLa(i, listOf(l[i], dublura(l[i], r))) } })
        },
        // Un segment citit de doua ori = 3 ancore: motorul nu ghiceste care e care (I4).
        p("segment_linie_dublata") { pl, r ->
            val prima = r.nextBoolean()
            pl.copy(post = { l -> l.cuSegmentul(pl, prima) { s -> listOf(s, dublura(s, r)) } }).segmenteOricare()
        },
        // Oferta intreaga citita de doua ori (aceleasi cifre): 4 ancore, pretul e acelasi.
        p("oferta_dublata_suprapusa") { pl, r ->
            val dx = r.nextInt(-6, 7)
            val dy = r.nextInt(2, 9)
            pl.copy(post = { l -> l + l.map { it.deplasata(dx, dy) } }).segmenteOricare()
        },
        p("pret_sub_segmente_departe") { pl, _ -> pl.copy(pretSub = true, dupaCursa = FILLERE) },
        p("coloane_segmente_stanga_mare") { pl, r ->
            val aliniere = r.nextInt(3)
            pl.copy(post = { l -> peColoane(l, pl, aliniere) })
        },
    )
}
