package app.trueride.core.parse

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T8 (apk 25): `:core-parse:replay` cadea pe o linie lunga. Cititorul de fixture-uri (`ReplayFixturi.obiectLinie`) cauta textul
 * cu `(?:[^"\\]|\\.)*`, o bucla de grup pe care regexul din Java o ruleaza recursiv, cate un nivel de stiva per caracter
 * (`Pattern$GroupTail.match`): destul de multe caractere si iese StackOverflowError. Unealta de replay, nu parserul de pe telefon.
 * Liniile sunt SINTETICE (litere si cifre inventate), niciun text de oferta (invariantul 2).
 */
class ReplayLiniiLungiTest {

    private fun fixtura(text: String): File {
        val f = File(createTempDirectory("replay-linii-lungi").toFile(), "uber-sintetic.json")
        f.writeText("""{ "aplicatie": "com.ubercab.driver", "linii": [ { "text": "$text", "stanga": 1, "sus": 2, "dreapta": 3, "jos": 4 } ] }""")
        return f
    }

    @Test
    fun `o linie de 200000 de caractere se citeste intreaga`() {
        val linii = ReplayFixturi.linii(fixtura("a1 ".repeat(66_667)))
        assertEquals(1, linii.size)
        assertEquals(200_001, linii.single().text.length)
    }

    @Test
    fun `o linie cu zeci de mii de secvente escape se citeste intreaga`() {
        val linii = ReplayFixturi.linii(fixtura("""é\"""".repeat(30_000)))
        assertEquals(60_000, linii.single().text.length)
    }

    @Test
    fun `textul cu ghilimele escapate nu rupe campurile de dupa el`() {
        val linii = ReplayFixturi.linii(fixtura("""ab\"cd"""))
        assertEquals("ab\"cd", linii.single().text)
        assertEquals(Cutie(1, 2, 3, 4), linii.single().cutie)
    }

    // Garda pe parserul care RULEAZA pe telefon: regulile (`sumaPattern`, `minutePattern` …) n-au bucle de grup, deci o linie
    // lunga costa timp liniar, nu stiva. Daca o regula noua introduce una, testul pica aici, nu pe un telefon.
    @Test
    fun `parserul de pe telefon trece de o linie de 200000 de caractere pe toate cele trei seturi de reguli`() {
        val lunga = LinieOcr("12 ".repeat(33_000) + "min (3 km) RON 1 lei • 4 km ".repeat(3_000), Cutie(0, 0, 100, 40))
        listOf("com.ubercab.driver", "ee.mtakso.driver").forEach { app ->
            ReplayFixturi.analizeaza(ReplayFixturi.parserPentru(app), listOf(lunga))
        }
    }
}
