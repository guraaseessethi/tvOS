package app.lumen

import app.lumen.ecp.RokuApp
import app.lumen.zeus.Action
import app.lumen.zeus.ZeusRules
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 9.2: the clients ignore actions with unknown types or IDs, whatever the model says. */
class ZeusRulesTest {
    private val apps = listOf(RokuApp("12", "appl", "Netflix"), RokuApp("tvinput.hdmi1", "tvin", "HDMI 1"))

    private fun clean(json: String) = ZeusRules.clean(JSONObject(json), apps)

    @Test fun keepsValidActions() {
        val a = clean(
            """{"reply":"Opening Netflix.","actions":[
                {"type":"launch_app","appId":"12"},
                {"type":"switch_input","inputId":"tvinput.hdmi1"},
                {"type":"set_sleep_timer","minutes":30},
                {"type":"set_theme","theme":"irises"},
                {"type":"power","state":"off"}
              ],"chips":["Go home"]}""",
        )
        assertEquals(
            listOf(
                Action("launch_app", appId = "12"),
                Action("switch_input", inputId = "tvinput.hdmi1"),
                Action("set_sleep_timer", minutes = 30),
                Action("set_theme", theme = "irises"),
                Action("power", state = "off"),
            ),
            a.actions,
        )
        assertEquals("Opening Netflix.", a.reply)
        assertEquals(listOf("Go home"), a.chips)
    }

    @Test fun dropsInventedIdsAndBadValues() {
        val a = clean(
            """{"reply":"x","actions":[
                {"type":"launch_app","appId":"999"},
                {"type":"switch_input","inputId":"tvinput.hdmi9"},
                {"type":"launch_app","appId":"tvinput.hdmi1"},
                {"type":"set_sleep_timer","minutes":2},
                {"type":"set_sleep_timer","minutes":500},
                {"type":"set_theme","theme":"neon"},
                {"type":"keypress","key":"PowerOff"},
                {"type":"factory_reset"},
                {"type":"power","state":"maybe"}
              ],"chips":[]}""",
        )
        assertTrue(a.actions.toString(), a.actions.isEmpty())
    }

    @Test fun clampsKeypressRepeat() {
        val a = clean("""{"reply":"x","actions":[{"type":"keypress","key":"VolumeUp","repeat":50},{"type":"keypress","key":"VolumeDown","repeat":0}],"chips":[]}""")
        assertEquals(listOf(10, 1), a.actions.map { it.repeat })
    }

    @Test fun trimsReplyChipsAndMessages() {
        val long = "word ".repeat(100)
        val a = clean(
            """{"reply":"  Hello   there  ","actions":[{"type":"show_message","text":"$long"}],
               "chips":["one","two","three","four","five","a chip that is far too long to fit on screen"]}""",
        )
        assertEquals("Hello there", a.reply)
        assertEquals(4, a.chips.size)
        assertEquals(80, a.actions.single().text!!.length)
    }

    @Test fun emptyReplyBecomesDone() {
        assertEquals("Done.", clean("""{"reply":"","actions":[],"chips":[]}""").reply)
    }

    @Test fun answerRoundTripsToTheTvShape() {
        val a = clean("""{"reply":"Hi","actions":[{"type":"keypress","key":"Play"}],"chips":["Go home"]}""")
        val json = a.toJson()
        assertEquals("Hi", json.getString("reply"))
        assertEquals("keypress", json.getJSONArray("actions").getJSONObject(0).getString("type"))
        assertEquals(1, json.getJSONArray("actions").getJSONObject(0).getInt("repeat"))
        assertEquals("Go home", json.getJSONArray("chips").getString(0))
    }

    @Test fun readsJsonWrappedInFencesOrWords() {
        val fenced = "```json\n{\"reply\":\"Opening Netflix.\",\"actions\":[{\"type\":\"launch_app\",\"appId\":\"12\"}],\"chips\":[]}\n```"
        val a = ZeusRules.parse(fenced, apps)
        assertEquals("Opening Netflix.", a.reply)
        assertEquals(listOf(Action("launch_app", appId = "12")), a.actions)
        assertEquals("Hi.", ZeusRules.parse("Sure! {\"reply\":\"Hi.\",\"actions\":[],\"chips\":[]} Hope that helps.", apps).reply)
    }

    @Test fun plainTextBecomesTheReply() {
        val a = ZeusRules.parse("I can't see what's playing, but I can open an app for you.", apps)
        assertEquals("I can't see what's playing, but I can open an app for you.", a.reply)
        assertTrue(a.actions.isEmpty())
    }

    @Test fun brokenJsonFallsBackToText() {
        assertEquals("{\"reply\": \"Opening", ZeusRules.parse("{\"reply\": \"Opening", apps).reply)
    }
}
