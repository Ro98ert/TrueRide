package app.trueride.core.parse.sintetic

import app.trueride.core.model.ParsedOffer
import app.trueride.core.parse.BOLT_RO_V1
import app.trueride.core.parse.Cutie
import app.trueride.core.parse.LinieOcr
import app.trueride.core.parse.OfferParser
import app.trueride.core.parse.ParserPeLimbi
import app.trueride.core.parse.UBER_EN_V1
import app.trueride.core.parse.UBER_RO_V1
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Un test explicit pentru fiecare forma reala din lotul dev (doar structura; toate cifrele sunt inventate, nicio
 * linie din lot). Asteptarea vine din decizii (D-080, D-122..124, D-130, D-143, D-148, D-288.4, D-299, D-305) si din
 * invariantele 5 si 6, nu din ce da parserul azi: cand parserul da ALTA valoare decat cea asteptata, testul ramane
 * aici cu `@Disabled("P-sint-N: ...")`.
 */
class RegresieTipareRealeTest {

    private val ro = OfferParser(UBER_RO_V1)
    private val en = OfferParser(UBER_EN_V1)
    private val bolt = OfferParser(BOLT_RO_V1)
    private val uber = ParserPeLimbi(listOf(ro, en))
    private val nb = " "

    private fun linie(text: String, sus: Int, inaltime: Int, recitita: Boolean = false) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime), recitita)

    private val corp = listOf(1000, 1100, 1200).map { linie("▢ ▢ ▢", it, 45) }

    private fun ofertaRo(
        pret: String = "14,20 RON",
        preluare: String = "▢: 6 min. (2.1 km)",
        cursa: String = "▢ 11 min. (5.4 km) ▢",
        extra: List<String> = emptyList(),
        recitita: Boolean = false,
    ): List<LinieOcr> = corp +
        linie(pret, 1300, 115, recitita) + linie(preluare, 1700, 45) + linie(cursa, 1800, 45) +
        extra.mapIndexed { i, t -> linie(t, 1900 + 100 * i, 45) }

    private fun ofertaEn(
        pret: String = "RON14.20",
        preluare: String = "6 mins (2.1 km)",
        cursa: String = "11 mins (5.4 km) ▢",
        extra: List<String> = emptyList(),
        recitita: Boolean = false,
    ): List<LinieOcr> = corp +
        linie(pret, 1300, 90, recitita) + linie(preluare, 1700, 46) + linie(cursa, 1800, 46) +
        extra.mapIndexed { i, t -> linie(t, 1900 + 100 * i, 46) }

    private fun ofertaBolt(
        pret: String = "14,20${nb}lei (▢, ▢ ▢)",
        preluare: String = "6 min$nb• 2.1 km",
        cursa: String = "11 min$nb• 5.4 km",
        extra: List<String> = emptyList(),
    ): List<LinieOcr> = corp +
        linie(pret, 1300, 114) + linie(preluare, 1700, 82) + linie(cursa, 1800, 82) +
        extra.mapIndexed { i, t -> linie(t, 1900 + 100 * i, 82) }

    private fun egal(asteptat: String, real: BigDecimal?) =
        assertEquals(0, BigDecimal(asteptat).compareTo(requireNotNull(real) { "asteptat $asteptat, primit null" }))

    private fun oricare(real: BigDecimal?, vararg permise: String) =
        assertTrue(real == null || permise.any { BigDecimal(it).compareTo(real) == 0 }, "valoare neasteptata: $real")

    private fun completa(o: ParsedOffer, pret: String, pm: String, pk: String, cm: String, ck: String) {
        egal(pret, o.price); egal(pm, o.pickupMinutes); egal(pk, o.pickupDistance)
        egal(cm, o.tripMinutes); egal(ck, o.tripDistance)
    }

    // ---------- Tinta 1: randul „DD min” + text sub cursa nu e ancora

    @Test
    fun `Uber RO - randul DD min si text sub cursa nu e ancora, oferta ramane completa`() {
        val o = uber.parseaza(ofertaRo(extra = listOf("3 min ▢ ▢ ▢")))

        completa(o, "14.20", "6", "2.1", "11", "5.4")
    }

    @Test
    fun `Uber EN - randul D min si text sub cursa nu e ancora, oferta ramane completa`() {
        val o = uber.parseaza(ofertaEn(extra = listOf("3 min ▢ ▢ ▢")))

        completa(o, "14.20", "6", "2.1", "11", "5.4")
    }

    @Test
    fun `Bolt - randul DD min fara punct-mijloc si randul Ora nu schimba citirea`() {
        val o = bolt.parseaza(ofertaBolt(extra = listOf("3 min ▢", "Oră")))

        completa(o, "14.20", "6", "2.1", "11", "5.4")
    }

    // ---------- bonus cu semn

    @Test
    fun `Uber RO - bonusul +RON, chiar mai inalt decat pretul, nu e pret`() {
        val linii = corp + linie("+3,50 RON ▢", 1250, 130) + ofertaRo().drop(3)

        egal("14.20", uber.parseaza(linii).price)
    }

    @Test
    fun `Uber EN - bonusul +RON3 si +RON 15 cu bara nu sunt pret`() {
        val a = corp + linie("+RON3.50", 1250, 100) + ofertaEn().drop(3)
        val b = corp + linie("+RON 15/▢", 1250, 100) + ofertaEn().drop(3)

        egal("14.20", uber.parseaza(a).price)
        egal("14.20", uber.parseaza(b).price)
    }

    // ---------- doua oferte in cadru (invariantul 5)

    @Test
    fun `Uber RO - doua oferte in cadru nu dau segmente amestecate, pretul e al uneia sau necunoscut`() {
        val a = ofertaRo()
        val b = ofertaRo(pret = "9,80 RON", preluare = "▢: 3 min. (1.2 km)", cursa = "▢ 7 min. (3.3 km) ▢")
            .map { it.copy(cutie = it.cutie.copy(sus = it.cutie.sus + 1500, jos = it.cutie.jos + 1500)) }
        val o = uber.parseaza(a + b)

        assertNull(o.pickupMinutes); assertNull(o.pickupDistance); assertNull(o.tripMinutes); assertNull(o.tripDistance)
        oricare(o.price, "14.20", "9.80")
    }

    @Test
    fun `Uber RO - doua sume pe o linie (prima fara RON) dau pretul uneia sau necunoscut`() {
        val o = uber.parseaza(ofertaRo(pret = "* 7,50 ▢ 9,20 RON"))

        oricare(o.price, "7.50", "9.20")
    }

    @Test
    fun `Uber EN - doua sume pe o linie dau pretul uneia sau necunoscut`() {
        val o = uber.parseaza(ofertaEn(pret = "4.20 RON12.50"))

        oricare(o.price, "4.20", "12.50")
    }

    // ---------- interval de minute

    @Test
    fun `Uber EN - intervalul D-DD min da una dintre margini sau necunoscut, km raman exacti`() {
        val o = uber.parseaza(ofertaEn(preluare = "8-12 mins (3.4 km)"))

        oricare(o.pickupMinutes, "8", "12")
        egal("3.4", o.pickupDistance)
        egal("11", o.tripMinutes)
    }

    @Test
    fun `Uber RO - intervalul D-D min in segment da una dintre margini sau necunoscut`() {
        val o = uber.parseaza(ofertaRo(preluare = "▢: 3-5 min. (1.5 km)"))

        oricare(o.pickupMinutes, "3", "5")
        egal("1.5", o.pickupDistance)
    }

    @Test
    fun `Uber RO - randul D-D min fara paranteza nu e ancora`() {
        val o = uber.parseaza(ofertaRo(extra = listOf("3-5 min ▢")))

        completa(o, "14.20", "6", "2.1", "11", "5.4")
    }

    // ---------- pret intreg si virgula pierduta

    @Test
    fun `Uber RO - DD RON fara recitire ramane necunoscut, recitit da 25 (D-305)`() {
        assertNull(uber.parseaza(ofertaRo(pret = "25 RON")).price)
        egal("25", uber.parseaza(ofertaRo(pret = "25 RON", recitita = true)).price)
    }

    @Test
    fun `Uber EN - RONDD fara recitire ramane necunoscut, recitit da 25 (D-299)`() {
        assertNull(uber.parseaza(ofertaEn(pret = "RON25")).price)
        egal("25", uber.parseaza(ofertaEn(pret = "RON25", recitita = true)).price)
    }

    @Test
    fun `pretul intreg de 1 sau 3 cifre ramane necunoscut chiar recitit (D-299 punctul 3)`() {
        assertNull(uber.parseaza(ofertaRo(pret = "7 RON", recitita = true)).price)
        assertNull(uber.parseaza(ofertaRo(pret = "125 RON", recitita = true)).price)
        assertNull(uber.parseaza(ofertaEn(pret = "RON7", recitita = true)).price)
        assertNull(uber.parseaza(ofertaEn(pret = "RON125", recitita = true)).price)
    }

    @Test
    fun `virgula pierduta - DDDD RON si RONDDDD raman necunoscute, chiar recitite (D-080)`() {
        assertNull(uber.parseaza(ofertaRo(pret = "1420 RON")).price)
        assertNull(uber.parseaza(ofertaRo(pret = "1420 RON", recitita = true)).price)
        assertNull(uber.parseaza(ofertaRo(pret = "1420${nb}RON")).price)
        assertNull(uber.parseaza(ofertaEn(pret = "RON1420")).price)
        assertNull(uber.parseaza(ofertaEn(pret = "RON1420", recitita = true)).price)
        assertNull(bolt.parseaza(ofertaBolt(pret = "1420${nb}lei")).price)
    }

    @Test
    fun `pretul intreg care a trecut prin harta de litere ramane necunoscut chiar recitit (D-299 punctul 6)`() {
        assertNull(uber.parseaza(ofertaRo(pret = "2O RON", recitita = true)).price)
        assertNull(uber.parseaza(ofertaEn(pret = "RON2O", recitita = true)).price)
    }

    // ---------- NBSP

    @Test
    fun `NBSP in loc de spatiu - RO inainte de RON, EN dupa RON, Bolt cu spatiu obisnuit`() {
        egal("14.20", uber.parseaza(ofertaRo(pret = "14,20${nb}RON")).price)
        egal("14.20", uber.parseaza(ofertaEn(pret = "RON${nb}14.20")).price)
        egal("14.20", bolt.parseaza(ofertaBolt(pret = "14,20 lei (▢, ▢ ▢)")).price)
    }

    @Test
    fun `NBSP intre numar si min - Bolt citeste exact, Uber RO citeste exact sau lasa necunoscut`() {
        egal("6", bolt.parseaza(ofertaBolt(preluare = "6${nb}min$nb•${nb}2.1${nb}km")).pickupMinutes)
        // `\s` din regex-ul Java nu prinde U+00A0: azi minutele raman necunoscute (pierdut, nu gresit); valoarea gresita ar fi eroare.
        oricare(uber.parseaza(ofertaRo(preluare = "▢: 6${nb}min. (2.1 km)")).pickupMinutes, "6")
    }

    // ---------- paranteza trunchiata, km cu virgula, unitati

    @Test
    fun `paranteza trunchiata (D▢) lasa km necunoscut si minutele exacte`() {
        val o = uber.parseaza(ofertaRo(preluare = "▢: 6 min. (2▢)"))

        assertNull(o.pickupDistance)
        egal("6", o.pickupMinutes)
        egal("14.20", o.price)
    }

    @Test
    fun `km cu virgula se citesc ca cei cu punct`() {
        completa(uber.parseaza(ofertaRo(preluare = "▢: 6 min. (2,1 km)", cursa = "▢ 11 min. (5,4 km)")), "14.20", "6", "2.1", "11", "5.4")
        completa(uber.parseaza(ofertaEn(preluare = "6 mins (2,1 km)")), "14.20", "6", "2.1", "11", "5.4")
        completa(bolt.parseaza(ofertaBolt(preluare = "6 min$nb• 2,1 km")), "14.20", "6", "2.1", "11", "5.4")
    }

    @Test
    fun `unitatile citite gresit Km, kn, krn, k m si Km cu punct se citesc ca km (D-288 punctul 4)`() {
        listOf("Km", "kn", "krn", "k m", "Km.").forEach { u ->
            completa(uber.parseaza(ofertaRo(preluare = "▢: 6 min. (2.1 $u)")), "14.20", "6", "2.1", "11", "5.4")
            completa(uber.parseaza(ofertaEn(preluare = "6 mins (2.1 $u)")), "14.20", "6", "2.1", "11", "5.4")
        }
    }

    @Test
    fun `unitatea mi (mile) lasa km necunoscut`() {
        assertNull(uber.parseaza(ofertaRo(preluare = "▢: 6 min. (2.1 mi)")).pickupDistance)
        assertNull(uber.parseaza(ofertaEn(preluare = "6 mins (2.1 mi)")).pickupDistance)
    }

    // ---------- val 2, perturbarea 1: valori la margini

    // D-329.1: preturile de 3-4 cifre stau pe o cursa lunga, ca lei/km sa ramana in banda setului.
    @Test
    fun `pretul de 3 si 4 cifre se citeste exact, cu separator de mii e exact sau necunoscut (D-080)`() {
        egal("123.45", uber.parseaza(ofertaRo(cursa = "▢ 150 min. (123.4 km) ▢", pret = "123,45 RON")).price)
        egal("123.45", uber.parseaza(ofertaEn(cursa = "150 mins (123.4 km) ▢", pret = "RON123.45")).price)
        egal("123.45", bolt.parseaza(ofertaBolt(cursa = "150 min$nb• 123.4 km", pret = "123,45${nb}lei (▢, ▢ ▢)")).price)
        egal("1234.56", uber.parseaza(ofertaRo(cursa = "▢ 150 min. (123.4 km) ▢", pret = "1234,56 RON")).price)
        // „234,56” nu are voie sa iasa din „1.234,56”: lipit de punct, numarul e taiat, deci ambiguu.
        oricare(uber.parseaza(ofertaRo(cursa = "▢ 150 min. (123.4 km) ▢", pret = "1.234,56 RON")).price, "1234.56")
        oricare(uber.parseaza(ofertaEn(cursa = "150 mins (123.4 km) ▢", pret = "RON1,234.56")).price, "1234.56")
        oricare(uber.parseaza(ofertaRo(cursa = "▢ 150 min. (123.4 km) ▢", pret = "1 234,56 RON")).price, "1234.56")
        oricare(bolt.parseaza(ofertaBolt(cursa = "150 min$nb• 123.4 km", pret = "1.234,56${nb}lei (▢, ▢ ▢)")).price, "1234.56")
    }

    @Test
    fun `minutele peste 60 si km peste 100 sau sub 1 se citesc exact`() {
        completa(uber.parseaza(ofertaRo(pret = "142,50 RON", preluare = "▢: 75 min. (60.5 km)", cursa = "▢ 150 min. (123.4 km) ▢")), "142.50", "75", "60.5", "150", "123.4")
        completa(uber.parseaza(ofertaEn(pret = "RON142.50", preluare = "75 mins (60.5 km)", cursa = "150 mins (123.4 km) ▢")), "142.50", "75", "60.5", "150", "123.4")
        completa(bolt.parseaza(ofertaBolt(pret = "142,50${nb}lei (▢, ▢ ▢)", preluare = "75 min$nb• 60.5 km", cursa = "150 min$nb• 123.4 km")), "142.50", "75", "60.5", "150", "123.4")
        completa(uber.parseaza(ofertaRo(preluare = "▢: 2 min. (0.4 km)")), "14.20", "2", "0.4", "11", "5.4")
        completa(uber.parseaza(ofertaRo(preluare = "▢: 2 min. (0,4 km)")), "14.20", "2", "0.4", "11", "5.4")
        completa(uber.parseaza(ofertaEn(preluare = "2 mins (0.4 km)")), "14.20", "2", "0.4", "11", "5.4")
        completa(bolt.parseaza(ofertaBolt(preluare = "2 min$nb• 0.4 km")), "14.20", "2", "0.4", "11", "5.4")
    }

    @Test
    fun `forma cu ore si o singura cifra de minute (D h D min) ramane necunoscuta, nu minutele singure`() {
        // Cu 2 cifre de minute („2 h 48 min”) garda D-312 (P-sint-4) da necunoscut: GardaCifreLipiteTest.
        oricare(uber.parseaza(ofertaRo(preluare = "▢: 1 h 5 min. (2.1 km)")).pickupMinutes, "65")
        oricare(uber.parseaza(ofertaEn(preluare = "1 h 5 mins (2.1 km)")).pickupMinutes, "65")
        oricare(bolt.parseaza(ofertaBolt(preluare = "1 h 5 min$nb• 2.1 km")).pickupMinutes, "65")
    }

    // ---------- val 2, perturbarea 2: linii sparte in doua casete pe acelasi rand

    private fun casete(text: String, stanga: Int, dreapta: Int, sus: Int, inaltime: Int) =
        LinieOcr(text, Cutie(stanga, sus, dreapta, sus + inaltime))

    private fun segmenteExacte(o: ParsedOffer) {
        egal("6", o.pickupMinutes); egal("2.1", o.pickupDistance); egal("11", o.tripMinutes); egal("5.4", o.tripDistance)
    }

    @Test
    fun `pretul spart in doua casete adiacente nu da jumatatea ca pret, segmentele raman exacte`() {
        val segmente = ofertaRo().drop(4)
        val spart = { a: String, b: String, h: Int -> corp + casete(a, 100, 150, 1300, h) + casete(b, 156, 330, 1300, h) + segmente }
        val o = uber.parseaza(spart("14,", "20 RON", 115))

        oricare(o.price, "14.20")
        segmenteExacte(o)
        oricare(uber.parseaza(spart("14", ",20 RON", 115)).price, "14.20")
        oricare(uber.parseaza(spart("14,20", "RON", 115)).price, "14.20")
        oricare(uber.parseaza(corp + casete("RON14.", 100, 200, 1300, 90) + casete("20", 206, 260, 1300, 90) + ofertaEn().drop(4)).price, "14.20")
        oricare(bolt.parseaza(corp + casete("14,", 100, 150, 1300, 114) + casete("20${nb}lei (▢, ▢ ▢)", 156, 400, 1300, 114) + ofertaBolt().drop(4)).price, "14.20")
    }

    @Test
    fun `segmentul spart in doua casete pe acelasi rand nu da cifre amestecate (invariantele 5 si 6)`() {
        val l = ofertaRo()
        val preluareSparta = corp + l[3] + casete("▢: 6 min.", 100, 300, 1700, 45) + casete("(2.1 km)", 306, 460, 1700, 45) + l[5]
        val o = uber.parseaza(preluareSparta)

        egal("14.20", o.price)   // o ancora ramane: pretul se pastreaza (D-130)
        oricare(o.pickupMinutes, "6"); oricare(o.pickupDistance, "2.1"); oricare(o.tripMinutes, "11"); oricare(o.tripDistance, "5.4")
        val ambele = uber.parseaza(corp + l[3] + casete("▢: 6 min.", 100, 300, 1700, 45) + casete("(2.1 km)", 306, 460, 1700, 45) +
            casete("▢ 11 min.", 100, 300, 1800, 45) + casete("(5.4 km) ▢", 306, 460, 1800, 45))
        oricare(ambele.price, "14.20"); oricare(ambele.pickupMinutes, "6"); oricare(ambele.tripDistance, "5.4")
    }

    // ---------- val 2, perturbarea 3: linie dublata, oferta citita de doua ori in acelasi cadru

    private fun dubla(l: LinieOcr) = l.copy(cutie = Cutie(l.cutie.stanga + 3, l.cutie.sus + 4, l.cutie.dreapta + 3, l.cutie.jos + 4))

    @Test
    fun `linia pretului dublata da acelasi pret, linia unui segment dublata lasa segmentele necunoscute`() {
        val l = ofertaRo()

        completa(uber.parseaza(l + dubla(l[3])), "14.20", "6", "2.1", "11", "5.4")
        completa(uber.parseaza(ofertaEn().let { it + dubla(it[3]) }), "14.20", "6", "2.1", "11", "5.4")
        val treiAncore = uber.parseaza(l + dubla(l[5]))
        egal("14.20", treiAncore.price)
        oricare(treiAncore.pickupMinutes, "6"); oricare(treiAncore.pickupDistance, "2.1")
        oricare(treiAncore.tripMinutes, "11"); oricare(treiAncore.tripDistance, "5.4")
    }

    @Test
    fun `oferta intreaga citita de doua ori da pretul ei si nicio cifra de segment amestecata`() {
        listOf(uber to ofertaRo(), uber to ofertaEn(), bolt to ofertaBolt()).forEach { (parser, l) ->
            val o = parser.parseaza(l + l.map(::dubla))

            egal("14.20", o.price)
            oricare(o.pickupMinutes, "6"); oricare(o.pickupDistance, "2.1"); oricare(o.tripMinutes, "11"); oricare(o.tripDistance, "5.4")
        }
    }

    // ---------- val 2, perturbarea 4: spatii in numar, punct final, liniute Unicode in interval

    @Test
    fun `spatiul in interiorul pretului (1 4,20) da pretul exact sau necunoscut, niciodata 4,20`() {
        oricare(uber.parseaza(ofertaRo(pret = "1 4,20 RON")).price, "14.20")
        oricare(uber.parseaza(ofertaRo(pret = "1${nb}4,20 RON")).price, "14.20")
        oricare(uber.parseaza(ofertaEn(pret = "RON1 4.20")).price, "14.20")
        oricare(bolt.parseaza(ofertaBolt(pret = "1 4,20${nb}lei (▢, ▢ ▢)")).price, "14.20")
        segmenteExacte(uber.parseaza(ofertaRo(pret = "1 4,20 RON")))
    }

    @Test
    fun `punctul final dupa pret si dupa unitate nu schimba cifrele`() {
        egal("14.20", uber.parseaza(ofertaRo(pret = "14,20 RON.")).price)
        // Azi „RON14.20.” ramane necunoscut (granita `(?![\d.,])`): pierdut, nu gresit.
        oricare(uber.parseaza(ofertaEn(pret = "RON14.20.")).price, "14.20")
        completa(uber.parseaza(ofertaRo(preluare = "▢: 6 min. (2.1 km.)")), "14.20", "6", "2.1", "11", "5.4")
        completa(uber.parseaza(ofertaRo(preluare = "▢: 6 min. (2.1 km).")), "14.20", "6", "2.1", "11", "5.4")
        completa(uber.parseaza(ofertaEn(preluare = "6 mins (2.1 km).")), "14.20", "6", "2.1", "11", "5.4")
    }

    @Test
    fun `liniuta Unicode in intervalul de minute (en dash, em dash, minus) da o margine sau necunoscut`() {
        listOf("–", "—", "−", " – ", " — ", " − ").forEach { d ->
            val ro = uber.parseaza(ofertaRo(preluare = "▢: 3${d}5 min. (1.5 km)"))
            val en = uber.parseaza(ofertaEn(preluare = "3${d}5 mins (1.5 km)"))

            oricare(ro.pickupMinutes, "3", "5"); oricare(en.pickupMinutes, "3", "5")
            egal("1.5", ro.pickupDistance); egal("1.5", en.pickupDistance)
        }
    }
}
