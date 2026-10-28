package app.lumen

import app.lumen.ecp.RokuApp
import app.lumen.zeus.Action
import app.lumen.zeus.LocalIntents
import app.lumen.zeus.Zeus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 9.3: obvious commands run on the phone without the network. */
class LocalIntentsTest {
    private val apps = listOf(
        RokuApp("12", "appl", "Netflix"),
        RokuApp("837", "appl", "YouTube"),
        RokuApp("13", "appl", "Prime Video"),
        RokuApp("tvinput.hdmi1", "tvin", "HDMI 1"),
        RokuApp("tvinput.hdmi2", "tvin", "PlayStation 5"),
    )
    private val names = mapOf("tvinput.hdmi1" to "Apple TV")

    private fun actions(text: String): List<Action> {
        val answer = LocalIntents.match(text, apps, names)
        assertNotNull("no local match for '$text'", answer)
        return answer!!.actions
    }

    @Test fun volumeUpDefaultsToTwoSteps() {
        assertEquals(listOf(Action("keypress", key = "VolumeUp", repeat = 2)), actions("volume up"))
    }

    @Test fun volumeAmountsFollowTheSpec() {
        assertEquals(3, actions("turn the volume down a bit").single().repeat)
        assertEquals(8, actions("volume up a lot").single().repeat)
        assertEquals(5, actions("volume up 5").single().repeat)
        assertEquals(10, actions("volume up 40").single().repeat)
    }

    @Test fun louderAndQuieter() {
        assertEquals("VolumeUp", actions("louder").single().key)
        assertEquals("VolumeDown", actions("Make it quieter!").single().key)
    }

    @Test fun muteAndPause() {
        assertEquals("VolumeMute", actions("mute").single().key)
        assertEquals("Play", actions("pause").single().key)
        assertEquals("Play", actions("resume the movie").single().key)
    }

    @Test fun opensAppsByName() {
        assertEquals(Action("launch_app", appId = "837"), actions("open YouTube").single())
        assertEquals(Action("launch_app", appId = "13"), actions("put on prime video on the tv").single())
        assertEquals(Action("launch_app", appId = "12"), actions("Launch Netflix app").single())
    }

    @Test fun unknownAppFallsThroughToZeus() {
        assertNull(LocalIntents.match("open disney plus", apps, names))
    }

    @Test fun switchesInputsByCustomOrTvName() {
        assertEquals(Action("switch_input", inputId = "tvinput.hdmi1"), actions("switch to apple tv").single())
        assertEquals(Action("switch_input", inputId = "tvinput.hdmi2"), actions("switch to playstation").single())
        assertEquals(Action("switch_input", inputId = "tvinput.hdmi1"), actions("switch to hdmi 1").single())
    }

    @Test fun powerNeedsExplicitWords() {
        assertEquals(Action("power", state = "off"), actions("turn off the tv").single())
        assertEquals(Action("power", state = "on"), actions("turn the tv on").single())
        assertNull(LocalIntents.match("I'm going to sleep", apps, names))
    }

    @Test fun sleepTimers() {
        assertEquals(Action("set_sleep_timer", minutes = 30), actions("sleep in 30 minutes").single())
        assertEquals(Action("set_sleep_timer", minutes = 45), actions("sleep timer for forty five minutes").single())
        assertEquals(Action("set_sleep_timer", minutes = 120), actions("sleep in 2 hours").single())
        assertEquals(Action("set_sleep_timer", minutes = 30), actions("sleep in half an hour").single())
        assertEquals(Action("cancel_sleep_timer"), actions("cancel the sleep timer").single())
    }

    @Test fun sleepTimerOutOfRangeIsNotLocal() {
        assertNull(LocalIntents.match("sleep in 2 minutes", apps, names))
    }

    @Test fun homeOpensLumen() {
        assertEquals(Action("open_lumen"), actions("go home").single())
        assertEquals(Action("open_lumen"), actions("open lumen").single())
    }

    @Test fun freeTextGoesToZeus() {
        assertNull(LocalIntents.match("what should I watch tonight?", apps, names))
        assertNull(LocalIntents.match("paint me something sunny", apps, names))
        assertNull(LocalIntents.match("", apps, names))
    }

    @Test fun explainsWhatZeusCanDo() {
        listOf("what can you do", "What can you do?", "help", "what can I ask").forEach {
            val a = LocalIntents.match(it, apps, names)
            assertNotNull(it, a)
            assertEquals(Zeus.ABILITIES, a!!.reply)
            assertTrue(a.actions.isEmpty())
        }
    }

    @Test fun startsGames() {
        assertEquals(Action("start_game", game = "bird"), actions("play flutter").single())
        assertEquals(Action("start_game", game = "dog"), actions("let's play pup run").single())
        assertEquals(Action("start_game", game = "catch"), actions("start star catcher").single())
        assertEquals(Action("start_game", game = "bird"), actions("play a game").single())
    }

    @Test fun playAloneStillPauses() {
        assertEquals("Play", actions("play").single().key)
    }

    @Test fun timersAlarmsAndSearch() {
        assertEquals(Action("set_timer", seconds = 600), actions("set a timer for 10 minutes").single())
        assertEquals(Action("set_timer", seconds = 300), actions("5 minute timer").single())
        assertEquals(Action("cancel_timer"), actions("cancel the timer").single())
        assertEquals(Action("cancel_sleep_timer"), actions("cancel the sleep timer").single())
        assertEquals("set_alarm", actions("set an alarm for 7 am").single().type)
        assertEquals("07:00", actions("wake me up at 7 am").single().time)
        assertEquals(Action("cancel_alarm"), actions("cancel my alarm").single())
        assertEquals(Action("search", query = "stranger things", appId = "12"), actions("find stranger things on netflix").single())
        assertEquals(Action("search", query = "interstellar"), actions("search for interstellar").single())
        // "on the tv" isn't an app, so this is still just opening YouTube.
        assertEquals(Action("launch_app", appId = "837"), actions("play youtube on the tv").single())
    }
}
