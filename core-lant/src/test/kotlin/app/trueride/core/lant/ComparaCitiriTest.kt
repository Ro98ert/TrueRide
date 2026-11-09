package app.trueride.core.lant

import app.trueride.core.model.OfferField
import app.trueride.core.model.ParsedOffer
import app.trueride.core.parse.BOLT_RO_V1
import app.trueride.core.parse.CititorFixturi
import app.trueride.core.parse.ComparatorFixturi
import app.trueride.core.parse.Cutie
import app.trueride.core.parse.LinieOcr
import app.trueride.core.parse.OfferParser
import app.trueride.core.parse.ParserOferta
import app.trueride.core.parse.ParserPeLimbi
import app.trueride.core.parse.UBER_EN_V1
import app.trueride.core.parse.UBER_RO_V1
import java.io.File
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * D-394: comparatia dintre prima citire (cardul) si a doua citire OCR, in cutiile primei citiri. Linii sintetice
 * cu cifre inventate si fixture-urile reale (la esec se tiparesc doar numere si nume de campuri, invariantul 2).
 */
class ComparaCitiriTest {

    private val boltCard = OfferParser(BOLT_RO_V1)
    private val uber = ParserPeLimbi(listOf(OfferParser(UBER_RO_V1), OfferParser(UBER_EN_V1)))
    private val compara = ComparaCitiri.PRODUS
    private val nb = " "

    private fun linie(text: String, sus: Int, inaltime: Int) =
        LinieOcr(text, Cutie(stanga = 100, sus = sus, dreapta = 700, jos = sus + inaltime))

    private val corp = listOf(1000, 1100, 1200).map { linie("▢ ▢ ▢", it, 45) }
    private val pret = linie("14,20${nb}lei (▢, ▢ ▢)", 1300, 114)
    private val pre = linie("6 min${nb}• 2.1 km", 1700, 82)
    private val cursa = linie("11 min${nb}• 5.4 km", 1800, 82)
    private val ecran = corp + pret + pre + cursa
    private val prima = boltCard.parseaza(ecran)
    private val card = boltCard.liniiFolosite(ecran)

    private fun cu(vechi: LinieOcr, text: String, deplasare: Int = 0) =
        LinieOcr(text, vechi.cutie.copy(sus = vechi.cutie.sus + deplasare, jos = vechi.cutie.jos + deplasare))

    private fun bolt(recitite: List<LinieOcr>, p: ParsedOffer = prima) =
        compara.compara(PlatformaSofer.BOLT, ecran, card, p, recitite)

    private fun neschimbate() = listOf(pret, pre, cursa)

    @Test
    fun `liniile deplasate sunt neverificabile`() {
        val c = bolt(listOf(cu(pret, pret.text, deplasare = 40), pre, cursa))

        assertEquals(RezultatVerificare.NEVERIFICABIL, c.rezultat)
        assertEquals(MotivNeverificabil.POZITIE, c.motiv)
    }

    @Test
    fun `cutia nodului mai inalta decat textul nu e deplasare`() {
        // Nodul pretului are 114 px, textul 40: centrele difera cu 27 px (< 28,5), marginile de jos cu 64 px.
        val text = LinieOcr(pret.text, Cutie(pret.cutie.stanga, pret.cutie.sus + 10, pret.cutie.dreapta, pret.cutie.sus + 50))

        assertEquals(RezultatVerificare.POTRIVIT, bolt(listOf(text, pre, cursa)).rezultat)
    }

    @Test
    fun `citiri identice sunt potrivite`() {
        val c = bolt(neschimbate())

        assertEquals(RezultatVerificare.POTRIVIT, c.rezultat)
        assertNull(c.motiv)
        assertTrue(c.diferite.isEmpty())
    }

    @Test
    fun `pret diferit e diferit pe pret`() {
        val c = bolt(listOf(cu(pret, "14,28${nb}lei (▢, ▢ ▢)"), pre, cursa))

        assertEquals(RezultatVerificare.DIFERIT, c.rezultat)
        assertEquals(listOf(OfferField.PRICE), c.diferite)
    }

    @Test
    fun `km cursa si minute preluare diferite dau doua campuri`() {
        val c = bolt(listOf(pret, cu(pre, "8 min 2.1 km"), cu(cursa, "11 min 5.9 km")))

        assertEquals(RezultatVerificare.DIFERIT, c.rezultat)
        assertEquals(listOf(OfferField.PICKUP_MINUTES, OfferField.TRIP_DISTANCE), c.diferite)
    }

    @Test
    fun `zecimale egale numeric sunt potrivite`() {
        val cuZero = prima.copy(pickupDistance = BigDecimal("2.10"), price = BigDecimal("14.2"))

        assertEquals(RezultatVerificare.POTRIVIT, bolt(neschimbate(), cuZero).rezultat)
    }

    @Test
    fun `linie de card nerecitita e neverificabila camp necitit`() {
        val c = bolt(listOf(pret, pre))

        assertEquals(RezultatVerificare.NEVERIFICABIL, c.rezultat)
        assertEquals(MotivNeverificabil.CAMP_NECITIT, c.motiv)
    }

    @Test
    fun `linie recitita fara camp citibil e neverificabila camp necitit`() {
        val c = bolt(listOf(pret, cu(pre, "▢ ▢ ▢"), cursa))

        assertEquals(RezultatVerificare.NEVERIFICABIL, c.rezultat)
        assertEquals(MotivNeverificabil.CAMP_NECITIT, c.motiv)
    }

    @Test
    fun `un camp diferit si altul necitit e tot diferit`() {
        val c = bolt(listOf(cu(pret, "14,28${nb}lei (▢, ▢ ▢)"), pre, cu(cursa, "▢ ▢ ▢")))

        assertEquals(RezultatVerificare.DIFERIT, c.rezultat)
        assertEquals(listOf(OfferField.PRICE), c.diferite)
    }

    @Test
    fun `doua linii OCR peste un nod se unesc in ordine`() {
        val stanga = LinieOcr("14,20", Cutie(100, 1320, 300, 1360))
        val dreapta = LinieOcr("lei (▢, ▢ ▢)", Cutie(320, 1320, 600, 1360))

        assertEquals(RezultatVerificare.POTRIVIT, bolt(listOf(dreapta, stanga, pre, cursa)).rezultat)
    }

    @Test
    fun `liniile straine din zona nu intra in comparatie`() {
        val f = fixtureComplete("uber-ro").first()
        val toate = CititorFixturi.linii(f)
        val liniiCard = uber.liniiFolosite(toate)
        val straine = toate.filter { it !in liniiCard }.map { it.copy(text = "99 min 9.9 km") }
        assertTrue(straine.isNotEmpty(), "fixtura nu are linii straine")

        val c = compara.compara(PlatformaSofer.UBER, toate, liniiCard, uber.parseaza(toate), liniiCard + straine)

        assertEquals(RezultatVerificare.POTRIVIT, c.rezultat, "straine: ${straine.size}, motiv ${c.motiv}, campuri ${c.diferite}")
    }

    @Test
    fun `perechile contin doar campurile diferite`() {
        val c = bolt(listOf(cu(pret, "14,28${nb}lei (▢, ▢ ▢)"), pre, cursa))

        assertEquals(c.diferite.toSet(), c.perechi.keys)
        val (veche, noua) = assertNotNull(c.perechi[OfferField.PRICE])
        assertEquals(0, BigDecimal("14.20").compareTo(veche))
        assertEquals(0, BigDecimal("14.28").compareTo(noua))
    }

    @Test
    fun `toString nu scrie cifre`() {
        val c = bolt(listOf(cu(pret, "14,28${nb}lei (▢, ▢ ▢)"), pre, cursa))

        assertTrue(c.toString().none { it.isDigit() }, "toString are cifre")
        assertTrue(c.toString().contains("PRICE"))
    }

    @Test
    fun `platforma fara parser e neverificabila esec`() {
        val c = ComparaCitiri(emptyMap()).compara(PlatformaSofer.BOLT, ecran, card, prima, neschimbate())

        assertEquals(RezultatVerificare.NEVERIFICABIL, c.rezultat)
        assertEquals(MotivNeverificabil.ESEC, c.motiv)
    }

    private val sursePrima: Map<String, Pair<PlatformaSofer, ParserOferta>> = mapOf(
        "uber" to (PlatformaSofer.UBER to uber),
        "uber-ro" to (PlatformaSofer.UBER to uber),
        "uber-en-oferte" to (PlatformaSofer.UBER to uber),
        "bolt-ro" to (PlatformaSofer.BOLT to boltCard),
    )

    private fun fixtureComplete(director: String): List<File> = ComparatorFixturi.fixturi(director).filter { f ->
        val e = CititorFixturi.eticheta(ComparatorFixturi.etichetaDe(f))
        listOf("price", "pickupMinutes", "tripMinutes", "pickupDistance", "tripDistance").all { e[it] != null } &&
            citeste(director, f).first.let { p -> OfferField.entries.all { valoareDin(p, it) != null } }
    }

    private fun citeste(director: String, f: File): Triple<ParsedOffer, List<LinieOcr>, List<LinieOcr>> {
        val parser = sursePrima.getValue(director).second
        val linii = CititorFixturi.linii(f)
        return Triple(parser.parseaza(linii), linii, parser.liniiFolosite(linii))
    }

    private fun valoareDin(o: ParsedOffer, c: OfferField) = when (c) {
        OfferField.PRICE -> o.price
        OfferField.PICKUP_MINUTES -> o.pickupMinutes
        OfferField.TRIP_MINUTES -> o.tripMinutes
        OfferField.PICKUP_DISTANCE -> o.pickupDistance
        OfferField.TRIP_DISTANCE -> o.tripDistance
    }

    private fun comparaFixtura(director: String, f: File, recitite: (List<LinieOcr>) -> List<LinieOcr>): Comparatie {
        val (p, linii, liniiCard) = citeste(director, f)
        return compara.compara(sursePrima.getValue(director).first, linii, liniiCard, p, recitite(liniiCard))
    }

    @Test
    fun `fixturi reale cu aceleasi linii sunt potrivite`() {
        var total = 0
        val nepotrivite = mutableListOf<String>()
        sursePrima.keys.forEach { d ->
            fixtureComplete(d).forEach { f ->
                total++
                val c = comparaFixtura(d, f) { it }
                if (c.rezultat != RezultatVerificare.POTRIVIT) nepotrivite += "$d: ${c.rezultat} ${c.motiv} ${c.diferite}"
            }
        }
        assertTrue(total >= 10, "fixture complete: $total")
        assertEquals(0, nepotrivite.count { it.contains("DIFERIT") }, "DIFERIT: $nepotrivite")
        assertEquals(emptyList(), nepotrivite, "nepotrivite din $total")
    }

    /** Schimba ultima cifra a primului numar cu doua zecimale de pe linia cu moneda (lei/RON). */
    private fun schimbaPretul(linii: List<LinieOcr>): List<LinieOcr> {
        val moneda = Regex("""\d+[.,]\d{2}""")
        var facut = false
        return linii.map { l ->
            val m = moneda.find(l.text)
            if (facut || m == null || !(l.text.contains("lei") || l.text.contains("RON"))) return@map l
            facut = true
            val i = m.range.last
            val d = l.text[i].digitToInt()
            l.copy(text = l.text.replaceRange(i, i + 1, ((d + 1) % 10).toString()))
        }
    }

    @Test
    fun `fixturi reale cu cifra schimbata pe pret sunt diferite`() {
        var total = 0
        val gresite = mutableListOf<String>()
        sursePrima.keys.forEach { d ->
            fixtureComplete(d).forEach { f ->
                total++
                val c = comparaFixtura(d, f, ::schimbaPretul)
                if (c.rezultat != RezultatVerificare.DIFERIT || c.diferite != listOf(OfferField.PRICE)) {
                    gresite += "$d: ${c.rezultat} ${c.motiv} ${c.diferite}"
                }
            }
        }
        assertEquals(emptyList(), gresite, "gresite din $total")
    }

    @Test
    fun `fixturi Bolt fara bulina sunt potrivite`() {
        val gresite = fixtureComplete("bolt-ro").mapNotNull { f ->
            val c = comparaFixtura("bolt-ro", f) { l -> l.map { it.copy(text = it.text.replace("•", "")) } }
            if (c.rezultat == RezultatVerificare.POTRIVIT) null else "${c.rezultat} ${c.motiv} ${c.diferite}"
        }
        assertEquals(emptyList(), gresite)
    }
}
