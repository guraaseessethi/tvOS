package app.lumen

import app.lumen.ecp.EcpParse
import app.lumen.ecp.RokuApp
import app.lumen.ecp.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** ECP responses in the shapes the Hisense Roku TV (OS 16) returns. */
class EcpParseTest {
    @Test fun parsesAppsAndInputsAndSkipsLumen() {
        val xml = """<?xml version="1.0" encoding="UTF-8" ?>
            <apps>
              <app id="tvinput.hdmi1" type="tvin" version="1.0.0">PlayStation 5</app>
              <app id="12" type="appl" version="5.2.1">Netflix</app>
              <app id="837" type="appl" version="2.21.1">YouTube</app>
              <app id="dev" type="appl" version="1.0.31">Lumen</app>
              <app id="2213" type="appl" version="1.0">Roku Media Player &amp; Music</app>
            </apps>"""
        assertEquals(
            listOf(
                RokuApp("tvinput.hdmi1", "tvin", "PlayStation 5"),
                RokuApp("12", "appl", "Netflix"),
                RokuApp("837", "appl", "YouTube"),
                RokuApp("2213", "appl", "Roku Media Player & Music"),
            ),
            EcpParse.parseApps(xml),
        )
    }

    @Test fun badXmlGivesNoApps() {
        assertEquals(emptyList<RokuApp>(), EcpParse.parseApps("<apps><app id=\"12\""))
    }

    @Test fun flattensDeviceInfo() {
        val m = EcpParse.flat(
            """<?xml version="1.0" encoding="UTF-8" ?>
            <device-info>
              <udn>abc</udn>
              <serial-number>X01234</serial-number>
              <model-name>6Series-50</model-name>
              <user-device-name>Living Room TV</user-device-name>
              <is-tv>true</is-tv>
              <power-mode>DisplayOff</power-mode>
            </device-info>""",
        )
        assertEquals("X01234", m["serial-number"])
        assertEquals("Living Room TV", m["user-device-name"])
        assertEquals("true", m["is-tv"])
        assertEquals("DisplayOff", m["power-mode"])
    }

    @Test fun recognisesWhatsOnScreen() {
        assertEquals(Screen.Lumen, EcpParse.screenOf("""<active-app><app id="dev" type="appl" version="1.0.31">Lumen</app></active-app>"""))
        assertEquals(Screen.Home, EcpParse.screenOf("""<active-app><app>Roku</app></active-app>"""))
        assertEquals(Screen.Home, EcpParse.screenOf("""<active-app><app id="562859" type="home">Home</app></active-app>"""))
        assertEquals(Screen.Other, EcpParse.screenOf("""<active-app><app id="12" type="appl">Netflix</app></active-app>"""))
        assertEquals(Screen.Other, EcpParse.screenOf("""<active-app><app id="tvinput.hdmi1" type="tvin">PS5</app></active-app>"""))
        assertNull(EcpParse.screenOf(""))
    }

    @Test fun buildsInputQueries() {
        assertEquals("", EcpParse.query(emptyMap()))
        assertEquals("?theme=%7B%22theme%22%3A%22snow%22%7D", EcpParse.query(mapOf("theme" to """{"theme":"snow"}""")))
        assertEquals("?a=hello%20world&b=1", EcpParse.query(linkedMapOf("a" to "hello world", "b" to "1")))
    }
}
