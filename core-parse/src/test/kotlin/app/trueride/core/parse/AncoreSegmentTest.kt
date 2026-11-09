package app.trueride.core.parse

import kotlin.test.Test
import kotlin.test.assertEquals

/** D-301: parserul arata ancorele de segment pe care le-a numarat, ca jurnalul sa poata spune cate si unde. */
class AncoreSegmentTest {

    private val uber = OfferParser(UBER_RO_V1)

    private fun linie(text: String, sus: Int, inaltime: Int = 45) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private val preluare = linie("4 min. (1.5 km)", sus = 1700)
    private val cursa = linie("9 min. (4.5 km)", sus = 1800)
    private val aTreia = linie("Zona 12 min. (3 km) libera", sus = 1900)
    private val pret = linie("9,47 RON", sus = 1232, inaltime = 115)

    @Test
    fun `o a treia linie cu litere si min intre paranteze apare printre ancore, de sus in jos`() {
        val ancore = uber.ancoreSegment(listOf(aTreia, pret, cursa, preluare))

        assertEquals(listOf(preluare, cursa, aTreia), ancore)
    }

    @Test
    fun `fara nicio linie de segment lista ancorelor e goala`() {
        assertEquals(emptyList(), uber.ancoreSegment(listOf(pret)))
    }

    @Test
    fun `ParserPeLimbi da ancorele setului castigator`() {
        val peLimbi = ParserPeLimbi(listOf(uber, OfferParser(UBER_EN_V1)))

        assertEquals(listOf(preluare, cursa, aTreia), peLimbi.ancoreSegment(listOf(preluare, cursa, aTreia, pret)))
    }
}
