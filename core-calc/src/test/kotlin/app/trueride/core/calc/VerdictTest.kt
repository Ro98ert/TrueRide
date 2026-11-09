package app.trueride.core.calc

import app.trueride.core.model.CostInput
import app.trueride.core.model.Distance
import app.trueride.core.model.DistanceUnit
import app.trueride.core.model.OfferInput
import app.trueride.core.model.Thresholds
import app.trueride.core.model.Verdict
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regula de verdict din D-082 (inlocuieste spec 4.4 punctul 5):
 *   pe un indicator: `>=` Excelent -> EXCELLENT; `>=` Bun -> GOOD; sub Bun -> WEAK;
 *   verdictul cardului = cea mai slaba treapta dintre indicatorii cu prag Bun activ
 *   si valoare calculabila; niciunul -> INSUFFICIENT_DATA.
 * Regula de 85 % a disparut: sub Bun e Slab, oricat de putin.
 */
class VerdictTest {

    private val costPeKm = CostInput(BigDecimal("0.63"), DistanceUnit.KM)

    /** Implicitele D-082.4: lei/km, Bun 2,50, Excelent 3,00. */
    private val implicite = Thresholds(minPerDistance = BigDecimal("2.50"), excellentPerDistance = BigDecimal("3.00"))

    /**
     * Oferta de control: 30 min, 14,7 km. Pretul il alegem in fiecare test ca
     * sa nimerim indicatorul dorit.
     *   venit pe ora = pret * 2   (60 / 30 = 2)
     *   venit pe km  = pret / 14,7
     */
    private fun oferta(pret: String) = OfferInput(
        price = BigDecimal(pret),
        pickupMinutes = BigDecimal("10"),
        tripMinutes = BigDecimal("20"),
        pickupDistance = Distance(BigDecimal("5.2"), DistanceUnit.KM),
        tripDistance = Distance(BigDecimal("9.5"), DistanceUnit.KM),
    )

    private fun verdict(pret: String, praguri: Thresholds): Verdict =
        OfferCalculator.evaluate(oferta(pret), costPeKm, praguri).verdict

    @Test
    fun `toti indicatorii peste pragul Bun dau verdictul GOOD`() {
        // 50 lei -> 100 lei/ora, peste pragul de 80
        assertEquals(Verdict.GOOD, verdict("50.00", Thresholds(minPerHour = BigDecimal("80"))))
    }

    @Test
    fun `un indicator exact pe pragul Bun este considerat atins, deci GOOD`() {
        // 40 lei -> exact 80 lei/ora. D-038: un minim atins e indeplinit.
        assertEquals(Verdict.GOOD, verdict("40.00", Thresholds(minPerHour = BigDecimal("80"))))
    }

    @Test
    fun `sub pragul Bun, oricat de putin, verdictul e WEAK`() {
        // 37 lei -> 74 lei/ora. Inainte de D-082 era FAIR (peste 85 % din 80).
        assertEquals(Verdict.WEAK, verdict("37.00", Thresholds(minPerHour = BigDecimal("80"))))
    }

    @Test
    fun `mult sub pragul Bun verdictul e WEAK`() {
        // 33 lei -> 66 lei/ora
        assertEquals(Verdict.WEAK, verdict("33.00", Thresholds(minPerHour = BigDecimal("80"))))
    }

    @Test
    fun `exact la 85 la suta din prag e acum WEAK, regula de 85 la suta a disparut`() {
        // 34 lei -> 68 lei/ora = exact 85 % din 80. Inainte de D-082 era FAIR.
        assertEquals(Verdict.WEAK, verdict("34.00", Thresholds(minPerHour = BigDecimal("80"))))
    }

    @Test
    fun `un singur indicator sub prag trage verdictul la WEAK chiar daca celalalt e bun`() {
        // 50 lei -> 100 lei/ora (peste 80) dar 3,40 lei/km fata de un prag de 10 lei/km.
        val praguri = Thresholds(minPerHour = BigDecimal("80"), minPerDistance = BigDecimal("10"))

        assertEquals(Verdict.WEAK, verdict("50.00", praguri))
    }

    @Test
    fun `fara niciun prag activ verdictul este INSUFFICIENT_DATA`() {
        // spec E4, starea `gol`: verdictul e dezactivat, cardul arata doar cifre
        assertEquals(Verdict.INSUFFICIENT_DATA, verdict("50.00", Thresholds()))
    }

    @Test
    fun `un prag activ fara indicator calculabil da INSUFFICIENT_DATA`() {
        // Prag pe ora, dar oferta nu are nicio durata citita
        val faraDurate = OfferInput(
            price = BigDecimal("50.00"),
            pickupDistance = Distance(BigDecimal("5.2"), DistanceUnit.KM),
            tripDistance = Distance(BigDecimal("9.5"), DistanceUnit.KM),
        )

        val rezultat = OfferCalculator.evaluate(faraDurate, costPeKm, Thresholds(minPerHour = BigDecimal("80")))

        assertEquals(Verdict.INSUFFICIENT_DATA, rezultat.verdict)
    }

    @Test
    fun `pragul pe minut participa la verdict ca oricare altul`() {
        // 30 lei -> 1,00 lei/minut, prag 2,00
        assertEquals(Verdict.WEAK, verdict("30.00", Thresholds(minPerMinute = BigDecimal("2.00"))))
    }

    // --- Pragul Excelent (D-082) -------------------------------------------

    @Test
    fun `exact pe pragul Excelent verdictul e EXCELLENT`() {
        // 50 lei -> 100 lei/ora = exact Excelent. D-082.3: la limita, verdictul mai bun.
        val praguri = Thresholds(minPerHour = BigDecimal("80"), excellentPerHour = BigDecimal("100"))

        assertEquals(Verdict.EXCELLENT, verdict("50.00", praguri))
    }

    @Test
    fun `intre Bun si Excelent verdictul e GOOD`() {
        // 45 lei -> 90 lei/ora
        val praguri = Thresholds(minPerHour = BigDecimal("80"), excellentPerHour = BigDecimal("100"))

        assertEquals(Verdict.GOOD, verdict("45.00", praguri))
    }

    @Test
    fun `fara Excelent, oricat de mult peste Bun, verdictul e cel mult GOOD`() {
        // 60 lei -> 120 lei/ora. D-082.7: Excelent gol -> peste Bun e Bun.
        assertEquals(Verdict.GOOD, verdict("60.00", Thresholds(minPerHour = BigDecimal("80"))))
    }

    @Test
    fun `un Excelent care nu e peste Bun e ignorat, nu inversat`() {
        // D-082.7: pana la corectare, verdictul merge doar cu pragul Bun.
        val egal = Thresholds(minPerHour = BigDecimal("80"), excellentPerHour = BigDecimal("80"))
        val subBun = Thresholds(minPerHour = BigDecimal("80"), excellentPerHour = BigDecimal("70"))

        assertEquals(Verdict.GOOD, verdict("50.00", egal), "100 lei/ora nu devine Excelent pe un Excelent egal cu Bun")
        assertEquals(Verdict.WEAK, verdict("37.00", subBun), "74 lei/ora sub Bun ramane Slab, desi e peste Excelentul gresit")
    }

    @Test
    fun `un Excelent fara Bun nu participa`() {
        // Pragul Bun decide participarea; un Excelent singur ar inventa un verdict.
        assertEquals(Verdict.INSUFFICIENT_DATA, verdict("50.00", Thresholds(excellentPerHour = BigDecimal("60"))))
    }

    @Test
    fun `verdictul e cea mai slaba treapta dintre indicatori`() {
        // 50 lei -> 100 lei/ora (Excelent de la 90) si 3,4013 lei/km (Bun de la 3, fara Excelent).
        val praguri = Thresholds(
            minPerHour = BigDecimal("80"),
            excellentPerHour = BigDecimal("90"),
            minPerDistance = BigDecimal("3.00"),
        )

        assertEquals(Verdict.GOOD, verdict("50.00", praguri))
    }

    @Test
    fun `EXCELLENT doar cand fiecare indicator activ e Excelent`() {
        // 3,4013 lei/km >= 3,40.
        val praguri = Thresholds(
            minPerHour = BigDecimal("80"),
            excellentPerHour = BigDecimal("90"),
            minPerDistance = BigDecimal("3.00"),
            excellentPerDistance = BigDecimal("3.40"),
        )

        assertEquals(Verdict.EXCELLENT, verdict("50.00", praguri))
    }

    @Test
    fun `pe implicitele D-082 egalitatea cu 2,50 si 3,00 da treapta de sus`() {
        // 36,75 / 14,7 = 2,50 exact; 44,10 / 14,7 = 3,00 exact.
        assertEquals(Verdict.GOOD, verdict("36.75", implicite))
        assertEquals(Verdict.EXCELLENT, verdict("44.10", implicite))
    }

    @Test
    fun `un ban sub fiecare prag implicit coboara o treapta`() {
        // 36,74 / 14,7 = 2,4993 (Slab); 44,09 / 14,7 = 2,9993 (Bun, desi se afiseaza "3,00").
        assertEquals(Verdict.WEAK, verdict("36.74", implicite))
        assertEquals(Verdict.GOOD, verdict("44.09", implicite))
    }
}
