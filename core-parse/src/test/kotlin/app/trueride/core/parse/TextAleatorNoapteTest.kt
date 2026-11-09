package app.trueride.core.parse

import app.trueride.core.model.ParsedOffer
import java.math.BigDecimal
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.fail
import org.junit.jupiter.api.Tag

/**
 * D-348.5: text aleator in parser. Liniile vin din alfabetul real al ofertelor (cifre, separatori, unitati, litere),
 * cu lungimi si ordini aleatoare; seed-ul vine din `-Ptrueride.noapte.seed`. Ruleaza doar prin `:core-parse:noapte`.
 *
 * Oracole (invariantele 4 si 5): (a) nicio exceptie; (b) orice numar din rezultat are cifrele ca subsir de cifre al
 * unei linii de intrare (nicio cifra inventata); (c) intrare fara nicio cifra -> niciun camp numeric; (d) acelasi seed,
 * aceeasi iesire; (e) fiecare dintre cele cinci campuri numerice iese cel putin o data citit (generatorul ajunge la
 * formele de care au nevoie regulile, nu doar la zgomot).
 * "Cifra" se judeca DUPA corectia literelor a setului de reguli (`corectiiCifre`: l/I/O/o -> cifre), citita din
 * obiectul de reguli, nu copiata: "l min (" citeste legitim minutul 1.
 * Mesajul de picare poarta DOAR seed, index si litera oracolului: textul generat nu ajunge in loguri (invariantul 2);
 * intrarea se regenereaza din seed + index.
 */
@Tag("noapte")
class TextAleatorNoapteTest {

    private val seed: Long = System.getProperty("trueride.noapte.seed")?.toLong() ?: 20261024L
    private val cazuri: Int = System.getProperty("trueride.noapte.cazuri")?.toInt() ?: 200

    // Toate seturile de reguli de azi: o regula noua e acoperita fara a atinge testul.
    private val seturi = listOf(UBER_RO_V1, UBER_EN_V1, BOLT_RO_V1)
    private val parsere = seturi.map { OfferParser(it) }

    private val unitati = listOf("RON", "lei", "Lei", "km", "Km", "kn", "mi", "min", "min.", "mins", "h", "x", "NET")
    private val simboluri = listOf("+", "-", "−", "•", "·", "(", ")", ":", ".", ",", "%", "▢", " ", " ")
    private val litere = "abcdeilmnoprstuvzRON"

    private fun Random.cifre(): String = buildString { repeat(1 + nextInt(5)) { append('0' + nextInt(10)) } }

    private fun Random.numar(): String {
        val zecimale = if (nextInt(3) == 0) "" else listOf(".", ",", ":").random(this) + cifre().take(1 + nextInt(2))
        return cifre() + zecimale
    }

    private fun Random.cuvant(): String = buildString { repeat(1 + nextInt(7)) { append(litere.random(this@cuvant)) } }

    /** 0-3 stricaciuni (sterge / schimba / insereaza un caracter): formele valide devin aproape-valide. */
    private fun Random.strica(t: String): String {
        val c = StringBuilder(t)
        repeat(nextInt(4)) {
            if (c.isEmpty()) return@repeat
            val poz = nextInt(c.length)
            val nou = (litere + "0123456789,.:() \u2022").random(this)
            when (nextInt(3)) {
                0 -> c.deleteCharAt(poz)
                1 -> c.setCharAt(poz, nou)
                else -> c.insert(poz, nou)
            }
        }
        return c.toString()
    }

    private fun Random.jeton(): String = when (nextInt(5)) {
        0, 1 -> numar()
        2 -> unitati.random(this)
        3 -> simboluri.random(this)
        else -> cuvant()
    }

    private fun Random.text(): String {
        val separator = if (nextBoolean()) " " else ""
        return List(1 + nextInt(8)) { jeton() }.joinToString(separator)
    }

    private fun linie(text: String, sus: Int, inaltime: Int, recitita: Boolean = false) =
        LinieOcr(text, Cutie(100, sus, 700, sus + inaltime), recitita)

    /**
     * Ecran cu forma ofertei: 3 linii de corp (etalonul de inaltime), doua segmente `N min (N.d km)` sau `N min • N.d km`
     * si o linie de pret mai inalta (>= 1,6x etalonul). Stricaciunile vin apoi, pe ~2/3 din linii.
     */
    private fun Random.ecranStructurat(): List<LinieOcr> {
        val corp = List(3) { linie("▢ ▢ ▢", 1400 + it * 100, 45) }
        val segmente = List(2) {
            val unit = listOf("min", "min.", "mins").random(this)
            val km = cifre().take(2) + listOf(".", ",", ":").random(this) + cifre().take(1)
            val text = if (nextInt(3) == 0) "${cifre().take(2)} $unit • $km km" else "${cifre().take(2)} $unit ($km km)"
            linie(text, 1700 + it * 100, 45)
        }
        val pret = if (nextInt(4) == 0) "RON${cifre().take(2)}.${cifre().take(2).padEnd(2, '0')}"
        else "${cifre().take(2)},${cifre().take(2).padEnd(2, '0')} RON"
        val toate = corp + segmente + linie(pret, 1232, 115)
        return toate.map { l -> if (nextInt(3) == 0) l else l.copy(text = strica(l.text)) }
    }

    /** Intrarea cu numarul `i`: ~40% ecran structurat, restul amestec liber; ~25% fara nicio cifra (oracolul c). */
    private fun liniiLa(i: Int): List<LinieOcr> {
        val r = Random(seed * 1_000_003L + i)
        val structurat = r.nextInt(100) < 40
        val brut = if (structurat) r.ecranStructurat() else r.amestec()
        return if (r.nextInt(4) == 0) brut.map { it.copy(text = it.text.filterNot(Char::isDigit)) } else brut
    }

    private fun Random.amestec(): List<LinieOcr> {
        val r = this
        var sus = r.nextInt(200)
        return List(r.nextInt(25)) {
            val text = r.text()
            val inaltime = 20 + r.nextInt(120)
            val linie = LinieOcr(text, Cutie(100, sus, 100 + r.nextInt(900), sus + inaltime), recitita = r.nextInt(6) == 0)
            sus += inaltime + r.nextInt(60)
            linie
        }
    }

    private fun numerice(o: ParsedOffer): List<BigDecimal> =
        listOfNotNull(o.price, o.pickupMinutes, o.tripMinutes, o.pickupDistance, o.tripDistance, o.surgeMultiplier)

    /** Cifrele unei linii dupa corectia setului (l/I -> 1, O/o -> 0): ce poate citi parserul acelui set ca numar. */
    private fun cifreCorectate(set: ParserRuleSet, text: String): String =
        text.map { set.reguli.corectiiCifre[it.toString()] ?: it.toString() }.joinToString("").filter(Char::isDigit)

    private fun cifreleNumarului(n: BigDecimal): String =
        (if (n.signum() == 0) "0" else n.stripTrailingZeros().toPlainString()).filter(Char::isDigit)

    /** Rezultatele tuturor parserelor pe intrarea `i`; o exceptie devine pica(a) fara sa copieze mesajul ei. */
    private fun parseaza(i: Int): List<ParsedOffer> {
        val linii = liniiLa(i)
        return parsere.map { p ->
            try {
                p.parseaza(linii)
            } catch (e: Exception) {
                fail("seed $seed index $i: oracol a (${e.javaClass.simpleName})")
            }
        }
    }

    @Test
    fun `nicio exceptie pe text aleator (a)`() {
        for (i in 0 until cazuri) parseaza(i)
    }

    @Test
    fun `nicio cifra inventata in rezultat (b)`() {
        for (i in 0 until cazuri) {
            val linii = liniiLa(i)
            parseaza(i).forEachIndexed { k, rezultat ->
                val din = linii.map { cifreCorectate(seturi[k], it.text) }
                for (n in numerice(rezultat)) {
                    if (din.none { cifreleNumarului(n) in it }) fail("seed $seed index $i: oracol b")
                }
            }
        }
    }

    @Test
    fun `intrare fara cifre nu da niciun camp numeric (c)`() {
        for (i in 0 until cazuri) {
            val linii = liniiLa(i)
            parseaza(i).forEachIndexed { k, rezultat ->
                // Fara cifre DUPA corectia setului: "l min (" are o cifra pentru RO, dar nu pentru Bolt.
                if (linii.none { cifreCorectate(seturi[k], it.text).isNotEmpty() } && numerice(rezultat).isNotEmpty()) {
                    fail("seed $seed index $i: oracol c")
                }
            }
        }
    }

    @Test
    fun `acelasi seed da aceeasi iesire`() {
        fun amprenta() = (0 until cazuri).map { i -> liniiLa(i).map { it.text } to parseaza(i) }
        // Fara assertEquals: la picare ar copia in XML-ul JUnit textul generat (invariantul 2).
        if (amprenta() != amprenta()) fail("seed $seed: oracol d")
    }

    @Test
    fun `fiecare camp numeric iese citit cel putin o data (e)`() {
        val nume = listOf("price", "pickupMinutes", "tripMinutes", "pickupDistance", "tripDistance")
        val numaratori = IntArray(5)
        for (i in 0 until cazuri) {
            for (o in parseaza(i)) {
                listOf(o.price, o.pickupMinutes, o.tripMinutes, o.pickupDistance, o.tripDistance)
                    .forEachIndexed { k, v -> if (v != null) numaratori[k]++ }
            }
        }
        val rezumat = nume.indices.joinToString(" ") { "${nume[it]}=${numaratori[it]}" }
        println("campuri citite (seed $seed, $cazuri cazuri, x3 seturi): $rezumat")
        if (numaratori.any { it == 0 }) fail("seed $seed: oracol e, numaratori $rezumat")
    }
}
