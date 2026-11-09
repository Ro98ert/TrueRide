package app.trueride.core.parse

/** Ca ParserRuleSet: date, nu cod (CLAUDE.md §5), ca setul sa poata fi servit remote. */
data class ReguliEcranCursa(
    val version: String,
    val minAppVersion: String,
    val locale: String,
    /** Toate trebuie sa apara, pe linii diferite sau nu. */
    val semnaturi: List<String>,
    /** Oricare gasit anuleaza potrivirea. */
    val excluderi: List<String> = emptyList(),
)

// Oferta peste ecranul de drum nu e drum: altfel un refuz din timpul drumului ar confirma oferta.
private val EXCLUDERI_OFERTA = listOf("""(?i)\baccept""")

/**
 * Ecranele reale din Sentry (D-396): pe citirile de oferta niciuna nu se potriveste. Bolt EN n-are set: 5 citiri
 * nu ajung si fara fixture-uri reale detectorul nu se scrie, deci o cursa acolo nu se numara.
 * Spatiul poate fi U+00A0 pe noduri, pe care `\s` din regex-ul Java nu il prinde (ca in ReguliBoltRo).
 */
val UBER_RO_CURSA_V1 = ReguliEcranCursa(
    "1.0.0", "0.1.0", "ro-RO", listOf("""(?i)\bpreia[\s ]+pasagerul\b"""), EXCLUDERI_OFERTA,
)

val UBER_EN_CURSA_V1 = ReguliEcranCursa(
    "1.0.0", "0.1.0", "en-US", listOf("""(?i)\bpicking[\s ]+up\b"""), EXCLUDERI_OFERTA,
)

val BOLT_RO_CURSA_V1 = ReguliEcranCursa(
    "1.0.0", "0.1.0", "ro-RO", listOf("""(?i)\bam[\s ]+ajuns\b"""), EXCLUDERI_OFERTA,
)
