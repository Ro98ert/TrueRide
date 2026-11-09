package app.trueride.core.parse.sintetic

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * D-329.1: o perturbare care schimba pretul, km sau minutele dupa oferta de baza trebuie sa lase adevarul in plaja
 * reala lei/km (1,40-5,30, ca [ValoriBazaLeiPeKmTest]). Altfel banda din reguli face pretul necunoscut si corpusul
 * raporteaza „necunoscut pierdut” fals. Se judeca doar variantele cu pret si km asteptati exact.
 */
class PerturbariLeiPeKmTest {

    @Test
    fun `adevarul fiecarei variante cu pret exact are lei pe km in plaja reala`() {
        val afara = GeneratorSintetic.corpus().filter { v -> GeneratorSintetic.leiPeKmInPlaja(v.asteptat) == false }
        assertTrue(afara.isEmpty(), "variante in afara plajei: ${afara.map { it.nume }}")
    }

    @Test
    fun `pretul de trei cifre vine cu o cursa lunga la cel mult 55 km pe ora`() {
        val p = PerturbariSintetice.TOATE.first { it.id == "pret_trei_cifre" }
        val variante = p.familii.flatMap { f -> (0 until GeneratorSintetic.PE_PERTURBARE).map { GeneratorSintetic.genereaza(f, p, it) } }

        val rele = variante.filter { v ->
            val pret = (v.asteptat.getValue(Camp.PRET) as Asteptare.Exact).v
            val km = (v.asteptat.getValue(Camp.KM_CURSA) as Asteptare.Exact).v
            val min = (v.asteptat.getValue(Camp.MIN_CURSA) as Asteptare.Exact).v
            pret < BigDecimal(100) || km.multiply(BigDecimal(60)) > min.multiply(BigDecimal(55))
        }

        assertTrue(rele.isEmpty(), "pret sub 100 lei sau peste 55 km/h: ${rele.map { it.nume }}")
    }
}
