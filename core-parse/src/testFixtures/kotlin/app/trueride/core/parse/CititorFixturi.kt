package app.trueride.core.parse

import java.io.File

/**
 * Citeste formatul propriu al fixture-urilor (FixturaJson: un obiect-linie pe rand).
 * Fara biblioteca JSON — formatul e al nostru si fix, ca in FixturiDinRepoTest din app.
 * Garda ca nu pierde linii e un test separat (FixturiUberTest).
 */
object CititorFixturi {

    private val obiectLinie = Regex(
        """\{ "text": "((?:[^"\\]|\\.)*)", "stanga": (-?\d+), "sus": (-?\d+), "dreapta": (-?\d+), "jos": (-?\d+) \}""",
    )
    private val campEticheta = Regex(""""(\w+)": (null|"([^"]*)")""")
    private val escape = Regex("""\\u([0-9a-fA-F]{4})|\\(["\\/])""")

    fun director(aplicatie: String = "uber"): File =
        File(requireNotNull(System.getProperty("fixtures.dir")) { "fixtures.dir lipseste din build" }, aplicatie)

    fun linii(fisier: File): List<LinieOcr> =
        obiectLinie.findAll(fisier.readText(Charsets.UTF_8)).map { m ->
            val (text, stanga, sus, dreapta, jos) = m.destructured
            LinieOcr(dezescapeaza(text), Cutie(stanga.toInt(), sus.toInt(), dreapta.toInt(), jos.toInt()))
        }.toList()

    /** Cheie -> valoare; `null` in JSON devine null. */
    fun eticheta(fisier: File): Map<String, String?> =
        campEticheta.findAll(fisier.readText(Charsets.UTF_8)).associate { m ->
            m.groupValues[1] to m.groups[3]?.value
        }

    private fun dezescapeaza(s: String): String =
        escape.replace(s) { m ->
            m.groups[1]?.let { it.value.toInt(16).toChar().toString() } ?: m.groupValues[2]
        }
}
