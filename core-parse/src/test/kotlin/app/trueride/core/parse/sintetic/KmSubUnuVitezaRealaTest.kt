package app.trueride.core.parse.sintetic

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * D-329.4: `km_sub_unu` pune un km sub 1 cu minute din aceeasi plaja de viteza ca oferta de baza
 * ([GeneratorSintetic.km], 14-55 km/h). Minutele inventate (0,1 km in 3 min = 2 km/h) cadeau sub viteza minima
 * din reguli (D-315, val 7/10) si raportau „necunoscut pierdut” pe corpusul „real”, desi minimul real e 11,3 km/h.
 */
class KmSubUnuVitezaRealaTest {

    @Test
    fun `km sub unu are viteza de cel putin 12 km pe ora pe segmentul lui`() {
        val p = PerturbariSintetice.TOATE.first { it.id == "km_sub_unu" }
        val variante = p.familii.flatMap { f -> (0 until GeneratorSintetic.PE_PERTURBARE).map { GeneratorSintetic.genereaza(f, p, it) } }

        val lente = variante.filter { v ->
            listOf(Camp.KM_PRELUARE to Camp.MIN_PRELUARE, Camp.KM_CURSA to Camp.MIN_CURSA).any { (k, m) ->
                val km = (v.asteptat.getValue(k) as Asteptare.Exact).v
                val min = (v.asteptat.getValue(m) as Asteptare.Exact).v
                km < BigDecimal.ONE && km.multiply(BigDecimal(60)) < min.multiply(BigDecimal(12))
            }
        }

        assertTrue(lente.isEmpty(), "variante sub 12 km/h: ${lente.map { it.nume }}")
    }
}
