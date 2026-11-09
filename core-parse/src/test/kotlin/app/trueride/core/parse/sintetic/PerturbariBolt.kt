package app.trueride.core.parse.sintetic

/**
 * Val 2, Bolt RO (nodurile Accessibility dau text exact, deci doar forme structurale): spatiul neintrerupt eliminat
 * sau inlocuit cu spatiu obisnuit, alt caracter in locul punctului-mijloc, randul „Ora” deasupra pretului (in
 * fixture-urile reale vine inaintea lui; lipsa lui e baza), pretul lipit de masca din acelasi nod.
 */
internal object PerturbariBolt {
    private val BOLT: Set<Familie> = setOf(Familie.BOLT_RO)

    val toate: List<Perturbare> = listOf(
        // Tiparele Bolt cer `[\s ]*`: „12,50lei”, „5min•” se citesc la fel ca cu NBSP.
        p("bolt_nbsp_eliminat", BOLT) { pl, _ ->
            pl.copy(pret = pl.pret.replace(NB, "")).cuSegmente { it.copy(spMin = "", spKm = "", sepAncora = "") }
        },
        p("bolt_nbsp_spatiu_obisnuit", BOLT) { pl, _ ->
            pl.copy(pret = pl.pret.replace(NB, " ")).cuSegmente { it.copy(spMin = " ", spKm = " ", sepAncora = " ") }
        },
        // `·` (U+00B7) e in tiparele Bolt (`[•·]`).
        p("bolt_bulina_punct_mijloc", BOLT) { pl, _ -> pl.cuSegmente { it.copy(bulina = "·") } },
        // Un separator pe care regulile nu-l cunosc = layout schimbat: ancora se pierde, iar fara ancora nu e oferta (D-130).
        p("bolt_bulina_liniuta", BOLT) { pl, r ->
            pl.cuSegmente { it.copy(bulina = listOf("-", "–").random(r)) }
                .segmenteOricare().seteaza(Camp.PRET to oricare(pl.v.pret))
        },
        p("bolt_ora_deasupra_pretului", BOLT) { pl, _ -> pl.copy(inainte = listOf("Oră") + pl.inainte) },
        p("bolt_pret_lipit_de_masca", BOLT) { pl, _ -> pl.copy(pret = pl.pret + "▢ ▢ ▢ ▢ ▢ ▢ ▢ ▢") },
    )
}
