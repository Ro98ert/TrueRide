package app.trueride.core.parse

import app.trueride.core.model.DistanceUnit
import app.trueride.core.model.PriceBasis
import java.math.BigDecimal

/**
 * Uber Driver, Romania — derivat din cele 8 fixture-uri din 2026-09-23 (D-031, D-032).
 * Pret "9,47 RON" (virgula zecimala), segmente "4 min. (1.5 km)" (punct zecimal),
 * bonus "+38 RON" (exclus de la pret prin "+").
 */
val UBER_RO_V1 = ParserRuleSet(
    version = "1.19.0",
    platform = Platforma.ANDROID,
    locale = "ro-RO",
    // Cand regulile vor fi servite remote (V2): setul 1.3.0 cere prima versiune a aplicatiei
    // care intelege `pretFormaPattern` (D-080); o aplicatie mai veche nu-l poate aplica.
    // Setul 1.4.0 adauga harta de corectii la minute (D-122); 1.5.0 o aplica si la km si pret (D-123, D-124);
    // 1.6.0 cere un rand de segment pentru pret (D-130); 1.8.0 tipar km tolerant (D-288.4);
    // 1.9.0 spatiu neintrerupt inainte de „RON” (D-299.4);
    // 1.10.0 pret intreg de exact 2 cifre („25 RON”), doar pe linia recitita de lant (D-305, ca Uber EN, D-299);
    // 1.11.0 garda D-312: pretul si minutele nu se citesc ca sufix al unui numar lipit de o litera, de „h” sau de alt grup de cifre;
    // 1.12.0 garda D-315 (P-sim-10): km au cel mult o zecimala (vezi `distantaPattern`);
    // 1.13.0 D-315 val B: „:” ca separator zecimal in km („3:4 km”), doar intre cifre, in paranteza, urmat de km;
    // 1.14.0 D-315 val8 (val8-uber-ro-km-cursa-zero): cursa cu minute > 0 si 0 km = km necunoscuti (vezi `respingeKmCursaZero`);
    // 1.15.0 D-315 val10 (val10-uber-viteza-min): cursa sub 5 km/h = km cu o cifra pierduta (min real 11,3 km/h, N=98);
    // 1.16.0 D-315 val13 (val13-prag-pret-uber-ro): pragul inaltimii pretului 1,8 -> 1,6 (vezi `pretInaltimeMinimaRelativa`);
    // 1.17.0 D-329.1: banda lei/km pe km totali (vezi `minLeiPeKm`/`maxLeiPeKm`);
    // 1.19.0 o litera confundabila (l, I, |) lipita dupa ultima cifra a km se ignora (vezi `distantaPattern`).
    minAppVersion = "0.1.0",
    reguli = ReguliOferta(
        currency = "RON",
        priceBasis = PriceBasis.NET,
        unitateDistanta = DistanceUnit.KM,
        // Granite pe ambele capete (nicio cifra/virgula/punct lipit inainte sau dupa)
        // ca "9, 47" sau "9,475" sa nu se citeasca drept "47"/"475" — un numar taiat
        // de restul liniei e OCR ambiguu, nu un pret (I1). D-299.4: inainte de „RON” poate fi spatiu obisnuit sau
        // spatiu neintrerupt (U+00A0, pe care `\s` din regex-ul Java nu-l prinde, deci scris explicit).
        // D-312 (P-sint-1): nici o litera lipita direct de prima cifra (dupa semn): din „S42,50 RON” nu se citeste „2,50”
        // (litera poate fi o cifra inlocuita de OCR). Literele din `corectiiCifre` se corecteaza pe calea largita
        // (tokenul incepe la litera), deci garda nu le atinge. Pusa DUPA semn: „S+8,50 RON” ramane bonus, nu pret.
        sumaPattern = """(?<![\d.,][\s\u00A0]?)([+\-−]\s*)?(?<!\p{L})(\d+(?:[.,]\d{1,2})?)(?![\d.,])[ \u00A0]RON""",
        // Cere paranteza distantei pe aceeasi linie — un banner cu "min." fara
        // paranteza (ex. "Ajungi in 5 min.") nu e un segment (I2, I3). F-031 (1.7.0): Uber scrie
        // uneori "min," inaintea parantezei; paranteza ramane obligatorie.
        segmentAncoraPattern = """min[.,]?\s*\(""",
        // Nu depinde de spatiu intre cifra si "min" (I2). Un numar precedat de "h "
        // (ora amestecata cu minutele, ex. "1 h 5 min.") nu e minutul cursei (I3).
        // Pe calea de corectie, `cuLitere` largeste si `\d` din lookbehind la literele hartii: „o 5 min” devine
        // necunoscut acolo (directia sigura, inv. 6).
        // D-312 (P-sint-3/4/5): numarul nu are voie sa fie sufixul altui numar: nici o LITERA SINGURA lipita in stanga
        // („S7 min” = cifra mancata de OCR, nu „7”), nici cifra sau „cifra + spatiu” inainte („2 h 48 min” -> 8,
        // „5 3 min” -> 3). Un cuvant lipit ramane citire directa („La5 min.”, F-025: 3 oferte reale); pe pret nu
        // exista o astfel de dovada, deci acolo orice litera lipita da necunoscut.
        // Intervalul „3-5 min” ramane citit ca inainte (liniuta nu e litera, cifra sau spatiu).
        minutePattern = """(?<!h\s)(?<!(?<!\p{L})\p{L})(?<!\d[\s\u00A0]?)(\d+)\s*min""",
        // D-125: nu dupa o a doua paranteza. Pe SM-A137F, „(l2.5 km)” a iesit „((2.5 km)”, iar fara
        // aceasta granita se citea sigur 2.5 in loc de 12.5. Paranteza dubla = km necunoscuti.
        // D-288.4 (1.8.0): spatiul dinaintea unitatii si „)” final sunt optionale, iar unitatea poate fi una dintre
        // citirile gresite cunoscute (lista fixa: Km, kn, krn, „k m”). Paranteza de deschidere ramane obligatorie,
        // iar dupa unitate nu urmeaza litera („kmh”, „kms”, „knots” nu sunt km): niciun numar de pe alta linie
        // sau fara unitate nu devine distanta. Fiecare unitate din lista are test (TiparKmTolerantTest).
        // D-315 (P-sim-10): cel mult O zecimala dupa separator. Uber afiseaza km cu o singura zecimala sau intregi (0 din 128 de
        // km reali din fixture-uri au 2+ zecimale, numarat pe fixtures/uber*); un punct zecimal mutat la stanga („12.5” ->
        // „1.25”, „4.5” -> „0.45”) da mereu 2 zecimale, deci e necunoscut, nu un km mai mic. Un km real (sub 1, peste 100,
        // intreg) are cel mult o zecimala si ramane citit.
        // D-315 val B (1.13.0): Uber a afisat o data „(3:4 km)”: „:” e separator zecimal NUMAI intre doua cifre, urmat de
        // „km”/„Km” (lookahead), deci nu ora „12:30”, nu „12:3 min”, nu eticheta „▢:” din fata; tot cel mult o zecimala.
        // 1.19.0 (teren, APK 22): OCR a lipit „l” dupa ultima cifra („(7.2l km)”, pe ecran „7.2 km”). O singura litera din
        // [lI|] intre ultima cifra si unitate se ignora, NU devine „1” (ecranul nu are cifra in plus). Cu litera din harta
        // lipita in dreapta, `urmatDeHarta` respinge citirea directa si calea largita reface acelasi numar, fara litera.
        distantaPattern = """(?<!\()\((\d+(?:[.,]\d|:\d(?=[lI|]?\s?(?:km|Km)(?!\p{L})))?)[lI|]?\s?(?:km|Km|kn|krn|k m)(?!\p{L})""",
        numarSegmente = 2,
        // D-315 val13 (1.16.0): pe arhiva reala (223 preturi Uber RO distincte) raportul pretului merge pana la 1,808; 8 oferte
        // distincte (26 fisiere) cu pret real la 1,711 si 1,770 ieseau SUB_PRAG, toate confirmate de o captura geamana completa
        // cu acelasi pret; pe cadrele cu segmente, nicio alta suma fara semn in [1,6; 1,8). 1,6 lasa ~6% marja sub 1,711.
        pretInaltimeMinimaRelativa = BigDecimal("1.6"),
        // Uber RO scrie pretul mereu cu doi bani ("9,47 RON"; 9 din 9 fixture-uri). Fara
        // ei, OCR-ul a pierdut sau a stricat separatorul: "12,34" -> "1234" (x100) sau
        // "12'34" -> 34. Pretul devine necunoscut (D-080, F-011).
        pretFormaPattern = """\d+[.,]\d{2}""",
        // D-122..D-124 (F-015 H2, F-019, F-025): pe fontul Uber in tema intunecata OCR-ul citeste „1” ca l/I
        // si „0” ca O/o — la minute, la km si la pret.
        // Harta e data: Bolt sau Uber EN pot avea alte confuzii fara cod nou.
        corectiiCifre = mapOf("l" to "1", "I" to "1", "O" to "0", "o" to "0"),
        // D-130 (F-020): fara niciun rand „min. (” pe ecran nu e oferta. Pe telefonul de test 2 castigul zilei (singura suma,
        // sus) a devenit pret in 32 din 41 de fixture-uri, iar pe OPPO 7 din 62 de ecrane fara ancora au dat card.
        pretCereSegment = true,
    ),
    // D-305: Uber RO afiseaza uneori suma intreaga („25 RON”); caseta OCR nu arata nimic dupa cifre. Exact 2 cifre:
    // un intreg de 2 cifre nu poate iesi dintr-o virgula pierduta („12,34” -> „1234”). Doar pe o linie recitita de
    // lant, cu aceleasi cifre, departe de marginea zonei (RecitirePret); fara recitire ramane FORMA_PRET.
    pretFormaDupaRecitirePattern = """\d{2}""",
    respingeKmCursaZero = true,
    minVitezaCursaKmH = BigDecimal("5"),
    // D-329.1: pe 99 de oferte Uber RO reale distincte (2026-10-23) lei/km merge de la 0,672 la 6,228; [min/2; max x 2]
    // rotunjit in afara. Leave-one-out: 0/99 in afara.
    minLeiPeKm = BigDecimal("0.33"),
    maxLeiPeKm = BigDecimal("12.46"),
)
