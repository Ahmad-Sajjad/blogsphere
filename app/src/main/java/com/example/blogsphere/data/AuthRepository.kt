package com.example.blogsphere.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * The single place that talks to Firebase for everything auth-related.
 *
 * Responsibilities:
 *  1. Sign up / sign in (email+password and Google) via **Firebase Authentication**.
 *  2. Store each user's profile in a **Cloud Firestore** document at `users/{uid}`.
 *  3. Load that profile into [DataStore.currentUser] so the rest of the (still in-memory)
 *     app keeps working unchanged.
 *
 * Every function that hits the network is a `suspend` function. Call them from a coroutine,
 * e.g. `lifecycleScope.launch { ... }`, and wrap them in try/catch to show errors.
 */
object AuthRepository {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    /** The `users` collection in Firestore. */
    private val usersCol get() = db.collection("users")

    /** Background scope for fire-and-forget writes (e.g. saving a bookmark toggle). */
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** True if a Firebase user session already exists (used by the splash screen). */
    val isLoggedIn: Boolean get() = auth.currentUser != null

    /** The current Firebase user id, or null if signed out. */
    val currentUid: String? get() = auth.currentUser?.uid

    // ---------------------------------------------------------------------------------------
    // Sign up / sign in
    // ---------------------------------------------------------------------------------------

    /**
     * Create a brand-new account with email + password, then write its profile document.
     * Returns the freshly created [User] (also set as [DataStore.currentUser]).
     *
     * Throws a [com.google.firebase.auth.FirebaseAuthException] on failure
     * (e.g. email already in use, weak password) — let the caller show the message.
     */
    suspend fun signUp(username: String, email: String, password: String, bio: String): User =
        withContext(Dispatchers.IO) {
            val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
            val uid = result.user!!.uid
            val profile = User(
                id = uid,
                username = username.trim(),
                email = email.trim(),
                bio = bio.trim(),
                // Convenience for the demo: registering the seeded admin email grants admin rights.
                isAdmin = email.trim().equals("admin@blog.com", ignoreCase = true)
            )
            usersCol.document(uid).set(profile.toMap()).await()
            applyToSession(profile)
            profile
        }

    /**
     * Sign in with an existing email + password, then load the profile into the session.
     * Throws on wrong credentials / no such user.
     */
    suspend fun signIn(email: String, password: String): User =
        withContext(Dispatchers.IO) {
            auth.signInWithEmailAndPassword(email.trim(), password).await()
            loadProfileIntoSession() ?: error("Signed in but profile document is missing.")
        }

    /**
     * Sign in with a Google account. [idToken] comes from the Google Sign-In flow in the UI.
     * Creates the profile document on the very first Google sign-in.
     */
    suspend fun signInWithGoogle(idToken: String): User =
        withContext(Dispatchers.IO) {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()
            val fbUser = result.user!!
            val uid = fbUser.uid

            val existing = usersCol.document(uid).get().await()
            if (!existing.exists()) {
                // First time this Google account signs in → build a profile from its Google data.
                val profile = User(
                    id = uid,
                    username = fbUser.displayName ?: (fbUser.email?.substringBefore("@") ?: "user"),
                    email = fbUser.email.orEmpty(),
                    avatarUri = fbUser.photoUrl?.toString()
                )
                usersCol.document(uid).set(profile.toMap()).await()
                applyToSession(profile)
                profile
            } else {
                loadProfileIntoSession()!!
            }
        }

    // ---------------------------------------------------------------------------------------
    // Session / profile
    // ---------------------------------------------------------------------------------------

    /**
     * Fetch `users/{uid}` for the currently signed-in Firebase user and push it into
     * [DataStore.currentUser]. Returns null if not signed in. Called on app start (splash)
     * and right after sign-in so the session survives app restarts.
     */
    suspend fun loadProfileIntoSession(): User? = withContext(Dispatchers.IO) {
        val uid = currentUid
        if (uid == null) {
            null
        } else {
            val snapshot = usersCol.document(uid).get().await()
            if (!snapshot.exists()) {
                null
            } else {
                val user = userFromSnapshot(uid, snapshot)
                applyToSession(user)
                user
            }
        }
    }

    /**
     * Persist the current in-memory user back to Firestore. Call this after changing the
     * profile, bookmarks, or joined groups so those changes survive a restart.
     */
    suspend fun saveCurrentUser() {
        val user = DataStore.currentUser ?: return
        usersCol.document(user.id).set(user.toMap(), SetOptions.merge()).await()
    }

    /**
     * Fire-and-forget version of [saveCurrentUser] for places that aren't coroutines
     * (RecyclerView adapters, click listeners). Errors are ignored — the in-memory copy
     * already reflects the change, this just persists it in the background.
     */
    fun saveCurrentUserAsync() {
        ioScope.launch { runCatching { saveCurrentUser() } }
    }

    /** Change the signed-in user's password (Firebase Auth, not stored in Firestore). */
    suspend fun changePassword(newPassword: String) {
        val fbUser = auth.currentUser ?: error("Not signed in.")
        fbUser.updatePassword(newPassword).await()
    }

    /** Send a "reset your password" email. Works even when signed out. */
    suspend fun sendPasswordReset(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /** Sign out of Firebase and clear the in-memory session. */
    fun signOut() {
        auth.signOut()
        DataStore.currentUser = null
    }

    // ---------------------------------------------------------------------------------------
    // Helpers — keep the bridge between Firestore docs and the in-memory User in one place
    // ---------------------------------------------------------------------------------------

    /** Set [DataStore.currentUser] and make sure the user also exists in the in-memory list. */
    private fun applyToSession(user: User) {
        DataStore.currentUser = user
        val idx = DataStore.users.indexOfFirst { it.id == user.id }
        if (idx >= 0) DataStore.users[idx] = user else DataStore.users.add(user)
    }

    /** Convert a [User] into the map of fields we store in Firestore. */
    private fun User.toMap(): Map<String, Any?> = mapOf(
        "username" to username,
        "email" to email,
        "bio" to bio,
        "avatarUri" to avatarUri,
        "isAdmin" to isAdmin,
        "joinedAt" to joinedAt,
        "joinedGroupIds" to joinedGroupIds.toList(),
        // Firestore has no Set type, so a Set is stored as a List.
        "bookmarkedBlogIds" to bookmarkedBlogIds.toList(),
        "following" to following.toList()
    )

    /** Rebuild a [User] from a Firestore document. */
    private fun userFromSnapshot(uid: String, doc: DocumentSnapshot): User {
        @Suppress("UNCHECKED_CAST")
        val groups = (doc.get("joinedGroupIds") as? List<String>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val bookmarks = (doc.get("bookmarkedBlogIds") as? List<String>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val following = (doc.get("following") as? List<String>) ?: emptyList()
        return User(
            id = uid,
            username = doc.getString("username").orEmpty(),
            email = doc.getString("email").orEmpty(),
            bio = doc.getString("bio").orEmpty(),
            avatarUri = doc.getString("avatarUri"),
            isAdmin = doc.getBoolean("isAdmin") ?: false,
            joinedAt = doc.getLong("joinedAt") ?: System.currentTimeMillis(),
            joinedGroupIds = groups.toMutableList(),
            bookmarkedBlogIds = bookmarks.toMutableSet(),
            following = following.toMutableList()
        )
    }
}
