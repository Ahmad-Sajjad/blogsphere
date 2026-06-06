package com.example.blogsphere.data

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Persists app content (users, groups, blogs, comments) in Cloud Firestore.
 *
 * Strategy: [DataStore] stays the in-memory cache the UI reads synchronously. This repository
 *  - [syncDown]: loads everything from Firestore into [DataStore] (and seeds Firestore once).
 *  - push/delete methods: fire-and-forget writes called from [DataStore] mutations so changes persist.
 *
 * Collections: `users`, `groups`, `blogs`, `comments` (comments are top-level with a `blogId`).
 */
object ContentRepository {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val usersCol get() = db.collection("users")
    private val groupsCol get() = db.collection("groups")
    private val blogsCol get() = db.collection("blogs")
    private val commentsCol get() = db.collection("comments")
    private val notificationsCol get() = db.collection("notifications")

    /** Background scope for write-through operations. */
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Active real-time snapshot listeners (empty when not listening). */
    private val listeners = mutableListOf<ListenerRegistration>()

    /** Listener for the signed-in user's notifications (unread badge). */
    private var notifListener: ListenerRegistration? = null

    // =======================================================================================
    // Load
    // =======================================================================================

    /**
     * Pull all content from Firestore into [DataStore]. On the very first run (empty `blogs`
     * collection) the demo data is written to Firestore first. Call before showing Home.
     */
    suspend fun syncDown() = withContext(Dispatchers.IO) {
        if (blogsCol.limit(1).get().await().isEmpty) {
            seedFirestore()
        }
        val users = usersCol.get().await().documents.mapNotNull { userFrom(it) }
        val groups = groupsCol.get().await().documents.mapNotNull { groupFrom(it) }
        val blogs = blogsCol.get().await().documents.mapNotNull { blogFrom(it) }
        val comments = commentsCol.get().await().documents.mapNotNull { commentFrom(it) }
        DataStore.replaceAll(users, groups, blogs, comments)
    }

    /** Write the demo users/groups/blogs to Firestore exactly once, in a single batch. */
    private suspend fun seedFirestore() {
        val seed = DataStore.buildSeed()
        val batch = db.batch()
        seed.users.forEach { batch.set(usersCol.document(it.id), userMap(it)) }
        seed.groups.forEach { batch.set(groupsCol.document(it.id), groupMap(it)) }
        seed.blogs.forEach { batch.set(blogsCol.document(it.id), blogMap(it)) }
        batch.commit().await()
    }

    // =======================================================================================
    // Real-time updates (live feed / likes / comments)
    // =======================================================================================

    /**
     * Start listening for changes to blogs, groups and comments. Each change updates the
     * matching slice of the [DataStore] cache and then calls [onChanged] (on the main thread)
     * so the UI can refresh. Idempotent — calling it twice does nothing.
     *
     * [onChanged] is supplied by the caller (e.g. HomeActivity) which broadcasts the existing
     * refresh actions; this keeps [ContentRepository] free of any Android Context.
     */
    fun startRealtime(onChanged: () -> Unit) {
        if (listeners.isNotEmpty()) return   // already listening

        listeners += blogsCol.addSnapshotListener { snap, _ ->
            if (snap != null) {
                DataStore.replaceBlogs(snap.documents.mapNotNull { blogFrom(it) })
                onChanged()
            }
        }
        listeners += groupsCol.addSnapshotListener { snap, _ ->
            if (snap != null) {
                DataStore.replaceGroups(snap.documents.mapNotNull { groupFrom(it) })
                onChanged()
            }
        }
        listeners += commentsCol.addSnapshotListener { snap, _ ->
            if (snap != null) {
                DataStore.replaceComments(snap.documents.mapNotNull { commentFrom(it) })
                onChanged()
            }
        }
    }

    /** Stop all real-time listeners (call when leaving the app / logging out). */
    fun stopRealtime() {
        listeners.forEach { it.remove() }
        listeners.clear()
        notifListener?.remove()
        notifListener = null
    }

    // =======================================================================================
    // Notifications (in-app)
    // =======================================================================================

    /**
     * Create a notification for [recipientId]. No-op if the recipient is the actor themselves
     * (you don't get notified about your own actions) or the recipient id is blank.
     */
    fun createNotification(
        recipientId: String,
        actorId: String,
        type: String,
        actorName: String,
        text: String,
        blogId: String? = null
    ) {
        if (recipientId.isBlank() || recipientId == actorId) return
        val n = Notification(
            id = db.collection("notifications").document().id,
            recipientId = recipientId,
            type = type,
            actorName = actorName,
            text = text,
            blogId = blogId
        )
        fire { notificationsCol.document(n.id).set(notifMap(n)).await() }
    }

    /** Load this user's notifications, newest first (sorted client-side, no index needed). */
    suspend fun loadNotifications(uid: String): List<Notification> = withContext(Dispatchers.IO) {
        notificationsCol.whereEqualTo("recipientId", uid).get().await()
            .documents.mapNotNull { notifFrom(it) }
            .sortedByDescending { it.timestamp }
    }

    /** Mark all of this user's notifications as read. */
    fun markNotificationsRead(uid: String) = fire {
        val snap = notificationsCol.whereEqualTo("recipientId", uid).get().await()
        val batch = db.batch()
        snap.documents.forEach { batch.update(it.reference, "read", true) }
        if (snap.documents.isNotEmpty()) batch.commit().await()
    }

    /**
     * Listen for changes to this user's notifications and report the unread count via
     * [onUnread] (on the main thread). Used to show the toolbar badge.
     */
    fun startNotificationsListener(uid: String, onUnread: (Int) -> Unit) {
        notifListener?.remove()
        notifListener = notificationsCol.whereEqualTo("recipientId", uid)
            .addSnapshotListener { snap, _ ->
                if (snap != null) {
                    val unread = snap.documents.count { it.getBoolean("read") != true }
                    onUnread(unread)
                }
            }
    }

    // =======================================================================================
    // Write-through (fire-and-forget — the in-memory cache already reflects the change)
    // =======================================================================================

    fun pushUser(u: User) = fire { usersCol.document(u.id).set(userMap(u)).await() }

    fun pushBlog(b: Blog) = fire { blogsCol.document(b.id).set(blogMap(b)).await() }

    fun deleteBlog(id: String) = fire {
        blogsCol.document(id).delete().await()
        deleteWhere(commentsCol, "blogId", id)
    }

    fun pushGroup(g: Group) = fire { groupsCol.document(g.id).set(groupMap(g)).await() }

    fun deleteGroup(id: String) = fire {
        groupsCol.document(id).delete().await()
        // Cascade: remove blogs in this group and their comments.
        val groupBlogs = blogsCol.whereEqualTo("groupId", id).get().await()
        groupBlogs.documents.forEach { doc ->
            deleteWhere(commentsCol, "blogId", doc.id)
            doc.reference.delete()
        }
    }

    fun deleteUser(id: String) = fire {
        usersCol.document(id).delete().await()
        // Cascade: remove this user's blogs (and their comments) and their comments.
        val authored = blogsCol.whereEqualTo("authorId", id).get().await()
        authored.documents.forEach { doc ->
            deleteWhere(commentsCol, "blogId", doc.id)
            doc.reference.delete()
        }
        deleteWhere(commentsCol, "authorId", id)
    }

    fun pushComment(c: Comment) = fire { commentsCol.document(c.id).set(commentMap(c)).await() }

    fun deleteComment(id: String) = fire { commentsCol.document(id).delete().await() }

    // ---- internals --------------------------------------------------------

    private fun fire(block: suspend () -> Unit) {
        io.launch { runCatching { block() } }
    }

    /** Delete every doc in [col] where [field] == [value]. */
    private suspend fun deleteWhere(col: CollectionReference, field: String, value: String) {
        col.whereEqualTo(field, value).get().await().documents.forEach { it.reference.delete() }
    }

    // =======================================================================================
    // Mappers — object <-> Firestore document. (Firestore has no Set, so Sets become Lists.)
    // =======================================================================================

    private fun userMap(u: User): Map<String, Any?> = mapOf(
        "username" to u.username,
        "email" to u.email,
        "bio" to u.bio,
        "avatarUri" to u.avatarUri,
        "isAdmin" to u.isAdmin,
        "joinedAt" to u.joinedAt,
        "joinedGroupIds" to u.joinedGroupIds.toList(),
        "bookmarkedBlogIds" to u.bookmarkedBlogIds.toList(),
        "following" to u.following.toList()
    )

    private fun userFrom(d: DocumentSnapshot): User? {
        val username = d.getString("username") ?: return null
        @Suppress("UNCHECKED_CAST")
        val groups = (d.get("joinedGroupIds") as? List<String>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val bookmarks = (d.get("bookmarkedBlogIds") as? List<String>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val following = (d.get("following") as? List<String>) ?: emptyList()
        return User(
            id = d.id,
            username = username,
            email = d.getString("email").orEmpty(),
            bio = d.getString("bio").orEmpty(),
            avatarUri = d.getString("avatarUri"),
            joinedGroupIds = groups.toMutableList(),
            bookmarkedBlogIds = bookmarks.toMutableSet(),
            isAdmin = d.getBoolean("isAdmin") ?: false,
            joinedAt = d.getLong("joinedAt") ?: System.currentTimeMillis(),
            following = following.toMutableList()
        )
    }

    private fun groupMap(g: Group): Map<String, Any?> = mapOf(
        "name" to g.name,
        "description" to g.description,
        "inviteCode" to g.inviteCode,
        "creatorId" to g.creatorId,
        "memberIds" to g.memberIds.toList()
    )

    private fun groupFrom(d: DocumentSnapshot): Group? {
        val name = d.getString("name") ?: return null
        @Suppress("UNCHECKED_CAST")
        val members = (d.get("memberIds") as? List<String>) ?: emptyList()
        return Group(
            id = d.id,
            name = name,
            description = d.getString("description").orEmpty(),
            inviteCode = d.getString("inviteCode").orEmpty(),
            creatorId = d.getString("creatorId").orEmpty(),
            memberIds = members.toMutableList()
        )
    }

    private fun blogMap(b: Blog): Map<String, Any?> = mapOf(
        "authorId" to b.authorId,
        "title" to b.title,
        "content" to b.content,
        "groupId" to b.groupId,
        "likedByUserIds" to b.likedByUserIds.toList(),
        "timestamp" to b.timestamp,
        "tags" to b.tags.toList(),
        "viewedBy" to b.viewedBy.toList()
    )

    private fun blogFrom(d: DocumentSnapshot): Blog? {
        val authorId = d.getString("authorId") ?: return null
        @Suppress("UNCHECKED_CAST")
        val likes = (d.get("likedByUserIds") as? List<String>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val tags = (d.get("tags") as? List<String>) ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val viewedBy = (d.get("viewedBy") as? List<String>) ?: emptyList()
        return Blog(
            id = d.id,
            authorId = authorId,
            title = d.getString("title").orEmpty(),
            content = d.getString("content").orEmpty(),
            groupId = d.getString("groupId"),
            likedByUserIds = likes.toMutableSet(),
            timestamp = d.getLong("timestamp") ?: System.currentTimeMillis(),
            tags = tags.toMutableList(),
            viewedBy = viewedBy.toMutableSet()
        )
    }

    private fun commentMap(c: Comment): Map<String, Any?> = mapOf(
        "blogId" to c.blogId,
        "authorId" to c.authorId,
        "text" to c.text,
        "timestamp" to c.timestamp
    )

    private fun commentFrom(d: DocumentSnapshot): Comment? {
        val blogId = d.getString("blogId") ?: return null
        return Comment(
            id = d.id,
            blogId = blogId,
            authorId = d.getString("authorId").orEmpty(),
            text = d.getString("text").orEmpty(),
            timestamp = d.getLong("timestamp") ?: System.currentTimeMillis()
        )
    }

    private fun notifMap(n: Notification): Map<String, Any?> = mapOf(
        "recipientId" to n.recipientId,
        "type" to n.type,
        "actorName" to n.actorName,
        "text" to n.text,
        "blogId" to n.blogId,
        "read" to n.read,
        "timestamp" to n.timestamp
    )

    private fun notifFrom(d: DocumentSnapshot): Notification? {
        val recipientId = d.getString("recipientId") ?: return null
        return Notification(
            id = d.id,
            recipientId = recipientId,
            type = d.getString("type").orEmpty(),
            actorName = d.getString("actorName").orEmpty(),
            text = d.getString("text").orEmpty(),
            blogId = d.getString("blogId"),
            read = d.getBoolean("read") ?: false,
            timestamp = d.getLong("timestamp") ?: System.currentTimeMillis()
        )
    }
}
