package app.trueride.core.lant

import app.trueride.core.lant.Eveniment.CadruNegru
import app.trueride.core.lant.Eveniment.EcranSchimbat
import app.trueride.core.lant.Eveniment.NoduriCitite
import app.trueride.core.lant.Eveniment.OcrCitit
import app.trueride.core.lant.Eveniment.OcrEsuat
import app.trueride.core.lant.PlatformaSofer.BOLT
import app.trueride.core.lant.PlatformaSofer.NECUNOSCUTA
import app.trueride.core.lant.PlatformaSofer.UBER
import app.trueride.core.model.CostInput
import app.trueride.core.model.DataCompleteness
import app.trueride.core.model.DistanceUnit
import app.trueride.core.model.Thresholds
import app.trueride.core.model.Verdict
import app.trueride.core.parse.OfferParser
import app.trueride.core.parse.UBER_RO_V1
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AutomatCardIesiriTest {

    private val uber = OfferParser(UBER_RO_V1)
    private val automat = AutomatCard(mapOf(UBER to uber, NECUNOSCUTA to uber))
    private val cuCost = Setari(
        cost = CostInput(BigDecimal("0.63"), DistanceUnit.KM),
        praguri = Thresholds(minPerHour = BigDecimal("30")),
    )

    private fun lant(setari: Setari, vararg evenimente: Eveniment): Pas =
        evenimente.fold(Pas(StareLant(setari = setari))) { pas, e -> automat.trateaza(pas.stare, e) }

    /** Schimbare de ecran + noduri fara text + OCR cu [linii]: drumul Uber de azi. */
    private fun prinOcr(platforma: PlatformaSofer, linii: List<app.trueride.core.parse.LinieOcr>, setari: Setari = cuCost) =
        lant(setari, EcranSchimbat(1, platforma), NoduriCitite(1, emptyList()), OcrCitit(1, linii))

    private fun egal(asteptat: String, real: BigDecimal?) =
        assertEquals(0, BigDecimal(asteptat).compareTo(requireNotNull(real)), "asteptat $asteptat, primit $real")

    @Test
    fun `OCR cu pret da succes cu evaluarea calculata`() {
        val card = assertIs<StareCard.Succes>(prinOcr(UBER, Ecrane.ofertaCompleta).stare.card)

        egal("9.47", card.oferta.price)
        egal("3.78", card.evaluare.estimatedCost)
        egal("5.69", card.evaluare.estimatedProfit)
        assertEquals(Verdict.GOOD, card.evaluare.verdict)
        assertEquals(DataCompleteness.FULL, card.evaluare.completeness)
    }

    @Test
    fun `OCR fara pret dar cu segmente da eroare necitibila abia dupa limita`() {
        val pas = prinOcr(UBER, Ecrane.ofertaFaraPret)
        assertEquals(StareCard.Gol, pas.stare.card)
        assertEquals(
            StareCard.Eroare(UBER, MotivEroare.NECITIBILA),
            automat.trateaza(pas.stare, Eveniment.AsteptareExpirata(1)).stare.card,
        )
    }

    @Test
    fun `OCR fara niciun camp de oferta ascunde cardul`() {
        assertEquals(StareCard.Gol, prinOcr(UBER, Ecrane.faraOferta).stare.card)
    }

    // D-130 (F-020): castigul zilei de pe ecranul principal nu devine card cu pret.
    @Test
    fun `ecranul principal cu castigul zilei nu produce card`() {
        assertEquals(StareCard.Gol, prinOcr(UBER, Ecrane.ecranPrincipal).stare.card)
    }

    @Test
    fun `cadrul negru da eroare de ecran protejat`() {
        val pas = lant(cuCost, EcranSchimbat(1, UBER), NoduriCitite(1, emptyList()), CadruNegru(1))

        assertEquals(StareCard.Eroare(UBER, MotivEroare.ECRAN_PROTEJAT), pas.stare.card)
    }

    @Test
    fun `OCR esuat ascunde cardul`() {
        val pas = lant(cuCost, EcranSchimbat(1, UBER), NoduriCitite(1, emptyList()), OcrEsuat(1))

        assertEquals(StareCard.Gol, pas.stare.card)
    }

    @Test
    fun `inchiderea ascunde cardul si ignora rezultatul in curs`() {
        val inchis = lant(cuCost, EcranSchimbat(1, UBER), NoduriCitite(1, emptyList()), Eveniment.Inchis)
        val tarziu = automat.trateaza(inchis.stare, OcrCitit(1, Ecrane.ofertaCompleta))
        val urmatoarea = automat.trateaza(tarziu.stare, EcranSchimbat(2, UBER))

        assertEquals(StareCard.Gol, inchis.stare.card)
        assertEquals(StareCard.Gol, tarziu.stare.card)
        assertEquals(StareCard.Gol, urmatoarea.stare.card)
        assertEquals(listOf<Comanda>(Comanda.CitesteNoduri(2)), urmatoarea.comenzi)
    }

    @Test
    fun `alta aplicatie in fata ascunde cifrele ofertei anterioare`() {
        val oferta = prinOcr(UBER, Ecrane.ofertaCompleta)
        assertIs<StareCard.Succes>(oferta.stare.card)

        val pas = automat.trateaza(oferta.stare, Eveniment.AltaAplicatie)

        assertEquals(StareCard.Gol, pas.stare.card)
    }

    @Test
    fun `alta aplicatie ignora citirea in curs, iar urmatorul ecran de sofer se citeste`() {
        val plecat = lant(cuCost, EcranSchimbat(1, UBER), NoduriCitite(1, emptyList()), Eveniment.AltaAplicatie)
        val tarziu = automat.trateaza(plecat.stare, OcrCitit(1, Ecrane.ofertaCompleta))
        val revenit = automat.trateaza(tarziu.stare, EcranSchimbat(2, UBER))

        assertEquals(StareCard.Gol, plecat.stare.card)
        assertEquals(StareCard.Gol, tarziu.stare.card)
        assertEquals(StareCard.Gol, revenit.stare.card)
        assertEquals(listOf<Comanda>(Comanda.CitesteNoduri(2)), revenit.comenzi)
    }

    @Test
    fun `oprirea ascunde cardul din orice stare`() {
        val dinSucces = automat.trateaza(prinOcr(UBER, Ecrane.ofertaCompleta).stare, Eveniment.Oprit)
        val dinIncarcare = lant(cuCost, EcranSchimbat(1, UBER), Eveniment.Oprit)
        val tarziu = automat.trateaza(dinIncarcare.stare, NoduriCitite(1, Ecrane.ofertaCompleta))

        assertEquals(StareCard.Gol, dinSucces.stare.card)
        assertEquals(StareCard.Gol, tarziu.stare.card)
    }

    @Test
    fun `captura intrerupta ascunde cardul cu oferta si nu cere nimic`() {
        val pas = automat.trateaza(prinOcr(UBER, Ecrane.ofertaCompleta).stare, Eveniment.CapturaIntrerupta)

        assertEquals(StareCard.Gol, pas.stare.card)
        assertTrue(pas.comenzi.isEmpty())
    }

    @Test
    fun `dupa captura intrerupta o citire tarzie nu mai pune cifre, iar ecranul urmator se citeste`() {
        val intrerupt = lant(cuCost, EcranSchimbat(1, UBER), NoduriCitite(1, emptyList()), Eveniment.CapturaIntrerupta)
        val tarziu = automat.trateaza(intrerupt.stare, OcrCitit(1, Ecrane.ofertaCompleta))
        val reluat = automat.trateaza(tarziu.stare, EcranSchimbat(2, UBER))

        assertEquals(StareCard.Gol, tarziu.stare.card)
        assertEquals(listOf<Comanda>(Comanda.CitesteNoduri(2)), reluat.comenzi)
    }

    @Test
    fun `setarile noi se aplica ofertei urmatoare`() {
        val fara = Setari(cost = null, praguri = Thresholds())
        val pas = lant(
            fara,
            Eveniment.SetariSchimbate(cuCost),
            EcranSchimbat(1, UBER),
            NoduriCitite(1, emptyList()),
            OcrCitit(1, Ecrane.ofertaCompleta),
        )

        egal("5.69", assertIs<StareCard.Succes>(pas.stare.card).evaluare.estimatedProfit)
    }

    @Test
    fun `fara profil de cost profitul e necunoscut iar verdictul vine din praguri`() {
        val setari = Setari(cost = null, praguri = Thresholds(minPerHour = BigDecimal("30")))
        val card = assertIs<StareCard.Succes>(prinOcr(UBER, Ecrane.ofertaCompleta, setari).stare.card)

        assertNull(card.evaluare.estimatedCost)
        assertNull(card.evaluare.estimatedProfit)
        assertEquals(Verdict.GOOD, card.evaluare.verdict)
    }

    @Test
    fun `timpul cursei lipsa marcheaza evaluarea ca partiala`() {
        val card = assertIs<StareCard.Succes>(prinOcr(UBER, Ecrane.ofertaFaraTimpCursa).stare.card)

        assertNull(card.oferta.tripMinutes)
        assertEquals(DataCompleteness.PARTIAL, card.evaluare.completeness)
    }

    @Test
    fun `pe Bolt fara reguli nicio oferta nu ajunge pe card`() {
        // Automatul testului n-are parser Bolt: o platforma fara reguli nu produce niciodata o oferta.
        val dupaNoduri = lant(cuCost, EcranSchimbat(1, BOLT), NoduriCitite(1, Ecrane.ofertaCompleta))
        val dupaOcr = automat.trateaza(dupaNoduri.stare, OcrCitit(1, Ecrane.ofertaCompleta))

        assertEquals(listOf<Comanda>(Comanda.FaOcr(1)), dupaNoduri.comenzi)
        assertEquals(StareCard.Gol, dupaOcr.stare.card)
    }

    @Test
    fun `fara accessibility platforma necunoscuta foloseste regulile Uber`() {
        val card = assertIs<StareCard.Succes>(prinOcr(NECUNOSCUTA, Ecrane.ofertaCompleta).stare.card)

        assertEquals(NECUNOSCUTA, card.platforma)
    }

    @Test
    fun `unitatea lipsa din oferta se ia din profil`() {
        val oferta = app.trueride.core.model.ParsedOffer(
            price = BigDecimal("10"),
            pickupMinutes = BigDecimal("5"),
            // Ambele distante: cu una lipsa, totalul de km e necunoscut (D-072)
            // si testul n-ar mai avea pe ce sa verifice unitatea.
            pickupDistance = BigDecimal("1"),
            tripDistance = BigDecimal("4"),
        )

        val e = Evaluare.calculeaza(oferta, cuCost)

        assertEquals(DistanceUnit.KM, e.totalDistance?.unit)
        assertTrue(e.estimatedCost != null)
    }

    @Test
    fun `cadrul negru in etapa nodurilor da eroare de ecran protejat fara a cere OCR`() {
        val pas = lant(cuCost, EcranSchimbat(1, UBER), CadruNegru(1))

        assertEquals(StareCard.Eroare(UBER, MotivEroare.ECRAN_PROTEJAT), pas.stare.card)
        assertTrue(pas.comenzi.isEmpty())
        assertEquals(Etapa.GATA, pas.stare.etapa)
    }

    @Test
    fun `nodurile cu pret sosite dupa cadrul negru nu ajung pe card`() {
        val pas = lant(cuCost, EcranSchimbat(1, UBER), CadruNegru(1), NoduriCitite(1, Ecrane.ofertaCompleta))

        assertEquals(StareCard.Eroare(UBER, MotivEroare.ECRAN_PROTEJAT), pas.stare.card)
    }

    @Test
    fun `cifrele ofertei anterioare dispar la intrarea in negru`() {
        val pas = lant(
            cuCost,
            EcranSchimbat(1, UBER), NoduriCitite(1, Ecrane.ofertaCompleta),
            EcranSchimbat(2, UBER), CadruNegru(2),
        )

        assertEquals(StareCard.Eroare(UBER, MotivEroare.ECRAN_PROTEJAT), pas.stare.card)
    }

    @Test
    fun `liniile cardului vin din parserul platformei, iar o platforma fara reguli nu are niciuna`() {
        // F-003/F-005: ancora se construieste doar din aceste linii.
        assertEquals(uber.liniiFolosite(Ecrane.ofertaCompleta), automat.liniiCard(UBER, Ecrane.ofertaCompleta))
        assertEquals(3, automat.liniiCard(UBER, Ecrane.ofertaCompleta).size)
        assertTrue(automat.liniiCard(BOLT, Ecrane.ofertaCompleta).isEmpty())
    }

    @Test
    fun `linia de recitit vine din parserul platformei, doar cand pretul n-are forma (D-173)`() {
        val pretFaraVirgula = Ecrane.ofertaCompleta.map { if (it.text == "9,47 RON") it.copy(text = "947 RON") else it }
        val pret = pretFaraVirgula.single { it.text == "947 RON" }

        assertEquals(pret, automat.liniaPretDeRecitit(UBER, pretFaraVirgula))
        assertNull(automat.liniaPretDeRecitit(UBER, Ecrane.ofertaCompleta))
        assertNull(automat.liniaPretDeRecitit(BOLT, pretFaraVirgula))
    }

    // ---------- D-288.4: recitirea marita a liniei km de la preluare

    private fun cuPreluare(text: String) = Ecrane.ofertaCompleta.map { if (it.text.startsWith("4 min.")) it.copy(text = text) else it }

    private val kmNecitit = cuPreluare("4 min. (1.5 xm)")

    @Test
    fun `linia km de recitit e preluarea, doar cand km lipsesc sau sunt imposibili (D-288_4)`() {
        assertEquals(kmNecitit.single { it.text == "4 min. (1.5 xm)" }, automat.liniaKmDeRecitit(UBER, kmNecitit))
        assertEquals(
            Ecrane.ofertaCompleta.single { it.text.startsWith("4 min.") }.cutie,
            automat.liniaKmDeRecitit(UBER, cuPreluare("4 min. (95 km)"))?.cutie,
        )
        assertNull(automat.liniaKmDeRecitit(UBER, Ecrane.ofertaCompleta))
        assertNull(automat.liniaKmDeRecitit(BOLT, kmNecitit))
    }

    @Test
    fun `recitirea km se accepta cand doar km de la preluare devin cititi si plauzibili`() {
        assertTrue(automat.recitireKmAcceptata(UBER, kmNecitit, Ecrane.ofertaCompleta))
        assertTrue(automat.recitireKmAcceptata(UBER, cuPreluare("4 min. (95 km)"), Ecrane.ofertaCompleta))
    }

    @Test
    fun `recitirea km cu viteza implauzibila se respinge, iar km raman necunoscuti (D-148)`() {
        assertFalse(automat.recitireKmAcceptata(UBER, kmNecitit, cuPreluare("4 min. (95 km)")))
    }

    @Test
    fun `recitirea km care mai schimba altceva in oferta se respinge`() {
        val altMinut = cuPreluare("5 min. (1.5 km)")
        val altPret = Ecrane.ofertaCompleta.map { if (it.text == "9,47 RON") it.copy(text = "9,57 RON") else it }
        val altaCursa = Ecrane.ofertaCompleta.map { if (it.text.startsWith("9 min.")) it.copy(text = "9 min. (4.6 km)") else it }

        assertFalse(automat.recitireKmAcceptata(UBER, kmNecitit, altMinut))
        assertFalse(automat.recitireKmAcceptata(UBER, kmNecitit, altPret))
        assertFalse(automat.recitireKmAcceptata(UBER, kmNecitit, altaCursa))
    }

    @Test
    fun `recitirea km care tot nu citeste km, sau care nu avea ce repara, se respinge`() {
        assertFalse(automat.recitireKmAcceptata(UBER, kmNecitit, kmNecitit))
        assertFalse(automat.recitireKmAcceptata(UBER, Ecrane.ofertaCompleta, Ecrane.ofertaCompleta))
        assertFalse(automat.recitireKmAcceptata(UBER, Ecrane.ofertaCompleta, cuPreluare("4 min. (1.6 km)")))
        assertFalse(automat.recitireKmAcceptata(BOLT, kmNecitit, Ecrane.ofertaCompleta))
    }
}
