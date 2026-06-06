package com.example.blogsphere.data

/**
 * A user profile.
 *
 * NOTE: there is no `password` field any more — credentials are handled entirely by
 * Firebase Authentication. `id` is the Firebase Auth UID for real users (and a fixed
 * "u_xxx" id for the seeded demo users that still live in [DataStore]).
 */
data class User(
    val id: String,
    var username: String,
    var email: String,
    var bio: String = "",
    var avatarUri: String? = null,
    /** Small Base64-encoded JPEG thumbnail of the avatar, stored in Firestore so the
     *  picture shows for every user on every device (no Cloud Storage needed). */
    var avatarData: String? = null,
    val joinedGroupIds: MutableList<String> = mutableListOf(),
    val bookmarkedBlogIds: MutableSet<String> = mutableSetOf(),
    var isAdmin: Boolean = false,
    val joinedAt: Long = System.currentTimeMillis(),
    /** User ids this user follows (drives the personalized "Following" feed). */
    val following: MutableList<String> = mutableListOf()
)

data class Blog(
    val id: String,
    val authorId: String,
    var title: String,
    var content: String,
    val groupId: String? = null,
    val likedByUserIds: MutableSet<String> = mutableSetOf(),
    var timestamp: Long = System.currentTimeMillis(),
    /** Free-text tags / categories for filtering and search. */
    val tags: MutableList<String> = mutableListOf(),
    /** Ids of users who have opened this blog (counted once per person). */
    val viewedBy: MutableSet<String> = mutableSetOf()
)

data class Group(
    val id: String,
    var name: String,
    var description: String,
    val inviteCode: String,
    val creatorId: String,
    val memberIds: MutableList<String> = mutableListOf()
)

data class Comment(
    val id: String,
    val blogId: String,
    val authorId: String,
    var text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class Story(
    val id: String,
    val authorId: String,
    val text: String,
    val bgColor: String, // hex
    val visibility: String, // "all" | "followers"
    val createdAt: Long = System.currentTimeMillis(),
    val viewedBy: MutableSet<String> = mutableSetOf()
)

/**
 * An in-app notification shown to [recipientId] (e.g. "alice liked your blog").
 * Created when another user likes/comments on your blog or follows you.
 */
data class Notification(
    val id: String,
    val recipientId: String,
    val type: String,          // "like" | "comment" | "follow"
    val actorName: String,     // who triggered it
    val text: String,          // ready-to-display message
    val blogId: String? = null,
    var read: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
