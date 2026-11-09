package app.trueride.core.parse.sintetic

/**
 * D-315.3: perturbarile INVENTATE la care parserul da azi o valoare GRESITA (nu necunoscut), gasite la prima rulare a
 * corpusului extins (2026-10-22). Parserul nu s-a schimbat: cazurile intra aici cu id `P-sim-N`, iar `@Disabled`-ul
 * cate unuia e in `CazuriGresiteDezactivateTest`, ca bucla de simulare sa le preia (o garda care da „necunoscut”,
 * acceptata doar cu poarta reala curata). Cheie `FAMILIE.perturbare`, ca in `GeneratorSintetic.CUNOSCUTE_GRESITE`.
 * „Indetectabil din text” = textul stricat arata ca un camp valid; doar o regula de plauzibilitate l-ar putea opri.
 */
internal object CunoscuteSimulare {
    private val TOATE_FAM_ORDINE = listOf(Familie.UBER_RO, Familie.UBER_EN, Familie.BOLT_RO)

    private fun psim(n: Int, perturbare: String, motiv: String, familii: List<Familie> = TOATE_FAM_ORDINE) =
        familii.associate { "$it.$perturbare" to "P-sim-$n: $motiv" }

    val TOATE: Map<String, String> =
        psim(2, "cifra_dublata_pret", "cifra dublata in partea intreaga a pretului da un pret plauzibil dar diferit (indetectabil din text)") +
            psim(3, "cifra_dublata_minute", "cifra dublata in minutele CURSEI da minute plauzibile dar diferite (indetectabil din text; exista curse reale de 111-112 min). Preluarea a iesit: `maxMinutePreluare` o face necunoscuta") +
            psim(4, "cifra_dublata_km", "cifra dublata in km da km plauzibili (viteza sub 150 km/h, D-148) dar diferiti") +
            psim(5, "cifra_pierduta_pret", "cifra pierduta din partea intreaga a pretului da un pret plauzibil dar diferit (indetectabil din text)") +
            psim(6, "cifra_pierduta_minute", "cifra pierduta din minute da minute plauzibile dar diferite (indetectabil din text)") +
            psim(7, "cifra_pierduta_km", "cifra pierduta din km da km plauzibili dar diferiti") +
            psim(8, "taiat_stanga_pret", "marginea din stanga taie prima cifra a pretului: pretul ramas e plauzibil dar gresit", listOf(Familie.UBER_RO, Familie.BOLT_RO)) +
            psim(9, "taiat_stanga_minute", "marginea din stanga taie prima cifra a minutelor: minutele ramase sunt plauzibile dar gresite")
    // P-sim-10 (zecimala_mutata_km) a iesit de aici (D-315): garda „cel mult o zecimala la km” in `distantaPattern` al celor 3
    // familii. Rest nedetectabil, nelistat fiindca generatorul nu produce un km intreg de o cifra: pe Bolt RO „5 km” mutat
    // devine „0.5 km”, identic cu un km real sub 1 (frecvente-erori: km_sub_unu).
}
