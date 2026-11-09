package app.trueride.core.parse.sintetic

import app.trueride.core.parse.LinieOcr
import java.math.BigDecimal
import kotlin.random.Random

/**
 * D-315.3, erori inventate la nivel de OFERTA: randuri dublate sau interschimbate, doua oferte partial suprapuse si
 * detectia ofertei (ancora lipsa, ancora in plus, randuri straine intre segmente, card partial). Invariantul 5:
 * nicio cifra din a doua oferta nu are voie sa apara (deci `Oricare` cu valorile primei, nu ale ambelor); invariantul
 * 6: ce nu se poate atribui sigur ramane necunoscut.
 */
internal object PerturbariInventateOferta {
    private const val DEPARTE_PX = 400
    private val TOATE_CAMPURILE = Camp.entries.map { it to NECUNOSCUT }.toTypedArray()

    /** Un rand de segment al unei alte oferte (alte minute, alti km): forma unei ancore straine. */
    private fun ancoraStraina(pl: Plan, r: Random): String {
        val m = r.nextInt(2, 20)
        return Seg.baza(pl.familie, m, GeneratorSintetic.km(m, r), prima = false).text(pl.familie)
    }

    private fun mutaLaCoada(l: List<LinieOcr>, i: Int): List<LinieOcr> = l + l[i].deplasata(0, DEPARTE_PX)

    private fun interschimba(l: List<LinieOcr>, i: Int): List<LinieOcr> =
        l.toMutableList().also { it[i] = l[i + 1]; it[i + 1] = l[i] }

    /** Valorile primei oferte: „exact sau necunoscut”, niciodata ale celei de-a doua (invariantul 5). */
    private fun segmenteleMele(pl: Plan, prima: Boolean): Array<Pair<Camp, Asteptare>> = arrayOf(
        campMin(prima) to oricare(pl.minAdevarat(prima)), campKm(prima) to oricare(pl.kmAdevarat(prima)),
    )

    private fun doiPartialSuprapusi(pl: Plan, r: Random): Plan {
        val b = GeneratorSintetic.baza(pl.familie, r)
        val lb = Randare.linii(b)
        val tip = r.nextInt(3)
        val pretB = lb[lb.indicePret(b)]
        val segB = lb[lb.indiceSegment(b, r.nextBoolean())]
        val alese = when (tip) {
            0 -> listOf(pretB)
            1 -> listOf(segB)
            else -> listOf(pretB, lb[lb.indiceSegment(b, true)])
        }
        val dx = r.nextInt(-6, 7)
        val dy = r.nextInt(30, 90)
        val planA = pl.copy(post = { l -> l + alese.map { it.deplasata(dx, dy) } }).seteaza(Camp.PRET to oricare(pl.v.pret))
        // Fara ancore in plus (tip 0) segmentele primei oferte raman citibile; cu o ancora in plus motorul nu ghiceste.
        return if (tip == 0) planA else planA.segmenteOricare()
    }

    private fun cardPartial(pl: Plan, r: Random): Plan {
        val lipsa = r.nextBoolean()
        val prezenta = !lipsa
        return pl.copy(post = { l -> l.toMutableList().also { it.removeAt(l.indiceSegment(pl, lipsa)) } }).seteaza(
            campMin(lipsa) to NECUNOSCUT, campKm(lipsa) to NECUNOSCUT, *segmenteleMele(pl, prezenta),
        )
    }

    private fun straineIntre(pl: Plan, r: Random): Plan {
        val sumaMica = Formate.pret(pl.familie, Formate.numarPret(pl.familie, BigDecimal.valueOf(r.nextInt(300, 3000).toLong(), 2)))
        val randuri = listOf(listOf("▢ ▢"), listOf("Oră"), listOf("${r.nextInt(10, 99)} ▢"), listOf("${r.nextInt(2, 30)} min ▢ ▢"), listOf(sumaMica))
        return pl.copy(intreSegmente = randuri.random(r))
    }

    val toate: List<Perturbare> = listOf(
        // Acelasi rand de pret / de segment citit a doua oara, mai jos (nu suprapus): pretul ramane acelasi, segmentele devin 3 ancore.
        p("rand_dublat_departe_pret", inventata = true) { pl, _ -> pl.copy(post = { l -> mutaLaCoada(l, l.indicePret(pl)) }) },
        p("rand_dublat_departe_segment", inventata = true) { pl, r ->
            val prima = r.nextBoolean()
            pl.copy(post = { l -> mutaLaCoada(l, l.indiceSegment(pl, prima)) }).segmenteOricare()
        },
        // Doua randuri alaturate din lista OCR au locurile schimbate; pozitiile de pe ecran raman: rezultatul nu se schimba.
        p("randuri_interschimbate", inventata = true) { pl, r ->
            val i = r.nextInt(Randare.linii(pl).size - 1)
            pl.copy(post = { l -> interschimba(l, i) })
        },
        p("doua_oferte_partial_suprapuse", inventata = true) { pl, r -> doiPartialSuprapusi(pl, r) },
        // Ambele ancore stricate: fara nicio ancora nu exista oferta, deci nici pretul (D-130).
        p("ancora_lipsa_ambele", inventata = true) { pl, r ->
            val unitate = listOf("", "rin", "mm").random(r)
            pl.cuSegmente { it.copy(minTxt = unitate) }.seteaza(*TOATE_CAMPURILE)
        },
        p("ancora_in_plus_intre_segmente", inventata = true) { pl, r -> pl.copy(intreSegmente = listOf(ancoraStraina(pl, r))).segmenteOricare() },
        p("ancora_in_plus_inainte", inventata = true) { pl, r -> pl.copy(dupaPret = listOf(ancoraStraina(pl, r))).segmenteOricare() },
        p("randuri_straine_intre_segmente", inventata = true) { pl, r -> straineIntre(pl, r) },
        p("card_partial_un_segment", inventata = true) { pl, r -> cardPartial(pl, r) },
    )
}
