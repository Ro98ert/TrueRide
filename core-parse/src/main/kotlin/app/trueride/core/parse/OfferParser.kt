package app.trueride.core.parse

import app.trueride.core.model.DistanceUnit
import app.trueride.core.model.OfferField
import app.trueride.core.model.ParsedOffer
import app.trueride.core.model.PriceBasis
import java.math.BigDecimal

/** De ce a ramas null un camp (D-109): doar coduri, niciodata text citit (invariantul 2). */
enum class MotivLipsa {
    FARA_SUMA, DOAR_CU_SEMN, FARA_ETALON, SUB_PRAG, FORMA_PRET,
    ANCORE_LIPSA, ANCORE_NUMAR, FARA_MINUTE, FARA_KM,
    /** F-036, D-148: km cititi, dar imposibili fata de minutele segmentului (punct zecimal pierdut). */
    KM_IMPLAUZIBIL,
    /** D-329.1: pret citit, dar lei/km pe km totali iese din banda setului (`minLeiPeKm`/`maxLeiPeKm`). */
    PRET_IMPLAUZIBIL,
}

/**
 * Motorul: aplica un [ParserRuleSet] pe liniile OCR ale unei oferte. Stabil —
 * cand aplicatia isi muta layout-ul se schimba setul de reguli, nu clasa asta.
 * Extrage doar cifre si unitati (invariantul 4); ce nu se poate citi ramane
 * null, niciodata 0 (invariantul 6).
 */
class OfferParser(private val setReguli: ParserRuleSet) : ParserOferta {

    private val reguli = setReguli.reguli
    private val suma = Regex(reguli.sumaPattern)
    private val ancoraSegment = Regex(reguli.segmentAncoraPattern)
    private val minute = Regex(reguli.minutePattern)
    private val distanta = Regex(reguli.distantaPattern)
    private val formaPret = Regex(reguli.pretFormaPattern)
    private val formaDupaRecitire = setReguli.pretFormaDupaRecitirePattern?.let(::Regex)
    private val harta = reguli.corectiiCifre
    private val litereHarta = harta.keys.joinToString("") { Regex.escape(it) }
    private val sumaCuLitere = cuLitere(reguli.sumaPattern)
    private val minuteCuLitere = cuLitere(reguli.minutePattern)
    private val distantaCuLitere = cuLitere(reguli.distantaPattern)

    /**
     * D-122..D-124: acelasi tipar din reguli, cu fiecare `\d` largit la cifre + literele din harta, ca
     * granitele si forma sa ramana cele ale regulii. Null = fara harta (fara corectie).
     */
    private fun cuLitere(tipar: String): Regex? =
        litereHarta.takeIf { it.isNotEmpty() }?.let { Regex(tipar.replace("""\d""", """[\d$it]""")) }

    private data class Suma(
        val text: String,
        val valoare: BigDecimal,
        val cuSemn: Boolean,
        val linie: LinieOcr,
        val corectata: Boolean = false,
        /** Suma e tot textul liniei (fara spatii la capete): nimic scris in plus, nicio a doua suma (D-299). */
        val doarSuma: Boolean = false,
    ) {
        val inaltime: Int get() = linie.cutie.inaltime
    }
    private data class Segment(
        val minute: BigDecimal?,
        val distanta: BigDecimal?,
        /** D-371: minutele dupa garzile de forma, dar inainte ca viteza preluarii sa le anuleze; egale cu [minute] in rest. */
        val minuteInainteDeViteza: BigDecimal? = minute,
        val minuteCorectate: Boolean = false,
        val distantaCorectata: Boolean = false,
        val distantaImplauzibila: Boolean = false,
    )

    /** Grupul numeric citit: `text` are deja literele inlocuite; `corectata` = a trecut prin harta. */
    private data class Citire(
        val potrivire: MatchResult,
        val text: String,
        val corectata: Boolean,
        /**
         * D-315 (val5-l-margine): o litera din harta, prima sau ultima din token, a facut tokenul de 3+ cifre din unul
         * cu cel mult 2 cifre reale. Litera de la margine e zgomot lipit de numar, nu o cifra pierduta.
         */
        val umflataLaMargine: Boolean = false,
    )

    /** Rezultatul alegerii pretului: suma aleasa SAU motivul pentru care nu exista una. */
    private sealed interface AlegerePret {
        data class Aleasa(val suma: Suma) : AlegerePret
        /** [linie] = linia aleasa, doar la FORMA_PRET: lantul o poate reciti marita (D-173). */
        data class Lipsa(val motiv: MotivLipsa, val linie: LinieOcr? = null) : AlegerePret
    }

    override fun parseaza(linii: List<LinieOcr>): ParsedOffer {
        val sumaPret = pretul(linii)
        val pret = sumaPret?.valoare
        val segmente = liniiSegment(linii).mapIndexed { i, l -> citesteSegment(l, preluare = i == 0) }
        val preluare = segmente.getOrNull(0)
        val cursa = segmente.getOrNull(1)
        val areDistanta = preluare?.distanta != null || cursa?.distanta != null

        return ParsedOffer(
            price = pret,
            currency = pret?.let { reguli.currency },
            priceBasis = if (pret != null) reguli.priceBasis else PriceBasis.UNKNOWN,
            pickupMinutes = preluare?.minute,
            tripMinutes = cursa?.minute,
            pickupDistance = preluare?.distanta,
            tripDistance = cursa?.distanta,
            distanceUnitDetected = if (areDistanta) reguli.unitateDistanta else null,
            fieldConfidence = incredere(sumaPret, preluare, cursa),
        )
    }

    /**
     * Care suma e pretul cursei (contributia ceruta de autor la pasul 6):
     * 1. o suma cu semn in fata (+, -, −, cu sau fara spatiu) e un ADAOS sau o
     *    SCADERE la castig (bonus, promotie), nu tariful — se exclude oricat de
     *    mare ar fi scrisa;
     * 2. dintre restul, pretul e suma scrisa cel mai mare (cutia cea mai inalta):
     *    e cifra pe care aplicatia vrea s-o vezi prima. Pe fixture-urile Uber RO,
     *    ~115 px fata de ~45 px.
     * 3. castigatoarea trebuie sa fie scrisa vizibil mai mare decat textul obisnuit
     *    (`pretInaltimeMinimaRelativa` x mediana liniilor care nu sunt sume). Daca
     *    linia pretului lipseste din cadru, o suma scrisa ca restul textului nu-i
     *    ia locul (M5). Fara linii de referinta nu exista etalon -> null.
     * 4. castigatoarea trebuie sa aiba forma pretului (`pretFormaPattern`, D-080; sau forma intreaga D-299,
     *    [formaIntreaga], doar pe o linie recitita). Se verifica DUPA alegere, nu ca filtru inainte: o suma
     *    stricata de OCR in linia pretului nu lasa o alta suma sa-i ia locul — pretul devine necunoscut (F-011).
     * Pozitia pe ecran nu intra in decizie: e prima care se schimba la un redesign.
     * Nicio suma ramasa -> null: "pret necunoscut" e mai bun decat bonusul afisat
     * ca pret (invariantii 5 si 6).
     */
    private fun alegePretul(sume: List<Suma>, referinta: BigDecimal?): AlegerePret {
        if (sume.isEmpty()) return AlegerePret.Lipsa(MotivLipsa.FARA_SUMA)
        val fataSemn = sume.filterNot { it.cuSemn }
        if (fataSemn.isEmpty()) return AlegerePret.Lipsa(MotivLipsa.DOAR_CU_SEMN)
        if (referinta == null) return AlegerePret.Lipsa(MotivLipsa.FARA_ETALON)
        val prag = referinta.multiply(reguli.pretInaltimeMinimaRelativa)
        val castigatoare = fataSemn
            .filter { BigDecimal(it.inaltime) >= prag }
            .maxByOrNull { it.inaltime }
            ?: return AlegerePret.Lipsa(MotivLipsa.SUB_PRAG)
        return if (formaPret.matches(castigatoare.text) || formaIntreaga(castigatoare)) {
            AlegerePret.Aleasa(castigatoare)
        } else {
            AlegerePret.Lipsa(MotivLipsa.FORMA_PRET, castigatoare.linie)
        }
    }

    /**
     * D-299: forma de pret intreg (`pretFormaDupaRecitirePattern`, Uber EN = exact 2 cifre) se accepta doar daca
     * lantul a marcat linia `recitita` (nu se deduce din text), suma NU a trecut prin harta de litere (`RON2O`
     * ramane necunoscut, D-299.6) si linia e doar suma (`RON20-RON30` nu e pret). Fara camp in reguli: niciodata.
     */
    private fun formaIntreaga(s: Suma): Boolean =
        s.linie.recitita && !s.corectata && s.doarSuma && formaDupaRecitire?.matches(s.text) == true

    /**
     * Liniile din care [parseaza] scoate valorile cardului: linia pretului ales si liniile
     * segmentelor (preluare, cursa), in aceasta ordine. Nimic altceva: nu ceasul din bara de
     * stare, nu bonusul, nu textul obisnuit. Ancora cardului (F-003, F-005) se construieste
     * doar din ele, ca cifrele din afara ofertei sa nu intinda zona peste harta.
     */
    override fun liniiFolosite(linii: List<LinieOcr>): List<LinieOcr> =
        listOfNotNull(pretul(linii)?.linie) + liniiSegment(linii)

    private fun pretul(linii: List<LinieOcr>): Suma? =
        (alegerePret(linii) as? AlegerePret.Aleasa)?.suma

    private fun alegerePret(linii: List<LinieOcr>): AlegerePret {
        if (reguli.pretCereSegment && ancore(linii).isEmpty()) return AlegerePret.Lipsa(MotivLipsa.ANCORE_LIPSA)
        val referinta = medianaInaltimii(linii.filterNot { suma.containsMatchIn(it.text) })
        val ales = alegePretul(linii.mapNotNull(::citesteSuma), referinta)
        return if (ales is AlegerePret.Aleasa && inAfaraBenzii(ales.suma.valoare, linii)) AlegerePret.Lipsa(MotivLipsa.PRET_IMPLAUZIBIL) else ales
    }

    /**
     * D-329.1: lei/km pe km totali (preluare + cursa) in afara benzii setului = cifra dublata sau pierduta la pret.
     * Comparatie prin inmultire (fara impartire, deci fara rotunjire si fara impartire la zero). Fara banda, fara km
     * pe vreun segment sau cu 0 km totali nu se judeca.
     */
    private fun inAfaraBenzii(pret: BigDecimal, linii: List<LinieOcr>): Boolean {
        val min = setReguli.minLeiPeKm
        val max = setReguli.maxLeiPeKm
        if (min == null && max == null) return false
        val km = liniiSegment(linii).mapIndexed { i, l -> citesteSegment(l, preluare = i == 0).distanta }
        if (km.isEmpty() || km.any { it == null }) return false
        val total = km.sumOf { it!! }
        if (total.signum() == 0) return false
        return (min != null && pret < total.multiply(min)) || (max != null && pret > total.multiply(max))
    }

    /**
     * De ce e null fiecare camp pe care [parseaza] nu l-a putut citi (D-109). Foloseste aceleasi
     * functii ca [parseaza] (alegerePret, ancore, citesteSegment), ca explicatia sa nu poata
     * diverge de rezultat. Doar coduri: nu intoarce text din ecran (invariantul 2).
     */
    override fun motiveLipsa(linii: List<LinieOcr>): Map<OfferField, MotivLipsa> = buildMap {
        (alegerePret(linii) as? AlegerePret.Lipsa)?.let { put(OfferField.PRICE, it.motiv) }
        putAll(motiveSegmente(ancore(linii)))
    }

    override fun liniaPretFaraForma(linii: List<LinieOcr>): LinieOcr? =
        (alegerePret(linii) as? AlegerePret.Lipsa)?.linie

    /**
     * D-288.4: preluarea e primul segment (ordinea vine din ancora, ca la [liniiSegment]). Se reciteste doar daca
     * minutele s-au citit si km nu: `distanta == null` e exact FARA_KM sau KM_IMPLAUZIBIL din [motiveSegmente].
     * D-367: minutele lipsa au metoda lor, [liniaMinutePreluareDeRecitit]; aceasta ramane neschimbata.
     * D-371: viteza implauzibila anuleaza si minutele, deci "minute citite" inseamna [Segment.minuteInainteDeViteza]:
     * altfel exact cazul pe care recitirea il repara (punct pierdut) ar iesi din ea.
     */
    override fun liniaKmPreluareDeRecitit(linii: List<LinieOcr>): LinieOcr? {
        val preluare = liniiSegment(linii).firstOrNull() ?: return null
        return preluare.takeIf { citesteSegment(it, preluare = true).let { s -> s.minuteInainteDeViteza != null && s.distanta == null } }
    }

    /**
     * D-367: oglinda lui [liniaKmPreluareDeRecitit] pentru minute. Se reciteste doar cand tokenul minutelor lipseste cu
     * totul: km cititi, minute null si NICIO cifra, „h”/„H” sau litera din harta inaintea ancorei segmentului. Km NU sunt
     * judecati dupa viteza aici (cu minute lipsa D-148 nu se evalueaza, `kmImposibili` intoarce false): "cititi" inseamna
     * doar ca tiparul i-a gasit. Validarea vine la acceptare: dupa recitire km trebuie sa ramana egali, iar cu minutele
     * citite D-148 ruleaza si anuleaza km imposibili, deci recitirea se respinge. Minutele anulate de o garda (D-312
     * lipit de litera / „h ” / alt numar, D-315 peste 99 sau umflate la margine) au mereu ceva inaintea ancorei: acolo
     * cifra nu a fost scapata, a fost citita, deci o recitire ar ghici.
     * D-391.2 (F-058): un cuvant doar din litere (1-3, fara cifra, fara „h”) lipit de ancora cere si el recitire, chiar
     * daca are litere din harta („Lal min”): OCR-ul a citit cifra ca litere. Cifra nu se deduce din litere (un „11 min”
     * deformat ar deveni 1); doar se reciteste. Ramane fara recitire ce a citit minute prin harta (D-315, „lOl”).
     */
    override fun liniaMinutePreluareDeRecitit(linii: List<LinieOcr>): LinieOcr? {
        val preluare = liniiSegment(linii).firstOrNull() ?: return null
        val s = citesteSegment(preluare, preluare = true)
        if (s.minute != null || s.distanta == null) return null
        val cereRecitire = faraCifreInainteaAncorei(preluare.text) ||
            (cuvantDeLitereInainteaAncorei(preluare.text) &&
                citeste(preluare.text, minute, minuteCuLitere, grup = 1) == null)
        return preluare.takeIf { cereRecitire }
    }

    /**
     * D-391.2: ultimul cuvant de dinaintea ancorei are 1-3 caractere, toate litere (nu „h”/„H”), iar restul textului de
     * dinaintea lui trece de regula D-367 (fara cifre, „h” sau litere din harta).
     */
    private fun cuvantDeLitereInainteaAncorei(text: String): Boolean {
        val ancora = ancoraSegment.find(text) ?: return false
        val inainte = text.substring(0, ancora.range.first).trimEnd()
        val cuvant = inainte.substringAfterLast(' ')
        val rest = inainte.removeSuffix(cuvant)
        return cuvant.length in 1..3 && cuvant.all { it.isLetter() && it != 'h' && it != 'H' } &&
            rest.none { it.isDigit() || it == 'h' || it == 'H' || it.toString() in harta }
    }

    /**
     * Textul de dinaintea primei ancore de segment nu are cifre, litere din harta sau „h”/„H” (D-367). „h” inseamna
     * formatul ore + minute („1 h 5 min.”, vezi garda D-312 din `minutePattern` la toate cele trei seturi): cu cifrele
     * pierdute, recitirea ar da doar minutele („5”) in loc de 65, deci o valoare gresita, nu una lipsa.
     */
    private fun faraCifreInainteaAncorei(text: String): Boolean {
        val ancora = ancoraSegment.find(text) ?: return false
        return text.substring(0, ancora.range.first).none { it.isDigit() || it == 'h' || it == 'H' || it.toString() in harta }
    }

    override fun minutePreluareInainteDeViteza(linii: List<LinieOcr>): BigDecimal? =
        liniiSegment(linii).firstOrNull()?.let { citesteSegment(it, preluare = true).minuteInainteDeViteza }

    private fun motiveSegmente(ancore: List<LinieOcr>): Map<OfferField, MotivLipsa> {
        val camp = listOf(
            OfferField.PICKUP_MINUTES to OfferField.PICKUP_DISTANCE,
            OfferField.TRIP_MINUTES to OfferField.TRIP_DISTANCE,
        )
        val global = when {
            ancore.isEmpty() -> MotivLipsa.ANCORE_LIPSA
            ancore.size != reguli.numarSegmente -> MotivLipsa.ANCORE_NUMAR
            else -> null
        }
        return buildMap {
            camp.forEachIndexed { i, (campMinute, campDistanta) ->
                val segment = ancore.getOrNull(i)?.let { citesteSegment(it, preluare = i == 0) }
                // Un segment pe care regulile nu-l prevad (numarSegmente < 2) nu are ancora a lui.
                val lipsaSegment = global ?: if (segment == null) MotivLipsa.ANCORE_NUMAR else null
                if (lipsaSegment != null || segment?.minute == null) {
                    // D-371: minute citite, dar anulate de viteza preluarii = acelasi motiv ca la km, nu "nu s-au citit".
                    val anulateDeViteza = segment?.minuteInainteDeViteza != null && segment.distantaImplauzibila
                    put(campMinute, lipsaSegment ?: if (anulateDeViteza) MotivLipsa.KM_IMPLAUZIBIL else MotivLipsa.FARA_MINUTE)
                }
                if (lipsaSegment != null || segment?.distanta == null) {
                    val motivKm = if (segment?.distantaImplauzibila == true) MotivLipsa.KM_IMPLAUZIBIL else MotivLipsa.FARA_KM
                    put(campDistanta, lipsaSegment ?: motivKm)
                }
            }
        }
    }

    /**
     * Ordinea se ia din ancora, nu din citirea reusita: o preluare ilizibila
     * ramane pe locul ei, iar cursa nu urca in locul ei. Daca numarul de
     * linii-ancora nu e cel asteptat, motorul nu ghiceste care e care (I4).
     */
    private fun liniiSegment(linii: List<LinieOcr>): List<LinieOcr> =
        ancore(linii).takeIf { it.size == reguli.numarSegmente }.orEmpty()

    /** Liniile-ancora de sus in jos, oricate sunt; [liniiSegment] decide daca numarul e cel bun. */
    private fun ancore(linii: List<LinieOcr>): List<LinieOcr> =
        linii.filter { ancoraSegment.containsMatchIn(it.text) }.sortedBy { it.cutie.sus }

    override fun ancoreSegment(linii: List<LinieOcr>): List<LinieOcr> = ancore(linii)

    /** Mediana inaltimilor; null pentru o lista goala. BigDecimal: fara virgula mobila. */
    private fun medianaInaltimii(linii: List<LinieOcr>): BigDecimal? {
        if (linii.isEmpty()) return null
        val h = linii.map { it.cutie.inaltime }.sorted()
        val mijloc = h.size / 2
        return if (h.size % 2 == 1) {
            BigDecimal(h[mijloc])
        } else {
            BigDecimal(h[mijloc - 1] + h[mijloc]).divide(BigDecimal(2))   // /2 e mereu exact
        }
    }

    private fun citesteSuma(linie: LinieOcr): Suma? {
        val c = citeste(linie.text, suma, sumaCuLitere, grup = 2) ?: return null
        return Suma(
            text = c.text,
            valoare = zecimal(c.text) ?: return null,
            cuSemn = c.potrivire.groupValues[1].isNotEmpty(),
            linie = linie,
            corectata = c.corectata,
            doarSuma = c.potrivire.value.trim() == linie.text.trim(),
        )
    }

    private fun citesteSegment(linie: LinieOcr, preluare: Boolean): Segment {
        val min = citeste(linie.text, minute, minuteCuLitere, grup = 1)
        val km = citeste(linie.text, distanta, distantaCuLitere, grup = 1)
        val minuteCitite = min?.let { zecimal(it.text) }
        // D-315: minutele umflate la 3+ cifre de o litera de margine sunt necunoscute (inv. 6), ca la `minuteImposibile`.
        val minuteUmflate = min?.umflataLaMargine == true
        val kmCititi = km?.let { zecimal(it.text) }
        // Viteza (D-148) se judeca pe minutele citite, nu pe cele anulate de garda: garda nu schimba soarta km.
        val implauzibil = kmImposibili(minuteCitite, kmCititi) || (!preluare && (kmPreaMici(minuteCitite, kmCititi) || kmCursaZero(minuteCitite, kmCititi)))
        // D-315, P-sim-3: preluare cu 3+ cifre = cifra dublata de OCR, nu o preluare reala (vezi `maxMinutePreluare`).
        val minuteImposibile = preluare && minuteCitite != null && setReguli.maxMinutePreluare?.let { minuteCitite > it } == true
        val minuteValide = minuteCitite.takeUnless { minuteImposibile || minuteUmflate }
        // D-371: la preluare, km respinsi de D-332 sau o viteza peste 65 km/h (`preluarePreaRapida`) anuleaza si minutele.
        val kmAnulati = implauzibil || (preluare && preluarePreaRapida(minuteCitite, kmCititi))
        return Segment(
            minute = minuteValide.takeUnless { preluare && kmAnulati },
            distanta = kmCititi.takeUnless { kmAnulati },
            minuteInainteDeViteza = minuteValide,
            minuteCorectate = min?.corectata == true && !minuteUmflate,
            distantaCorectata = km?.corectata == true,
            distantaImplauzibila = kmAnulati,
        )
    }

    /**
     * F-036, D-148: OCR-ul pierde punctul zecimal mic („7.1 km” -> „71 km”), iar numarul ramane valid
     * ca forma. Il prinde doar fizica: viteza medie a segmentului. Minutele + 0,5 acopera rotunjirea
     * aplicatiei („1 min” poate fi 1,4 min). Peste prag km devin necunoscuti — punctul nu se ghiceste.
     * D-332: pragul e 100 km/h sub 30 de minute (nicio oferta reala masurata nu trece de ~63) si 150 de la 30.
     * 0 minute sau minute lipsa: viteza nu se poate calcula, deci nu se verifica. Doar km (singura unitate
     * din reguli azi); pentru mile pragul ar trebui convertit.
     */
    private fun kmImposibili(minute: BigDecimal?, km: BigDecimal?): Boolean {
        if (minute == null || km == null || minute.signum() <= 0) return false
        if (reguli.unitateDistanta != DistanceUnit.KM) return false
        // km / ((min + 0,5) / 60) > prag  <=>  km * 60 > prag * (min + 0,5): fara impartire, fara rotunjire.
        val prag = if (minute < MINUTE_SEGMENT_LUNG) VITEZA_MAXIMA_SCURT_KM_H else VITEZA_MAXIMA_LUNG_KM_H
        return km.multiply(MINUTE_PE_ORA) > prag.multiply(minute.add(TOLERANTA_ROTUNJIRE_MIN))
    }

    /**
     * D-371 (2026-10-26): preluarea cu viteza medie peste [VITEZA_MAXIMA_PRELUARE_KM_H] nu e citita corect: OCR-ul
     * pierde uneori orele din „2 h 22 min.” si citeste 22 in loc de 142, iar viteza implicita tradeaza citirea (maximul
     * real masurat: 62,64 km/h). Aceeasi forma ca [kmImposibili] (fara impartire, minute + 0,5), dar un singur prag, fara
     * treapta de la 30 de minute. Ce trece pragul isi pierde minutele SI km: nu se stie care dintre ele e gresit.
     */
    private fun preluarePreaRapida(minute: BigDecimal?, km: BigDecimal?): Boolean {
        if (minute == null || km == null || minute.signum() <= 0) return false
        if (reguli.unitateDistanta != DistanceUnit.KM) return false
        return km.multiply(MINUTE_PE_ORA) > VITEZA_MAXIMA_PRELUARE_KM_H.multiply(minute.add(TOLERANTA_ROTUNJIRE_MIN))
    }

    /**
     * D-315, P-sim-7: limita inferioara de viteza a cursei (`minVitezaCursaKmH`), oglinda lui D-148. Viteza se judeca pe
     * minutele afisate, fara toleranta: km * 60 < prag * minute. Minute 0 sau lipsa: nu se judeca (impartirea la zero e stare).
     */
    private fun kmPreaMici(minute: BigDecimal?, km: BigDecimal?): Boolean {
        val prag = setReguli.minVitezaCursaKmH ?: return false
        if (minute == null || km == null || minute.signum() <= 0) return false
        if (reguli.unitateDistanta != DistanceUnit.KM) return false
        return km.multiply(MINUTE_PE_ORA) < prag.multiply(minute)
    }

    /**
     * D-315, val8-uber-ro-km-cursa-zero: cursa cu minute > 0 si exact 0 km nu exista (`respingeKmCursaZero`). Comparatie pe
     * `signum()`, nu `equals` (0.0 si 0 sunt aceeasi valoare). Minute 0 sau lipsa: nu se judeca.
     */
    private fun kmCursaZero(minute: BigDecimal?, km: BigDecimal?): Boolean =
        setReguli.respingeKmCursaZero && minute != null && km != null && minute.signum() > 0 && km.signum() == 0

    /**
     * Citirea unui numar (D-122..D-124, F-025):
     * 1. directa, cu tiparul din reguli — dar NU daca are lipita in stanga o litera din harta: din „l0 min.”
     *    tiparul ar lua doar „0”, un numar prea mic, citit sigur;
     * 2. altfel, tiparul largit la literele hartii; tokenul nu are voie sa fie lipit de alta litera sau cifra
     *    (nu se stie unde incepe numarul), iar fiecare litera trece prin harta.
     * „La5 min.”: „a” nu e in harta, deci „5” ramane citire directa.
     * F-038: la Uber EN moneda e lipita in fata („RON1l.50”), deci tiparul nu inchide numarul la dreapta:
     * citirea directa ar lua doar „1”. Nici o litera din harta lipita in dreapta nu lasa citirea directa.
     * Lipirea pe calea largita se verifica in fata potrivirii intregi, nu a grupului: „RON” face parte din tipar.
     */
    private fun citeste(text: String, direct: Regex, cuLitere: Regex?, grup: Int): Citire? {
        direct.find(text)?.takeUnless { lipitDe(text, it, grup) { c -> c.toString() in harta } || urmatDeHarta(text, it, grup) }
            ?.let { return Citire(it, it.groupValues[grup], corectata = false) }
        val m = cuLitere?.find(text)?.takeUnless { lipitDe(text, it, grup = 0, Char::isLetterOrDigit) } ?: return null
        val token = m.groupValues[grup]
        return Citire(
            potrivire = m,
            text = token.map { harta[it.toString()] ?: it.toString() }.joinToString(""),
            corectata = token.any { it.toString() in harta },
            umflataLaMargine = token.length >= 3 && token.count { it.isDigit() } <= 2 &&
                (token.first().toString() in harta || token.last().toString() in harta),
        )
    }

    /** Caracterul de imediat dinaintea grupului [grup] indeplineste [conditie]. */
    private fun lipitDe(text: String, m: MatchResult, grup: Int, conditie: (Char) -> Boolean): Boolean {
        val start = m.groups[grup]?.range?.first ?: return false
        return start > 0 && conditie(text[start - 1])
    }

    /** Caracterul de imediat dupa grupul [grup] e o litera din harta (F-038). */
    private fun urmatDeHarta(text: String, m: MatchResult, grup: Int): Boolean {
        val dupa = (m.groups[grup]?.range?.last ?: return false) + 1
        return dupa < text.length && text[dupa].toString() in harta
    }

    /** Virgula, punctul sau „:” (D-315 valB, doar daca regula setului l-a lasat sa treaca) zecimal -> BigDecimal; orice altceva (ex. "▢") -> null. */
    private fun zecimal(text: String): BigDecimal? = text.replace(',', '.').replace(':', '.').toBigDecimalOrNull()

    // v1: o regula care a gasit campul da incredere 1; un camp corectat din litere (D-122..D-124) da
    // INCREDERE_CORECTATA; campul negasit lipseste din harta.
    private fun incredere(pret: Suma?, preluare: Segment?, cursa: Segment?) =
        buildMap {
            if (pret != null) put(OfferField.PRICE, increderea(pret.corectata))
            if (preluare?.minute != null) put(OfferField.PICKUP_MINUTES, increderea(preluare.minuteCorectate))
            if (preluare?.distanta != null) put(OfferField.PICKUP_DISTANCE, increderea(preluare.distantaCorectata))
            if (cursa?.minute != null) put(OfferField.TRIP_MINUTES, increderea(cursa.minuteCorectate))
            if (cursa?.distanta != null) put(OfferField.TRIP_DISTANCE, increderea(cursa.distantaCorectata))
        }

    private fun increderea(corectata: Boolean) = if (corectata) INCREDERE_CORECTATA else BigDecimal.ONE

    companion object {
        /** D-122: un camp refacut dintr-o litera nu e o citire sigura; sub 1, ca un consumator viitor sa-l poata trata aparte. */
        val INCREDERE_CORECTATA: BigDecimal = BigDecimal("0.9")

        /** D-332 (2026-10-23, modifica D-148): 100 km/h sub 30 de minute, 150 km/h de la 30. */
        private val VITEZA_MAXIMA_SCURT_KM_H = BigDecimal("100")
        private val VITEZA_MAXIMA_LUNG_KM_H = BigDecimal("150")
        /** D-371 (2026-10-26): peste aceasta viteza medie, preluarea isi pierde minutele si km (real masurat: max 62,64). */
        private val VITEZA_MAXIMA_PRELUARE_KM_H = BigDecimal("65")
        private val MINUTE_SEGMENT_LUNG = BigDecimal("30")
        private val TOLERANTA_ROTUNJIRE_MIN = BigDecimal("0.5")
        private val MINUTE_PE_ORA = BigDecimal("60")
    }
}
