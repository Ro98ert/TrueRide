package app.trueride.core.lant

import app.trueride.core.lant.Eveniment.EcranSchimbat
import app.trueride.core.lant.Eveniment.NoduriCitite
import app.trueride.core.lant.Eveniment.OcrCitit
import app.trueride.core.lant.PlatformaSofer.UBER
import app.trueride.core.model.OfferField
import app.trueride.core.model.ParsedOffer
import app.trueride.core.model.Thresholds
import app.trueride.core.parse.OfferParser
import app.trueride.core.parse.UBER_RO_V1
import java.lang.reflect.Modifier
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * D-368: dezacordul intre cadre. Doua citiri ale ACELEIASI oferte (cardul cu cifre a ramas pe ecran intre ele, D-134,
 * deci nicio oferta noua nu l-a scos) care dau valori cunoscute diferite pentru acelasi camp. Necunoscut -> cunoscut
 * (sau invers) e completitudine, deja masurata, nu dezacord. Pasul expune doar numele campurilor, niciodata valorile.
 */
class AutomatCardDezacordTest {

    private val uber = OfferParser(UBER_RO_V1)
    private val automat = AutomatCard(mapOf(UBER to uber, PlatformaSofer.NECUNOSCUTA to uber))
    private val start = StareLant(setari = Setari(cost = null, praguri = Thresholds()))

    private fun dupa(pas: Pas, vararg evenimente: Eveniment): Pas =
        evenimente.fold(pas) { p, e -> automat.trateaza(p.stare, e) }

    private val cuCifre = dupa(Pas(start), EcranSchimbat(1, UBER), NoduriCitite(1, Ecrane.ofertaCompleta))

    @Test
    fun `aceeasi oferta citita cu alt pret da un dezacord pe pret`() {
        val pas = dupa(cuCifre, EcranSchimbat(2, UBER), NoduriCitite(2, Ecrane.ofertaAltPret))

        assertEquals(listOf(OfferField.PRICE), pas.dezacord)
    }

    @Test
    fun `aceleasi cifre nu dau dezacord`() {
        val pas = dupa(cuCifre, EcranSchimbat(2, UBER), NoduriCitite(2, Ecrane.ofertaCompleta))

        assertEquals(emptyList<OfferField>(), pas.dezacord)
    }

    @Test
    fun `necunoscut apoi cunoscut nu e dezacord`() {
        val faraTimp = dupa(Pas(start), EcranSchimbat(1, UBER), NoduriCitite(1, Ecrane.ofertaFaraTimpCursa))
        val pas = dupa(faraTimp, EcranSchimbat(2, UBER), NoduriCitite(2, Ecrane.ofertaCompleta))

        assertEquals(emptyList<OfferField>(), pas.dezacord)
    }

    @Test
    fun `cunoscut apoi necunoscut nu e dezacord`() {
        val pas = dupa(cuCifre, EcranSchimbat(2, UBER), NoduriCitite(2, Ecrane.ofertaFaraTimpCursa))

        assertEquals(emptyList<OfferField>(), pas.dezacord)
    }

    @Test
    fun `o oferta noua intre cele doua citiri nu e dezacord`() {
        listOf(Eveniment.AltaAplicatie, Eveniment.Inchis, Eveniment.CapturaIntrerupta).forEach { intrerupere ->
            val pas = dupa(
                cuCifre, intrerupere,
                EcranSchimbat(2, UBER), NoduriCitite(2, Ecrane.ofertaAltPret),
            )

            assertEquals(emptyList<OfferField>(), pas.dezacord, intrerupere.toString())
        }
    }

    @Test
    fun `citirea prin OCR peste un card cu cifre se judeca la fel`() {
        val pas = dupa(
            cuCifre,
            EcranSchimbat(2, UBER), NoduriCitite(2, Ecrane.faraOferta), OcrCitit(2, Ecrane.ofertaAltPret),
        )

        assertEquals(listOf(OfferField.PRICE), pas.dezacord)
    }

    @Test
    fun `un rezultat vechi, respins, nu da dezacord`() {
        val pas = dupa(cuCifre, EcranSchimbat(2, UBER), EcranSchimbat(3, UBER), NoduriCitite(2, Ecrane.ofertaAltPret))

        assertEquals(emptyList<OfferField>(), pas.dezacord)
    }

    @Test
    fun `doua campuri cunoscute si diferite se numara fiecare, in ordinea campurilor`() {
        val pas = dupa(cuCifre, EcranSchimbat(2, UBER), NoduriCitite(2, Ecrane.ofertaAltPretSiKmCursa))

        assertEquals(listOf(OfferField.PRICE, OfferField.TRIP_DISTANCE), pas.dezacord)
    }

    // D-368.5: 3+ campuri diferite deodata nu mai sunt citiri gresite ale aceleiasi oferte, ci o oferta noua.
    @Test
    fun `trei campuri diferite sunt o oferta noua, deci nu se numara nimic`() {
        val pas = dupa(cuCifre, EcranSchimbat(2, UBER), NoduriCitite(2, Ecrane.ofertaAltTreiCampuri))

        assertEquals(emptyList<OfferField>(), pas.dezacord)
        assertIs<StareCard.Succes>(pas.stare.card)
    }

    @Test
    fun `detectorul compara numeric, nu textual`() {
        val a = ParsedOffer(price = BigDecimal("9.50"), tripDistance = BigDecimal("4.5"))
        val b = ParsedOffer(price = BigDecimal("9.5"), tripDistance = BigDecimal("4.6"))

        assertEquals(listOf(OfferField.TRIP_DISTANCE), DezacordCadre.campuri(a, b))
    }

    @Test
    fun `rezultatul pasului nu poarta text liber`() {
        val campuriText = Pas::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
            .filter { "java.lang.String" in it.genericType.typeName || "java.lang.CharSequence" in it.genericType.typeName }
        assertTrue(campuriText.isEmpty(), campuriText.toString())
    }
}
