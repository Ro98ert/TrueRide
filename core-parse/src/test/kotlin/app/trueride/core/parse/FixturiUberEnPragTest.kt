package app.trueride.core.parse

import app.trueride.core.model.OfferField
import java.io.File
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * F-033: ofertele Uber EN reale de pe telefonul de test 2 (2026-10-06, en-RO), etichete verificate de un agent Fable (D-141).
 * 14 au iesit SUB_PRAG cu pragul 1,8 mostenit din RO. Set mic: M4 cere 100%.
 */
class FixturiUberEnPragTest {

    private val fixturi: List<File> = ComparatorFixturi.fixturi("uber-en-prag")
    private val uberRo: List<File> = ComparatorFixturi.fixturi("uber") + ComparatorFixturi.fixturi("uber-ro")
    private val en = OfferParser(UBER_EN_V1)
    private val ro = OfferParser(UBER_RO_V1)
    private val selectie = ParserPeLimbi(listOf(ro, en))

    /** Cadrele taiate / suprapuse din raport (suma cea mai inalta la 0,54-0,70 x mediana): niciodata pret. */
    private val taiate = fixturi.filter { it.name.endsWith("-n9356.json") || it.name.endsWith("-n9412.json") }

    private fun cuPrag(set: ParserRuleSet, prag: String) =
        OfferParser(set.copy(reguli = set.reguli.copy(pretInaltimeMinimaRelativa = BigDecimal(prag))))

    @Test
    fun `setul are cel putin 30 de oferte si fiecare are eticheta`() {
        assertTrue(fixturi.size >= 30, "gasite ${fixturi.size} in ${CititorFixturi.director("uber-en-prag")}")
        fixturi.forEach { assertTrue(ComparatorFixturi.etichetaDe(it).exists(), "lipseste eticheta ${it.name}") }
    }

    /**
     * D-141.5: pretul se compara pe toate cele 32; celelalte campuri doar unde eticheta are o valoare.
     * In 6 oferte masca ascunde o litera sau punctul pierdut (eticheta `null`), iar parserul citeste
     * valoarea prin corectiile D-122..D-124 - acelea sunt F-035, nu pragul.
     */
    private fun gresitePeEticheta(parser: ParserOferta): List<String> = fixturi.mapNotNull { f ->
        val e = CititorFixturi.eticheta(ComparatorFixturi.etichetaDe(f))
        val d = ComparatorFixturi.diferente(parser.parseaza(CititorFixturi.linii(f)), e)
            .filter { camp -> camp == "price" || e[camp] != null }
        if (d.isEmpty()) null else "${f.name}: $d"
    }

    @Test
    fun `M4 Uber EN prag - toate ofertele corecte cu setul EN`() {
        val gresite = gresitePeEticheta(en)
        println("M4 Uber EN prag: ${fixturi.size - gresite.size} / ${fixturi.size}")

        assertEquals(emptyList(), gresite)
    }

    // Review Focus 1: un prag coborat nu are voie sa transforme un cadru taiat intr-un card (inv. 5, 6).
    @Test
    fun `cadrele cu oferta taiata nu dau pret la niciun prag candidat`() {
        assertEquals(2, taiate.size, "cadrele n9356 si n9412 lipsesc din set")
        listOf("1.4", "1.5", "1.6", "1.7", "1.8").forEach { prag ->
            taiate.forEach { f ->
                val linii = CititorFixturi.linii(f)
                assertNull(cuPrag(UBER_EN_V1, prag).parseaza(linii).price, "${f.name} la pragul $prag")
                assertEquals(MotivLipsa.SUB_PRAG, cuPrag(UBER_EN_V1, prag).motiveLipsa(linii)[OfferField.PRICE], f.name)
            }
        }
    }

    // Review Focus 4: selectia pe limbi da pe EN exact rezultatul setului EN si nu schimba nimic pe RO.
    @Test
    fun `selectia pe limbi nu schimba rezultatele RO si da pe EN rezultatul setului EN`() {
        assertEquals(ComparatorFixturi.gresite(fixturi, en), ComparatorFixturi.gresite(fixturi, selectie))
        assertEquals(ComparatorFixturi.gresite(uberRo, ro), ComparatorFixturi.gresite(uberRo, selectie))
    }

    // Review Focus 4: oricat ar cobori pragul unui set, el nu citeste ecranele celuilalt (sumaPattern-urile difera:
    // „RON” inainte la EN, „ RON” dupa la RO), deci castigatorul din ParserPeLimbi nu se schimba.
    @Test
    fun `schimbarea pragului unui set nu schimba setul castigator pe ecranele celuilalt`() {
        val roLaMinim = cuPrag(UBER_RO_V1, "1.0")
        val enLaMinim = cuPrag(UBER_EN_V1, "1.0")

        fixturi.forEach { f -> assertNull(roLaMinim.parseaza(CititorFixturi.linii(f)).price, "RO a citit EN: ${f.name}") }
        uberRo.forEach { f -> assertNull(enLaMinim.parseaza(CititorFixturi.linii(f)).price, "EN a citit RO: ${f.name}") }
        assertEquals(ComparatorFixturi.gresite(fixturi, en), ComparatorFixturi.gresite(fixturi, ParserPeLimbi(listOf(roLaMinim, en))))
        assertEquals(ComparatorFixturi.gresite(uberRo, ro), ComparatorFixturi.gresite(uberRo, ParserPeLimbi(listOf(ro, enLaMinim))))
    }

    // D-109: motivele raportului nu pot diverge de rezultat (ca in MotiveLipsaTest, dar pe setul EN).
    @Test
    fun `motivele sunt coerente cu parseaza pe toate fixture-urile EN prag`() {
        fixturi.forEach { f ->
            val linii = CititorFixturi.linii(f)
            val nule = OfferField.values().toSet() - en.parseaza(linii).fieldConfidence.keys

            assertEquals(nule, en.motiveLipsa(linii).keys, f.name)
        }
    }
}
