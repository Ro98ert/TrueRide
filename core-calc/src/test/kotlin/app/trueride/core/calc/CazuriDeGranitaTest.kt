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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Cele patru clase de cazuri cerute explicit de criteriul M2 (spec 10):
 * impartire la zero, camp lipsa, unitati mixte, valoare negativa.
 *
 * Fiecare test verifica un singur comportament si e numit dupa comportament,
 * nu dupa functia apelata.
 */
class CazuriDeGranitaTest {

    private val costPeKm = CostInput(BigDecimal("0.63"), DistanceUnit.KM)
    private val praguri = Thresholds(minPerHour = BigDecimal("80"))
    private val faraPraguri = Thresholds()

    // ---------------------------------------------------------------- //
    // 1. Impartire la zero — spec 4.4, regula 3                        //
    // ---------------------------------------------------------------- //

    @Test
    fun `timpul total zero face indicatorii pe timp indisponibili, nu infinit si nu zero`() {
        // Arrange — o oferta in care ambele durate sunt zero
        val oferta = OfferInput(
            price = BigDecimal("33.07"),
            pickupMinutes = BigDecimal.ZERO,
            tripMinutes = BigDecimal.ZERO,
            pickupDistance = Distance(BigDecimal("5.2"), DistanceUnit.KM),
            tripDistance = Distance(BigDecimal("9.5"), DistanceUnit.KM),
        )

        // Act
        val rezultat = OfferCalculator.evaluate(oferta, costPeKm, faraPraguri)

        // Assert — indisponibil inseamna null, nu 0
        assertNull(rezultat.perHour, "venit pe ora la timp zero")
        assertNull(rezultat.perMinute, "venit pe minut la timp zero")
        // ...dar indicatorul pe distanta nu are nimic de-a face cu timpul
        assertNotNull(rezultat.perDistance, "venit pe distanta ramane calculabil")
    }

    @Test
    fun `distanta totala zero face venitul pe distanta indisponibil`() {
        val oferta = OfferInput(
            price = BigDecimal("33.07"),
            pickupMinutes = BigDecimal("10"),
            tripMinutes = BigDecimal("20"),
            pickupDistance = Distance(BigDecimal.ZERO, DistanceUnit.KM),
            tripDistance = Distance(BigDecimal.ZERO, DistanceUnit.KM),
        )

        val rezultat = OfferCalculator.evaluate(oferta, costPeKm, faraPraguri)

        assertNull(rezultat.perDistance, "venit pe distanta la distanta zero")
        assertNotNull(rezultat.perHour, "venit pe ora ramane calculabil")
    }

    // ---------------------------------------------------------------- //
    // 2. Camp lipsa — spec 4.4, regula 4                               //
    // ---------------------------------------------------------------- //

    @Test
    fun `distanta de preluare lipsa produce un rezultat marcat PARTIAL`() {
        // Arrange — exact scenariul din spec 4.4 regula 4: distanta pana la
        // client nu e vizibila pe ecran
        val oferta = OfferInput(
            price = BigDecimal("33.07"),
            pickupMinutes = BigDecimal("10"),
            tripMinutes = BigDecimal("20"),
            pickupDistance = null,
            tripDistance = Distance(BigDecimal("9.5"), DistanceUnit.KM),
        )

        val rezultat = OfferCalculator.evaluate(oferta, costPeKm, faraPraguri)

        // Se calculeaza cu ce exista, dar rezultatul isi declara incompletitudinea.
        // Ce depinde de distanta totala e acoperit in sectiunea 6 (D-072).
        assertEquals(DataCompleteness.PARTIAL, rezultat.completeness)
    }

    @Test
    fun `un camp lipsa nu devine niciodata zero`() {
        // Daca timpul de preluare lipseste, timpul total nu e 0 + 20 = 20.
        // Nici doar 20 (cursa): e necunoscut, si marcat partial (D-072).
        val oferta = OfferInput(
            price = BigDecimal("33.07"),
            pickupMinutes = null,
            tripMinutes = BigDecimal("20"),
            pickupDistance = Distance(BigDecimal("5.2"), DistanceUnit.KM),
            tripDistance = Distance(BigDecimal("9.5"), DistanceUnit.KM),
        )

        val rezultat = OfferCalculator.evaluate(oferta, costPeKm, faraPraguri)

        assertNull(rezultat.totalMinutes)
        assertEquals(DataCompleteness.PARTIAL, rezultat.completeness)
    }

    @Test
    fun `pretul lipsa face toti indicatorii de venit indisponibili`() {
        val oferta = OfferInput(
            price = null,
            pickupMinutes = BigDecimal("10"),
            tripMinutes = BigDecimal("20"),
            pickupDistance = Distance(BigDecimal("5.2"), DistanceUnit.KM),
            tripDistance = Distance(BigDecimal("9.5"), DistanceUnit.KM),
        )

        val rezultat = OfferCalculator.evaluate(oferta, costPeKm, praguri)

        assertNull(rezultat.perHour)
        assertNull(rezultat.perDistance)
        assertNull(rezultat.perMinute)
        assertNull(rezultat.estimatedProfit, "profitul are nevoie de pret")
        assertEquals(Verdict.INSUFFICIENT_DATA, rezultat.verdict)
        // ...dar costul masinii nu depinde de pret si ramane calculabil
        assertNotNull(rezultat.estimatedCost)
    }

    @Test
    fun `fara profil de cost, costul si profitul sunt indisponibile dar restul se calculeaza`() {
        val oferta = OfferInput(
            price = BigDecimal("33.07"),
            pickupMinutes = BigDecimal("10"),
            tripMinutes = BigDecimal("20"),
            pickupDistance = Distance(BigDecimal("5.2"), DistanceUnit.KM),
            tripDistance = Distance(BigDecimal("9.5"), DistanceUnit.KM),
        )

        val rezultat = OfferCalculator.evaluate(
            oferta,
            CostInput(costPerDistance = null, unit = DistanceUnit.KM),
            faraPraguri,
        )

        assertNull(rezultat.estimatedCost)
        assertNull(rezultat.estimatedProfit)
        assertNotNull(rezultat.perHour)
    }

    // ---------------------------------------------------------------- //
    // 3. Unitati mixte                                                  //
    // ---------------------------------------------------------------- //

    @Test
    fun `distantele in unitati diferite se normalizeaza la unitatea costului inainte de adunare`() {
        // Arrange — preluarea citita in km, cursa citita in mile
        val oferta = OfferInput(
            price = BigDecimal("33.07"),
            pickupMinutes = BigDecimal("10"),
            tripMinutes = BigDecimal("20"),
            pickupDistance = Distance(BigDecimal("5.2"), DistanceUnit.KM),
            tripDistance = Distance(BigDecimal("3"), DistanceUnit.MI),
        )

        val rezultat = OfferCalculator.evaluate(oferta, costPeKm, faraPraguri)

        // 3 mi = 4,828032 km; total = 5,2 + 4,828032 = 10,028032 km
        assertEquals(DistanceUnit.KM, rezultat.totalDistance?.unit)
        assertEquals(0, rezultat.totalDistance?.amount?.compareTo(BigDecimal("10.028032")))
    }

    @Test
    fun `un cost exprimat pe mila impune normalizarea distantelor in mile`() {
        val oferta = OfferInput(
            price = BigDecimal("33.07"),
            pickupMinutes = BigDecimal("10"),
            tripMinutes = BigDecimal("20"),
            pickupDistance = Distance(BigDecimal("1.609344"), DistanceUnit.KM),
            tripDistance = Distance(BigDecimal("2"), DistanceUnit.MI),
        )

        val rezultat = OfferCalculator.evaluate(
            oferta,
            CostInput(BigDecimal("1.00"), DistanceUnit.MI),
            faraPraguri,
        )

        // 1,609344 km = 1 mi; total = 3 mi
        assertEquals(DistanceUnit.MI, rezultat.totalDistance?.unit)
        assertEquals(0, rezultat.totalDistance?.amount?.compareTo(BigDecimal("3")))
    }

    // ---------------------------------------------------------------- //
    // 4. Valoare negativa                                               //
    // ---------------------------------------------------------------- //

    @Test
    fun `o distanta negativa se trateaza ca necitita, nu se aduna`() {
        // O distanta negativa nu exista fizic: e o citire gresita a parserului
        // (semn de minus preluat dintr-un element vecin). Se propaga ca lipsa,
        // niciodata ca numar, pentru ca un total micsorat de un termen negativ
        // ar produce un venit-pe-km fals de optimist. Ca lipsa, face totalul
        // de distanta necunoscut (D-072).
        val oferta = OfferInput(
            price = BigDecimal("33.07"),
            pickupMinutes = BigDecimal("10"),
            tripMinutes = BigDecimal("20"),
            pickupDistance = Distance(BigDecimal("-5.2"), DistanceUnit.KM),
            tripDistance = Distance(BigDecimal("9.5"), DistanceUnit.KM),
        )

        val rezultat = OfferCalculator.evaluate(oferta, costPeKm, faraPraguri)

        assertNull(rezultat.totalDistance)
        assertEquals(DataCompleteness.PARTIAL, rezultat.completeness)
    }

    @Test
    fun `un pret negativ se trateaza ca necitit`() {
        val oferta = OfferInput(
            price = BigDecimal("-33.07"),
            pickupMinutes = BigDecimal("10"),
            tripMinutes = BigDecimal("20"),
            pickupDistance = Distance(BigDecimal("5.2"), DistanceUnit.KM),
            tripDistance = Distance(BigDecimal("9.5"), DistanceUnit.KM),
        )

        val rezultat = OfferCalculator.evaluate(oferta, costPeKm, praguri)

        assertNull(rezultat.perHour)
        assertEquals(Verdict.INSUFFICIENT_DATA, rezultat.verdict)
    }

    @Test
    fun `o durata negativa se trateaza ca necitita`() {
        val oferta = OfferInput(
            price = BigDecimal("33.07"),
            pickupMinutes = BigDecimal("-10"),
            tripMinutes = BigDecimal("20"),
            pickupDistance = Distance(BigDecimal("5.2"), DistanceUnit.KM),
            tripDistance = Distance(BigDecimal("9.5"), DistanceUnit.KM),
        )

        val rezultat = OfferCalculator.evaluate(oferta, costPeKm, faraPraguri)

        assertNull(rezultat.totalMinutes, "durata negativa = lipsa, deci total necunoscut")
        assertEquals(DataCompleteness.PARTIAL, rezultat.completeness)
    }

    // ---------------------------------------------------------------- //
    // 5. Absenta totala — starile in care motorul trebuie sa taca       //
    // ---------------------------------------------------------------- //

    @Test
    fun `o oferta complet goala nu produce niciun indicator`() {
        val rezultat = OfferCalculator.evaluate(OfferInput(), costPeKm, praguri)

        assertNull(rezultat.totalMinutes)
        assertNull(rezultat.totalDistance)
        assertNull(rezultat.perHour)
        assertNull(rezultat.perDistance)
        assertNull(rezultat.perMinute)
        assertNull(rezultat.estimatedCost)
        assertNull(rezultat.estimatedProfit)
        assertEquals(Verdict.INSUFFICIENT_DATA, rezultat.verdict)
        assertEquals(DataCompleteness.PARTIAL, rezultat.completeness)
    }

    @Test
    fun `fara nicio distanta citita, costul estimat ramane indisponibil desi profilul de cost exista`() {
        // Profilul de cost e complet, dar nu are pe ce sa se aplice.
        // Un cost de 0 lei ar fi un raspuns fals: masina consuma, doar ca
        // noi nu stim cat a mers.
        val oferta = OfferInput(
            price = BigDecimal("33.07"),
            pickupMinutes = BigDecimal("10"),
            tripMinutes = BigDecimal("20"),
        )

        val rezultat = OfferCalculator.evaluate(oferta, costPeKm, faraPraguri)

        assertNull(rezultat.estimatedCost)
        assertNull(rezultat.estimatedProfit)
        assertNull(rezultat.perDistance)
        assertNotNull(rezultat.perHour, "indicatorii pe timp raman calculabili")
    }

    @Test
    fun `un cost negativ pe distanta se trateaza ca lipsa de profil de cost`() {
        val oferta = OfferInput(
            price = BigDecimal("33.07"),
            pickupMinutes = BigDecimal("10"),
            tripMinutes = BigDecimal("20"),
            pickupDistance = Distance(BigDecimal("5.2"), DistanceUnit.KM),
            tripDistance = Distance(BigDecimal("9.5"), DistanceUnit.KM),
        )

        val rezultat = OfferCalculator.evaluate(
            oferta,
            CostInput(BigDecimal("-0.63"), DistanceUnit.KM),
            faraPraguri,
        )

        assertNull(rezultat.estimatedCost)
        assertNull(rezultat.estimatedProfit)
    }

    // ---------------------------------------------------------------- //
    // 6. Numitor incomplet — D-072 (F-006)                              //
    // ---------------------------------------------------------------- //
    // Un total cu un segment lipsa nu e "aproape corect": pe teren, lei/km
    // impartit doar la distanta preluarii a iesit de ~35x prea mare. Totalul
    // e necunoscut daca lipseste oricare segment; indicatorii care depind de
    // el devin "—". Cei cu numitorul complet raman calculati.
    // Datele: pret 33,07 · 10 + 20 min · 5,2 + 9,5 km (total 30 min, 14,7 km).

    @Test
    fun `distanta cursei lipsa face lei pe km, totalul de km si costul indisponibile`() {
        // Arrange — cazul de pe teren: distanta cursei necitita
        val oferta = OfferInput(
            price = BigDecimal("33.07"),
            pickupMinutes = BigDecimal("10"),
            tripMinutes = BigDecimal("20"),
            pickupDistance = Distance(BigDecimal("5.2"), DistanceUnit.KM),
            tripDistance = null,
        )

        // Act
        val rezultat = OfferCalculator.evaluate(oferta, costPeKm, faraPraguri)

        // Assert — nimic calculat pe distanta ramasa (33,07 / 5,2 = 6,36 fals)
        assertNull(rezultat.totalDistance, "total km cu cursa necitita")
        assertNull(rezultat.perDistance, "lei/km cu cursa necitita")
        assertNull(rezultat.estimatedCost, "cost pe km cu cursa necitita")
        assertNull(rezultat.estimatedProfit, "profitul depinde de cost")
        // ...dar numitorul pe timp e complet: 33,07 * 60 / 30 = 66,14
        assertEquals(0, rezultat.perHour?.compareTo(BigDecimal("66.14")))
        assertEquals(0, rezultat.totalMinutes?.compareTo(BigDecimal("30")))
        assertNotNull(rezultat.perMinute)
        assertEquals(DataCompleteness.PARTIAL, rezultat.completeness)
    }

    @Test
    fun `distanta preluarii lipsa face lei pe km, totalul de km si costul indisponibile`() {
        val oferta = OfferInput(
            price = BigDecimal("33.07"),
            pickupMinutes = BigDecimal("10"),
            tripMinutes = BigDecimal("20"),
            pickupDistance = null,
            tripDistance = Distance(BigDecimal("9.5"), DistanceUnit.KM),
        )

        val rezultat = OfferCalculator.evaluate(oferta, costPeKm, faraPraguri)

        assertNull(rezultat.totalDistance, "total km cu preluarea necitita")
        assertNull(rezultat.perDistance, "lei/km cu preluarea necitita")
        assertNull(rezultat.estimatedCost, "cost pe km cu preluarea necitita")
        assertEquals(0, rezultat.perHour?.compareTo(BigDecimal("66.14")))
        assertNotNull(rezultat.perMinute)
        assertEquals(DataCompleteness.PARTIAL, rezultat.completeness)
    }

    @Test
    fun `minutele cursei lipsa fac lei pe ora, lei pe minut si timpul total indisponibile`() {
        // Cazul celor 2 din 8 fixture-uri Uber cu timpul cursei mascat (D-035)
        val oferta = OfferInput(
            price = BigDecimal("33.07"),
            pickupMinutes = BigDecimal("10"),
            tripMinutes = null,
            pickupDistance = Distance(BigDecimal("5.2"), DistanceUnit.KM),
            tripDistance = Distance(BigDecimal("9.5"), DistanceUnit.KM),
        )

        val rezultat = OfferCalculator.evaluate(oferta, costPeKm, faraPraguri)

        assertNull(rezultat.totalMinutes, "timp total cu cursa necitita")
        assertNull(rezultat.perHour, "lei/ora cu cursa necitita")
        assertNull(rezultat.perMinute, "lei/min cu cursa necitita")
        // ...dar numitorul pe distanta e complet: 14,7 km, cost 0,63 * 14,7 = 9,261
        assertEquals(0, rezultat.totalDistance?.amount?.compareTo(BigDecimal("14.7")))
        assertNotNull(rezultat.perDistance)
        assertEquals(0, rezultat.estimatedCost?.compareTo(BigDecimal("9.261")))
        assertEquals(DataCompleteness.PARTIAL, rezultat.completeness)
    }

    @Test
    fun `minutele preluarii lipsa fac lei pe ora, lei pe minut si timpul total indisponibile`() {
        val oferta = OfferInput(
            price = BigDecimal("33.07"),
            pickupMinutes = null,
            tripMinutes = BigDecimal("20"),
            pickupDistance = Distance(BigDecimal("5.2"), DistanceUnit.KM),
            tripDistance = Distance(BigDecimal("9.5"), DistanceUnit.KM),
        )

        val rezultat = OfferCalculator.evaluate(oferta, costPeKm, faraPraguri)

        assertNull(rezultat.totalMinutes, "timp total cu preluarea necitita")
        assertNull(rezultat.perHour, "lei/ora cu preluarea necitita")
        assertNull(rezultat.perMinute, "lei/min cu preluarea necitita")
        assertEquals(0, rezultat.totalDistance?.amount?.compareTo(BigDecimal("14.7")))
        assertNotNull(rezultat.perDistance)
        assertNotNull(rezultat.estimatedCost)
        assertEquals(DataCompleteness.PARTIAL, rezultat.completeness)
    }

    @Test
    fun `verdictul nu se sprijina pe un lei pe km calculat pe distanta ramasa`() {
        // Cu suma partiala, 33,07 / 5,2 = 6,36 lei/km trecea pragul de 3 si
        // dadea verdict BUN pe o cifra falsa. Fara lei/km, pragul nu participa.
        val oferta = OfferInput(
            price = BigDecimal("33.07"),
            pickupMinutes = BigDecimal("10"),
            tripMinutes = BigDecimal("20"),
            pickupDistance = Distance(BigDecimal("5.2"), DistanceUnit.KM),
            tripDistance = null,
        )

        val rezultat = OfferCalculator.evaluate(
            oferta,
            costPeKm,
            Thresholds(minPerDistance = BigDecimal("3")),
        )

        assertEquals(Verdict.INSUFFICIENT_DATA, rezultat.verdict)
    }
}
