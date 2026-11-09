package app.trueride.core.parse

import app.trueride.core.model.OfferField
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * F-033, D-143: pragul EN coborat (1,5) nu slabeste M5. Cutiile imita telefonul de test 2: mediana ~46 px (cea la care
 * 1,8 pica), pretul 80 px. Toate cifrele sunt inventate.
 */
class PragPretUberEnTest {

    private val en = OfferParser(UBER_EN_V1)

    private fun linie(text: String, sus: Int, inaltime: Int = 46) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private val corp = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }
    private val segmente = listOf(linie("6 mins (2.1 km) ▢", sus = 1700), linie("14 mins (7.2 km) ▢", sus = 1800))

    private fun egal(asteptat: String, real: BigDecimal?) =
        assertEquals(0, BigDecimal(asteptat).compareTo(requireNotNull(real) { "asteptat $asteptat, primit null" }))

    // Cel mai mic raport real din raportul telefonului de test 2: ~1,74 (CalibrarePragPretTest, uber-en-prag). 80 / 46 = 1,74.
    @Test
    fun `pretul la 1,74 x mediana se citeste`() {
        egal("12.30", en.parseaza(corp + segmente + linie("RON12.30", sus = 1232, inaltime = 80)).price)
    }

    // F-033, D-143.1: pragul EN e camp propriu (1,5), nu mostenit din RO. RO s-a mutat intre timp la 1,6 (D-315 val13),
    // iar D-143 a respins explicit 1,6 pentru EN. 72 / 46 = 1,565: peste pragul EN, sub cel RO. Daca EN mosteneste
    // din nou pragul RO (defectul F-033), pretul iese SUB_PRAG, nu citit. Testul vede defectul DOAR cat pragul RO curent
    // (1,6, D-315 val13, `ReguliUberRo.kt`) e peste 1,565: daca RO coboara la 1,565 sau sub, testul devine orb si cele
    // 72 px trebuie mutate intre noul prag RO si 1,5.
    @Test
    fun `F-033 pretul la 1,565 x mediana se citeste pe EN chiar daca e sub pragul RO`() {
        val linii = corp + segmente + linie("RON12.30", sus = 1232, inaltime = 72)

        egal("12.30", en.parseaza(linii).price)
        assertNull(en.motiveLipsa(linii)[OfferField.PRICE])
    }

    // M5 (Review Focus 2): linia pretului lipseste, o suma scrisa ca textul obisnuit nu-i ia locul.
    @Test
    fun `fara linia pretului o suma de inaltimea textului ramane SUB_PRAG`() {
        val linii = corp + segmente + linie("RON5.00", sus = 1000, inaltime = 46)

        assertNull(en.parseaza(linii).price)
        assertEquals(MotivLipsa.SUB_PRAG, en.motiveLipsa(linii)[OfferField.PRICE])
    }

    // Cea mai inalta suma secundara vazuta pe EN: 1,25 x (D-128 Context). 58 / 46 = 1,26 < 1,5.
    @Test
    fun `fara linia pretului o suma la 1,26 x mediana ramane SUB_PRAG`() {
        val linii = corp + segmente + linie("RON5.00", sus = 1000, inaltime = 58)

        assertNull(en.parseaza(linii).price)
        assertEquals(MotivLipsa.SUB_PRAG, en.motiveLipsa(linii)[OfferField.PRICE])
    }
}
