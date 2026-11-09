package app.trueride.core.parse.sintetic

import app.trueride.core.parse.BOLT_RO_V1
import app.trueride.core.parse.OfferParser
import app.trueride.core.parse.ParserOferta
import app.trueride.core.parse.ParserPeLimbi
import app.trueride.core.parse.UBER_EN_V1
import app.trueride.core.parse.UBER_RO_V1
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.random.Random

/**
 * Generator determinist de oferte sintetice: `kotlin.random.Random(seed)` cu seed fix pe (familie, perturbare, index),
 * valori inventate in plaje plauzibile. Nicio linie din lot; doar structura formelor reale (cifra -> valoare inventata).
 */
object GeneratorSintetic {

    const val SEED = 20261022
    const val PE_PERTURBARE = 20
    private const val INCERCARI = 200

    /**
     * Perturbari al caror rezultat azi e o VALOARE gresita (nu doar necunoscut): cheie `FAMILIE.perturbare` -> motiv.
     * Dupa D-312 a ramas doar P-sint-2 (limita D-148, decizia deschisa O-31): testele ei stau in
     * `CazuriGresiteDezactivateTest` cu `@Disabled`, iar aici sunt scoase din numaratoarea GRESIT (coloana separata
     * `GRESIT_CUNOSCUT`). P-sint-1/3/4/5 sunt oprite de garda din reguli si numara ca GRESIT daca revin.
     * `P-sim-N` (D-315.3) vin din [CunoscuteSimulare]: erori inventate la care parserul da azi o valoare gresita.
     */
    val CUNOSCUTE_GRESITE: Map<String, String> = mapOf(
        "UBER_RO.km_punct_pierdut_plauzibil" to
            "P-sint-2: limita D-148: punctul zecimal pierdut cu viteza sub 150 km/h se citeste ca km x10",
        "UBER_EN.km_punct_pierdut_plauzibil" to
            "P-sint-2: limita D-148: punctul zecimal pierdut cu viteza sub 150 km/h se citeste ca km x10",
        "BOLT_RO.km_punct_pierdut_plauzibil" to
            "P-sint-2: limita D-148: punctul zecimal pierdut cu viteza sub 150 km/h se citeste ca km x10",
    ) + CunoscuteSimulare.TOATE

    fun cunoscutGresit(v: Varianta): Boolean = "${v.familie}.${v.perturbare}" in CUNOSCUTE_GRESITE

    /** Parserele pe care se masoara o familie: setul ei nativ si, pentru Uber, perechea RO+EN ca in `ReplayFixturi`. */
    fun seturi(f: Familie): List<Pair<String, ParserOferta>> = when (f) {
        Familie.UBER_RO -> listOf("UBER_RO_V1" to OfferParser(UBER_RO_V1), "UBER_RO+EN" to uberPeLimbi)
        Familie.UBER_EN -> listOf("UBER_EN_V1" to OfferParser(UBER_EN_V1), "UBER_RO+EN" to uberPeLimbi)
        Familie.BOLT_RO -> listOf("BOLT_RO_V1" to OfferParser(BOLT_RO_V1))
    }

    private val uberPeLimbi = ParserPeLimbi(listOf(OfferParser(UBER_RO_V1), OfferParser(UBER_EN_V1)))

    fun corpus(): List<Varianta> = PerturbariSintetice.TOATE.flatMap { p ->
        p.familii.flatMap { f -> (0 until PE_PERTURBARE).map { genereaza(f, p, it) } }
    }

    fun genereaza(f: Familie, p: Perturbare, i: Int, seedBaza: Int = SEED): Varianta {
        val r = Random(seedBaza + (f.name + p.id).hashCode() * 31 + i)
        // D-329.1: perturbarile care schimba pretul pe text (litera, intreg recitit) pot scoate adevarul din plaja
        // lei/km; se trage din nou, cu acelasi Random, pana cand adevarul e o oferta plauzibila.
        val plan = generateSequence { p.aplica(baza(f, r), r) }.take(INCERCARI).firstOrNull { leiPeKmInPlaja(it.asteptat) != false }
            ?: error("${f.name}.${p.id}.$i: adevar in afara plajei lei/km dupa $INCERCARI incercari")
        val linii = plan.post(Randare.linii(plan)).let { if (plan.amesteca) it.shuffled(r) else it }
        return Varianta("${f.name}.${p.id}.${"%02d".format(i)}", f, p.id, linii, plan.asteptat)
    }

    fun baza(f: Familie, r: Random): Plan = bazaDin(f, valori(r))

    /**
     * Lei/km al adevarului in plaja reala 1,40-5,30 (D-329.1)? null cand pretul sau un km nu sunt asteptati exact:
     * atunci banda din reguli nu are pe ce judeca adevarul. Comparatie prin inmultire, fara impartire.
     */
    fun leiPeKmInPlaja(a: Map<Camp, Asteptare>): Boolean? {
        val pret = (a[Camp.PRET] as? Asteptare.Exact)?.v ?: return null
        val kmPre = (a[Camp.KM_PRELUARE] as? Asteptare.Exact)?.v ?: return null
        val kmCursa = (a[Camp.KM_CURSA] as? Asteptare.Exact)?.v ?: return null
        val total = kmPre + kmCursa
        return pret >= total.multiply(LEI_PE_KM_MIN) && pret <= total.multiply(LEI_PE_KM_MAX)
    }

    private val LEI_PE_KM_MIN = BigDecimal("1.40")
    private val LEI_PE_KM_MAX = BigDecimal("5.30")

    /**
     * Pret 3-10 lei (1 din 7) sau 10-70 lei, adus in plaja 1,40-5,30 lei/km pe km totali (D-329.1: plaja reala comuna a
     * celor 3 seturi; altfel banda lei/km din reguli ar respinge combinatii inventate). Aceleasi apeluri aleatoare ca
     * inainte, deci restul corpusului nu se muta. Km din viteza 14-55 km/h, plauzibili fata de minute (D-148).
     */
    fun valori(r: Random): Valori {
        val centi = if (r.nextInt(7) == 0) r.nextInt(300, 1000) else r.nextInt(1000, 7000)
        val minPre = r.nextInt(1, 16)
        val minCursa = r.nextInt(3, 46)
        val kmPre = km(minPre, r)
        val kmCursa = km(minCursa, r)
        return Valori(BigDecimal.valueOf(centi.toLong(), 2), minPre, kmPre, minCursa, kmCursa).inPlaja()
    }

    /** Pretul adus in plaja lei/km pe km totali (D-329.1); km si minutele raman. */
    fun Valori.inPlaja(): Valori {
        val total = kmPre + kmCursa
        return copy(
            pret = pret.max(total.multiply(LEI_PE_KM_MIN).setScale(2, RoundingMode.UP))
                .min(total.multiply(LEI_PE_KM_MAX).setScale(2, RoundingMode.DOWN)),
        )
    }

    fun km(min: Int, r: Random): BigDecimal = BigDecimal.valueOf(maxOf(2, min * r.nextInt(14, 56) / 6).toLong(), 1)

    fun bazaDin(f: Familie, v: Valori): Plan {
        val num = Formate.numarPret(f, v.pret)
        return Plan(
            familie = f, v = v, pretNum = num, pret = Formate.pret(f, num),
            pre = Seg.baza(f, v.minPre, v.kmPre, prima = true), cu = Seg.baza(f, v.minCursa, v.kmCursa, prima = false),
            asteptat = asteptatExact(v),
        )
    }

    fun asteptatExact(v: Valori): Map<Camp, Asteptare> = mapOf(
        Camp.PRET to Asteptare.Exact(v.pret),
        Camp.MIN_PRELUARE to Asteptare.Exact(BigDecimal(v.minPre)),
        Camp.KM_PRELUARE to Asteptare.Exact(v.kmPre),
        Camp.MIN_CURSA to Asteptare.Exact(BigDecimal(v.minCursa)),
        Camp.KM_CURSA to Asteptare.Exact(v.kmCursa),
    )
}

/** Forma textului pretului pe familie: RO `12,50 RON`, EN `RON12.50`, Bolt `12,50<NBSP>lei (▢, ▢ ▢)`. */
object Formate {
    fun separator(f: Familie): Char = if (f == Familie.UBER_EN) '.' else ','

    fun alternativ(f: Familie): Char = if (f == Familie.UBER_EN) ',' else '.'

    fun numarPret(f: Familie, pret: BigDecimal): String = pret.toPlainString().replace('.', separator(f))

    fun pret(f: Familie, num: String): String = when (f) {
        Familie.UBER_RO -> "$num RON"
        Familie.UBER_EN -> "RON$num"
        Familie.BOLT_RO -> "$num${NB}lei (▢, ▢ ▢)"
    }
}

/**
 * [inventata] = tipul de eroare nu are corespondent real in Sentry (D-315.3): se raporteaza separat de cele reale
 * si primeste o pondere fixa in procentul ponderat, nu frecventa de la soferi.
 */
class Perturbare(val id: String, val familii: Set<Familie>, val inventata: Boolean = false, val aplica: (Plan, Random) -> Plan)
