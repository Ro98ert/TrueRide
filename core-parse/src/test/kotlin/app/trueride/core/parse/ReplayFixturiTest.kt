package app.trueride.core.parse

import app.trueride.core.model.ParsedOffer
import java.io.File
import java.math.BigDecimal
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** D-288.6: replay-ul pe fixture-uri trase din Sentry. Liniile sunt sintetice (cifre inventate). */
class ReplayFixturiTest {

    private val uber = ReplayFixturi.parserPentru("com.ubercab.driver")

    private fun l(text: String, sus: Int, h: Int, w: Int) = LinieOcr(text, Cutie(88, sus, 88 + w, sus + h))

    // Aceleasi inaltimi ca pe un telefon de ~2400 px: suma de 82 px, restul de ~40 px.
    private fun ecran(pret: String?, preluare: String?, cursa: String?) = buildList {
        add(l("▢", 900, 40, 200)); add(l("▢", 1000, 38, 150)); add(l("▢", 2200, 44, 200))
        if (pret != null) add(l(pret, 1469, 82, 480))
        if (preluare != null) add(l(preluare, 1750, 48, 450))
        if (cursa != null) add(l(cursa, 1890, 50, 430))
    }

    private val preluare = "3 mins (1.2 km) away"
    private val cursa = "11 mins (4.5 km) trip"

    @Test
    fun `oferta cu toate cele cinci campuri e succes si completa, fara motive`() {
        val r = ReplayFixturi.ruleaza(uber, ecran("RON12.34", preluare, cursa))

        assertEquals(ReplayFixturi.Rezultat("succes", true, emptyMap()), r)
    }

    @Test
    fun `fara km la preluare e succes partial cu motivul pe camp`() {
        val r = ReplayFixturi.ruleaza(uber, ecran("RON12.34", "3 min ( km) away", cursa))

        assertEquals("succes", r.stare)
        assertEquals(false, r.completa)
        assertEquals(mapOf("KM_PRELUARE" to "FARA_KM"), r.motive)
    }

    @Test
    fun `pret fara forma si segmente citite e eroare cu PRET FORMA_PRET`() {
        val r = ReplayFixturi.ruleaza(uber, ecran("RON12", preluare, cursa))

        assertEquals("eroare", r.stare)
        assertEquals(mapOf("PRET" to "FORMA_PRET"), r.motive)
    }

    @Test
    fun `nimic de oferta e gol, iar motivele pe toate cele cinci campuri`() {
        val r = ReplayFixturi.ruleaza(uber, ecran(null, null, null))

        assertEquals("gol", r.stare)
        assertEquals(setOf("PRET", "MIN_PRELUARE", "KM_PRELUARE", "MIN_CURSA", "KM_CURSA"), r.motive.keys)
    }

    @Test
    fun `parserul se alege dupa pachetul din fixture`() {
        assertTrue(ReplayFixturi.parserPentru("ee.mtakso.driver") !is ParserPeLimbi)
        assertTrue(ReplayFixturi.parserPentru("com.ubercab.driver") is ParserPeLimbi)
        assertTrue(ReplayFixturi.parserPentru("necunoscuta") is ParserPeLimbi)
    }

    @Test
    fun `linia de iesire are fisier, stare, completa si motive sortate`() {
        val r = ReplayFixturi.Rezultat("succes", false, mapOf("KM_CURSA" to "FARA_KM", "PRET" to "FORMA_PRET"))

        assertEquals("x.json\tsucces\t0\tKM_CURSA=FARA_KM,PRET=FORMA_PRET", ReplayFixturi.linieTsv("x.json", r))
        assertEquals("y.json\tgol\t0\t-", ReplayFixturi.linieTsv("y.json", ReplayFixturi.Rezultat("gol", false, emptyMap())))
    }

    @Test
    fun `directorul se reface fisier cu fisier, in ordine, fara etichete`() {
        val dir = createTempDirectory("replay").toFile()
        fun scrie(nume: String, pret: String) = File(dir, nume).writeText(
            """{
  "format": 1,
  "aplicatie": "com.ubercab.driver",
  "linii": [
    { "text": "$pret", "stanga": 88, "sus": 1469, "dreapta": 568, "jos": 1551 },
    { "text": "$preluare", "stanga": 88, "sus": 1750, "dreapta": 538, "jos": 1798 },
    { "text": "$cursa", "stanga": 88, "sus": 1890, "dreapta": 518, "jos": 1940 },
    { "text": "▢", "stanga": 88, "sus": 900, "dreapta": 288, "jos": 940 },
    { "text": "▢", "stanga": 88, "sus": 1000, "dreapta": 288, "jos": 1038 }
  ]
}
""",
            Charsets.UTF_8,
        )
        scrie("b.json", "RON12"); scrie("a.json", "RON12.34"); scrie("a.asteptat.json", "RON99.99")
        val iesire = File(dir, "iesire.tsv")

        val n = ReplayFixturi.ruleazaDirector(dir, iesire)

        assertEquals(2, n)
        assertEquals(
            listOf("a.json\tsucces\t1\t-", "b.json\teroare\t0\tPRET=FORMA_PRET"),
            iesire.readLines(Charsets.UTF_8),
        )
        dir.deleteRecursively()
    }

    private fun oferta(pret: String?, pm: String = "3", km: String = "1.2", tm: String = "11", kc: String = "4.5") =
        ParsedOffer(
            price = pret?.let { BigDecimal(it) },
            pickupMinutes = BigDecimal(pm), pickupDistance = BigDecimal(km),
            tripMinutes = BigDecimal(tm), tripDistance = BigDecimal(kc),
        )

    @Test
    fun `amprenta are 16 caractere hex si nu contine valorile in clar`() {
        val a = ReplayFixturi.amprenta(oferta("12.34"), "succes")

        assertTrue(Regex("[0-9a-f]{16}").matches(a), a)
        assertEquals(a, ReplayFixturi.amprenta(oferta("12.34"), "succes"))
    }

    @Test
    fun `amprenta nu depinde de zerourile de la coada`() {
        assertEquals(
            ReplayFixturi.amprenta(oferta("12.5", km = "1.2"), "succes"),
            ReplayFixturi.amprenta(oferta("12.50", km = "1.20"), "succes"),
        )
    }

    @Test
    fun `amprenta difera cand se schimba oricare dintre cele cinci valori`() {
        val baza = ReplayFixturi.amprenta(oferta("12.34"), "succes")
        val variante = listOf(
            oferta("12.35"), oferta("12.34", pm = "4"), oferta("12.34", km = "1.3"),
            oferta("12.34", tm = "12"), oferta("12.34", kc = "4.6"),
        ).map { ReplayFixturi.amprenta(it, "succes") }

        assertEquals(5, variante.toSet().size)
        assertTrue(baza !in variante)
    }

    @Test
    fun `camp necunoscut are token propriu, iar eroare si gol au amprenta din stare`() {
        val lipsa = ParsedOffer(price = BigDecimal("12.34"))
        val zero = ParsedOffer(price = BigDecimal("12.34"), pickupMinutes = BigDecimal.ZERO)

        assertTrue(ReplayFixturi.amprenta(lipsa, "succes") != ReplayFixturi.amprenta(zero, "succes"))
        assertEquals(ReplayFixturi.amprenta(ParsedOffer(), "gol"), ReplayFixturi.amprenta(ParsedOffer(), "gol"))
        assertTrue(ReplayFixturi.amprenta(ParsedOffer(), "gol") != ReplayFixturi.amprenta(ParsedOffer(), "eroare"))
    }

    @Test
    fun `fisierul de valori are nume si amprenta, iar TSV-ul ramane cel de patru coloane`() {
        val dir = createTempDirectory("replay").toFile()
        File(dir, "a.json").writeText(
            """{
  "format": 1,
  "aplicatie": "com.ubercab.driver",
  "linii": [
    { "text": "RON12.34", "stanga": 88, "sus": 1469, "dreapta": 568, "jos": 1551 },
    { "text": "$preluare", "stanga": 88, "sus": 1750, "dreapta": 538, "jos": 1798 },
    { "text": "$cursa", "stanga": 88, "sus": 1890, "dreapta": 518, "jos": 1940 },
    { "text": "▢", "stanga": 88, "sus": 900, "dreapta": 288, "jos": 940 },
    { "text": "▢", "stanga": 88, "sus": 1000, "dreapta": 288, "jos": 1038 }
  ]
}
""",
            Charsets.UTF_8,
        )
        val iesire = File(dir, "iesire.tsv")
        val valori = File(dir, "valori.tsv")

        ReplayFixturi.ruleazaDirector(dir, iesire, valori)

        assertEquals(listOf("a.json\tsucces\t1\t-"), iesire.readLines(Charsets.UTF_8))
        val rand = valori.readLines(Charsets.UTF_8).single().split("\t")
        assertEquals("a.json", rand[0])
        assertTrue(Regex("[0-9a-f]{16}").matches(rand[1]), rand[1])
        assertEquals(2, rand.size)
        dir.deleteRecursively()
    }

    // ---------- D-313: marcajul `recitita` din fixture ajunge la parser ca pe telefon

    private fun obiect(text: String, sus: Int, h: Int, recitita: Boolean = false) =
        """    { "text": "$text", "stanga": 100, "sus": $sus, "dreapta": 700, "jos": ${sus + h}${if (recitita) ", \"recitita\": true" else ""} }"""

    /** Ecran Uber EN sintetic cu pretul intreg „RON25” (cifre inventate), ca in `PretIntregUberEnTest`. */
    private fun fixturaIntreg(recitita: Boolean) = """{
  "format": 1,
  "aplicatie": "com.ubercab.driver",
  "linii": [
${obiect("RON25", 1232, 90, recitita)},
${obiect("6 mins (2.1 km) ▢", 1700, 46)},
${obiect("14 mins (7.2 km) ▢", 1800, 46)},
${obiect("▢ ▢ ▢", 1400, 46)},
${obiect("▢ ▢ ▢", 1500, 46)},
${obiect("▢ ▢ ▢", 1600, 46)}
  ]
}
"""

    @Test
    fun `fixture-ul vechi, fara marcaj, se citeste ca inainte si nicio linie nu e recitita`() {
        val dir = createTempDirectory("replay").toFile()
        val f = File(dir, "veche.json")
        f.writeText(fixturaIntreg(recitita = false).replace("RON25", "RON25 \\u00e9\\\"x"), Charsets.UTF_8)

        val linii = ReplayFixturi.linii(f)

        assertEquals(6, linii.size)
        assertEquals(CititorFixturi.linii(f), linii)
        assertTrue(linii.none { it.recitita })
        dir.deleteRecursively()
    }

    @Test
    fun `marcajul recitita se citeste doar pe linia care il are, restul liniei ramane la fel`() {
        val dir = createTempDirectory("replay").toFile()
        val f = File(dir, "noua.json").apply { writeText(fixturaIntreg(recitita = true), Charsets.UTF_8) }
        val fara = File(dir, "fara.json").apply { writeText(fixturaIntreg(recitita = false), Charsets.UTF_8) }

        val linii = ReplayFixturi.linii(f)

        assertEquals(listOf(true, false, false, false, false, false), linii.map { it.recitita })
        assertEquals(ReplayFixturi.linii(fara), linii.map { it.copy(recitita = false) })
        dir.deleteRecursively()
    }

    @Test
    fun `pretul intreg Uber EN se citeste doar cu marcajul, exact ca lantul de pe telefon`() {
        val dir = createTempDirectory("replay").toFile()
        File(dir, "a-cu.json").writeText(fixturaIntreg(recitita = true), Charsets.UTF_8)
        File(dir, "b-fara.json").writeText(fixturaIntreg(recitita = false), Charsets.UTF_8)
        val iesire = File(dir, "iesire.tsv")

        ReplayFixturi.ruleazaDirector(dir, iesire)

        assertEquals(
            listOf("a-cu.json\tsucces\t1\t-", "b-fara.json\teroare\t0\tPRET=FORMA_PRET"),
            iesire.readLines(Charsets.UTF_8),
        )
        // Acelasi rezultat ca parserul pe liniile construite cum le construieste lantul (RecitirePret.aplica: copy(recitita = true)).
        val caPeTelefon = ReplayFixturi.linii(File(dir, "b-fara.json")).map { if (it.text == "RON25") it.copy(recitita = true) else it }
        assertEquals(
            ReplayFixturi.ruleaza(uber, ReplayFixturi.linii(File(dir, "a-cu.json"))),
            ReplayFixturi.ruleaza(uber, caPeTelefon),
        )
        dir.deleteRecursively()
    }
}
