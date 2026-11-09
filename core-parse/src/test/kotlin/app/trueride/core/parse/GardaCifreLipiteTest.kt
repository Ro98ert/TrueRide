package app.trueride.core.parse

import app.trueride.core.model.ParsedOffer
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * D-312 (P-sint-1/3/4/5): un numar lipit de o litera din afara `corectiiCifre`, de „h” sau de alt grup de cifre
 * nu se citeste ca sufix („S42,50” -> 2,50; „2 h 48 min” -> 8; „5 3 min” -> 3): campul e necunoscut (invariantul 6),
 * restul ofertei ramane citit. A doua jumatate a fisierului fixeaza ce NU are voie sa se piarda: moneda lipita
 * in stanga cifrelor (Uber EN), bonusul cu semn, literele din harta, minutele normale si intervalul. Linii sintetice,
 * cifre inventate (CLAUDE.md §9).
 */
class GardaCifreLipiteTest {

    private val ro = OfferParser(UBER_RO_V1)
    private val en = OfferParser(UBER_EN_V1)
    private val bolt = OfferParser(BOLT_RO_V1)
    private val nb = " "

    private fun linie(text: String, sus: Int, inaltime: Int, recitita: Boolean = false) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime), recitita)

    private val corp = listOf(1000, 1100, 1200).map { linie("▢ ▢ ▢", it, 45) }

    private fun ro(
        pret: String = "14,20 RON",
        pre: String = "▢: 6 min. (2.1 km)",
        cursa: String = "▢ 11 min. (5.4 km) ▢",
        bonus: String? = null,
        inaltimeBonus: Int = 45,
    ) = corp + linie(pret, 1300, 115) + listOfNotNull(bonus?.let { linie(it, 1450, inaltimeBonus) }) +
        linie(pre, 1700, 45) + linie(cursa, 1800, 45)

    private fun en(
        pret: String = "RON14.20",
        pre: String = "6 mins (2.1 km)",
        cursa: String = "11 mins (5.4 km) ▢",
        bonus: String? = null,
        recitita: Boolean = false,
    ) = corp + linie(pret, 1300, 90, recitita) + listOfNotNull(bonus?.let { linie(it, 1450, 46) }) +
        linie(pre, 1700, 46) + linie(cursa, 1800, 46)

    private fun bolt(pre: String = "6 min$nb• 2.1 km", cursa: String = "11 min$nb• 5.4 km") =
        corp + linie("14,20${nb}lei (▢, ▢ ▢)", 1300, 114) + linie(pre, 1700, 82) + linie(cursa, 1800, 82)

    private fun egal(asteptat: String, real: BigDecimal?) =
        assertEquals(0, BigDecimal(asteptat).compareTo(requireNotNull(real) { "asteptat $asteptat, primit null" }))

    /** Restul ofertei ramane citit: doar campul din test are voie sa lipseasca. */
    private fun segmenteIntacte(o: ParsedOffer, preluare: Boolean = true) {
        if (preluare) { egal("6", o.pickupMinutes) }
        egal("2.1", o.pickupDistance); egal("11", o.tripMinutes); egal("5.4", o.tripDistance)
    }

    // ---------- P-sint-1: litera nemapata in stanga pretului Uber RO

    @Test
    fun `Uber RO - litera nemapata lipita de pret da pret necunoscut, nu pretul fara prima cifra`() {
        listOf("S42,50 RON", "B14,20 RON", "Z12,50 RON", "A9,45 RON", "SB14,20 RON").forEach {
            val o = ro.parseaza(ro(pret = it))
            assertNull(o.price, it)
            assertNull(o.currency, it)
            segmenteIntacte(o)
        }
    }

    @Test
    fun `Uber RO - bonusul cu semn dupa o litera nemapata ramane bonus si nu devine pret`() {
        // Daca semnul s-ar pierde, bonusul (mai inalt decat pretul) ar lua locul pretului.
        egal("14.20", ro.parseaza(ro(bonus = "S+8,50 RON", inaltimeBonus = 130)).price)
    }

    // ---------- P-sint-3: litera nemapata in stanga minutelor de 2 cifre (Uber RO si EN)

    @Test
    fun `Uber RO - litera nemapata lipita de minute da minute necunoscute, restul ramane`() {
        val o = ro.parseaza(ro(pre = "▢: S7 min. (2.1 km)"))
        assertNull(o.pickupMinutes)
        egal("14.20", o.price); egal("2.1", o.pickupDistance); egal("11", o.tripMinutes)
        val cursa = ro.parseaza(ro(cursa = "▢ B24 min. (5.4 km) ▢"))
        assertNull(cursa.tripMinutes)
        egal("6", cursa.pickupMinutes); egal("5.4", cursa.tripDistance)
    }

    @Test
    fun `Uber EN - litera nemapata lipita de minute da minute necunoscute, restul ramane`() {
        val o = en.parseaza(en(pre = "S7 mins (2.1 km)"))
        assertNull(o.pickupMinutes)
        egal("14.20", o.price); egal("2.1", o.pickupDistance); egal("11", o.tripMinutes)
        val cursa = en.parseaza(en(cursa = "B24 mins (5.4 km) ▢"))
        assertNull(cursa.tripMinutes)
        egal("6", cursa.pickupMinutes); egal("5.4", cursa.tripDistance)
    }

    // ---------- P-sint-4: „D h DD min” (ore + minute de 2 cifre)

    @Test
    fun `ore si minute de 2 cifre dau minute necunoscute, nu ultima cifra - Uber RO, Uber EN, Bolt RO`() {
        assertNull(ro.parseaza(ro(pre = "▢: 2 h 48 min. (2.1 km)")).pickupMinutes)
        assertNull(en.parseaza(en(pre = "2 h 48 mins (2.1 km)")).pickupMinutes)
        assertNull(bolt.parseaza(bolt(pre = "2 h 48 min$nb• 2.1 km")).pickupMinutes)
        segmenteIntacte(ro.parseaza(ro(pre = "▢: 2 h 48 min. (2.1 km)")), preluare = false)
        segmenteIntacte(bolt.parseaza(bolt(pre = "2 h 48 min$nb• 2.1 km")), preluare = false)
    }

    @Test
    fun `ora lipita de minute (2h48 min) nu da nici ea ultimele cifre`() {
        assertNull(ro.parseaza(ro(pre = "▢: 2h48 min. (2.1 km)")).pickupMinutes)
        assertNull(en.parseaza(en(pre = "2h48 mins (2.1 km)")).pickupMinutes)
        assertNull(bolt.parseaza(bolt(pre = "2h48 min$nb• 2.1 km")).pickupMinutes)
    }

    // ---------- P-sint-5: „D D min” (spatiu in interiorul minutelor)

    @Test
    fun `spatiu in minute (5 3 min) da minute necunoscute, nu ultimul grup - Uber RO si EN`() {
        assertNull(ro.parseaza(ro(pre = "▢: 5 3 min. (2.1 km)")).pickupMinutes)
        assertNull(ro.parseaza(ro(pre = "▢: 5${nb}3 min. (2.1 km)")).pickupMinutes)
        assertNull(en.parseaza(en(pre = "5 3 mins (2.1 km)")).pickupMinutes)
        segmenteIntacte(ro.parseaza(ro(pre = "▢: 5 3 min. (2.1 km)")), preluare = false)
    }

    // ---------- Ne-regresie: ce trebuie sa ramana citit

    @Test
    fun `Uber EN - moneda lipita in stanga cifrelor nu e litera straina (RONDD,DD si RONDD recitit)`() {
        egal("14.20", en.parseaza(en()).price)
        egal("142.50", en.parseaza(en(pret = "RON142.50", cursa = "150 mins (123.4 km) ▢")).price)   // D-329.1: in banda lei/km
        egal("14", en.parseaza(en(pret = "RON14", recitita = true)).price)
        egal("14", en.parseaza(en(pret = "RON${nb}14", recitita = true)).price)
        egal("14.20", en.parseaza(en(pret = "RON${nb}14.20")).price)
        assertNull(en.parseaza(en(pret = "RON14", recitita = false)).price)
    }

    @Test
    fun `bonusul cu semn ramane bonus - +RON si +DD,DD RON nu devin pret`() {
        egal("14.20", en.parseaza(en(bonus = "+RON8.50")).price)
        egal("14.20", en.parseaza(en(bonus = "+ RON8.50")).price)
        egal("14.20", ro.parseaza(ro(bonus = "+8,50 RON ▢")).price)
        egal("14.20", ro.parseaza(ro(bonus = "+ 8,50 RON")).price)
        egal("14.20", ro.parseaza(ro(bonus = "+8,50 RON", inaltimeBonus = 130)).price)
    }

    @Test
    fun `literele din corectiiCifre lipite se corecteaza ca pana acum`() {
        val pret = ro.parseaza(ro(pret = "l4,20 RON"))
        egal("14.20", pret.price)
        egal("10.50", ro.parseaza(ro(pret = "1O,50 RON")).price)
        egal("10.50", ro.parseaza(ro(pret = "lo,50 RON")).price)
        egal("12", ro.parseaza(ro(pre = "▢: l2 min. (2.1 km)")).pickupMinutes)
        egal("10", ro.parseaza(ro(pre = "▢: 1o min. (2.1 km)")).pickupMinutes)
        egal("11", ro.parseaza(ro(pre = "▢: II min. (2.1 km)")).pickupMinutes)
        egal("10", ro.parseaza(ro(pre = "▢: lO min. (2.1 km)")).pickupMinutes)
        egal("12", en.parseaza(en(pre = "l2 mins (2.1 km)")).pickupMinutes)
        egal("10", en.parseaza(en(cursa = "I0 mins (5.4 km) ▢")).tripMinutes)
    }

    @Test
    fun `minute normale - DD min, D min, D-D min (interval) si ultimul rand cu ancora`() {
        egal("12", ro.parseaza(ro(pre = "▢: 12 min. (2.1 km)")).pickupMinutes)
        egal("12", en.parseaza(en(pre = "12 mins (2.1 km)")).pickupMinutes)
        egal("12", bolt.parseaza(bolt(pre = "12 min$nb• 2.1 km")).pickupMinutes)
        egal("6", ro.parseaza(ro()).pickupMinutes)
        egal("6", en.parseaza(en()).pickupMinutes)
        egal("6", bolt.parseaza(bolt()).pickupMinutes)
        egal("75", ro.parseaza(ro(pre = "▢: 75 min. (2.1 km)")).pickupMinutes)
        // „3-5 min”: intervalul ramane citit exact ca inainte de D-312 (capatul din dreapta, lipit de „min”).
        egal("5", ro.parseaza(ro(pre = "▢: 3-5 min. (2.1 km)")).pickupMinutes)
        egal("5", en.parseaza(en(pre = "3-5 mins (2.1 km)")).pickupMinutes)
        egal("5", ro.parseaza(ro(pre = "▢: 3 – 5 min. (2.1 km)")).pickupMinutes)
        egal("5", bolt.parseaza(bolt(pre = "3-5 min$nb• 2.1 km")).pickupMinutes)
    }

    @Test
    fun `un cuvant lipit de minute ramane citire directa (La5 min, F-025), doar litera singura da necunoscut`() {
        egal("5", ro.parseaza(ro(pre = "La5 min. (2.1 km)")).pickupMinutes)
        egal("5", en.parseaza(en(pre = "La5 mins (2.1 km)")).pickupMinutes)
        assertNull(ro.parseaza(ro(pre = "S5 min. (2.1 km)")).pickupMinutes)
        assertNull(ro.parseaza(ro(pre = "▢: S5 min. (2.1 km)")).pickupMinutes)
    }

    @Test
    fun `o litera urmata de spatiu inaintea numarului nu blocheaza citirea`() {
        egal("14.20", ro.parseaza(ro(pret = "Total 14,20 RON")).price)
        egal("6", ro.parseaza(ro(pre = "Ajungi in 6 min. (2.1 km)")).pickupMinutes)
        egal("6", en.parseaza(en(pre = "Pickup in 6 mins (2.1 km)")).pickupMinutes)
        egal("14.20", ro.parseaza(ro(pret = "▢14,20 RON")).price)
    }
}
