package app.trueride.core.parse

import java.io.File
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * F-033, F-034: calibrarea pragului `pretInaltimeMinimaRelativa` cu parserul real, prin API-ul public. Regula 3
 * e monotona in prag, deci „pragul maxim la care pretul inca se citeste” = inaltimea pretului / mediana (la 0,01),
 * masurat de aceeasi functie care decide pe telefon. Cu linia pretului scoasa, acelasi baleiaj da pragul maxim la
 * care ALTA suma ar lua locul pretului (riscul M5). Tipareste doar numaratori si rapoarte (invariantul 2).
 * Setul RO de calibrare (`.probe-shots/fixturi-raport/f033-ro/calibrare/`, ignorat de git) e optional: lipsa
 * lui da 0 fixture-uri, nu un test cazut.
 */
class CalibrarePragPretTest {

    /** 1,00 .. 3,20 din 0,01 in 0,01; BigDecimal, fara virgula mobila (CLAUDE.md §5). */
    private val praguri: List<BigDecimal> = (100..320).map { BigDecimal(it).movePointLeft(2) }

    private fun cuPrag(set: ParserRuleSet, prag: BigDecimal) =
        OfferParser(set.copy(reguli = set.reguli.copy(pretInaltimeMinimaRelativa = prag)))

    /** Pragul maxim din [praguri] la care [linii] mai dau pret; null = nici la 1,00 nu exista pret. */
    private fun pragMaxim(set: ParserRuleSet, linii: List<LinieOcr>): BigDecimal? =
        praguri.lastOrNull { cuPrag(set, it).parseaza(linii).price != null }

    /** Pragul maxim la care o alta suma ar deveni pret dupa ce linia pretului lipseste (M5); null = niciuna. */
    private fun pragMaximAltaSuma(set: ParserRuleSet, linii: List<LinieOcr>): BigDecimal? {
        val laMinim = cuPrag(set, praguri.first())
        val pret = laMinim.parseaza(linii).price ?: return null
        // Contractul din OfferParser.liniiFolosite: linia pretului vine prima. Daca se schimba, masura "alta" ar
        // scoate o linie de segment si ar da cifre false; de aceea verificam ca prima linie poarta cifrele pretului.
        val liniaPretului = laMinim.liniiFolosite(linii).first()
        val cifre = { x: String -> x.filter(Char::isDigit) }
        check(cifre(liniaPretului.text).contains(cifre(pret.stripTrailingZeros().toPlainString()))) {
            "prima linie din liniiFolosite nu e linia pretului (contractul OfferParser s-a schimbat)"
        }
        return pragMaxim(set, linii - liniaPretului)
    }

    private data class Masura(val nume: String, val pret: BigDecimal?, val alta: BigDecimal?)

    private fun masoara(fisiere: List<File>, set: ParserRuleSet): List<Masura> = fisiere.map { f ->
        val linii = CititorFixturi.linii(f)
        Masura(f.name, pragMaxim(set, linii), pragMaximAltaSuma(set, linii))
    }

    /**
     * Fixture-urile RO din rapoarte, in afara repo-ului (Task 2 din planul F-033). `fixtures.dir`/../ = radacina.
     * Setul lipseste pe un checkout curat: anuntam asta in output, ca un 0 sa nu treaca drept verificare.
     */
    private fun fisiereCalibrareRo(): List<File> {
        val dir = File(CititorFixturi.director("uber").parentFile.parentFile, ".probe-shots/fixturi-raport/f033-ro/calibrare")
        val fisiere = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }
        if (fisiere.isEmpty()) println("set RO de calibrare absent (${dir.path}): plafonul k pe RO neverificat")
        else println("set RO de calibrare: ${fisiere.size} fixture-uri (${dir.path})")
        return fisiere
    }

    private fun tipareste(titlu: String, masuri: List<Masura>, candidati: List<String>) {
        println("== $titlu: ${masuri.size} fixture-uri")
        masuri.forEach { println("${it.nume}  pret=${it.pret ?: "-"}  alta=${it.alta ?: "-"}") }
        val preturi = masuri.mapNotNull { it.pret }.sorted()
        val alte = masuri.mapNotNull { it.alta }.sorted()
        println("pret: n=${preturi.size} min=${preturi.firstOrNull()} max=${preturi.lastOrNull()} | alta: n=${alte.size} max=${alte.lastOrNull()}")
        candidati.map(::BigDecimal).forEach { p ->
            println("prag $p: preturi care trec=${preturi.count { it >= p }}/${preturi.size}  alte sume care ar trece=${alte.count { it >= p }}")
        }
    }

    // Test de masurare/documentare: nu verifica un comportament, ci tipareste tabelul (numaratori si rapoarte) din
    // care s-au ales pragurile din D-143. Singura asertiune garanteaza ca setul F-033 nu a disparut din repo.
    @Test
    fun `tabelul de calibrare EN si RO - doar numaratori si rapoarte`() {
        val en = listOf("1.4", "1.5", "1.6", "1.7", "1.8")
        val ro = listOf("1.8", "1.9", "2.0", "2.05", "2.1")
        tipareste("uber-en-prag", masoara(ComparatorFixturi.fixturi("uber-en-prag"), UBER_EN_V1), en)
        tipareste("uber-en-oferte", masoara(ComparatorFixturi.fixturi("uber-en-oferte"), UBER_EN_V1), en)
        tipareste("uber (RO)", masoara(ComparatorFixturi.fixturi("uber"), UBER_RO_V1), ro)
        tipareste("uber-ro", masoara(ComparatorFixturi.fixturi("uber-ro"), UBER_RO_V1), ro)
        tipareste("calibrare RO (rapoarte, in afara repo-ului)", masoara(fisiereCalibrareRo(), UBER_RO_V1), ro)
        assertTrue(ComparatorFixturi.fixturi("uber-en-prag").size >= 30, "setul F-033 lipseste")
    }

    // Controlul pozitiv al instrumentului: pe cele 9 oferte RO (D-031) pretul e la 2,28-2,67 x mediana
    // (ReguliUberRo.kt:40), deci baleiajul trebuie sa vada praguri >= 2,2 si nicio alta suma fara semn.
    @Test
    fun `pe fixture-urile RO din repo instrumentul vede pretul peste 2,2 si nicio alta suma`() {
        val masuri = masoara(ComparatorFixturi.fixturi("uber"), UBER_RO_V1)

        assertTrue(masuri.isNotEmpty(), "niciun fixture RO")
        masuri.forEach { m ->
            assertTrue(m.pret != null && m.pret >= BigDecimal("2.2"), "${m.nume}: pret=${m.pret}")
            assertTrue(m.alta == null || m.alta <= BigDecimal("1.3"), "${m.nume}: alta=${m.alta}")
        }
    }

    // Controlul pozitiv al masurii "alta" (#0268: un 0 fara control nu spune nimic): cadru sintetic cu pretul la
    // 115 px si o a doua suma fara semn la 90 px, mediana 45 px -> alta suma ia locul pretului pana la 2,00 exact.
    @Test
    fun `controlul pozitiv - o a doua suma fara semn de 2 ori mediana e vazuta de masura alta`() {
        fun linie(text: String, sus: Int, inaltime: Int) = LinieOcr(text, Cutie(100, sus, 700, sus + inaltime))
        val cadru = listOf(
            linie("9,47 RON", 0, 115), linie("5,00 RON", 130, 90),
            linie("4 min. (1.5 km) ▢", 240, 45), linie("14 min. (7.2 km) ▢", 300, 45),
            linie("▢", 360, 45), linie("▢", 420, 45), linie("▢", 480, 45),
        )

        assertEquals(BigDecimal("2.55"), pragMaxim(UBER_RO_V1, cadru))
        assertEquals(BigDecimal("2.00"), pragMaximAltaSuma(UBER_RO_V1, cadru))
    }

    // D-143 (EN): pragul sta la >= 10% sub cel mai mic pret real etichetat (1,74 masurat) si nicio alta suma nu
    // urca peste 90% din prag (masurat: nicio alta suma fara semn pe EN). Cadrele taiate (price null in
    // eticheta) nu intra in marja de jos: nu sunt preturi.
    @Test
    fun `pragul EN pastreaza marjele fata de preturile reale si fata de celelalte sume`() {
        val prag = UBER_EN_V1.reguli.pretInaltimeMinimaRelativa
        listOf("uber-en-prag", "uber-en-oferte").forEach { aplicatie ->
            ComparatorFixturi.fixturi(aplicatie).forEach { f ->
                val linii = CititorFixturi.linii(f)
                val areEticheta = CititorFixturi.eticheta(ComparatorFixturi.etichetaDe(f))["price"] != null
                val pret = pragMaxim(UBER_EN_V1, linii)
                val alta = pragMaximAltaSuma(UBER_EN_V1, linii)
                if (areEticheta) assertTrue(pret != null && pret >= prag.multiply(BigDecimal("1.10")), "${f.name}: pret=$pret")
                assertTrue(alta == null || alta <= prag.multiply(BigDecimal("0.90")), "${f.name}: alta=$alta")
            }
        }
    }

    // D-143 (RO, F-034): RO ramane 1,8. Pe fixture-urile RO din repo pretul sta la >= 2% peste prag (masurat:
    // cel mai mic 2,28); pe setul de calibrare (in afara repo-ului, optional; cel mai mic pret 2,13) nicio
    // „a doua suma” fara semn nu trece pragul: k = 0.
    @Test
    fun `pragul RO pastreaza marja fata de preturile din repo si plafonul k pe setul de calibrare`() {
        val prag = UBER_RO_V1.reguli.pretInaltimeMinimaRelativa
        val plafonK = 0   // D-143: numarul acceptat de „a doua sume” RO peste prag
        (ComparatorFixturi.fixturi("uber") + ComparatorFixturi.fixturi("uber-ro")).forEach { f ->
            val pret = pragMaxim(UBER_RO_V1, CititorFixturi.linii(f))
            assertTrue(pret != null && pret >= prag.multiply(BigDecimal("1.02")), "${f.name}: pret=$pret")
        }
        val pesteprag = masoara(fisiereCalibrareRo(), UBER_RO_V1).count { it.alta != null && it.alta >= prag }
        println("calibrare RO: a doua suma peste prag in $pesteprag fixture-uri (plafon $plafonK)")
        assertTrue(pesteprag <= plafonK, "a doua suma trece pragul RO in $pesteprag fixture-uri, plafon $plafonK")
    }
}
