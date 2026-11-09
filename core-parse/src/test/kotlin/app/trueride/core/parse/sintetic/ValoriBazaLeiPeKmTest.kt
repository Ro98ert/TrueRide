package app.trueride.core.parse.sintetic

import java.math.BigDecimal
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * D-329.1: oferta de baza are pretul legat de km (lei/km 1,40-5,30, in plaja reala a tuturor seturilor), altfel banda
 * lei/km din reguli face pretul necunoscut pe combinatii inventate si raporteaza „necunoscut pierdut” fals (ca D-329.4).
 */
class ValoriBazaLeiPeKmTest {

    @Test
    fun `pretul de baza are lei pe km in plaja reala`() {
        val afara = (0 until 2000).map { GeneratorSintetic.valori(Random(it)) }.filter { v ->
            val km = v.kmPre + v.kmCursa
            v.pret < km.multiply(BigDecimal("1.40")) || v.pret > km.multiply(BigDecimal("5.30"))
        }
        assertTrue(afara.isEmpty(), "oferte de baza in afara plajei: ${afara.size}")
    }
}
