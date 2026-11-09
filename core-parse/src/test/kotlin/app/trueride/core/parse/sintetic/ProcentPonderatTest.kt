package app.trueride.core.parse.sintetic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** D-315.2: procentul de eroare ponderat dupa frecventa reala; randuri si frecvente INVENTATE. */
class ProcentPonderatTest {

    private val tsv = """
        platforma	limba	perturbare	numar
        uber	ro	curat	60
        uber	ro	pret_un_ban	30
        uber	ro	total	100
        uber	en	curat	10
        uber	en	total	10
        bolt	ro	curat	5
        bolt	ro	total	5
    """.trimIndent() + "\n"

    private fun celula(set: String, familie: Familie, id: String, corect: Int = 0, nec: Int = 0, pierdut: Int = 0, gresit: Int = 0, cunoscut: Int = 0) =
        Celula(set, familie, id, corect, nec, pierdut, gresit, cunoscut)

    @Test
    fun `frecventele se citesc din TSV, iar baza se potriveste cu curat`() {
        val f = Frecvente.dinText(tsv)

        assertEquals(60.0, f.pondere(Familie.UBER_RO, "baza", inventata = false))
        assertEquals(30.0, f.pondere(Familie.UBER_RO, "pret_un_ban", inventata = false))
        assertEquals(0.0, f.pondere(Familie.UBER_RO, "km_cu_virgula", inventata = false))
        assertEquals(10.0, f.pondere(Familie.UBER_EN, "baza", inventata = false))
    }

    @Test
    fun `o perturbare inventata cantareste 1 la suta din randurile reale ale platformei si limbii ei`() {
        val f = Frecvente.dinText(tsv)

        assertEquals(1.0, f.pondere(Familie.UBER_RO, "confuzie_ocr_pret", inventata = true))
        assertEquals(0.1, f.pondere(Familie.UBER_EN, "confuzie_ocr_pret", inventata = true), 1e-9)
        assertEquals(0.05, f.pondere(Familie.BOLT_RO, "confuzie_ocr_pret", inventata = true), 1e-9)
    }

    @Test
    fun `fara fisier ponderile sunt egale`() {
        val f = Frecvente.dinFisier(File("/nu/exista/frecvente.tsv"))

        assertNull(f)
        assertEquals(1.0, Frecvente.pondere(null, Familie.UBER_RO, "pret_un_ban", inventata = false))
        assertEquals(1.0, Frecvente.pondere(null, Familie.BOLT_RO, "confuzie_ocr_pret", inventata = true))
    }

    @Test
    fun `procentul e media claselor ponderata cu frecventa, doar pe grupul cerut`() {
        val celule = listOf(
            celula("S", Familie.UBER_RO, "baza", corect = 20),
            celula("S", Familie.UBER_RO, "pret_un_ban", cunoscut = 20),
            celula("S", Familie.UBER_RO, "confuzie_ocr_pret", pierdut = 20),
        )

        val randuri = ProcentPonderat.calculeaza(celule, Frecvente.dinText(tsv), inventate = setOf("confuzie_ocr_pret"))

        val real = randuri.single { it.set == "S" && it.grup == "real" }
        assertEquals(66.67, real.corect, 0.01)
        assertEquals(33.33, real.gresitCunoscut, 0.01)
        assertEquals(0.0, real.gresit)
        val inventat = randuri.single { it.set == "S" && it.grup == "inventat" }
        assertEquals(100.0, inventat.necPierdut, 0.001)
        assertEquals("ponderat", real.mod)
    }

    @Test
    fun `fara frecvente fiecare perturbare cantareste la fel si modul e egal`() {
        val celule = listOf(
            celula("S", Familie.UBER_RO, "baza", corect = 20),
            celula("S", Familie.UBER_RO, "pret_un_ban", nec = 20),
        )

        val real = ProcentPonderat.calculeaza(celule, null, inventate = emptySet()).single()

        assertEquals(50.0, real.corect, 0.001)
        assertEquals(50.0, real.necCorect, 0.001)
        assertEquals("egal", real.mod)
    }

    @Test
    fun `liniile TSV au antet, procente cu doua zecimale si totalul de 100`() {
        val celule = listOf(celula("S", Familie.UBER_RO, "baza", corect = 1, nec = 2))

        val text = ProcentPonderat.tsv(ProcentPonderat.calculeaza(celule, null, emptySet()))

        val linii = text.trim().lines()
        assertEquals("set\tgrup\tmod\tperturbari\tGRESIT\tNEC_PIERDUT\tNEC_CORECT\tCORECT\tGRESIT_CUNOSCUT", linii[0])
        assertEquals(listOf("S", "real", "egal", "1", "0.00", "0.00", "66.67", "33.33", "0.00"), linii[1].split("\t"))
        assertTrue(linii[1].split("\t").drop(4).sumOf { it.toDouble() } in 99.99..100.01)
    }
}
