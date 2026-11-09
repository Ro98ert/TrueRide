package app.trueride.core.model

import java.lang.reflect.Modifier
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * R2 din Poarta 0-bis (D-027): contractul G2 nu are niciun camp de text liber.
 *
 * De ce test si nu regula scrisa: invariantul 4 ("parserul nu extrage adrese
 * si nume") trebuie sa fie o proprietate a TIPULUI. Un camp `String` e o usa
 * prin care o adresa poate trece de la parser la card fara sa se opreasca
 * nimic. Testul pica in ziua in care cineva deschide usa, nu la review.
 */
class ContractFaraTextLiberTest {

    /** Control pozitiv: daca detectorul NU vede campurile astea, testul R2 e orb. */
    @Suppress("unused")
    private class CuTextLiber(
        val pret: BigDecimal?,
        val eticheta: String?,
        val note: List<String>,
    )

    @Test
    fun `detectorul prinde un camp String si unul care contine String`() {
        assertEquals(listOf("eticheta", "note"), campuriCuText(CuTextLiber::class.java, permise = emptySet()))
    }

    @Test
    fun `ParsedOffer nu are niciun camp de text liber`() {
        // `currency` e String, dar validat ISO 4217 in constructor — vezi testele de mai jos.
        assertEquals(emptyList<String>(), campuriCuText(ParsedOffer::class.java, permise = setOf("currency")))
    }

    @Test
    fun `currency accepta un cod ISO 4217`() {
        assertEquals("RON", ParsedOffer(currency = "RON").currency)
    }

    @Test
    fun `currency lipsa ramane null`() {
        assertEquals(null, ParsedOffer().currency)
    }

    @Test
    fun `currency refuza text liber`() {
        assertFailsWith<IllegalArgumentException> { ParsedOffer(currency = "Strada Lalelelor 3") }
        assertFailsWith<IllegalArgumentException> { ParsedOffer(currency = "ron") }
        assertFailsWith<IllegalArgumentException> { ParsedOffer(currency = "RONX") }
    }
}

/**
 * Numele campurilor al caror tip contine `String` sau `CharSequence`, inclusiv
 * in parametri generici (`List<String>`, `Map<String, _>`).
 *
 * `genericType.typeName` si nu `type`: `type` ar vedea `java.util.List` si ar
 * rata `String`-ul din interior. Reflectie Java, nu `kotlin-reflect`:
 * `core-model` nu primeste dependente (build.gradle.kts).
 */
private fun campuriCuText(clasa: Class<*>, permise: Set<String>): List<String> =
    clasa.declaredFields
        .filterNot { Modifier.isStatic(it.modifiers) || it.isSynthetic }
        .filter { camp ->
            val tip = camp.genericType.typeName
            "java.lang.String" in tip || "java.lang.CharSequence" in tip
        }
        .map { it.name }
        .filterNot { it in permise }
        .sorted()
