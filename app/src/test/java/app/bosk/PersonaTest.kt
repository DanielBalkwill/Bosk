package app.bosk

import app.bosk.core.ai.Persona
import app.bosk.core.ai.PersonaStyle
import app.bosk.core.ai.Verbosity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonaTest {

    @Test
    fun defaultPromptNamesCompanionAndForbidsEmoji() {
        val p = Persona().systemPrompt()
        assertTrue(p.startsWith("You are Bosk,"))
        assertTrue(p.contains(PersonaStyle.COMPANION.instruction))
        assertTrue(p.contains(Verbosity.BALANCED.instruction))
        assertTrue(p.contains("Do not use emoji."))
    }

    @Test
    fun customNameIsUsed() {
        assertTrue(Persona(name = "Sage").systemPrompt().startsWith("You are Sage,"))
    }

    @Test
    fun blankNameFallsBackToBosk() {
        assertTrue(Persona(name = "   ").systemPrompt().startsWith("You are Bosk,"))
    }

    @Test
    fun styleInstructionSwitches() {
        val socratic = Persona(style = PersonaStyle.SOCRATIC).systemPrompt()
        assertTrue(socratic.contains(PersonaStyle.SOCRATIC.instruction))
        assertFalse(socratic.contains(PersonaStyle.COMPANION.instruction))
    }

    @Test
    fun customStyleOmitsBuiltInInstructionButKeepsCustomText() {
        val p = Persona(style = PersonaStyle.CUSTOM, customInstruction = "Reply only in haiku.").systemPrompt()
        assertTrue(p.contains("Reply only in haiku."))
        // CUSTOM has a blank built-in instruction, so no other style's directive leaks in.
        assertFalse(p.contains(PersonaStyle.COMPANION.instruction))
    }

    @Test
    fun emojiToggleRemovesProhibition() {
        assertFalse(Persona(useEmoji = true).systemPrompt().contains("Do not use emoji."))
    }

    @Test
    fun verbosityDirectiveIncluded() {
        assertTrue(Persona(verbosity = Verbosity.BRIEF).systemPrompt().contains(Verbosity.BRIEF.instruction))
        assertTrue(Persona(verbosity = Verbosity.DETAILED).systemPrompt().contains(Verbosity.DETAILED.instruction))
    }

    @Test
    fun encodeDecodeRoundTrips() {
        val original = Persona(
            name = "Coach K",
            style = PersonaStyle.COACH,
            verbosity = Verbosity.DETAILED,
            customInstruction = "Use sports analogies.",
            useEmoji = true,
        )
        assertEquals(original, Persona.decode(Persona.encode(original)))
    }

    @Test
    fun decodeBlankOrGarbageIsDefault() {
        assertEquals(Persona(), Persona.decode(""))
        assertEquals(Persona(), Persona.decode("not json"))
        assertEquals(Persona(), Persona.decode(null))
    }
}
