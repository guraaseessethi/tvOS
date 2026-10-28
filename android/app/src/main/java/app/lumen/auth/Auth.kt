package app.lumen.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class Account(val uid: String, val name: String, val email: String)

/** Firebase accounts: email + password, or Google (spec addition, owner request 28 Oct). */
object Auth {

    /** False until app/google-services.json is added and the app rebuilt. */
    fun ready(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private val auth get() = FirebaseAuth.getInstance()

    fun current(context: Context): Account? {
        if (!ready(context)) return null
        val user = auth.currentUser ?: return null
        val s = state.value
        return if (s != null && s.uid == user.uid) s else user.toAccount()
    }

    private val state = MutableStateFlow<Account?>(null)
    private var listening = false

    /**
     * The signed-in account. Firebase's own listener doesn't fire when the name changes, which is
     * why a new account used to show its email prefix as the name: we refresh this ourselves after
     * every profile change, and fall back to the name saved in Firestore.
     */
    fun accounts(context: Context): StateFlow<Account?> {
        if (!ready(context)) return state
        if (!listening) {
            listening = true
            state.value = auth.currentUser?.toAccount()
            auth.addAuthStateListener { a -> state.value = a.currentUser?.toAccount() }
        }
        return state
    }

    private fun refresh() {
        state.value = auth.currentUser?.toAccount()
    }

    private fun FirebaseUser.toAccount(): Account {
        val known = displayName?.trim()?.takeIf { it.isNotEmpty() }
        if (known == null) fillNameFromProfile(uid)
        return Account(uid, known ?: "", email ?: "")
    }

    /** No display name yet (e.g. right after sign-up): use the one saved in users/{uid}. */
    private fun fillNameFromProfile(uid: String) {
        FirebaseFirestore.getInstance().collection("users").document(uid).get().addOnSuccessListener { doc ->
            val n = doc.getString("name")?.trim().orEmpty()
            val cur = state.value
            if (n.isNotEmpty() && cur != null && cur.uid == uid && cur.name.isEmpty()) state.value = cur.copy(name = n)
        }
    }

    /** Returns null on success, or a sentence to show. */
    suspend fun signUp(name: String, email: String, password: String, confirm: String): String? {
        val n = name.trim()
        val e = email.trim()
        when {
            n.isEmpty() -> return "Tell us your name."
            !android.util.Patterns.EMAIL_ADDRESS.matcher(e).matches() -> return "That email doesn't look right."
            password.length < 8 -> return "Use at least 8 characters for the password."
            password != confirm -> return "The passwords don't match."
        }
        return guard {
            val user = auth.createUserWithEmailAndPassword(e, password).await().user ?: error("no user")
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(n.take(24)).build()).await()
            user.reload().await()
            saveProfile(user.uid, n.take(24), e)
            refresh()
        }
    }

    suspend fun signIn(email: String, password: String): String? {
        if (email.isBlank() || password.isEmpty()) return "Enter your email and password."
        return guard { auth.signInWithEmailAndPassword(email.trim(), password).await() }
    }

    suspend fun resetPassword(email: String): String? {
        if (email.isBlank()) return "Enter your email first, then tap Forgot password."
        return guard { auth.sendPasswordResetEmail(email.trim()).await() }
    }

    /**
     * Google sign-in through Credential Manager. Needs the Google provider on in Firebase and this
     * phone's SHA-1 in the Firebase Android app, then a fresh google-services.json.
     */
    suspend fun signInWithGoogle(activityContext: Context): String? {
        val clientIdRes = activityContext.resources.getIdentifier("default_web_client_id", "string", activityContext.packageName)
        if (clientIdRes == 0) {
            return "Google sign-in isn't set up yet: turn on the Google provider in Firebase > Authentication, " +
                "then download google-services.json again and rebuild."
        }
        val clientId = activityContext.getString(clientIdRes)
        val manager = CredentialManager.create(activityContext)
        val google = (try {
            // The Google button flow first (shows every account on the phone)…
            val button = GetSignInWithGoogleOption.Builder(clientId).build()
            googleCredential(manager.getCredential(activityContext, GetCredentialRequest.Builder().addCredentialOption(button).build()).credential)
        } catch (e: GetCredentialCancellationException) {
            return ""
        } catch (e: NoCredentialException) {
            // …then the bottom sheet, for phones where the button flow has nothing to offer.
            try {
                val sheet = GetGoogleIdOption.Builder().setFilterByAuthorizedAccounts(false).setServerClientId(clientId).build()
                googleCredential(manager.getCredential(activityContext, GetCredentialRequest.Builder().addCredentialOption(sheet).build()).credential)
            } catch (e2: GetCredentialCancellationException) {
                return ""
            } catch (e2: GetCredentialException) {
                return googleError(e2)
            }
        } catch (e: GetCredentialException) {
            return googleError(e)
        }) ?: return "Google didn't return an account."

        return guard {
            val user = auth.signInWithCredential(GoogleAuthProvider.getCredential(google.idToken, null)).await().user
                ?: error("no user")
            // Use the person's Google name, never their email, for Zeus and the leaderboard.
            val name = listOfNotNull(user.displayName, google.displayName, google.givenName)
                .map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.contains("@") } ?: "Player"
            if (user.displayName.isNullOrBlank() || user.displayName!!.contains("@")) {
                user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.take(24)).build()).await()
                user.reload().await()
            }
            saveProfile(user.uid, (user.displayName ?: name).take(24), user.email ?: google.id)
            refresh()
        }
    }

    private fun googleCredential(cred: Credential): GoogleIdTokenCredential? =
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            GoogleIdTokenCredential.createFrom(cred.data)
        } else {
            null
        }

    private fun googleError(e: GetCredentialException): String {
        val msg = e.message.orEmpty()
        return when {
            msg.contains("28444") || msg.contains("Developer console", true) || msg.contains("10:") ->
                "Google sign-in needs this phone's SHA-1 in Firebase (Project settings > Your apps > Add fingerprint). " +
                    "Then download google-services.json again and rebuild."
            e is NoCredentialException -> "No Google account on this phone. Add one in Android Settings > Passwords & accounts."
            else -> "Google sign-in didn't work: ${msg.ifEmpty { e.type }}"
        }
    }

    fun signOut(context: Context) {
        if (!ready(context)) return
        auth.signOut()
        state.value = null
        // Forget the chosen Google account too, so the next sign-in can pick another one.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            runCatching { CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest()) }
        }
    }

    suspend fun rename(context: Context, name: String): String? {
        if (!ready(context)) return "Firebase isn't set up."
        if (name.isBlank()) return "Tell us your name."
        val user = auth.currentUser ?: return "You're signed out."
        return guard {
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim().take(24)).build()).await()
            user.reload().await()
            saveProfile(user.uid, name.trim().take(24), user.email ?: "")
            refresh()
        }
    }

    private suspend fun saveProfile(uid: String, name: String, email: String) {
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .set(mapOf("name" to name, "email" to email, "updatedAt" to FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
    }

    private suspend fun guard(block: suspend () -> Unit): String? = try {
        block(); null
    } catch (e: FirebaseAuthException) {
        when (e.errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> "That email already has an account. Sign in instead."
            "ERROR_INVALID_EMAIL" -> "That email doesn't look right."
            "ERROR_WEAK_PASSWORD" -> "Pick a stronger password."
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" -> "Email or password is wrong."
            "ERROR_TOO_MANY_REQUESTS" -> "Too many tries. Wait a minute and try again."
            "ERROR_OPERATION_NOT_ALLOWED" -> "This sign-in method is off. Turn it on in Firebase > Authentication."
            else -> e.message ?: "Something went wrong."
        }
    } catch (e: Exception) {
        e.message ?: "Something went wrong. Check your internet and try again."
    }
}
