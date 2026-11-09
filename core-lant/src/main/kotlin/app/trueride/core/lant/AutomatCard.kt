package app.trueride.core.lant

import app.trueride.core.model.OfferField
import app.trueride.core.model.ParsedOffer
import app.trueride.core.parse.LinieOcr
import app.trueride.core.parse.ParserOferta
import java.math.BigDecimal

/**
 * Automatul cardului: (stare, eveniment) -> (stare noua, comenzi). Functie pura:
 * nu citeste ecranul, nu stie de fire de executie, nu tine ceas.
 *
 * - D-033: la o schimbare de ecran se citesc intai nodurile; daca dau un pret,
 *   acela e rezultatul, altfel se cere OCR.
 * - D-035: iesirea din citire — pret -> Succes; timp/distanta fara pret ->
 *   Eroare; nimic -> Gol.
 * - D-074: citirea nu pune cardul pe ecran. Doar daca cardul arata deja un
 *   rezultat, schimbarea il inlocuieste cu cercul (`Incarcare`), care dispare
 *   dupa [LIMITA_CERC_MS] fara rezultat. Limita ascunde doar cercul: citirea
 *   continua, iar rezultatul ei tarziu pune cardul inapoi.
 * - D-134 (F-030): peste un card cu cifre (`Succes`), o schimbare de ecran pe aceeasi platforma
 *   (pulsul ofertei Uber) lasa cardul pe loc, fara cerc si fara limita, pana la citirea noua.
 *   Aceleasi cifre lasa cardul neschimbat; alte cifre, fara oferta, alta aplicatie sau ecran
 *   negru il inlocuiesc imediat. Relaxarea invariantului 5 e exact aceasta, pentru aceeasi oferta.
 * - D-303: un cadru cu segment, dar fara pret, nu da imediat „Nu am putut citi”. Cu aceleasi minute
 *   si km ca oferta afisata (`Succes`, aceeasi platforma) cardul ramane pe loc; altfel (oferta noua)
 *   cercul inlocuieste cifrele vechi (din `Gol` ramane `Gol`) si porneste limita [LIMITA_CERC_MS].
 *   Eroarea apare doar daca limita expira cu pretul inca lipsa (`eroarePendinta`); orice rezultat
 *   mai nou o sterge.
 * - Invariantul 5: un rezultat se accepta doar daca poarta `n`-ul ultimei
 *   schimbari si etapa asteptata. Orice altceva e o oferta trecuta.
 * - Un parser per platforma: o platforma fara reguli (Bolt, azi) nu produce
 *   niciodata o oferta — regulile Uber pe un ecran Bolt ar putea alege alta
 *   cifra drept pret.
 * - D-048: `CadruNegru` inchide oferta curenta atat din etapa nodurilor, cat si
 *   din OCR — pe un ecran protejat nu se citeste nimic.
 */
class AutomatCard(private val parsere: Map<PlatformaSofer, ParserOferta>) {

    fun trateaza(stare: StareLant, eveniment: Eveniment): Pas = when (eveniment) {
        is Eveniment.EcranSchimbat -> ecranSchimbat(stare, eveniment)
        is Eveniment.NoduriCitite ->
            dacaCurent(stare, eveniment.n, Etapa.NODURI) { noduriCitite(stare, eveniment) }
        is Eveniment.OcrCitit ->
            dacaCurent(stare, eveniment.n, Etapa.OCR) { ocrCitit(stare, eveniment) }
        // D-394: verificarea doar numara; cardul nu se corecteaza din a doua citire.
        is Eveniment.VerificareCitita -> Pas(stare)
        is Eveniment.OcrEsuat ->
            dacaCurent(stare, eveniment.n, Etapa.OCR) { incheie(stare, StareCard.Gol) }
        // D-048: poarta din executor trimite CadruNegru si din etapa nodurilor.
        is Eveniment.CadruNegru -> dacaCurent(stare, eveniment.n, Etapa.NODURI, Etapa.OCR) {
            incheie(stare, StareCard.Eroare(stare.platforma, MotivEroare.ECRAN_PROTEJAT))
        }
        // D-069: `incheie` pune etapa pe GATA, deci o citire inca in curs nu mai
        // reafiseaza cardul peste aplicatia din fata cand se termina.
        Eveniment.Inchis, Eveniment.Oprit, Eveniment.AltaAplicatie, Eveniment.CapturaIntrerupta ->
            incheie(stare, StareCard.Gol)
        is Eveniment.SetariSchimbate -> Pas(stare.copy(setari = eveniment.setari))
        is Eveniment.AsteptareExpirata -> asteptareExpirata(stare, eveniment.n)
    }

    private fun ecranSchimbat(stare: StareLant, e: Eveniment.EcranSchimbat): Pas {
        if (e.n <= stare.ultimulN) return Pas(stare)
        // D-134: cifrele raman cat Uber/Bolt arata aceeasi oferta; recitirea decide.
        val pastreazaCifre = stare.card is StareCard.Succes && e.platforma == stare.platforma
        // D-225 (F-039): cat ecranul ramane negru, o rafala Accessibility nu reaprinde cercul peste mesaj;
        // citirea se termina tot cu CadruNegru (D-048), deci cardul nu clipeste. Mesajul n-are cifre (invariantul 5).
        val pastreazaMesajul = e.negru && e.platforma == stare.platforma &&
            stare.card == StareCard.Eroare(e.platforma, MotivEroare.ECRAN_PROTEJAT)
        // D-074: cercul doar peste un rezultat afisat; din Gol se citeste in fundal.
        val peEcran = stare.card != StareCard.Gol
        val cercNou = peEcran && !pastreazaCifre && !pastreazaMesajul && stare.card !is StareCard.Incarcare
        val noua = stare.copy(
            card = when {
                pastreazaCifre || pastreazaMesajul -> stare.card
                peEcran -> StareCard.Incarcare(e.platforma)
                else -> StareCard.Gol
            },
            ultimulN = e.n,
            etapa = Etapa.NODURI,
            eroarePendinta = false,
            platforma = e.platforma,
            nCerc = if (cercNou) e.n else stare.nCerc,
        )
        val comenzi = listOf(Comanda.CitesteNoduri(e.n)) +
            if (cercNou) listOf(Comanda.PornesteAsteptare(e.n)) else emptyList()
        return Pas(noua, comenzi)
    }

    /**
     * D-074.3: ascunde doar cercul; etapa ramane, deci rezultatul tarziu tot ajunge pe card.
     * D-303: cu eroarea pendinta a acestei oferte, limita da `Eroare(NECITIBILA)` in loc de `Gol`.
     */
    private fun asteptareExpirata(stare: StareLant, n: Long): Pas = when {
        stare.eroarePendinta && n == stare.nCerc ->
            incheie(stare, StareCard.Eroare(stare.platforma, MotivEroare.NECITIBILA))
        stare.card is StareCard.Incarcare && n == stare.nCerc -> Pas(stare.copy(card = StareCard.Gol))
        else -> Pas(stare)
    }

    private fun noduriCitite(stare: StareLant, e: Eveniment.NoduriCitite): Pas {
        val oferta = parseaza(stare.platforma, e.linii)
        if (oferta?.price != null) return incheieCuSucces(stare, oferta)
        return Pas(stare.copy(etapa = Etapa.OCR), listOf(Comanda.FaOcr(e.n)))
    }

    private fun ocrCitit(stare: StareLant, e: Eveniment.OcrCitit): Pas {
        val o = parseaza(stare.platforma, e.linii) ?: return incheie(stare, StareCard.Gol)
        val areSegment = o.pickupMinutes != null || o.tripMinutes != null ||
            o.pickupDistance != null || o.tripDistance != null
        return when {
            o.price != null -> incheieCuSucces(stare, o)
            areSegment -> segmentFaraPret(stare, o, e.n)
            else -> incheie(stare, StareCard.Gol)
        }
    }

    /** D-303: aceeasi oferta -> cardul pe loc; oferta noua -> fara cifre vechi, eroarea doar dupa limita. */
    private fun segmentFaraPret(stare: StareLant, o: ParsedOffer, n: Long): Pas {
        val afisat = stare.card
        if (afisat is StareCard.Succes && afisat.platforma == stare.platforma && acelasiSegment(afisat.oferta, o)) {
            return incheie(stare, afisat)
        }
        val card = if (stare.card == StareCard.Gol) StareCard.Gol else StareCard.Incarcare(stare.platforma)
        // O limita deja in curs (cercul pus de schimbarea de ecran) nu se dubleaza.
        val limitaNoua = !(stare.card is StareCard.Incarcare && stare.nCerc >= n)
        val noua = stare.copy(
            card = card, etapa = Etapa.GATA, eroarePendinta = true,
            nCerc = if (limitaNoua) n else stare.nCerc,
        )
        return Pas(noua, if (limitaNoua) listOf(Comanda.PornesteAsteptare(n)) else emptyList())
    }

    private fun acelasiSegment(a: ParsedOffer, b: ParsedOffer) =
        a.pickupMinutes.eg(b.pickupMinutes) && a.tripMinutes.eg(b.tripMinutes) &&
            a.pickupDistance.eg(b.pickupDistance) && a.tripDistance.eg(b.tripDistance)

    /**
     * Liniile din care parserul [platforma] scoate valorile cardului (F-003, F-005): doar din ele
     * se construieste ancora cifrelor. Platforma fara reguli -> niciuna, deci nicio ancora.
     */
    fun liniiCard(platforma: PlatformaSofer, linii: List<LinieOcr>): List<LinieOcr> =
        parsere[platforma]?.liniiFolosite(linii).orEmpty()

    /** D-173: linia pretului pe care parserul [platforma] a ales-o si a respins-o pentru forma; null = nimic de recitit. */
    fun liniaPretDeRecitit(platforma: PlatformaSofer, linii: List<LinieOcr>): LinieOcr? =
        parsere[platforma]?.liniaPretFaraForma(linii)

    /** D-288.4: linia km de la preluare pe care parserul [platforma] n-a putut-o citi (sau a gasit-o imposibila); null = nimic de recitit. */
    fun liniaKmDeRecitit(platforma: PlatformaSofer, linii: List<LinieOcr>): LinieOcr? =
        parsere[platforma]?.liniaKmPreluareDeRecitit(linii)

    /** D-367: linia minutelor de la preluare pe care parserul [platforma] n-a gasit-o deloc (cifra scapata de OCR); null = nimic de recitit. */
    fun liniaMinuteDeRecitit(platforma: PlatformaSofer, linii: List<LinieOcr>): LinieOcr? =
        parsere[platforma]?.liniaMinutePreluareDeRecitit(linii)

    /**
     * Recitirea marita a liniei de la preluare se accepta doar daca schimba EXACT un lucru, prin aceleasi reguli si
     * aceleasi verificari ca prima citire; orice alta diferenta = alta oferta sau citire nesigura (invariantul 5):
     * raman liniile primei citiri si campul necunoscut (invariantul 6). Doua cai simetrice:
     * - D-288.4, km: km de la preluare trec din necunoscut in cititi (plauzibili dupa viteza, D-148), minutele,
     *   pretul si segmentul cursei raman identice. D-371: cu km implauzibili parserul anuleaza si minutele, deci
     *   „identice” se judeca pe minutele de dinainte de viteza ([ParserOferta.minutePreluareInainteDeViteza]), nu pe
     *   cele din `parseaza` (null); dupa recitire minutele trebuie sa fie aceleasi si citite, cu km plauzibili;
     * - D-367, minute: minutele de la preluare trec din necunoscut in cititi DIRECT (incredere 1, nu o litera
     *   corectata prin harta, D-122), km de la preluare raman cititi si egali (cifra noua nu a facut km imposibili:
     *   D-148 i-ar fi anulat si ar fi picat egalitatea), pretul si cursa identice. Minutele peste 99 se resping
     *   fiindca parserul le anuleaza (`pickupMinutes` ramane null), nu din cauza egalitatii km.
     * Niciodata ambele campuri de la preluare odata.
     */
    fun recitireKmAcceptata(platforma: PlatformaSofer, inainte: List<LinieOcr>, dupa: List<LinieOcr>): Boolean {
        val parser = parsere[platforma] ?: return false
        val a = parser.parseaza(inainte)
        val b = parser.parseaza(dupa)
        val restulIdentic = a.price.eg(b.price) && a.tripMinutes.eg(b.tripMinutes) && a.tripDistance.eg(b.tripDistance)
        val kmReparati = a.pickupDistance == null && b.pickupDistance != null &&
            parser.minutePreluareInainteDeViteza(inainte).eg(b.pickupMinutes)
        val minuteReparate = a.pickupMinutes == null && b.pickupMinutes != null && minuteCititeDirect(b) &&
            a.pickupDistance != null && a.pickupDistance.eg(b.pickupDistance)
        return restulIdentic && (kmReparati || minuteReparate)
    }

    /**
     * D-368: campurile cunoscute in ambele citiri ale liniei de la preluare ([inainte], [dupa]) cu valori diferite. Apelantul
     * il intreaba doar pentru o recitire respinsa: o diferenta cunoscut -> cunoscut explica de ce, iar necunoscut ->
     * cunoscut (repararea) nu e dezacord. Doar nume de campuri; platforma fara reguli -> nimic de comparat.
     */
    fun recitireDezacord(platforma: PlatformaSofer, inainte: List<LinieOcr>, dupa: List<LinieOcr>): List<OfferField> {
        val parser = parsere[platforma] ?: return emptyList()
        return DezacordCadre.campuri(parser.parseaza(inainte), parser.parseaza(dupa))
    }

    /** Minutele de la preluare s-au citit cu tiparul din reguli (incredere 1), nu prin harta de litere (0,9). */
    private fun minuteCititeDirect(o: ParsedOffer): Boolean =
        o.fieldConfidence[OfferField.PICKUP_MINUTES]?.compareTo(BigDecimal.ONE) == 0

    /** Egalitate numerica (nu `equals`: 1.5 si 1.50 sunt aceeasi valoare); null egal doar cu null. */
    private fun BigDecimal?.eg(altul: BigDecimal?): Boolean =
        if (this == null || altul == null) this == null && altul == null else compareTo(altul) == 0

    private fun parseaza(platforma: PlatformaSofer, linii: List<LinieOcr>): ParsedOffer? =
        parsere[platforma]?.parseaza(linii)

    private fun succes(stare: StareLant, oferta: ParsedOffer) =
        StareCard.Succes(stare.platforma, oferta, Evaluare.calculeaza(oferta, stare.setari))

    /**
     * D-368: un Succes nou peste un card cu cifre ramas pe ecran (aceeasi platforma, D-134) e o recitire a aceleiasi oferte;
     * campurile cunoscute si diferite in ele (cel mult doua, D-368.5) sunt dezacord intre cadre. Din orice alta stare (Gol, cerc, Eroare: o oferta
     * noua a scos cardul vechi) nu se compara nimic.
     */
    private fun incheieCuSucces(stare: StareLant, oferta: ParsedOffer): Pas {
        val afisat = stare.card
        val dezacord = if (afisat is StareCard.Succes && afisat.platforma == stare.platforma) {
            DezacordCadre.dintreCadre(afisat.oferta, oferta)
        } else {
            emptyList()
        }
        return incheie(stare, succes(stare, oferta)).copy(dezacord = dezacord)
    }

    private fun incheie(stare: StareLant, card: StareCard) =
        Pas(stare.copy(card = card, etapa = Etapa.GATA, eroarePendinta = false))

    private inline fun dacaCurent(stare: StareLant, n: Long, vararg etape: Etapa, pas: () -> Pas): Pas =
        if (n == stare.ultimulN && stare.etapa in etape) pas() else Pas(stare)
}
