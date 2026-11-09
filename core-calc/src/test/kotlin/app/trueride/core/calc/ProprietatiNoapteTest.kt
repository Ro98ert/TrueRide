package app.trueride.core.calc

import app.trueride.core.model.CostInput
import app.trueride.core.model.DataCompleteness
import app.trueride.core.model.Distance
import app.trueride.core.model.DistanceUnit
import app.trueride.core.model.Evaluation
import app.trueride.core.model.OfferInput
import app.trueride.core.model.Thresholds
import java.math.BigDecimal
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import org.junit.jupiter.api.Tag

/**
 * D-348.4: proprietatile invariantilor din CLAUDE.md §5, verificate peste intrari generate cu un seed.
 * Unde un test de zi alege cateva cazuri la mana, aici se alege la intamplare — dar reproductibil: acelasi
 * `-Ptrueride.noapte.seed` da aceleasi intrari. Ruleaza doar prin `:core-calc:noapte` (tag `noapte`).
 *
 * Mesajele de picare poarta DOAR seed-ul si indexul intrarii (invariantul 2: nicio valoare cu unitate in loguri);
 * intrarea se regenereaza din ele.
 */
@Tag("noapte")
class ProprietatiNoapteTest {

    private val seed: Long = System.getProperty("trueride.noapte.seed")?.toLong() ?: 20261024L
    private val cazuri: Int = System.getProperty("trueride.noapte.cazuri")?.toInt() ?: 200

    private class Intrare(val oferta: OfferInput, val cost: CostInput, val praguri: Thresholds)

    // ---------------------------------------------------------------- //
    // Generator: lipsuri, zerouri si negative apar des, nu doar rar     //
    // ---------------------------------------------------------------- //

    /** null ~20%, negativ ~10%, zero ~10%, restul pozitiv cu 0-3 zecimale. */
    private fun Random.valoare(): BigDecimal? {
        val zar = nextInt(10)
        return when {
            zar < 2 -> null
            zar == 2 -> BigDecimal.valueOf(-(1 + nextLong(5000)), nextInt(4))
            zar == 3 -> BigDecimal.ZERO
            else -> BigDecimal.valueOf(1 + nextLong(500_000), nextInt(4))
        }
    }

    private fun Random.unitate() = if (nextBoolean()) DistanceUnit.KM else DistanceUnit.MI

    private fun Random.distanta(): Distance? = valoare()?.let { Distance(it, unitate()) }

    private fun Random.prag(): BigDecimal? = if (nextInt(3) == 0) null else BigDecimal.valueOf(nextLong(20_000), 2)

    private fun Random.intrare() = Intrare(
        OfferInput(valoare(), valoare(), valoare(), distanta(), distanta()),
        CostInput(valoare(), unitate()),
        Thresholds(prag(), prag(), prag(), prag(), prag(), prag()),
    )

    /** Intrarea cu numarul `i` a seed-ului, fara sa depinda de celelalte (regenerabila din seed + index). */
    private fun intrareLa(i: Int): Intrare = Random(seed * 1_000_003L + i).intrare()

    private fun evalueaza(x: Intrare): Evaluation = OfferCalculator.evaluate(x.oferta, x.cost, x.praguri)

    private fun peste(proprietate: (Intrare, Int) -> Unit) {
        for (i in 0 until cazuri) {
            try {
                proprietate(intrareLa(i), i)
            } catch (e: AssertionError) {
                fail("seed $seed index $i: ${e.javaClass.simpleName}")
            } catch (e: Exception) {
                fail("seed $seed index $i: exceptie ${e.javaClass.simpleName}")
            }
        }
    }

    /** Valoare imposibila fizic = lipsa (pasul 1 al calculatorului). */
    private fun BigDecimal?.citit(): BigDecimal? = this?.takeIf { it.signum() >= 0 }
    private fun Distance?.citita(): Distance? = this?.takeIf { it.amount.signum() >= 0 }

    // ---------------------------------------------------------------- //

    @Test
    fun `rezultatul nu contine Double sau Float in tipurile lui`() {
        val tipuriInterzise = setOf(Double::class.javaPrimitiveType, Float::class.javaPrimitiveType,
            Double::class.javaObjectType, Float::class.javaObjectType)
        for (clasa in listOf(Evaluation::class.java, Distance::class.java)) {
            for (camp in clasa.declaredFields) {
                assertTrue(camp.type !in tipuriInterzise, "campul ${clasa.simpleName}.${camp.name} e in virgula mobila")
            }
        }
    }

    @Test
    fun `timp total zero sau lipsa da indicatori pe timp indisponibili fara exceptie`() {
        peste { x, _ ->
            val ridicare = x.oferta.pickupMinutes.citit()
            val cursa = x.oferta.tripMinutes.citit()
            if (ridicare == null || cursa == null || ridicare.add(cursa).signum() == 0) {
                val r = evalueaza(x)
                assertNull(r.perHour, "perHour trebuia sa fie indisponibil")
                assertNull(r.perMinute, "perMinute trebuia sa fie indisponibil")
            }
        }
    }

    @Test
    fun `un camp lipsa da rezultat necunoscut si completitudine partiala, niciodata zero`() {
        peste { x, _ ->
            val o = x.oferta
            val pret = o.price.citit()
            val timpComplet = o.pickupMinutes.citit() != null && o.tripMinutes.citit() != null
            val distComplet = o.pickupDistance.citita() != null && o.tripDistance.citita() != null
            val r = evalueaza(x)
            val tot = pret != null && timpComplet && distComplet
            assertEquals(if (tot) DataCompleteness.FULL else DataCompleteness.PARTIAL, r.completeness, "completitudine")
            if (!timpComplet) assertNull(r.totalMinutes, "totalMinutes")
            if (!distComplet) assertNull(r.totalDistance, "totalDistance")
            if (pret == null || !timpComplet) {
                assertNull(r.perHour, "perHour")
                assertNull(r.perMinute, "perMinute")
            }
            if (pret == null || !distComplet) assertNull(r.perDistance, "perDistance")
            if (x.cost.costPerDistance.citit() == null || !distComplet) assertNull(r.estimatedCost, "estimatedCost")
            if (pret == null || r.estimatedCost == null) assertNull(r.estimatedProfit, "estimatedProfit")
        }
    }

    @Test
    fun `acelasi input de doua ori da acelasi rezultat`() {
        peste { x, _ -> assertEquals(evalueaza(x), evalueaza(x), "rezultate diferite") }
    }

    @Test
    fun `acelasi seed da aceeasi iesire pe toata secventa`() {
        val intai = (0 until cazuri).map { evalueaza(intrareLa(it)) }
        val aDoua = (0 until cazuri).map { evalueaza(intrareLa(it)) }
        for (i in 0 until cazuri) assertEquals(intai[i], aDoua[i], "seed $seed index $i: iesiri diferite")
    }

    @Test
    fun `rezultatul intern are scara calculului BigDecimal direct, fara rotunjire de afisare`() {
        val mc = OfferCalculator.WORKING_PRECISION
        peste { x, _ ->
            val o = x.oferta
            val pret = o.price.citit()
            val ridicare = o.pickupMinutes.citit()
            val cursa = o.tripMinutes.citit()
            val dRidicare = o.pickupDistance.citita()?.convertTo(x.cost.unit, mc)?.amount
            val dCursa = o.tripDistance.citita()?.convertTo(x.cost.unit, mc)?.amount
            val minute = if (ridicare != null && cursa != null) ridicare.add(cursa) else null
            val distanta = if (dRidicare != null && dCursa != null) dRidicare.add(dCursa) else null
            fun imparte(a: BigDecimal?, b: BigDecimal?) =
                if (a == null || b == null || b.signum() == 0) null else a.divide(b, mc)
            val r = evalueaza(x)
            // `equals` pe BigDecimal compara si scara: 2.50 != 2.5. Orice rotunjire intermediara ar schimba scara.
            assertEquals(imparte(pret?.multiply(BigDecimal("60")), minute), r.perHour, "perHour")
            assertEquals(imparte(pret, distanta), r.perDistance, "perDistance")
            assertEquals(imparte(pret, minute), r.perMinute, "perMinute")
            val cost = x.cost.costPerDistance.citit()
            val costAsteptat = if (cost != null && distanta != null) cost.multiply(distanta, mc) else null
            assertEquals(costAsteptat, r.estimatedCost, "estimatedCost")
            val profit = if (pret != null && costAsteptat != null) pret.subtract(costAsteptat, mc) else null
            assertEquals(profit, r.estimatedProfit, "estimatedProfit")
        }
    }
}
