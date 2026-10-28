package app.lumen

import app.lumen.zeus.WakePhrase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WakePhraseTest {
    @Test fun findsTheWakePhraseAndTheCommandAfterIt() {
        assertEquals("open YouTube", WakePhrase.after("hey Zeus open YouTube"))
        assertEquals("turn it up", WakePhrase.after("Hey, Zeus, turn it up."))
        assertEquals("", WakePhrase.after("hey zeus"))
        assertEquals("", WakePhrase.after("Hey Zeus?"))
    }

    @Test fun acceptsWhatSpeechRecognitionUsuallyHears() {
        listOf("hey zoos", "hey seuss", "hay zeus", "okay zeus", "hey juice", "hi zues").forEach {
            assertEquals(it, "", WakePhrase.after(it))
        }
        assertEquals("pause", WakePhrase.after("um hey Seuss pause"))
    }

    @Test fun ignoresEverydayTalk() {
        assertNull(WakePhrase.after("open youtube"))
        assertNull(WakePhrase.after("the zeus statue was huge"))
        assertNull(WakePhrase.after("hey there"))
        assertNull(WakePhrase.after("heyzeus"))
        assertNull(WakePhrase.after(""))
    }
}
