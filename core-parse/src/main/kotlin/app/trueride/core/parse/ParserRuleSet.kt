package app.trueride.core.parse

import app.trueride.core.model.DistanceUnit
import app.trueride.core.model.PriceBasis
import java.math.BigDecimal

enum class Platforma { ANDROID, IOS }

/**
 * Regulile unei aplicatii de sofer, ca DATE (CLAUDE.md §5): pattern-uri ca siruri,
 * nu Regex compilate, ca setul sa poata fi servit remote in v1 (V2) fara cod nou.
 *
 * - `sumaPattern`: grupul 1 = semn optional (+, -, −; adaos la castig sau scazamant,
 *   niciodata pretul cursei), grupul 2 = suma;
 * - `segmentAncoraPattern`: recunoaste o linie de segment (preluare/cursa), chiar
 *   daca cifrele ei nu se pot citi — ordinea segmentelor nu se strica;
 * - `minutePattern`, `distantaPattern`: grupul 1 = numarul;
 * - `numarSegmente`: cate linii-ancora sunt asteptate (Uber RO = 2, preluare + cursa).
 *   Daca numarul gasit difera, motorul nu ghiceste care e care — toate campurile de
 *   segment devin null (invariantul 6).
 * - `pretInaltimeMinimaRelativa`: pretul trebuie scris de cel putin atatea ori mai
 *   inalt decat mediana liniilor care nu sunt sume. Relativ, nu in pixeli, ca sa
 *   nu depinda de densitatea ecranului.
 * - `pretFormaPattern`: forma pe care trebuie s-o aiba INTREG numarul sumei alese ca
 *   pret (grupul 2 din `sumaPattern`). Altfel pretul e necunoscut, nu alt candidat.
 *   Se aplica doar pretului: bonusurile cu semn raman recunoscute de `sumaPattern`.
 * - `corectiiCifre` (D-122..D-124): litere pe care OCR-ul le pune in locul cifrelor (ex. `l`/`I`
 *   -> 1, `O`/`o` -> 0), la minute, km si pret. Un numar cu o astfel de litera lipita in stanga nu
 *   e citire directa. Corectia porneste DOAR cand citirea directa esueaza, cu acelasi tipar largit
 *   la aceste litere, si DOAR pe un token nelipit de alta litera sau cifra; campul corectat
 *   primeste incredere sub 1. Gol = fara corectie.
 * - `pretCereSegment` (D-130): fara niciun rand-ancora de segment pe ecran nu e o oferta, deci nicio suma
 *   nu e pret (`ANCORE_LIPSA`). Pe ecranul principal Uber castigul zilei e singura suma mare (F-020).
 *   Un singur rand-ancora (al doilea ilizibil) pastreaza pretul. `false` = pretul nu depinde de segmente.
 */
data class ReguliOferta(
    val currency: String,
    val priceBasis: PriceBasis,
    val unitateDistanta: DistanceUnit,
    val sumaPattern: String,
    val segmentAncoraPattern: String,
    val minutePattern: String,
    val distantaPattern: String,
    val numarSegmente: Int,
    val pretInaltimeMinimaRelativa: BigDecimal,
    val pretFormaPattern: String,
    val corectiiCifre: Map<String, String> = emptyMap(),
    val pretCereSegment: Boolean = false,
)

/**
 * Spec §5.7. `fetchedAt` intra odata cu regulile servite remote (V2).
 *
 * `pretFormaDupaRecitirePattern` (D-299): forma in plus pe care o poate avea pretul DOAR pe o linie marcata
 * `LinieOcr.recitita` (lantul a citit-o inca o data, marita, cu aceleasi cifre). Null = nicio forma in plus
 * (Bolt; Uber EN si Uber RO o au, D-299 si D-305). Optional: o aplicatie care nu-l cunoaste ramane pe `pretFormaPattern` (pret necunoscut, nu gresit).
 *
 * `maxMinutePreluare` (D-315, P-sim-3): cel mai mare numar de minute pe care il poate avea PRELUAREA (primul segment). Peste
 * el minutele preluarii sunt necunoscute (invariantul 6), restul campurilor raman. De ce: pe 963 oferte reale complete
 * maximul preluarii e 25 min (Uber RO 19, Uber EN 25, Bolt 19), deci 100+ e o cifra dublata de OCR („25” -> „225”), iar
 * 99 e de peste 3x extremul real. NU se aplica cursei (exista curse reale de 111-112 min). Aceeasi valoare pe cele 3 seturi,
 * fiindca limita vine din ce e preluarea, nu din aplicatie. Un set o poate schimba; null = fara garda.
 *
 * `minVitezaCursaKmH` (D-315, P-sim-7, val7-bolt-viteza-min): viteza medie MINIMA (km/h) a CURSEI; sub ea km cursei sunt
 * necunoscuti (invariantul 6), simetric cu limita superioara D-148. De ce: o cifra pierduta din km da un km plauzibil dar
 * prea mic fata de minute. Null = fara limita (implicit; doar Bolt o seteaza). Nu se aplica preluarii.
 *
 * `respingeKmCursaZero` (D-315, val8-uber-ro-km-cursa-zero): cursa cu minute > 0 si exact 0 km nu exista; 0.0 vine din OCR
 * gresit, deci km cursei sunt necunoscuti (invariantul 6), nu zero. Implicit false (doar Uber RO il seteaza). Nu se aplica preluarii.
 *
 * `minLeiPeKm` / `maxLeiPeKm` (D-329.1): banda pretului pe km totali (preluare + cursa). In afara ei pretul e necunoscut
 * (`PRET_IMPLAUZIBIL`, invariantul 6): o cifra dublata sau pierduta la pret. Banda = [min/2; max x 2] din lei/km pe ofertele
 * reale complete ale setului, rotunjita in afara la 2 zecimale. Null = fara limita pe capatul respectiv.
 */
data class ParserRuleSet(
    val version: String,
    val platform: Platforma,
    val locale: String,
    val minAppVersion: String,
    val reguli: ReguliOferta,
    val pretFormaDupaRecitirePattern: String? = null,
    val maxMinutePreluare: BigDecimal? = BigDecimal("99"),
    val minVitezaCursaKmH: BigDecimal? = null,
    val respingeKmCursaZero: Boolean = false,
    val minLeiPeKm: BigDecimal? = null,
    val maxLeiPeKm: BigDecimal? = null,
)
