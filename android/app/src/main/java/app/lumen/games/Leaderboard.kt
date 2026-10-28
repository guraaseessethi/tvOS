package app.lumen.games

import android.content.Context
import app.lumen.auth.Auth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await

/** The three TV games. ids match GameKit's kitInit ids on the Roku. */
enum class Game(val id: String, val title: String, val blurb: String) {
    Bird("bird", "Flutter", "One-button flier through painted columns"),
    Dog("dog", "Pup Run", "Jump the haystacks in the sunflower field"),
    Catch("catch", "Star Catcher", "Catch the falling stars");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id }
    }
}

data class Entry(val uid: String, val name: String, val score: Int)

data class Submitted(val best: Int, val rank: Int, val newBest: Boolean)

/**
 * Firestore layout: leaderboard/{game}/scores/{uid} = {name, score, updatedAt}.
 * One row per player holding their best; a lower score never overwrites it.
 */
object Leaderboard {
    private fun scores(game: Game) =
        FirebaseFirestore.getInstance().collection("leaderboard").document(game.id).collection("scores")

    fun top(context: Context, game: Game, limit: Long = 25): Flow<List<Entry>> {
        if (!Auth.ready(context)) return flowOf(emptyList())
        return callbackFlow {
            val reg = scores(game).orderBy("score", Query.Direction.DESCENDING).limit(limit)
                .addSnapshotListener { snap, _ ->
                    if (snap != null) trySend(snap.documents.map {
                        Entry(it.id, it.getString("name") ?: "Player", (it.getLong("score") ?: 0).toInt())
                    })
                }
            awaitClose { reg.remove() }
        }
    }

    /** Saves a finished run for the signed-in player. Returns null when signed out or offline. */
    suspend fun submit(context: Context, game: Game, points: Int): Submitted? {
        val me = Auth.current(context) ?: return null
        if (points <= 0) return null
        return runCatching {
            val db = FirebaseFirestore.getInstance()
            val ref = scores(game).document(me.uid)
            val best = db.runTransaction { tx ->
                val old = (tx.get(ref).getLong("score") ?: 0).toInt()
                if (points > old) {
                    tx.set(ref, mapOf("name" to me.name, "score" to points, "updatedAt" to FieldValue.serverTimestamp()))
                    points
                } else old
            }.await()
            val above = scores(game).whereGreaterThan("score", best).count().get(AggregateSource.SERVER).await().count
            Submitted(best = best, rank = above.toInt() + 1, newBest = best == points)
        }.getOrNull()
    }

    suspend fun topOnce(context: Context, game: Game, limit: Long): List<Entry> {
        if (!Auth.ready(context)) return emptyList()
        return runCatching {
            scores(game).orderBy("score", Query.Direction.DESCENDING).limit(limit).get().await().documents.map {
                Entry(it.id, it.getString("name") ?: "Player", (it.getLong("score") ?: 0).toInt())
            }
        }.getOrDefault(emptyList())
    }

    suspend fun myBest(context: Context, game: Game): Int {
        val me = Auth.current(context) ?: return 0
        return runCatching { (scores(game).document(me.uid).get().await().getLong("score") ?: 0).toInt() }.getOrDefault(0)
    }
}
