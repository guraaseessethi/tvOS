package app.lumen.ecp

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/** Finds Rokus with SSDP M-SEARCH, ST roku:ecp (spec 15.2). */
class SsdpDiscovery(private val context: Context, private val ecp: EcpClient) {

    suspend fun search(timeoutMs: Int = 3000): List<FoundTv> {
        val ips = withContext(Dispatchers.IO) {
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val lock = wifi.createMulticastLock("lumen-ssdp").apply { setReferenceCounted(false); acquire() }
            val found = linkedSetOf<String>()
            try {
                DatagramSocket().use { s ->
                    s.soTimeout = 400
                    val msg = ("M-SEARCH * HTTP/1.1\r\nHost: 239.255.255.250:1900\r\n" +
                        "Man: \"ssdp:discover\"\r\nST: roku:ecp\r\nMX: 2\r\n\r\n").toByteArray()
                    val group = InetAddress.getByName("239.255.255.250")
                    repeat(2) { s.send(DatagramPacket(msg, msg.size, group, 1900)) }
                    val buf = ByteArray(2048)
                    val end = System.currentTimeMillis() + timeoutMs
                    while (System.currentTimeMillis() < end) {
                        try {
                            val p = DatagramPacket(buf, buf.size)
                            s.receive(p)
                            val text = String(p.data, 0, p.length)
                            val loc = Regex("(?im)^location:\\s*http://([0-9.]+):8060").find(text)
                            found += loc?.groupValues?.get(1) ?: p.address.hostAddress.orEmpty()
                        } catch (_: SocketTimeoutException) {
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                lock.release()
            }
            found.filter { it.isNotEmpty() }
        }
        return ips.mapNotNull { ip -> ecp.deviceInfo(ip)?.let { FoundTv(ip, it.name) } }
    }
}
