package app.trueride.core.parse.sintetic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * D-315.3: erori plauzibile pe care soferii nu le-au trimis (inca), marcate `inventata = true`. Testele verifica
 * structura fiecarei familii noi (ce se strica, ce se asteapta): asteptarea e scrisa de mana in generator, iar
 * aici se verifica ca generatorul chiar produce forma descrisa. Ce da parserul pe ele masoara `CorpusSinteticTest`.
 */
class PerturbariInventateTest {

    private val noi = listOf(
        "confuzie_ocr_pret", "confuzie_ocr_minute", "confuzie_ocr_km",
        "cifra_dublata_pret", "cifra_dublata_minute", "cifra_dublata_km",
        "cifra_pierduta_pret", "cifra_pierduta_minute", "cifra_pierduta_km",
        "rand_dublat_departe_pret", "rand_dublat_departe_segment", "randuri_interschimbate",
        "taiat_stanga_pret", "taiat_stanga_minute", "taiat_stanga_in_cuvant_minute", "taiat_dreapta_in_cuvant_minute",
        "unitate_minute_mi", "unitate_minute_m", "unitate_km_m",
        "zecimala_mutata_pret", "zecimala_mutata_km", "doua_oferte_partial_suprapuse",
        "ancora_lipsa_ambele", "ancora_in_plus_intre_segmente", "ancora_in_plus_inainte",
        "randuri_straine_intre_segmente", "card_partial_un_segment",
    )

    private fun perturbare(id: String) = PerturbariSintetice.TOATE.first { it.id == id }

    private fun variante(id: String): List<Varianta> {
        val p = perturbare(id)
        return p.familii.flatMap { f -> (0 until GeneratorSintetic.PE_PERTURBARE).map { GeneratorSintetic.genereaza(f, p, it) } }
    }

    private fun Varianta.necunoscute() = Camp.entries.filter { asteptat.getValue(it) == Asteptare.Necunoscut }
    private fun Varianta.exacte() = Camp.entries.filter { asteptat.getValue(it) is Asteptare.Exact }
    private fun Varianta.oricare() = Camp.entries.filter { asteptat.getValue(it) is Asteptare.Oricare }
    private val cuvinteFixe = Regex("RON|lei|mins?|Oră")

    /** Litere din D-288.2 ramase dupa ce se scot monedele si unitatile (in care „i” apare firesc). */
    private fun Varianta.jetoaneConfuzie() = linii.sumOf { l -> cuvinteFixe.replace(l.text, "").count { it in "SBZGqi" } }

    @Test
    fun `fiecare perturbare noua exista, e marcata inventata si are cel putin 20 de variante`() {
        noi.forEach { id ->
            val p = perturbare(id)
            assertTrue(p.inventata, "$id trebuie sa fie inventata")
            assertTrue(variante(id).size >= GeneratorSintetic.PE_PERTURBARE, "$id are putine variante")
        }
    }

    @Test
    fun `identificatorii perturbarilor sunt unici`() {
        val ids = PerturbariSintetice.TOATE.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `perturbarile cu corespondent real in Sentry nu sunt marcate inventate, restul da`() {
        listOf("rand_interval_minute", "km_cu_virgula", "nbsp_pret", "pret_cifra_in_coada", "litera_mapata_pret", "pret_fara_separator",
            "pret_intreg_recitit", "bolt_nbsp_eliminat", "ordine_amestecata", "baza").forEach {
            assertTrue(!perturbare(it).inventata, "$it are corespondent real")
        }
        listOf("unitate_Km", "pret_un_ban", "bolt_bulina_liniuta", "pret_spart_doua_casete", "litera_nemapata_pret_prima_cifra").forEach {
            assertTrue(perturbare(it).inventata, "$it nu a fost vazuta la soferi")
        }
    }

    @Test
    fun `confuziile OCR pun o litera din D-288 2 in locul unei cifre si cer necunoscut doar pe campul stricat`() {
        listOf("confuzie_ocr_pret" to 1, "confuzie_ocr_minute" to 1, "confuzie_ocr_km" to 1).forEach { (id, n) ->
            variante(id).forEach { v ->
                assertEquals(n, v.necunoscute().size, v.nume)
                assertEquals(4, v.exacte().size, v.nume)
                assertEquals(n, v.jetoaneConfuzie(), "${v.nume}: o singura litera de confuzie")
            }
        }
        variante("confuzie_ocr_pret").forEach { assertEquals(listOf(Camp.PRET), it.necunoscute(), it.nume) }
        variante("confuzie_ocr_minute").forEach { assertTrue(it.necunoscute().single() in listOf(Camp.MIN_PRELUARE, Camp.MIN_CURSA), it.nume) }
        variante("confuzie_ocr_km").forEach { assertTrue(it.necunoscute().single() in listOf(Camp.KM_PRELUARE, Camp.KM_CURSA), it.nume) }
    }

    @Test
    fun `cifra dublata, pierduta, taiata la stanga si zecimala mutata cer necunoscut pe un singur camp`() {
        val campuri = mapOf(
            "cifra_dublata_pret" to listOf(Camp.PRET), "cifra_pierduta_pret" to listOf(Camp.PRET),
            "taiat_stanga_pret" to listOf(Camp.PRET), "zecimala_mutata_pret" to listOf(Camp.PRET),
            "cifra_dublata_minute" to listOf(Camp.MIN_PRELUARE, Camp.MIN_CURSA),
            "cifra_pierduta_minute" to listOf(Camp.MIN_PRELUARE, Camp.MIN_CURSA),
            "taiat_stanga_minute" to listOf(Camp.MIN_PRELUARE, Camp.MIN_CURSA),
            "cifra_dublata_km" to listOf(Camp.KM_PRELUARE, Camp.KM_CURSA), "cifra_pierduta_km" to listOf(Camp.KM_PRELUARE, Camp.KM_CURSA),
            "zecimala_mutata_km" to listOf(Camp.KM_PRELUARE, Camp.KM_CURSA), "unitate_km_m" to listOf(Camp.KM_PRELUARE, Camp.KM_CURSA),
        )
        campuri.forEach { (id, permise) ->
            variante(id).forEach { v ->
                assertEquals(1, v.necunoscute().size, v.nume)
                assertTrue(v.necunoscute().single() in permise, "${v.nume}: ${v.necunoscute()}")
                assertEquals(4, v.exacte().size, v.nume)
            }
        }
    }

    @Test
    fun `randul dublat departe apare de doua ori, la mai mult de 100 px`() {
        variante("rand_dublat_departe_pret").forEach { v ->
            val pret = v.linii.filter { l -> l.text.contains("RON") || l.text.contains("lei") }.groupBy { it.text }.values.single { it.size == 2 }

            assertTrue(kotlin.math.abs(pret[0].cutie.sus - pret[1].cutie.sus) > 100, v.nume)
            assertEquals(Asteptare.Exact::class, v.asteptat.getValue(Camp.PRET)::class, v.nume)
        }
        variante("rand_dublat_departe_segment").forEach { v ->
            assertEquals(4, v.oricare().size, v.nume)
            assertTrue(v.linii.groupBy { it.text }.values.any { it.size == 2 && kotlin.math.abs(it[0].cutie.sus - it[1].cutie.sus) > 100 }, v.nume)
        }
    }

    @Test
    fun `randurile interschimbate sunt aceleasi linii cu doua locuri schimbate fata de ordinea de pe ecran`() {
        variante("randuri_interschimbate").forEach { v ->
            val dupaPozitie = v.linii.sortedBy { it.cutie.sus }

            assertEquals(2, v.linii.indices.count { v.linii[it] != dupaPozitie[it] }, v.nume)
            assertEquals(5, v.exacte().size, v.nume)
        }
    }

    @Test
    fun `ancora stricata sau in plus muta segmentele in exact sau necunoscut, iar pretul ramane exact`() {
        listOf("taiat_stanga_in_cuvant_minute", "taiat_dreapta_in_cuvant_minute", "ancora_in_plus_intre_segmente",
            "ancora_in_plus_inainte").forEach { id ->
            variante(id).forEach { v ->
                assertEquals(4, v.oricare().size, "${v.nume}: segmentele")
                assertEquals(Asteptare.Exact::class, v.asteptat.getValue(Camp.PRET)::class, "${v.nume}: pretul")
            }
        }
    }

    @Test
    fun `unitatea minutelor confundata - o ancora ramasa da segmente exact sau necunoscut, zero ancore dau totul necunoscut`() {
        listOf("unitate_minute_mi", "unitate_minute_m").forEach { id ->
            val v = variante(id)
            v.forEach {
                val unSegment = it.oricare().size == 4 && it.asteptat.getValue(Camp.PRET) is Asteptare.Exact
                assertTrue(unSegment || it.necunoscute() == Camp.entries, it.nume)
            }
            assertTrue(v.any { it.oricare().size == 4 } && v.any { it.necunoscute().size == 5 }, "$id: ambele cazuri trebuie sa apara")
        }
    }

    @Test
    fun `fara nicio ancora nu exista oferta, deci toate cele cinci campuri sunt necunoscute`() {
        variante("ancora_lipsa_ambele").forEach { v ->
            assertEquals(Camp.entries, v.necunoscute(), v.nume)
            assertTrue(v.linii.none { it.text.contains("min") }, "${v.nume}: a ramas un cuvant `min`")
        }
    }

    @Test
    fun `randurile straine dintre segmente nu schimba nimic din ce e asteptat`() {
        variante("randuri_straine_intre_segmente").forEach { v ->
            assertEquals(5, v.exacte().size, v.nume)
            assertTrue(v.linii.size > 6, v.nume)
        }
    }

    @Test
    fun `cardul partial are un singur segment - al lui ramane exact sau necunoscut, al celuilalt necunoscut`() {
        variante("card_partial_un_segment").forEach { v ->
            assertEquals(2, v.necunoscute().size, v.nume)
            assertEquals(2, v.oricare().size, v.nume)
            assertEquals(Asteptare.Exact::class, v.asteptat.getValue(Camp.PRET)::class, v.nume)
        }
    }

    @Test
    fun `doua oferte partial suprapuse - pretul poate fi doar al primeia, nicio cifra din a doua (inv 5)`() {
        variante("doua_oferte_partial_suprapuse").forEach { v ->
            val pret = v.asteptat.getValue(Camp.PRET) as Asteptare.Oricare

            assertEquals(1, pret.valori.size, v.nume)
            v.asteptat.filterKeys { it != Camp.PRET }.values.forEach { a ->
                assertTrue(a is Asteptare.Oricare && a.valori.size == 1 || a is Asteptare.Exact || a == Asteptare.Necunoscut, v.nume)
            }
        }
    }
}
