package app.trueride.core.parse.sintetic

/**
 * Perturbarile cu corespondent REAL (D-315): forma apare in randurile de oferta din arhiva Sentry a ultimelor 7 zile
 * (2026-10-22, numar > 0 la cel putin o (platforma, limba)) sau in familiile reale din
 * jurnalul B1 (privat). Restul perturbarilor sunt inventate: plauzibile,
 * dar nevazute la soferi in fereastra aceea. `km_punct_pierdut_*` nu intra aici: un „D km” intreg nu dovedeste
 * ca punctul s-a pierdut (pe Bolt textul vine exact din noduri). Lista se actualizeaza cand apare o forma noua.
 */
internal object PerturbariReale {
    val IDS: Set<String> = setOf(
        "baza",
        // pret
        "nbsp_pret", "pret_fara_separator", "pret_separator_alternativ", "pret_cifra_in_coada", "pret_intreg_nerecitit",
        "pret_intreg_recitit", "pret_trei_cifre", "pret_mii_separator", "litera_mapata_pret", "litera_in_loc_de_separator",
        // segment
        "km_cu_virgula", "km_intreg", "km_sub_unu", "km_peste_100", "min_fara_punct", "min_fara_spatiu", "minute_peste_60",
        "litera_mapata_minute", "litera_minute_ultima_cifra", "litera_nemapata_minute_prima_cifra",
        "litera_nemapata_minute_alta_cifra", "litera_mapata_km", "litera_nemapata_km", "paranteza_trunchiata_masca",
        // randuri in plus si oferta in ansamblu
        "rand_interval_minute", "rand_DD_min_text_sub_cursa", "suma_mica_in_plus", "bonus_mai_mic", "bonus_mai_inalt",
        "bonus_en_per_unitate", "taiat_segment_fara_ancora", "a_treia_ancora", "doua_oferte", "pret_sub_segmente",
        "ordine_amestecata",
        // Bolt
        "bolt_nbsp_eliminat", "bolt_nbsp_spatiu_obisnuit", "bolt_pret_lipit_de_masca",
    )
}
