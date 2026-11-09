package app.trueride.core.lant

import app.trueride.core.lant.Eveniment.EcranSchimbat
import app.trueride.core.lant.Eveniment.NoduriCitite
import app.trueride.core.lant.Eveniment.OcrCitit
import app.trueride.core.lant.PlatformaSofer.UBER
import app.trueride.core.model.Thresholds
import app.trueride.core.parse.OfferParser
import app.trueride.core.parse.UBER_RO_V1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AutomatCardOrdineTest {

    private val uber = OfferParser(UBER_RO_V1)
    private val automat = AutomatCard(mapOf(UBER to uber, PlatformaSofer.NECUNOSCUTA to uber))
    private val start = StareLant(setari = Setari(cost = null, praguri = Thresholds()))

    private fun lant(vararg evenimente: Eveniment): Pas =
        evenimente.fold(Pas(start)) { pas, e -> automat.trateaza(pas.stare, e) }

    @Test
    fun `din gol schimbarea de ecran nu pune cardul pe ecran si cere nodurile`() {
        val pas = lant(EcranSchimbat(1, UBER))

        assertEquals(StareCard.Gol, pas.stare.card)
        assertEquals(listOf<Comanda>(Comanda.CitesteNoduri(1)), pas.comenzi)
    }

    @Test
    fun `nodurile cu pret dau succes fara OCR`() {
        val pas = lant(EcranSchimbat(1, UBER), NoduriCitite(1, Ecrane.ofertaCompleta))

        assertIs<StareCard.Succes>(pas.stare.card)
        assertTrue(pas.comenzi.isEmpty())
    }

    @Test
    fun `nodurile fara pret cer OCR pe acelasi numar`() {
        val pas = lant(EcranSchimbat(1, UBER), NoduriCitite(1, Ecrane.faraOferta))

        assertEquals(StareCard.Gol, pas.stare.card)
        assertEquals(listOf<Comanda>(Comanda.FaOcr(1)), pas.comenzi)
    }

    @Test
    fun `rezultatul unei schimbari vechi nu ajunge pe card`() {
        val pas = lant(
            EcranSchimbat(1, UBER),
            NoduriCitite(1, Ecrane.faraOferta),
            EcranSchimbat(2, UBER),
            OcrCitit(1, Ecrane.ofertaCompleta),
        )

        assertEquals(StareCard.Gol, pas.stare.card)
        assertTrue(pas.comenzi.isEmpty())
    }

    @Test
    fun `o schimbare cu numar vechi sau repetat se ignora`() {
        val dupaDoi = lant(EcranSchimbat(2, UBER))
        val repetat = automat.trateaza(dupaDoi.stare, EcranSchimbat(2, UBER))
        val vechi = automat.trateaza(dupaDoi.stare, EcranSchimbat(1, UBER))

        assertEquals(dupaDoi.stare, repetat.stare)
        assertTrue(repetat.comenzi.isEmpty())
        assertEquals(dupaDoi.stare, vechi.stare)
        assertTrue(vechi.comenzi.isEmpty())
    }

    @Test
    fun `nodurile repetate dupa cererea de OCR se ignora`() {
        val pas = lant(
            EcranSchimbat(1, UBER),
            NoduriCitite(1, Ecrane.faraOferta),
            NoduriCitite(1, Ecrane.ofertaCompleta),
        )

        assertEquals(StareCard.Gol, pas.stare.card)
        assertTrue(pas.comenzi.isEmpty())
    }

    @Test
    fun `evenimentele nu isi scriu liniile in toString`() {
        val text = listOf(
            NoduriCitite(1, Ecrane.ofertaCompleta),
            OcrCitit(1, Ecrane.ofertaCompleta),
        ).joinToString { it.toString() }

        assertFalse("RON" in text, text)
        assertFalse("km" in text, text)
        assertTrue("linii=6" in text, text)
    }

    // --- D-074: cardul apare doar cu rezultatul; la oferte inlantuite, doar cercul, max 3 s ---
    // D-134: peste un card cu cifre pulsul nu mai pune cercul (AutomatCardPulsTest). Cercul ramane
    // peste un card fara cifre (eroare), deci testele de mai jos pornesc de la o eroare afisata.

    private fun dupa(pas: Pas, vararg evenimente: Eveniment): Pas =
        evenimente.fold(pas) { p, e -> automat.trateaza(p.stare, e) }

    /** Cardul arata deja un rezultat fara cifre pentru n=1 (eroare: segmente fara pret, dupa limita, D-303). */
    private val cuRezultat = lant(
        EcranSchimbat(1, UBER), NoduriCitite(1, emptyList()), OcrCitit(1, Ecrane.ofertaFaraPret),
        Eveniment.AsteptareExpirata(1),
    )

    @Test
    fun `dupa un rezultat fara cifre schimbarea de ecran arata doar cercul si porneste limita`() {
        val pas = dupa(cuRezultat, EcranSchimbat(2, UBER))

        assertEquals(StareCard.Incarcare(UBER), pas.stare.card)
        assertEquals(
            listOf(Comanda.CitesteNoduri(2), Comanda.PornesteAsteptare(2)),
            pas.comenzi,
        )
    }

    @Test
    fun `dupa o eroare schimbarea de ecran arata tot cercul`() {
        val eroare = lant(
            EcranSchimbat(1, UBER), NoduriCitite(1, emptyList()), OcrCitit(1, Ecrane.ofertaFaraPret),
            Eveniment.AsteptareExpirata(1),
        )
        assertIs<StareCard.Eroare>(eroare.stare.card)

        val pas = dupa(eroare, EcranSchimbat(2, UBER))

        assertEquals(StareCard.Incarcare(UBER), pas.stare.card)
    }

    @Test
    fun `cercul nu poarta nicio cifra din oferta anterioara`() {
        val pas = dupa(cuRezultat, EcranSchimbat(2, UBER))

        assertFalse(pas.stare.card.toString().any { it.isDigit() }, pas.stare.card.toString())
    }

    @Test
    fun `schimbarile urmatoare pastreaza cercul fara sa reporneasca limita`() {
        val pas = dupa(cuRezultat, EcranSchimbat(2, UBER), EcranSchimbat(3, UBER))

        assertEquals(StareCard.Incarcare(UBER), pas.stare.card)
        assertEquals(listOf<Comanda>(Comanda.CitesteNoduri(3)), pas.comenzi)
    }

    @Test
    fun `limita expirata ascunde cercul`() {
        val pas = dupa(cuRezultat, EcranSchimbat(2, UBER), Eveniment.AsteptareExpirata(2))

        assertEquals(StareCard.Gol, pas.stare.card)
        assertTrue(pas.comenzi.isEmpty())
    }

    @Test
    fun `limita curge de la aparitia cercului chiar daca ecranul se tot schimba`() {
        val pas = dupa(
            cuRezultat,
            EcranSchimbat(2, UBER), EcranSchimbat(3, UBER), EcranSchimbat(4, UBER),
            Eveniment.AsteptareExpirata(2),
        )

        assertEquals(StareCard.Gol, pas.stare.card)
    }

    @Test
    fun `limita unui cerc anterior se ignora`() {
        val alDoileaCerc = dupa(
            cuRezultat,
            EcranSchimbat(2, UBER), NoduriCitite(2, emptyList()), OcrCitit(2, Ecrane.ofertaFaraPret),
            Eveniment.AsteptareExpirata(2), EcranSchimbat(3, UBER),
        )

        val pas = dupa(alDoileaCerc, Eveniment.AsteptareExpirata(2))

        assertEquals(StareCard.Incarcare(UBER), pas.stare.card)
        assertEquals(alDoileaCerc.stare, pas.stare)
    }

    @Test
    fun `limita sosita dupa rezultat nu ascunde rezultatul`() {
        val pas = dupa(
            cuRezultat,
            EcranSchimbat(2, UBER), NoduriCitite(2, Ecrane.ofertaCompleta),
            Eveniment.AsteptareExpirata(2),
        )

        assertIs<StareCard.Succes>(pas.stare.card)
    }

    @Test
    fun `rezultatul sosit dupa expirarea limitei pune cardul inapoi cu cifrele`() {
        val expirat = dupa(
            cuRezultat,
            EcranSchimbat(2, UBER), NoduriCitite(2, Ecrane.faraOferta),
            Eveniment.AsteptareExpirata(2),
        )
        assertEquals(StareCard.Gol, expirat.stare.card)

        val pas = dupa(expirat, OcrCitit(2, Ecrane.ofertaCompleta))

        assertIs<StareCard.Succes>(pas.stare.card)
    }

    @Test
    fun `dupa expirare o schimbare noua citeste in fundal fara cerc`() {
        val pas = dupa(
            cuRezultat,
            EcranSchimbat(2, UBER), Eveniment.AsteptareExpirata(2),
            EcranSchimbat(3, UBER),
        )

        assertEquals(StareCard.Gol, pas.stare.card)
        assertEquals(listOf<Comanda>(Comanda.CitesteNoduri(3)), pas.comenzi)
    }

    @Test
    fun `inchis oprit si alta aplicatie scot cercul, iar limita tarzie nu schimba nimic`() {
        val cerc = dupa(cuRezultat, EcranSchimbat(2, UBER))

        for (e in listOf(Eveniment.Inchis, Eveniment.Oprit, Eveniment.AltaAplicatie)) {
            val scos = dupa(cerc, e)
            val tarziu = dupa(scos, Eveniment.AsteptareExpirata(2))
            val urmatoarea = dupa(tarziu, EcranSchimbat(3, UBER))

            assertEquals(StareCard.Gol, scos.stare.card, "$e")
            assertEquals(scos.stare, tarziu.stare, "$e")
            assertEquals(StareCard.Gol, urmatoarea.stare.card, "$e")
        }
    }

    @Test
    fun `limita cercului este de 3 secunde`() {
        assertEquals(3_000L, LIMITA_CERC_MS)
    }
}
