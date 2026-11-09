package app.trueride.core.calc

import app.trueride.core.model.CostInput
import app.trueride.core.model.DataCompleteness
import app.trueride.core.model.Distance
import app.trueride.core.model.DistanceUnit
import app.trueride.core.model.OfferInput
import app.trueride.core.model.Thresholds
import app.trueride.core.model.Verdict
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Vectorul de test verificat din spec 4.5.
 *
 * Acesta este **primul test scris in tot proiectul**, inainte de orice linie de
 * implementare. Valorile lui nu sunt inventate: au fost verificate impotriva
 * unei implementari reale in productie. Daca acest test trece, cele sapte
 * formule sunt corecte pe cazul de referinta; daca pica, orice altceva e
 * irelevant.
 */
class VectorDeReferintaTest {

    /** Intrarile din tabelul de intrari, spec 4.5. */
    private val oferta = OfferInput(
        price = BigDecimal("33.07"),
        pickupMinutes = BigDecimal("10"),
        tripMinutes = BigDecimal("20"),
        pickupDistance = Distance(BigDecimal("5.2"), DistanceUnit.KM),
        tripDistance = Distance(BigDecimal("9.5"), DistanceUnit.KM),
    )

    private val cost = CostInput(
        costPerDistance = BigDecimal("0.63"),
        unit = DistanceUnit.KM,
    )

    private val praguri = Thresholds(
        minPerHour = BigDecimal("80"),
        minPerDistance = BigDecimal("3.00"),
    )

    @Test
    fun `vectorul de referinta reproduce exact cele sapte iesiri si verdictul`() {
        // Act
        val rezultat = OfferCalculator.evaluate(oferta, cost, praguri)

        // Assert — cele sapte iesiri, rotunjite pentru afisare
        assertEquals(BigDecimal("30"), DisplayRounding.minutes(rezultat.totalMinutes), "timp total")
        assertEquals(BigDecimal("14.70"), DisplayRounding.distance(rezultat.totalDistance?.amount), "distanta totala")
        assertEquals(BigDecimal("2.25"), DisplayRounding.money(rezultat.perDistance), "venit pe distanta")
        assertEquals(BigDecimal("66"), DisplayRounding.perHour(rezultat.perHour), "venit pe ora")
        assertEquals(BigDecimal("1.10"), DisplayRounding.money(rezultat.perMinute), "venit pe minut")
        assertEquals(BigDecimal("9.26"), DisplayRounding.money(rezultat.estimatedCost), "cost estimat")
        assertEquals(BigDecimal("23.81"), DisplayRounding.money(rezultat.estimatedProfit), "profit estimat")

        // Assert — verdictul
        assertEquals(Verdict.WEAK, rezultat.verdict, "verdict")
        assertEquals(DataCompleteness.FULL, rezultat.completeness, "completitudine")
    }

    @Test
    fun `distanta totala pastreaza unitatea kilometri`() {
        val rezultat = OfferCalculator.evaluate(oferta, cost, praguri)

        assertEquals(DistanceUnit.KM, rezultat.totalDistance?.unit)
    }

    @Test
    fun `venitul pe distanta intra nerotunjit in comparatia cu pragul`() {
        // Nota de implementare din spec 4.5: valoarea afisata e 2,25, dar cea
        // comparata cu pragul e 2,2497. Aici verificam ca motorul chiar
        // pastreaza valoarea nerotunjita, nu cea de pe card.
        val rezultat = OfferCalculator.evaluate(oferta, cost, praguri)

        val perDistance = requireNotNull(rezultat.perDistance)
        assertEquals(
            0,
            perDistance.compareTo(BigDecimal("2.249659863945578")),
            "venit pe distanta nerotunjit, la precizia de lucru",
        )
    }
}
