package app.lumen.ecp

/** One entry from /query/apps. type is "appl" for apps, "tvin" for inputs. */
data class RokuApp(val id: String, val type: String, val name: String) {
    val isInput get() = type == "tvin"
}

/** The bits of /query/device-info Lumen uses (spec 15.3). */
data class DeviceInfo(
    val name: String,
    val model: String,
    val serial: String,
    val powerMode: String,
    val isTv: Boolean,
) {
    val isOn get() = powerMode == "PowerOn"
}

/** What's on screen: "lumen", "home" (Roku's own home), "other" (an app or input). */
enum class Screen { Lumen, Home, Other }

data class FoundTv(val ip: String, val name: String)
