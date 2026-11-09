package app.trueride.core.parse

import app.trueride.core.model.DistanceUnit
import app.trueride.core.model.PriceBasis
import java.math.BigDecimal

/**
 * Uber Driver in engleza, cu km (en-GB, en-RO) — 6 oferte reale din 2026-10-05 (F5, D-131.3).
 * Pret "RON9.47" (moneda lipita INAINTE, punct zecimal), segmente "4 mins (1.5 km)".
 * en-US arata mile (D-135.5, amanat): distanta nu se potriveste, deci km raman necunoscuti (inv. 6).
 * 1.1.0 (F-033, D-143): pragul de inaltime al pretului e propriu setului (1,5), nu mostenit din RO.
 * 1.2.0 (D-288.4): tiparul km tolerant al RO (spatiu si „)” optionale, confuziile unitatii) vine prin copy().
 * 1.3.0 (D-299): pretul intreg de exact 2 cifre (`pretFormaDupaRecitirePattern`), doar pe linia recitita marita de lant;
 * spatiul neintrerupt (U+00A0) dupa „RON” in `sumaPattern`.
 * 1.4.0 (D-312): garda pe `minutePattern`: minutele nu se citesc ca sufix al unui numar lipit de o litera, de „h” sau de alt
 * grup de cifre (P-sint-3/4/5). Pretul nu are garda: moneda „RON” lipita in stanga cifrelor face parte din tipar.
 * 1.5.0 (D-315, pret_separator_alternativ): virgula in locul punctului zecimal se citeste ca pret (`pretFormaPattern`).
 * 1.6.0 (D-315, P-sim-10): km cu cel mult o zecimala (`distantaPattern` mostenit din RO): „1.25 km” e un punct mutat, nu un km.
 * 1.7.0 (D-315, val9): `distantaPattern` propriu, fara ramura „:” a RO 1.13.0 („3:4 km”): pe EN nu exista dovada (0 din 4 fisiere).
 * 1.8.0 (D-315, val10-uber-viteza-min): `minVitezaCursaKmH` = 5, explicit (nu se mosteneste din RO): pe oferte reale EN viteza cursei are minimul 14,7 km/h.
 * 1.9.0 (D-329.1): banda lei/km proprie: pe 37 de oferte EN reale distincte (2026-10-23) lei/km 1,359-5,389; [min/2; max x 2]
 * rotunjit in afara; leave-one-out 0/37.
 * 1.11.0 (F-054): o litera confundabila (l, I, |) lipita dupa ultima cifra a km se ignora (ca la RO 1.19.0, vezi `distantaPattern`).
 */
val UBER_EN_V1 = ParserRuleSet(
    version = "1.11.0",
    platform = Platforma.ANDROID,
    locale = "en",
    // D-299: `pretFormaDupaRecitirePattern` e optional, deci ramane 0.1.0: o aplicatie care nu-l cunoaste il ignora
    // si pastreaza FORMA_PRET (necunoscut, nu gresit). Niciun camp vechi nu isi schimba sensul; `sumaPattern` e doar
    // un regex mai larg.
    minAppVersion = "0.1.0",
    reguli = UBER_RO_V1.reguli.copy(
        // Grupul 1 = semn, grupul 2 = suma (motorul). Granitele ca la RO: o cifra lipita dupa = OCR ambiguu.
        // D-299.9: dupa „RON” poate fi spatiu sau spatiu neintrerupt; `\s` din regex-ul Java nu prinde U+00A0.
        sumaPattern = """([+\-−]\s*)?RON[\s\u00A0]?(\d+(?:[.,]\d{1,2})?)(?![\d.,])""",
        // „mins (” / „min (”: paranteza cu distanta ramane obligatorie (I2, I3). Fara punct: „min. (” e forma RO,
        // iar setul EN nu trebuie sa citeasca segmentele RO.
        segmentAncoraPattern = """mins?\s*\(""",
        // D-312: ca la RO, numarul nu e sufixul altui numar (litera singura lipita, „h ” + cifre, „D D”).
        minutePattern = """(?<!h\s)(?<!(?<!\p{L})\p{L})(?<!\d[\s\u00A0]?)(\d+)\s*mins?""",
        // Pretul EN are punct zecimal si doi bani; „RON1234” (punctul pierdut de OCR) e necunoscut, nu ghicit.
        // D-315: si virgula („RON12,47”); exact 2 cifre dupa ea (mii ar avea 3), deci nu e separator de mii.
        pretFormaPattern = """\d+[.,]\d{2}""",
        // F-033, D-143: pragul EN e CAMP PROPRIU, nu mostenit din RO prin copy(): o recalibrare RO nu-l muta.
        // Pe telefonul de test 2 (en-RO, font 1,15) pretul e la 1,74-2,36 x mediana (CalibrarePragPretTest pe
        // fixtures/uber-en-prag), nu 2,28-2,67 ca la RO: 12 din 26 de preturi reale ieseau SUB_PRAG cu 1,8.
        // Nicio alta suma fara semn nu ia locul pretului la vreun prag candidat; cadrele taiate stau la 0,54-0,70.
        pretInaltimeMinimaRelativa = BigDecimal("1.5"),
        // D-315 val9: „:” zecimal la km e tolerat NUMAI pe RO (RO 1.13.0): toate cele 4 fisiere reale cu „D:D km” sunt ro-RO,
        // pe EN 0. Camp propriu, nu mostenit (ca F-033/D-143): o schimbare RO nu muta EN. Cel mult o zecimala (P-sim-10).
        distantaPattern = """(?<!\()\((\d+(?:[.,]\d)?)[lI|]?\s?(?:km|Km|kn|krn|k m)(?!\p{L})""",
    ),
    // D-299: Uber EN afiseaza uneori suma intreaga („RON25”); caseta OCR nu arata nimic dupa cifre. Exact 2 cifre:
    // un intreg de 2 cifre nu poate iesi dintr-un punct pierdut („RON9.47” -> „RON947”). Se aplica doar pe o linie
    // recitita de lant, cu aceleasi cifre, departe de marginea zonei (RecitirePret).
    pretFormaDupaRecitirePattern = """\d{2}""",
    minVitezaCursaKmH = BigDecimal("5"),
    minLeiPeKm = BigDecimal("0.67"),
    maxLeiPeKm = BigDecimal("10.78"),
)
