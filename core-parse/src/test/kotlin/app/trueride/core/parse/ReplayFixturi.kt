package app.trueride.core.parse

import app.trueride.core.model.OfferField
import app.trueride.core.model.ParsedOffer
import java.io.File
import java.math.BigDecimal
import java.security.MessageDigest

/**
 * D-288.6: reciteste cu parserul REAL (regulile din cod) fixture-urile trase din Sentry Logs de
 * un script privat. Se porneste prin `./gradlew :core-parse:replay` (task-ul din build.gradle.kts);
 * scrie un rand TSV per fixture, iar agregarea si comparatia cu telefonul se fac in Python.
 *
 * Alegerea parserului si starea cardului urmeaza `ModulLant.automat()` si `AutomatCard.clasifica`; motivele
 * au numele campurilor din jurnal (`ConversiiJurnal.camp`). Daca acolo se schimba ceva, se schimba si aici.
 * Sta in sursele de test: nu intra in APK si nu cere nicio dependenta noua.
 */
object ReplayFixturi {

    /** [stare]: succes (pret citit), eroare (segmente fara pret = NECITIBILA) sau gol; [completa]: toate cele 5 campuri. */
    data class Rezultat(val stare: String, val completa: Boolean, val motive: Map<String, String>)

    private const val PACHET_BOLT = "ee.mtakso.driver"

    /** Bolt are regulile lui; Uber si orice altceva (ca NECUNOSCUTA pe telefon) citesc cu RO apoi EN. */
    fun parserPentru(aplicatie: String): ParserOferta =
        if (aplicatie == PACHET_BOLT) {
            OfferParser(BOLT_RO_V1)
        } else {
            ParserPeLimbi(listOf(OfferParser(UBER_RO_V1), OfferParser(UBER_EN_V1)))
        }

    fun ruleaza(parser: ParserOferta, linii: List<LinieOcr>): Rezultat = analizeaza(parser, linii).first

    /** Ca [ruleaza], plus amprenta valorilor; o singura parsare, ca TSV-ul si amprenta sa vina din acelasi rezultat. */
    fun analizeaza(parser: ParserOferta, linii: List<LinieOcr>): Pair<Rezultat, String> {
        val o = parser.parseaza(linii)
        val areSegment = o.pickupMinutes != null || o.tripMinutes != null ||
            o.pickupDistance != null || o.tripDistance != null
        val stare = when {
            o.price != null -> "succes"
            areSegment -> "eroare"
            else -> "gol"
        }
        val completa = o.price != null && o.pickupMinutes != null && o.tripMinutes != null &&
            o.pickupDistance != null && o.tripDistance != null
        val motive = parser.motiveLipsa(linii).entries.associate { (camp, motiv) -> numeCamp(camp) to motiv.name }
        return Rezultat(stare, completa, motive) to amprenta(o, stare)
    }

    /**
     * Primele 16 hex din SHA-256 peste cele 5 valori (pret, km/min preluare, km/min cursa), ca replay-uri
     * diferite sa poata fi comparate fara ca valorile sa ajunga in clar. `stripTrailingZeros` face 12.50 = 12.5;
     * un camp lipsa e `?`, nu 0 (invariantul 6). Eroare/gol nu au pret, deci amprenta vine din stare.
     */
    fun amprenta(o: ParsedOffer, stare: String): String {
        if (stare != "succes") return hash(stare)
        val valori = listOf(o.price, o.pickupDistance, o.pickupMinutes, o.tripDistance, o.tripMinutes)
        return hash(valori.joinToString("|") { canonic(it) })
    }

    private fun canonic(v: BigDecimal?): String =
        if (v == null) "?" else if (v.signum() == 0) "0" else v.stripTrailingZeros().toPlainString()

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(16)

    private fun numeCamp(f: OfferField): String = when (f) {
        OfferField.PRICE -> "PRET"
        OfferField.PICKUP_MINUTES -> "MIN_PRELUARE"
        OfferField.PICKUP_DISTANCE -> "KM_PRELUARE"
        OfferField.TRIP_MINUTES -> "MIN_CURSA"
        OfferField.TRIP_DISTANCE -> "KM_CURSA"
    }

    /** `fisier <TAB> stare <TAB> 0|1 <TAB> CAMP=MOTIV,... (sortat) sau -`. */
    fun linieTsv(nume: String, r: Rezultat): String {
        val motive = r.motive.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" }.ifEmpty { "-" }
        return "$nume\t${r.stare}\t${if (r.completa) 1 else 0}\t$motive"
    }

    /**
     * D-313: ca `CititorFixturi.linii`, plus campul optional `"recitita": true` al liniei (pus de scriptul privat dupa
     * ` recitite=` din jurnal). Il da parserului ca `LinieOcr.recitita`, exact cum il pune lantul de pe telefon
     * (`RecitirePret.aplica`: `copy(recitita = true)`). Fixture-ul vechi, fara camp, da `recitita = false`. Cititorul comun nu
     * stie campul si ar sari peste linia marcata, deci replay-ul are cititorul lui.
     */
    fun linii(fisier: File): List<LinieOcr> {
        val continut = fisier.readText(Charsets.UTF_8)
        val rezultat = mutableListOf<LinieOcr>()
        var i = continut.indexOf(INCEPUT_LINIE)
        while (i >= 0) {
            val start = i + INCEPUT_LINIE.length
            val sfarsit = sfarsitText(continut, start)
            val m = if (sfarsit < 0) null else coadaLinie.matchAt(continut, sfarsit)
            if (m != null) {
                val (stanga, sus, dreapta, jos, recitita) = m.destructured
                rezultat += LinieOcr(dezescapeaza(continut.substring(start, sfarsit)), Cutie(stanga.toInt(), sus.toInt(), dreapta.toInt(), jos.toInt()), recitita.isNotEmpty())
            }
            i = continut.indexOf(INCEPUT_LINIE, start)
        }
        return rezultat
    }

    private const val INCEPUT_LINIE = "{ \"text\": \""

    // Textul NU se cauta cu regex: `(?:[^"\\]|\\.)*` ruleaza recursiv in Java, un nivel de stiva per caracter
    // (`Pattern$GroupTail.match`), si o linie lunga da StackOverflowError (T8, apk 25). Regexul ramas n-are bucle de grup.
    private val coadaLinie = Regex(
        """", "stanga": (-?\d+), "sus": (-?\d+), "dreapta": (-?\d+), "jos": (-?\d+)(, "recitita": true)? \}""",
    )

    /** Indexul ghilimelei care inchide textul incepand de la [start] (`\x` e o pereche, ghilimeaua escapata nu inchide), sau -1. */
    private fun sfarsitText(s: String, start: Int): Int {
        var i = start
        while (i < s.length) {
            when (s[i]) {
                '\\' -> i++
                '"' -> return i
            }
            i++
        }
        return -1
    }

    private val escape = Regex("""\\u([0-9a-fA-F]{4})|\\(["\\/])""")

    private fun dezescapeaza(s: String): String =
        escape.replace(s) { m -> m.groups[1]?.let { it.value.toInt(16).toChar().toString() } ?: m.groupValues[2] }

    private val aplicatie = Regex(""""aplicatie":\s*"([^"]*)"""")

    fun aplicatieDin(fisier: File): String =
        aplicatie.find(fisier.readText(Charsets.UTF_8))?.groupValues?.get(1) ?: "necunoscuta"

    /** Toate `*.json` din [director] (fara `.asteptat.json`), in ordinea numelor; intoarce cate fixture-uri a citit. */
    fun ruleazaDirector(director: File, iesire: File, valori: File? = null): Int {
        val fisiere = director.listFiles { f -> f.name.endsWith(".json") && !f.name.endsWith(".asteptat.json") }
            .orEmpty().sortedBy { it.name }
        val analize = fisiere.map { f -> f.name to analizeaza(parserPentru(aplicatieDin(f)), linii(f)) }
        iesire.writeText(analize.joinToString("") { (nume, a) -> linieTsv(nume, a.first) + "\n" }, Charsets.UTF_8)
        valori?.writeText(analize.joinToString("") { (nume, a) -> "$nume\t${a.second}\n" }, Charsets.UTF_8)
        return fisiere.size
    }
}

fun main(args: Array<String>) {
    require(args.size in 2..3) { "utilizare: ReplayFixturi <director fixture-uri> <fisier iesire .tsv> [fisier valori]" }
    val n = ReplayFixturi.ruleazaDirector(File(args[0]), File(args[1]), args.getOrNull(2)?.let(::File))
    println("replay: $n fixture-uri")
}
