package app.trueride.core.parse

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Teren (APK 22, Uber EN, sursa OCR): pe segmentul cursei OCR-ul a lipit o litera mica dupa ultima cifra a km
 * („(D.Dl km)”), desi pe ecran scrie „D.D km”. Cifrele erau citite sigur, dar tiparul cerea unitatea imediat
 * dupa numar: KM_CURSA = FARA_KM, card „Date insuficiente”. O singura litera confundabila (l, I, |) intre ultima
 * cifra si unitate se ignora (nu devine cifra: ecranul nu are cifra in plus). Toate cifrele sunt inventate.
 */
class LiteraLipitaKmUberEnTest {

    private val en = OfferParser(UBER_EN_V1)

    private fun linie(text: String, sus: Int, inaltime: Int = 46) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private val corp = listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it) }

    private fun oferta(preluare: String, cursa: String) =
        corp + linie("RON12.30", sus = 1232, inaltime = 90) + linie(preluare, sus = 1700) + linie(cursa, sus = 1800)

    private fun egal(asteptat: String, real: BigDecimal?) =
        assertEquals(0, BigDecimal(asteptat).compareTo(requireNotNull(real) { "asteptat $asteptat, primit null" }))

    private fun kmCursa(segment: String) = en.parseaza(oferta("6 mins (2.1 km) ▢", segment)).tripDistance

    @Test
    fun `litera l lipita dupa ultima cifra a km cursei se ignora`() {
        egal("7.2", kmCursa("14 mins (7.2l km) ▢"))
    }

    @Test
    fun `I majuscul si bara verticala lipite dupa ultima cifra se ignora`() {
        egal("7.2", kmCursa("14 mins (7.2I km) ▢"))
        egal("7.2", kmCursa("14 mins (7.2| km) ▢"))
    }

    @Test
    fun `litera lipita dupa un km intreg ramane necunoscuta`() {
        // „12l” e ambiguu: calea largita il citeste 121 (l = cifra 1), nu 12. Fara dovada, necunoscut (inv. 6); aici
        // 121 km in 14 min iese oricum KM_IMPLAUZIBIL.
        assertNull(kmCursa("14 mins (12l km) ▢"))
    }

    @Test
    fun `aceeasi litera lipita pe segmentul de preluare se ignora`() {
        egal("2.1", en.parseaza(oferta("6 mins (2.1l km) ▢", "14 mins (7.2 km) ▢")).pickupDistance)
    }

    @Test
    fun `litera lipita nu devine cifra`() {
        // „7.2l” nu se citeste „7.21” (doua zecimale: oricum respins) si nici nu schimba valoarea.
        egal("7.2", kmCursa("14 mins (7.2l km) ▢"))
    }

    @Test
    fun `doua litere lipite raman necunoscute`() {
        assertNull(kmCursa("14 mins (7.2ll km) ▢"))
    }

    @Test
    fun `litera de alt fel lipita ramane necunoscuta`() {
        assertNull(kmCursa("14 mins (7.2a km) ▢"))
    }

    @Test
    fun `litera in fata ramane cazul D-125 neschimbat`() {
        assertNull(kmCursa("14 mins ((7.2 km) ▢"))
    }

    @Test
    fun `versiunea regulilor Uber EN este 1_11_0`() {
        assertEquals("1.11.0", UBER_EN_V1.version)
    }
}
