package app.trueride.core.parse.sintetic

import org.junit.jupiter.api.Disabled
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Perturbarile in care parserul dadea o VALOARE gresita (nu doar necunoscut) pe corpusul sintetic. Asteptarea e
 * necunoscut (invariantul 6). P-sint-1/3/4/5 sunt active din D-312 (garda din tiparele `Reguli*.kt`); P-sint-2 (km cu
 * punctul zecimal pierdut sub pragul D-332) ramane `@Disabled`: limita acceptata in D-335. Motivele ramase:
 * `GeneratorSintetic.CUNOSCUTE_GRESITE`. P-sim-1..10 (D-315.3) sunt erorile INVENTATE la care parserul da o valoare
 * gresita; toate stau `@Disabled`, ca bucla de simulare sa le preia (parserul nu s-a schimbat).
 */
class CazuriGresiteDezactivateTest {

    private fun faraValoareGresita(familie: Familie, perturbare: String) = faraValoareGresita(perturbare, familie)

    private fun faraValoareGresita(perturbare: String, vararg familii: Familie) = familii.forEach { familie ->
        val p = PerturbariSintetice.TOATE.first { it.id == perturbare }
        val gresite = (0 until GeneratorSintetic.PE_PERTURBARE).map { GeneratorSintetic.genereaza(familie, p, it) }.flatMap { v ->
            GeneratorSintetic.seturi(familie).mapNotNull { (set, parser) ->
                val o = parser.parseaza(v.linii)
                if (Clasificare.varianta(v.asteptat, o) == Clasa.GRESIT) "${v.nume} [$set]: ${Clasificare.gresite(v.asteptat, o)}" else null
            }
        }
        assertTrue(gresite.isEmpty(), "GRESIT=${gresite.size}: ${gresite.take(3)}")
    }

    @Test
    fun `Uber RO - litera nemapata inaintea pretului nu da un pret taiat`() =
        faraValoareGresita(Familie.UBER_RO, "litera_nemapata_pret_prima_cifra")

    @Disabled("P-sint-2: limita acceptata D-335 - punct zecimal pierdut la km sub pragul D-332 se citeste ca km x10 (Uber RO)")
    @Test
    fun `Uber RO - punctul pierdut la km cu viteza plauzibila ramane necunoscut`() =
        faraValoareGresita(Familie.UBER_RO, "km_punct_pierdut_plauzibil")

    @Disabled("P-sint-2: limita acceptata D-335 - punct zecimal pierdut la km sub pragul D-332 se citeste ca km x10 (Uber EN)")
    @Test
    fun `Uber EN - punctul pierdut la km cu viteza plauzibila ramane necunoscut`() =
        faraValoareGresita(Familie.UBER_EN, "km_punct_pierdut_plauzibil")

    @Disabled("P-sint-2: limita acceptata D-335 - punct zecimal pierdut la km sub pragul D-332 se citeste ca km x10 (Bolt RO)")
    @Test
    fun `Bolt RO - punctul pierdut la km cu viteza plauzibila ramane necunoscut`() =
        faraValoareGresita(Familie.BOLT_RO, "km_punct_pierdut_plauzibil")

    @Test
    fun `Uber RO - litera nemapata inaintea minutelor nu da minute taiate`() =
        faraValoareGresita(Familie.UBER_RO, "litera_nemapata_minute_prima_cifra")

    @Test
    fun `Uber EN - litera nemapata inaintea minutelor nu da minute taiate`() =
        faraValoareGresita(Familie.UBER_EN, "litera_nemapata_minute_prima_cifra")

    @Test
    fun `Uber RO - ore si minute de 2 cifre nu dau ultima cifra`() = faraValoareGresita(Familie.UBER_RO, "ore_si_minute")

    @Test
    fun `Uber EN - ore si minute de 2 cifre nu dau ultima cifra`() = faraValoareGresita(Familie.UBER_EN, "ore_si_minute")

    @Test
    fun `Bolt RO - ore si minute de 2 cifre nu dau ultima cifra`() = faraValoareGresita(Familie.BOLT_RO, "ore_si_minute")

    @Test
    fun `Uber RO - spatiul in interiorul minutelor nu da ultimul grup de cifre`() =
        faraValoareGresita(Familie.UBER_RO, "spatiu_in_minute")

    @Test
    fun `Uber EN - spatiul in interiorul minutelor nu da ultimul grup de cifre`() =
        faraValoareGresita(Familie.UBER_EN, "spatiu_in_minute")

    @Test
    fun `P-sim-1 - litera D-288 pct 2 in locul primei cifre a pretului nu da restul cifrelor ca pret`() =
        faraValoareGresita("confuzie_ocr_pret", Familie.BOLT_RO)

    @Disabled("P-sim-2: cifra dublata in pret ramane necunoscut (toate)")
    @Test
    fun `P-sim-2 - cifra dublata in pret ramane necunoscut`() =
        faraValoareGresita("cifra_dublata_pret", Familie.UBER_RO, Familie.UBER_EN, Familie.BOLT_RO)

    /**
     * P-sim-3, jumatatea PRELUARE (D-315, perspectiva 1): minutele dublate din preluare au 3 cifre (10-45 -> >= 100), iar
     * pe 963 oferte reale preluarea nu trece de 25 min. Garda e `ParserRuleSet.maxMinutePreluare`. Jumatatea CURSA
     * (cursa reala de 111-112 min exista) ramane in testul `@Disabled` de mai jos. Varianta intra aici doar daca
     * asteptarea ei pe minutele preluarii e „necunoscut”, adica segmentul stricat e preluarea.
     */
    private fun faraGresitPeMinutePreluare(familie: Familie) {
        val p = PerturbariSintetice.TOATE.first { it.id == "cifra_dublata_minute" }
        val pePreluare = (0 until GeneratorSintetic.PE_PERTURBARE).map { GeneratorSintetic.genereaza(familie, p, it) }
            .filter { it.asteptat[Camp.MIN_PRELUARE] == Asteptare.Necunoscut }
        assertTrue(pePreluare.isNotEmpty(), "nicio varianta cu preluarea stricata: filtrul nu mai prinde nimic")
        GeneratorSintetic.seturi(familie).forEach { (set, parser) ->
            val gresite = pePreluare.filter { Clasificare.varianta(it.asteptat, parser.parseaza(it.linii)) == Clasa.GRESIT }
            assertTrue(gresite.isEmpty(), "[$set] GRESIT=${gresite.size} din ${pePreluare.size} (preluare): ${gresite.take(3).map { it.nume }}")
        }
    }

    @Test
    fun `P-sim-3 - Uber RO - minutele preluarii dublate (3 cifre) raman necunoscute`() = faraGresitPeMinutePreluare(Familie.UBER_RO)

    @Test
    fun `P-sim-3 - Uber EN - minutele preluarii dublate (3 cifre) raman necunoscute`() = faraGresitPeMinutePreluare(Familie.UBER_EN)

    @Test
    fun `P-sim-3 - Bolt RO - minutele preluarii dublate (3 cifre) raman necunoscute`() = faraGresitPeMinutePreluare(Familie.BOLT_RO)

    @Disabled("P-sim-3: cifra dublata in minutele cursei ramane necunoscuta (preluarea e oprita de maxMinutePreluare)")
    @Test
    fun `P-sim-3 - cifra dublata in minute ramane necunoscuta`() =
        faraValoareGresita("cifra_dublata_minute", Familie.UBER_RO, Familie.UBER_EN, Familie.BOLT_RO)

    @Disabled("P-sim-4: cifra dublata in km ramane necunoscuta (toate)")
    @Test
    fun `P-sim-4 - cifra dublata in km ramane necunoscuta`() =
        faraValoareGresita("cifra_dublata_km", Familie.UBER_RO, Familie.UBER_EN, Familie.BOLT_RO)

    @Disabled("P-sim-5: cifra pierduta din pret ramane necunoscuta (toate)")
    @Test
    fun `P-sim-5 - cifra pierduta din pret ramane necunoscuta`() =
        faraValoareGresita("cifra_pierduta_pret", Familie.UBER_RO, Familie.UBER_EN, Familie.BOLT_RO)

    @Disabled("P-sim-6: cifra pierduta din minute ramane necunoscuta (toate)")
    @Test
    fun `P-sim-6 - cifra pierduta din minute ramane necunoscuta`() =
        faraValoareGresita("cifra_pierduta_minute", Familie.UBER_RO, Familie.UBER_EN, Familie.BOLT_RO)

    @Disabled("P-sim-7: cifra pierduta din km ramane necunoscuta (toate)")
    @Test
    fun `P-sim-7 - cifra pierduta din km ramane necunoscuta`() =
        faraValoareGresita("cifra_pierduta_km", Familie.UBER_RO, Familie.UBER_EN, Familie.BOLT_RO)

    @Disabled("P-sim-8: pretul taiat la stanga ramane necunoscut (Uber RO, Bolt RO)")
    @Test
    fun `P-sim-8 - pretul taiat la stanga ramane necunoscut`() =
        faraValoareGresita("taiat_stanga_pret", Familie.UBER_RO, Familie.BOLT_RO)

    @Disabled("P-sim-9: minutele taiate la stanga raman necunoscute (toate)")
    @Test
    fun `P-sim-9 - minutele taiate la stanga raman necunoscute`() =
        faraValoareGresita("taiat_stanga_minute", Familie.UBER_RO, Familie.UBER_EN, Familie.BOLT_RO)

    @Test
    fun `P-sim-10 - punctul zecimal mutat la stanga ramane necunoscut`() =
        faraValoareGresita("zecimala_mutata_km", Familie.UBER_RO, Familie.UBER_EN, Familie.BOLT_RO)
}
