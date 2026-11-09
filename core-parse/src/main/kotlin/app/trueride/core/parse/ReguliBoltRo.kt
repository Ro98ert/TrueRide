package app.trueride.core.parse

import app.trueride.core.model.DistanceUnit
import app.trueride.core.model.PriceBasis
import java.math.BigDecimal

/**
 * Bolt Driver, Romania, citit din nodurile Accessibility (F-029, F3): 4 oferte si 2 ecrane fara oferta
 * de pe telefonul lui Sofer 1 (2026-10-05), etichete verificate independent (D-032).
 * Pret "21,40 lei (▢, ▢ ▢)" (virgula zecimala, lei dupa numar, uneori lipit de alt text in acelasi nod),
 * segmente "5 min • 2.3 km" (punct zecimal, km uneori fara zecimale). Intre numar si unitate nodurile
 * pun un spatiu neintrerupt (U+00A0), pe care `\s` din regex-ul Java nu il prinde: tiparele il cer explicit.
 */
val BOLT_RO_V1 = ParserRuleSet(
    version = "1.6.0",
    platform = Platforma.ANDROID,
    locale = "ro-RO",
    minAppVersion = "0.1.0",
    reguli = ReguliOferta(
        currency = "RON",
        // Bolt afiseaza pretul brut, inainte de comision; D-135.6: se afiseaza cum e citit.
        priceBasis = PriceBasis.GROSS,
        unitateDistanta = DistanceUnit.KM,
        // 1.2.0 (D-315, P-sim-1): nici o litera lipita direct de prima cifra (dupa semn), ca la Uber RO (D-312): din „S42,50 lei”
        // nu se citeste „2,50” (litera poate fi o cifra inlocuita de OCR; ea nu se ghiceste, `corectiiCifre` e gol). Dupa semn: „S+8,50 lei” ramane bonus.
        sumaPattern = """(?<![\d.,][\s\u00A0]?)([+\-−][\s\u00A0]*)?(?<!\p{L})(\d+(?:[.,]\d{1,2})?)(?![\d.,])[\s\u00A0]*lei""",
        // Randul de segment are punctul-mijloc intre minute si km; un banner cu „min” fara el nu e segment.
        segmentAncoraPattern = """min[\s\u00A0]*[•·]""",
        // D-312 (P-sint-4/5): numarul nu e sufixul altui numar („2 h 48 min” -> 8, „5 3 min” -> 3) si nu are o litera singura lipita (ca la RO, un cuvant lipit nu blocheaza).
        minutePattern = """(?<!h\s)(?<!(?<!\p{L})\p{L})(?<!\d[\s\u00A0]?)(\d+)[\s\u00A0]*min""",
        // 1.3.0 (D-315, P-sim-10): cel mult O zecimala („2.3 km”, „5 km”); „0.23 km” e un punct zecimal mutat, deci necunoscut.
        distantaPattern = """[•·][\s\u00A0]*(\d+(?:[.,]\d)?)[\s\u00A0]*km""",
        numarSegmente = 2,
        // Nodurile dau cutia nodului, nu a textului: pretul are 1,39 x mediana (nod simplu) sau 2,98 x (nod lipit
        // de alt text). 1,2 il desparte de restul nodurilor; ecranele fara oferta le opresc pretCereSegment si forma.
        pretInaltimeMinimaRelativa = BigDecimal("1.2"),
        // Pretul Bolt are mereu doi bani ("21,40"); "400 lei" fara bani e alta suma (bonus, tinta).
        pretFormaPattern = """\d+[.,]\d{2}""",
        // Nodurile au textul exact, fara confuziile OCR de litere.
        corectiiCifre = emptyMap(),
        // D-130: fara rand de segment (castigul zilei, sus) nu e oferta.
        pretCereSegment = true,
    ),
    // 1.4.0 (D-315, P-sim-7, val7-bolt-viteza-min): cursa sub 7 km/h = km cu o cifra pierduta. Pe 530 oferte Bolt reale
    // viteza cursei are minimul 14,0 (p1 16,2): 7 e 2x sub minim, deci nicio cursa reala nu e atinsa.
    minVitezaCursaKmH = BigDecimal("7"),
    // 1.5.0 (D-329.1): banda lei/km pe km totali. Pe 527 oferte Bolt reale distincte (arhiva + lotul de noapte, 2026-10-23)
    // lei/km merge de la 0,473 la 6,853; [min/2; max x 2] rotunjit in afara. Leave-one-out: 1/527 in afara (minimul extrem).
    minLeiPeKm = BigDecimal("0.23"),
    maxLeiPeKm = BigDecimal("13.71"),
)

/**
 * D-394: SOLUL regulilor pentru a doua citire (OCR), nu pentru card. Textul OCR al unui rand Bolt e „N min X km” fara bulina
 * (50 din 63 de linii masurate), pe cand nodurile o au; `BOLT_RO_V1` o cere si ar da segmente necunoscute. Aici bulina e
 * optionala: ancora e „min” urmat de bulina/punct/cratima sau direct de cifra, iar distanta nu cere bulina, dar cere ca
 * numarul sa nu fie lipit de o litera (garda D-315: litera poate fi o cifra inlocuita de OCR). `corectiiCifre` ramane gol.
 * Versiunea si regulile cardului raman cele din [BOLT_RO_V1]; setul nu intra in nicio harta de reguli.
 */
val BOLT_RO_OCR_VERIFICARE = BOLT_RO_V1.copy(
    reguli = BOLT_RO_V1.reguli.copy(
        segmentAncoraPattern = """min[\s\u00A0]*(?:[•·.\-–]|(?=\d))""",
        distantaPattern = """(?<![\d.,\p{L}])(\d+(?:[.,]\d)?)[\s\u00A0]*km""",
    ),
)
