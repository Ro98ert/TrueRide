package app.trueride.core.model

import java.math.BigDecimal

/**
 * Ce inseamna "merita" pentru acest sofer anume (spec 5.3, D-082).
 *
 * Fiecare indicator are doua praguri. `minPer*` e pragul "Bun": de la el
 * verdictul e Bun (D-038: un minim atins e indeplinit). `excellentPer*` e pragul
 * "Excelent". `minPer*` pastreaza numele vechi: sensul lui nu s-a schimbat.
 *
 * `null` pe pragul Bun = indicatorul nu participa la verdict, oricare ar fi
 * pragul Excelent. `null` pe Excelent = indicatorul da cel mult Bun (D-082.7).
 * Toate pragurile Bun null = verdictul e dezactivat, cardul arata doar cifre
 * (spec E4, starea `gol`).
 */
data class Thresholds(
    val minPerHour: BigDecimal? = null,
    val minPerDistance: BigDecimal? = null,
    val minPerMinute: BigDecimal? = null,
    val excellentPerHour: BigDecimal? = null,
    val excellentPerDistance: BigDecimal? = null,
    val excellentPerMinute: BigDecimal? = null,
) {
    /** True daca cel putin un prag Bun e activ; un Excelent singur nu da verdict. */
    val isAnyActive: Boolean
        get() = minPerHour != null || minPerDistance != null || minPerMinute != null
}
