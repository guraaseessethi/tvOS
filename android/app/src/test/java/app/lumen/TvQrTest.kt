package app.lumen

import app.lumen.ecp.TvQr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvQrTest {
    @Test fun readsTheTvsPairingCode() {
        assertEquals(TvQr.Target("192.168.2.33", "Living Room TV"), TvQr.parse("lumen://connect?ip=192.168.2.33&name=Living%20Room%20TV"))
        assertEquals(TvQr.Target("10.0.0.5", null), TvQr.parse("lumen://connect?ip=10.0.0.5"))
    }

    @Test fun acceptsPlainTvAddresses() {
        assertEquals(TvQr.Target("192.168.2.33", null), TvQr.parse("http://192.168.2.33:8060/"))
        assertEquals(TvQr.Target("192.168.2.33", null), TvQr.parse(" 192.168.2.33 "))
    }

    @Test fun rejectsOtherCodes() {
        assertNull(TvQr.parse("https://example.com/menu"))
        assertNull(TvQr.parse("lumen://connect?ip=999.1.1.1"))
        assertNull(TvQr.parse("lumen://connect?name=TV"))
        // The old Mac helper's pairing page is not a TV address.
        assertNull(TvQr.parse("http://192.168.2.21:8977/phone?code=abc"))
        assertNull(TvQr.parse(""))
        assertNull(TvQr.parse(null))
    }
}
