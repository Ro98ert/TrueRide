package app.trueride.core.parse

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClasificatorEcranCursaTest {

    private val set = ReguliEcranCursa("1.0.0", "0.1.0", "ro-RO", listOf("""\bSEMNA\b""", """\bSEMNB\b"""), listOf("""\bSEMNX\b"""))

    private fun linii(vararg t: String) = t.mapIndexed { i, s -> LinieOcr(s, Cutie(0, i * 10, 100, i * 10 + 8)) }

    @Test
    fun `toate semnaturile prezente inseamna ecran de drum`() {
        assertTrue(ClasificatorEcranCursa(listOf(set)).esteDrumSprePasager(linii("SEMNA", "12 min", "SEMNB")))
    }

    @Test
    fun `o semnatura lipsa nu numara`() {
        assertFalse(ClasificatorEcranCursa(listOf(set)).esteDrumSprePasager(linii("SEMNA")))
    }

    @Test
    fun `o excludere anuleaza potrivirea`() {
        assertFalse(ClasificatorEcranCursa(listOf(set)).esteDrumSprePasager(linii("SEMNA", "SEMNB", "SEMNX")))
    }

    @Test
    fun `lista goala nu e ecran de drum`() {
        assertFalse(ClasificatorEcranCursa(listOf(set)).esteDrumSprePasager(emptyList()))
    }

    @Test
    fun `un set fara semnaturi nu potriveste nimic`() {
        assertFalse(ClasificatorEcranCursa(listOf(set.copy(semnaturi = emptyList()))).esteDrumSprePasager(linii("SEMNA")))
    }
}
