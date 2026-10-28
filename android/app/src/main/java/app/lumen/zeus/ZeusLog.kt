package app.lumen.zeus

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** One conversation shared by the phone and the TV, so Zeus remembers both and both screens show it. */
object ZeusLog {
    data class Exchange(
        val user: String,
        val reply: String,
        val receipts: List<String> = emptyList(),
        val fromTv: Boolean = false,
        val at: Long = System.currentTimeMillis(),
    )

    private val items = MutableStateFlow<List<Exchange>>(emptyList())
    val exchanges: StateFlow<List<Exchange>> = items

    fun add(e: Exchange) = items.update { (it + e).takeLast(20) }

    /** The last few turns, oldest first, for the model. */
    fun history(turns: Int = 6): List<Turn> =
        items.value.flatMap { listOf(Turn(false, it.user), Turn(true, it.reply)) }.takeLast(turns)

    /** True while the phone is listening, thinking or speaking, so "Hey Zeus" stays quiet. */
    val micBusy = MutableStateFlow(false)
}
