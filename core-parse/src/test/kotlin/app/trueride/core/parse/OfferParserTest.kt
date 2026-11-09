package app.trueride.core.parse

import app.trueride.core.model.DistanceUnit
import app.trueride.core.model.OfferField
import app.trueride.core.model.PriceBasis
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfferParserTest {

    private val parser = OfferParser(UBER_RO_V1)

    // Cutiile imita fixture-urile reale: pretul ~115 px inaltime, restul ~45 px.
    private fun linie(text: String, sus: Int, inaltime: Int = 45) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    // Textul obisnuit al cardului (~45 px): etalonul fata de care se judeca marimea
    // pretului (M5). Un ecran real are mereu asemenea linii (~20 pe fixture-uri).
    private fun corp(inaltime: Int = 45) =
        listOf(1400, 1500, 1600).map { linie("▢ ▢ ▢", sus = it, inaltime = inaltime) }

    // D-130: un pret cere cel putin un rand de segment pe ecran. Testele despre alegerea pretului
    // primesc segmentele unei oferte reale, de aceeasi inaltime ca textul obisnuit.
    private fun segmente(inaltime: Int = 45) =
        listOf(linie("4 min. (1.5 km)", sus = 1700, inaltime = inaltime), linie("9 min. (4.5 km)", sus = 1800, inaltime = inaltime))

    // Motorul fara regula D-130: pentru motivele care nu depind de segmente (ex. lipsa etalonului).
    private val faraRegulaSegment =
        OfferParser(UBER_RO_V1.copy(reguli = UBER_RO_V1.reguli.copy(pretCereSegment = false)))

    private fun egal(asteptat: String, real: BigDecimal?) {
        requireNotNull(real) { "asteptat $asteptat, primit null" }
        assertEquals(0, BigDecimal(asteptat).compareTo(real), "asteptat $asteptat, primit $real")
    }

    @Test
    fun `pretul cu virgula zecimala devine BigDecimal exact`() {
        val oferta = parser.parseaza(corp() + segmente() + linie("9,47 RON", sus = 1232, inaltime = 115))

        egal("9.47", oferta.price)
        assertEquals("9.47", oferta.price?.toPlainString())
        assertEquals("RON", oferta.currency)
        assertEquals(PriceBasis.NET, oferta.priceBasis)
    }

    @Test
    fun `o suma cu plus nu e niciodata pret`() {
        val oferta = parser.parseaza(listOf(linie("▢ +38 RON ▢ ▢", sus = 1985)))

        assertNull(oferta.price)
        assertNull(oferta.currency)
        assertEquals(PriceBasis.UNKNOWN, oferta.priceBasis)
    }

    @Test
    fun `bonusul nu devine pret nici cand e scris mai mare`() {
        val oferta = parser.parseaza(
            corp() + segmente() + listOf(
                linie("9,47 RON", sus = 1232, inaltime = 115),
                linie("▢ +38 RON ▢ ▢", sus = 1985, inaltime = 200),
            ),
        )

        egal("9.47", oferta.price)
    }

    @Test
    fun `dintre doua sume fara plus castiga cea scrisa mai mare`() {
        val oferta = parser.parseaza(
            corp() + segmente() + listOf(
                linie("5,00 RON", sus = 1000, inaltime = 45),
                linie("12,30 RON", sus = 1200, inaltime = 115),
            ),
        )

        egal("12.30", oferta.price)
    }

    // M5: cand linia pretului lipseste din cadru, o suma scrisa ca textul obisnuit
    // nu are voie sa-i ia locul — "necunoscut" e mai bun decat un pret gresit.
    @Test
    fun `o suma scrisa ca textul obisnuit nu e luata drept pret`() {
        val oferta = parser.parseaza(corp() + segmente() + linie("5,00 RON", sus = 1000, inaltime = 45))

        assertNull(oferta.price)
    }

    @Test
    fun `fara linii de referinta pretul e necunoscut`() {
        val oferta = faraRegulaSegment.parseaza(listOf(linie("9,47 RON", sus = 1232, inaltime = 115)))

        assertNull(oferta.price)
    }

    // Pragul e fata de mediana ecranului, nu in pixeli: pe un telefon cu densitate
    // dubla totul e de doua ori mai inalt, iar decizia trebuie sa fie aceeasi.
    @Test
    fun `pragul de marime e relativ la ecran, nu in pixeli absoluti`() {
        val oferta = parser.parseaza(
            corp(inaltime = 90) + segmente(inaltime = 90) + listOf(
                linie("5,00 RON", sus = 1000, inaltime = 100),
                linie("9,47 RON", sus = 1232, inaltime = 230),
            ),
        )

        egal("9.47", oferta.price)
    }

    @Test
    fun `preluarea e segmentul de sus chiar daca liniile vin in alta ordine`() {
        val oferta = parser.parseaza(
            listOf(
                linie("▢ 9 min. (4.5 km)", sus = 1788),
                linie("▢ 4 min. (1.5 km) ▢", sus = 1659),
            ),
        )

        egal("4", oferta.pickupMinutes)
        egal("1.5", oferta.pickupDistance)
        egal("9", oferta.tripMinutes)
        egal("4.5", oferta.tripDistance)
        assertEquals(DistanceUnit.KM, oferta.distanceUnitDetected)
    }

    @Test
    fun `minutele ilizibile raman necunoscute, nu zero`() {
        val oferta = parser.parseaza(
            listOf(
                linie("▢ 10 min. (4.2 km) ▢", sus = 1651),
                linie("▢ ▢ min. (3.9 km)", sus = 1791),
            ),
        )

        assertNull(oferta.tripMinutes)
        egal("3.9", oferta.tripDistance)
    }

    @Test
    fun `distanta in metri nu e citita ca km si nu muta cursa pe preluare`() {
        val oferta = parser.parseaza(
            listOf(
                linie("▢ 3 min. (800 m) ▢", sus = 1659),
                linie("▢ 9 min. (4.5 km)", sus = 1788),
            ),
        )

        egal("3", oferta.pickupMinutes)
        assertNull(oferta.pickupDistance)
        egal("9", oferta.tripMinutes)
        egal("4.5", oferta.tripDistance)
    }

    @Test
    fun `fara linii oferta e goala si incompleta`() {
        val oferta = parser.parseaza(emptyList())

        assertNull(oferta.price)
        assertNull(oferta.pickupMinutes)
        assertNull(oferta.tripDistance)
        assertNull(oferta.distanceUnitDetected)
        assertFalse(oferta.isComplete)
        assertTrue(oferta.fieldConfidence.isEmpty())
    }

    // I1 — sumaPattern fara granita accepta un semn lipit/departat de cifre sau
    // un numar taiat de restul liniei; toate patru sunt OCR ambiguu, nu pret.
    @Test
    fun `un semn langa suma, oriunde ar fi scris, scoate pretul din calcul`() {
        val cazuriAmbigue = listOf(
            "▢ + 38 RON ▢",
            "9, 47 RON",
            "9,475 RON",
            "-2,00 RON",
        )

        cazuriAmbigue.forEach { text ->
            val oferta = parser.parseaza(listOf(linie(text, sus = 1232, inaltime = 115)))
            assertNull(oferta.price, "text=\"$text\" ar trebui sa dea price null")
        }
    }

    // I2 — ancora cere paranteza distantei pe aceeasi linie, nu depinde de spatiu/punct.
    @Test
    fun `min lipit de cifra, fara spatiu inainte, tot devine segment`() {
        val oferta = parser.parseaza(
            listOf(
                linie("▢ 4min. (1.5 km) ▢", sus = 1659),
                linie("▢ 9 min. (4.5 km)", sus = 1788),
            ),
        )

        egal("4", oferta.pickupMinutes)
        egal("1.5", oferta.pickupDistance)
        egal("9", oferta.tripMinutes)
        egal("4.5", oferta.tripDistance)
    }

    @Test
    fun `min fara punct final tot devine segment`() {
        val oferta = parser.parseaza(
            listOf(
                linie("▢ 4 min (1.5 km) ▢", sus = 1659),
                linie("▢ 9 min. (4.5 km)", sus = 1788),
            ),
        )

        egal("4", oferta.pickupMinutes)
        egal("1.5", oferta.pickupDistance)
        egal("9", oferta.tripMinutes)
        egal("4.5", oferta.tripDistance)
    }

    // F-031: Uber scrie uneori "min," in loc de "min." inaintea parantezei cu km.
    @Test
    fun `virgula dupa min este tot o ancora de segment`() {
        val oferta = parser.parseaza(
            listOf(
                linie("▢ 4 min, (1.5 km) ▢", sus = 1659),
                linie("▢ 9 min. (4.5 km)", sus = 1788),
            ),
        )

        egal("4", oferta.pickupMinutes)
        egal("1.5", oferta.pickupDistance)
        egal("9", oferta.tripMinutes)
        egal("4.5", oferta.tripDistance)
    }

    // Controlul I3 pentru virgula: fara paranteza un rand cu "min," ramane banner.
    @Test
    fun `virgula nu face dintr-un banner fara paranteza un segment`() {
        val oferta = parser.parseaza(
            listOf(
                linie("Ajungi in 5 min, ▢", sus = 1500),
                linie("▢ 4 min. (1.5 km) ▢", sus = 1659),
                linie("▢ 9 min. (4.5 km)", sus = 1788),
            ),
        )

        egal("4", oferta.pickupMinutes)
        egal("9", oferta.tripMinutes)
    }

    // I3 — orice alta linie cu "min." dar fara paranteza nu e segment; orele nu se
    // amesteca cu minutele.
    @Test
    fun `un banner cu min fara paranteza nu devine segment si nu strica ordinea`() {
        val oferta = parser.parseaza(
            listOf(
                linie("Ajungi in 5 min.", sus = 1500),
                linie("▢ 4 min. (1.5 km) ▢", sus = 1659),
                linie("▢ 9 min. (4.5 km)", sus = 1788),
            ),
        )

        egal("4", oferta.pickupMinutes)
        egal("1.5", oferta.pickupDistance)
        egal("9", oferta.tripMinutes)
        egal("4.5", oferta.tripDistance)
    }

    @Test
    fun `o ora amestecata cu minute da minute necunoscute dar pastreaza distanta`() {
        val oferta = parser.parseaza(
            listOf(
                linie("▢ 1 h 5 min. (80 km) ▢", sus = 1659),
                linie("▢ 9 min. (4.5 km)", sus = 1788),
            ),
        )

        assertNull(oferta.pickupMinutes)
        egal("80", oferta.pickupDistance)
        egal("9", oferta.tripMinutes)
        egal("4.5", oferta.tripDistance)
    }

    // I4 — numarul de linii-ancora trebuie sa fie exact numarSegmente (2 la Uber RO),
    // altfel motorul nu ghiceste: toate cele patru campuri de segment devin null.
    @Test
    fun `un singur rand ancora face cele patru campuri de segment null`() {
        val oferta = parser.parseaza(listOf(linie("▢ 4 min. (1.5 km) ▢", sus = 1659)))

        assertNull(oferta.pickupMinutes)
        assertNull(oferta.pickupDistance)
        assertNull(oferta.tripMinutes)
        assertNull(oferta.tripDistance)
        assertNull(oferta.distanceUnitDetected)
    }

    @Test
    fun `trei randuri ancora fac cele patru campuri de segment null`() {
        val oferta = parser.parseaza(
            listOf(
                linie("▢ 4 min. (1.5 km) ▢", sus = 1659),
                linie("▢ 9 min. (4.5 km)", sus = 1788),
                linie("▢ 2 min. (0.5 km)", sus = 1900),
            ),
        )

        assertNull(oferta.pickupMinutes)
        assertNull(oferta.pickupDistance)
        assertNull(oferta.tripMinutes)
        assertNull(oferta.tripDistance)
        assertNull(oferta.distanceUnitDetected)
    }

    @Test
    fun `increderea exista doar pentru campurile gasite`() {
        val oferta = parser.parseaza(
            listOf(
                linie("13,04 RON", sus = 1242, inaltime = 115),
                linie("▢ 10 min. (4.2 km) ▢", sus = 1651),
                linie("▢ ▢ min. (3.9 km)", sus = 1791),
            ),
        )

        assertEquals(
            setOf(OfferField.PRICE, OfferField.PICKUP_MINUTES, OfferField.PICKUP_DISTANCE, OfferField.TRIP_DISTANCE),
            oferta.fieldConfidence.keys,
        )
        assertTrue(oferta.isComplete)
    }

    // F-003/F-005: ancora cardului se construieste doar din liniile din care parserul a scos valorile afisate.

    @Test
    fun `liniile folosite sunt doar linia pretului ales si liniile segmentelor`() {
        val ceas = LinieOcr("12:34", Cutie(stanga = 40, sus = 20, dreapta = 160, jos = 60))
        val pret = linie("9,47 RON", sus = 1232, inaltime = 115)
        val preluare = linie("4 min. (1.5 km)", sus = 1700)
        val cursa = linie("9 min. (4.5 km)", sus = 1800)
        val linii = listOf(ceas) + corp() + listOf(
            linie("+38 RON", sus = 1100, inaltime = 130),
            linie("12 RON", sus = 1900),
            pret,
            preluare,
            cursa,
        )

        assertEquals(listOf(pret, preluare, cursa), parser.liniiFolosite(linii))
    }

    @Test
    fun `fara pret citit raman doar segmentele, iar cu alt numar de segmente doar pretul`() {
        val preluare = linie("4 min. (1.5 km)", sus = 1700)
        val cursa = linie("9 min. (4.5 km)", sus = 1800)
        val pret = linie("9,47 RON", sus = 1232, inaltime = 115)

        assertEquals(listOf(preluare, cursa), parser.liniiFolosite(corp() + listOf(preluare, cursa)))
        assertEquals(listOf(pret), parser.liniiFolosite(corp() + listOf(pret, preluare)))
        assertEquals(emptyList<LinieOcr>(), parser.liniiFolosite(corp()))
    }

    // D-130 (F-020): pe ecranul principal Uber, castigul zilei e singura suma mare. Fara niciun rand de
    // segment pe ecran nu e o oferta, deci suma nu e pret (invariantul 5). Cifrele sunt inventate.
    @Test
    fun `un pret fara niciun rand de segment nu e pret`() {
        val oferta = parser.parseaza(corp() + linie("123,45 RON", sus = 150, inaltime = 100))

        assertNull(oferta.price)
        assertNull(oferta.currency)
        assertEquals(PriceBasis.UNKNOWN, oferta.priceBasis)
    }

    // Controlul: aceeasi intrare, cu regula oprita, da pretul. Dovada ca regula il opreste, nu forma sumei.
    @Test
    fun `cu regula oprita acelasi ecran da pretul`() {
        val faraRegula = OfferParser(UBER_RO_V1.copy(reguli = UBER_RO_V1.reguli.copy(pretCereSegment = false)))

        egal("123.45", faraRegula.parseaza(corp() + linie("123,45 RON", sus = 150, inaltime = 100)).price)
    }

    // Un singur rand de segment citit (al doilea ilizibil) tot e o oferta: pretul ramane.
    @Test
    fun `un singur rand de segment pastreaza pretul`() {
        val oferta = parser.parseaza(
            corp() + linie("9,47 RON", sus = 1232, inaltime = 115) + linie("4 min. (1.5 km)", sus = 1700),
        )

        egal("9.47", oferta.price)
    }

    @Test
    fun `regulile Uber RO cer segment pentru pret si accepta virgula dupa min de la versiunea 1_7_0`() {
        assertTrue(UBER_RO_V1.reguli.pretCereSegment)
        assertEquals("1.19.0", UBER_RO_V1.version)
    }
}
