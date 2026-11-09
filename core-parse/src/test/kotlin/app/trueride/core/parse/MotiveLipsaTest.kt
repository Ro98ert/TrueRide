package app.trueride.core.parse

import app.trueride.core.model.OfferField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MotiveLipsaTest {

    private val parser = OfferParser(UBER_RO_V1)

    private val campuriSegment = setOf(
        OfferField.PICKUP_MINUTES, OfferField.PICKUP_DISTANCE,
        OfferField.TRIP_MINUTES, OfferField.TRIP_DISTANCE,
    )

    private fun linie(text: String, sus: Int, inaltime: Int = 45) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private fun corp() = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }

    private val preluare = linie("4 min. (1.5 km)", sus = 1700)
    private val cursa = linie("9 min. (4.5 km)", sus = 1800)
    private val pret = linie("9,47 RON", sus = 1232, inaltime = 115)

    private fun motivPret(linii: List<LinieOcr>) = parser.motiveLipsa(linii)[OfferField.PRICE]

    // Motorul fara regula D-130: un rand de segment e si el o linie de referinta, deci FARA_ETALON
    // exista doar in seturile care nu cer segment.
    private val faraRegulaSegment =
        OfferParser(UBER_RO_V1.copy(reguli = UBER_RO_V1.reguli.copy(pretCereSegment = false)))

    @Test
    fun `oferta completa nu are niciun camp lipsa`() {
        assertEquals(emptyMap(), parser.motiveLipsa(corp() + pret + preluare + cursa))
    }

    @Test
    fun `fara nicio suma pretul lipseste cu FARA_SUMA`() {
        assertEquals(MotivLipsa.FARA_SUMA, motivPret(corp() + preluare + cursa))
    }

    @Test
    fun `doar sume cu semn dau DOAR_CU_SEMN`() {
        assertEquals(MotivLipsa.DOAR_CU_SEMN, motivPret(corp() + preluare + cursa + linie("▢ +38 RON ▢", sus = 1985)))
    }

    @Test
    fun `fara linii de referinta motivul e FARA_ETALON`() {
        assertEquals(MotivLipsa.FARA_ETALON, faraRegulaSegment.motiveLipsa(listOf(pret))[OfferField.PRICE])
    }

    @Test
    fun `o suma scrisa ca textul obisnuit e SUB_PRAG`() {
        assertEquals(MotivLipsa.SUB_PRAG, motivPret(corp() + preluare + cursa + linie("5,00 RON", sus = 1000)))
    }

    @Test
    fun `castigatoarea fara doi bani e FORMA_PRET, nu alt candidat`() {
        val stricat = linie("1234 RON", sus = 1232, inaltime = 115)
        val altaSuma = linie("5,00 RON", sus = 1000, inaltime = 100)

        assertEquals(MotivLipsa.FORMA_PRET, motivPret(corp() + preluare + cursa + stricat + altaSuma))
    }

    @Test
    fun `fara nicio ancora toate cele patru campuri de segment sunt ANCORE_LIPSA`() {
        val motive = parser.motiveLipsa(corp() + pret)

        assertEquals(campuriSegment, motive.keys - OfferField.PRICE)
        assertTrue(motive.values.all { it == MotivLipsa.ANCORE_LIPSA })
    }

    @Test
    fun `un numar gresit de ancore da ANCORE_NUMAR pe toate cele patru campuri`() {
        val motive = parser.motiveLipsa(corp() + pret + preluare)

        assertEquals(campuriSegment, motive.keys)
        assertTrue(motive.values.all { it == MotivLipsa.ANCORE_NUMAR })
    }

    @Test
    fun `minute ilizibile dau FARA_MINUTE, iar distanta ramane citita`() {
        val motive = parser.motiveLipsa(corp() + pret + linie("▢ ▢ min. (3.9 km)", sus = 1700) + cursa)

        assertEquals(mapOf(OfferField.PICKUP_MINUTES to MotivLipsa.FARA_MINUTE), motive)
    }

    @Test
    fun `distanta in metri da FARA_KM, iar minutele ramin citite`() {
        val motive = parser.motiveLipsa(corp() + pret + linie("3 min. (800 m)", sus = 1700) + cursa)

        assertEquals(mapOf(OfferField.PICKUP_DISTANCE to MotivLipsa.FARA_KM), motive)
    }

    @Test
    fun `motivele sunt coerente cu parseaza pe toate fixture-urile`() {
        val fixturi = ComparatorFixturi.fixturi("uber")
        assertTrue(fixturi.isNotEmpty(), "niciun fixture")

        fixturi.forEach { f -> assertCoerent(CititorFixturi.linii(f), f.name) }
    }

    @Test
    fun `motivele sunt coerente cu parseaza si pe cazurile sintetice`() {
        val cazuri = listOf(
            corp(), corp() + pret, corp() + preluare + cursa, listOf(pret),
            corp() + linie("5,00 RON", sus = 1000), corp() + linie("▢ +38 RON", sus = 1985) + preluare,
        )

        cazuri.forEachIndexed { i, linii -> assertCoerent(linii, "caz $i") }
    }

    private fun assertCoerent(linii: List<LinieOcr>, eticheta: String) {
        val nule = OfferField.values().toSet() - parser.parseaza(linii).fieldConfidence.keys

        assertEquals(nule, parser.motiveLipsa(linii).keys, eticheta)
    }

    // D-130: fara niciun rand de segment, pretul lipseste si el cu ANCORE_LIPSA, nu doar segmentele.
    @Test
    fun `fara nicio ancora pretul lipseste cu ANCORE_LIPSA`() {
        val motive = parser.motiveLipsa(corp() + pret)

        assertEquals(MotivLipsa.ANCORE_LIPSA, motive[OfferField.PRICE])
        assertEquals(campuriSegment + OfferField.PRICE, motive.keys)
    }
}
