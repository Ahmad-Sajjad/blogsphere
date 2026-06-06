package com.example.blogsphere.data

import java.util.UUID

/**
 * In-memory cache of all app content (users, blogs, groups, comments).
 *
 * The UI reads from these lists **synchronously**, exactly as before. What changed with
 * Firebase:
 *  - On startup/login the lists are filled from Cloud Firestore via [ContentRepository.syncDown].
 *  - Every mutation below also writes through to Firestore (fire-and-forget) so data persists
 *    and stays consistent across restarts and devices.
 *
 * [buildSeed] still defines the demo data; it's written to Firestore once on first run
 * (and used in-memory as an offline fallback).
 */
object DataStore {

    val users: MutableList<User> = mutableListOf()
    val blogs: MutableList<Blog> = mutableListOf()
    val groups: MutableList<Group> = mutableListOf()
    val comments: MutableList<Comment> = mutableListOf()
    val stories: MutableList<Story> = mutableListOf()

    var currentUser: User? = null

    init {
        // Offline fallback: keeps the app usable before the first Firestore sync completes.
        seedInMemory()
    }

    // ---- lookups ---------------------------------------------------------

    fun findUserById(id: String): User? = users.firstOrNull { it.id == id }
    fun findUserByEmail(email: String): User? =
        users.firstOrNull { it.email.equals(email.trim(), ignoreCase = true) }

    fun findGroupById(id: String): Group? = groups.firstOrNull { it.id == id }
    fun findGroupByCode(code: String): Group? =
        groups.firstOrNull { it.inviteCode.equals(code.trim(), ignoreCase = true) }

    fun findBlogById(id: String): Blog? = blogs.firstOrNull { it.id == id }

    fun blogsByAuthor(userId: String): List<Blog> =
        blogs.filter { it.authorId == userId }.sortedByDescending { it.timestamp }

    fun blogsInGroup(groupId: String): List<Blog> =
        blogs.filter { it.groupId == groupId }.sortedByDescending { it.timestamp }

    /** Blogs the given user should see on Home: public blogs + blogs in groups user joined. */
    fun blogsVisibleTo(user: User): List<Blog> =
        blogs.filter { b ->
            b.groupId == null || user.joinedGroupIds.contains(b.groupId)
        }.sortedByDescending { it.timestamp }

    fun userGroups(user: User): List<Group> =
        groups.filter { it.memberIds.contains(user.id) }

    // ---- mutations (each also persists to Firestore) ---------------------

    fun addUser(u: User) {
        users.add(u)
        ContentRepository.pushUser(u)
    }

    fun addBlog(b: Blog) {
        blogs.add(0, b)
        ContentRepository.pushBlog(b)
    }

    fun deleteBlog(id: String) {
        blogs.removeAll { it.id == id }
        comments.removeAll { it.blogId == id }
        users.forEach { it.bookmarkedBlogIds.remove(id) }
        ContentRepository.deleteBlog(id)
    }

    fun addGroup(g: Group) {
        groups.add(g)
        ContentRepository.pushGroup(g)
    }

    fun deleteGroup(id: String) {
        groups.removeAll { it.id == id }
        blogs.removeAll { it.groupId == id }
        users.forEach { it.joinedGroupIds.remove(id) }
        ContentRepository.deleteGroup(id)
    }

    fun deleteUser(id: String) {
        users.removeAll { it.id == id }
        val authoredBlogIds = blogs.filter { it.authorId == id }.map { it.id }
        blogs.removeAll { it.authorId == id }
        comments.removeAll { it.authorId == id || authoredBlogIds.contains(it.blogId) }
        groups.forEach { it.memberIds.remove(id) }
        ContentRepository.deleteUser(id)
    }

    fun joinGroup(user: User, group: Group) {
        if (!group.memberIds.contains(user.id)) group.memberIds.add(user.id)
        if (!user.joinedGroupIds.contains(group.id)) user.joinedGroupIds.add(group.id)
        ContentRepository.pushGroup(group)   // user side is saved by the caller (AuthRepository)
    }

    fun leaveGroup(user: User, group: Group) {
        group.memberIds.remove(user.id)
        user.joinedGroupIds.remove(group.id)
        ContentRepository.pushGroup(group)
    }

    fun toggleLike(blog: Blog, userId: String): Boolean {
        val nowLiked = if (blog.likedByUserIds.contains(userId)) {
            blog.likedByUserIds.remove(userId); false
        } else {
            blog.likedByUserIds.add(userId); true
        }
        ContentRepository.pushBlog(blog)
        // Notify the author when someone else likes their blog.
        if (nowLiked && userId != blog.authorId) {
            val actor = findUserById(userId)?.username ?: "Someone"
            ContentRepository.createNotification(
                recipientId = blog.authorId, actorId = userId, type = "like",
                actorName = actor, text = "$actor liked your blog \"${blog.title}\"",
                blogId = blog.id
            )
        }
        return nowLiked
    }

    // ---- bookmarks (stored on the user doc, saved by the caller) ---------

    fun toggleBookmark(blog: Blog, user: User): Boolean {
        return if (user.bookmarkedBlogIds.contains(blog.id)) {
            user.bookmarkedBlogIds.remove(blog.id); false
        } else {
            user.bookmarkedBlogIds.add(blog.id); true
        }
    }

    fun isBookmarked(blog: Blog, user: User): Boolean =
        user.bookmarkedBlogIds.contains(blog.id)

    fun bookmarkedBlogs(user: User): List<Blog> =
        user.bookmarkedBlogIds.mapNotNull { id -> findBlogById(id) }
            .sortedByDescending { it.timestamp }

    // ---- comments --------------------------------------------------------

    fun commentsForBlog(blogId: String): List<Comment> =
        comments.filter { it.blogId == blogId }.sortedBy { it.timestamp }

    fun addComment(c: Comment) {
        comments.add(c)
        ContentRepository.pushComment(c)
        // Notify the blog author when someone else comments.
        val b = findBlogById(c.blogId)
        if (b != null && c.authorId != b.authorId) {
            val actor = findUserById(c.authorId)?.username ?: "Someone"
            ContentRepository.createNotification(
                recipientId = b.authorId, actorId = c.authorId, type = "comment",
                actorName = actor, text = "$actor commented on \"${b.title}\"",
                blogId = b.id
            )
        }
    }

    fun deleteComment(id: String) {
        comments.removeAll { it.id == id }
        ContentRepository.deleteComment(id)
    }

    // ---- helpers ---------------------------------------------------------

    fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    fun newId(): String = UUID.randomUUID().toString()

    /**
     * Replace the whole in-memory cache with data loaded from Firestore.
     * Called by [ContentRepository.syncDown]. The signed-in [currentUser] instance is kept
     * so existing screen references stay valid.
     */
    fun replaceAll(
        newUsers: List<User>,
        newGroups: List<Group>,
        newBlogs: List<Blog>,
        newComments: List<Comment>
    ) {
        users.clear(); users.addAll(newUsers)
        groups.clear(); groups.addAll(newGroups)
        blogs.clear(); blogs.addAll(newBlogs)
        comments.clear(); comments.addAll(newComments)
        currentUser?.let { cu ->
            val i = users.indexOfFirst { it.id == cu.id }
            if (i >= 0) users[i] = cu else users.add(cu)
        }
    }

    /** Replace just the blogs slice of the cache (used by the real-time listener). */
    fun replaceBlogs(newBlogs: List<Blog>) {
        blogs.clear(); blogs.addAll(newBlogs)
    }

    /** Replace just the groups slice of the cache (used by the real-time listener). */
    fun replaceGroups(newGroups: List<Group>) {
        groups.clear(); groups.addAll(newGroups)
    }

    /** Replace just the comments slice of the cache (used by the real-time listener). */
    fun replaceComments(newComments: List<Comment>) {
        comments.clear(); comments.addAll(newComments)
    }

    /** Replace just the stories slice of the cache (used by the real-time listener). */
    fun replaceStories(newStories: List<Story>) {
        stories.clear(); stories.addAll(newStories)
    }

    fun activeStories() = stories.filter {
        System.currentTimeMillis() - it.createdAt < 86_400_000
    }

    fun storiesVisibleTo(viewer: User) = activeStories().filter {
        it.visibility == "all" || it.authorId == viewer.id || viewer.following.contains(it.authorId)
    }

    fun activeStoriesByAuthor(authorId: String) = activeStories().filter { it.authorId == authorId }

    fun hasUnseenStory(authorId: String, viewerId: String): Boolean {
        val active = activeStoriesByAuthor(authorId)
        return active.isNotEmpty() && active.any { !it.viewedBy.contains(viewerId) }
    }

    fun resetAndReseed() {
        users.clear()
        blogs.clear()
        groups.clear()
        comments.clear()
        currentUser = null
        seedInMemory()
    }

    // ---- seed ------------------------------------------------------------

    /** Bundle of demo data, used both for the in-memory fallback and the Firestore seed. */
    class SeedBundle(
        val users: List<User>,
        val groups: List<Group>,
        val blogs: List<Blog>
    )

    private fun seedInMemory() {
        val s = buildSeed()
        users.addAll(s.users)
        groups.addAll(s.groups)
        blogs.addAll(s.blogs)
    }

    /** Build (but don't store) the demo users, groups and blogs. */
    fun buildSeed(): SeedBundle {
        val now = System.currentTimeMillis()
        val min = 60_000L
        val hour = 60 * min
        val day = 24 * hour

        // These seeded users only provide authorship/display data for the demo blogs.
        // They are NOT login accounts — real sign-in goes through Firebase Auth.
        // (To log in as the demo admin, register admin@blog.com via Sign Up.)
        val admin = User(
            id = "u_admin",
            username = "admin",
            email = "admin@blog.com",
            bio = "Platform administrator. Keeping BlogSphere tidy.",
            isAdmin = true,
            joinedAt = now - 30 * day
        )
        val alice = User(
            id = "u_alice",
            username = "alice",
            email = "alice@blog.com",
            bio = "Android dev. Kotlin enthusiast. Coffee addict ☕",
            joinedAt = now - 20 * day
        )
        val bob = User(
            id = "u_bob",
            username = "bob",
            email = "bob@blog.com",
            bio = "Foodie by day, gamer by night.",
            joinedAt = now - 14 * day
        )
        val charlie = User(
            id = "u_charlie",
            username = "charlie",
            email = "charlie@blog.com",
            bio = "Just here to read great blogs.",
            joinedAt = now - 7 * day
        )

        val tech = Group(
            id = "g_tech",
            name = "Tech Talk",
            description = "Everything about software engineering and mobile dev.",
            inviteCode = "TECH42",
            creatorId = alice.id,
            memberIds = mutableListOf(alice.id, admin.id)
        )
        val foodies = Group(
            id = "g_food",
            name = "Foodies",
            description = "Restaurants, recipes, reviews.",
            inviteCode = "FOOD99",
            creatorId = bob.id,
            memberIds = mutableListOf(bob.id, admin.id)
        )

        alice.joinedGroupIds.addAll(listOf(tech.id))
        bob.joinedGroupIds.addAll(listOf(foodies.id))
        admin.joinedGroupIds.addAll(listOf(tech.id, foodies.id))

        val seedBlogs = listOf(
            Blog("b1", alice.id,
                "My First App in Kotlin",
                "Today I built my very first Android app using Kotlin and Jetpack. The learning curve was steep, but seeing 'Hello World' run on my own phone was magical. Here's what I learned:\n\n1. Kotlin's null safety saved me from so many crashes.\n2. Activities have a lifecycle you MUST respect.\n3. RecyclerView is not as scary as it looks once you break it down.\n\nLooking forward to building more!",
                null, mutableSetOf(bob.id, charlie.id, admin.id), now - 30 * min),
            Blog("b2", bob.id,
                "Best biryani in town",
                "Finally tried that new place on MG Road. The chicken biryani was flavorful, the raita was cold and creamy, and the portions were generous. 9/10, would eat again. Pro tip: ask for extra boiled egg.",
                null, mutableSetOf(alice.id, charlie.id), now - 2 * hour),
            Blog("b3", charlie.id,
                "Why I switched to dark mode",
                "After years of being a light-mode loyalist, I finally gave dark mode a real shot. My eyes thank me during late-night coding sessions. OLED battery life is a bonus.",
                null, mutableSetOf(alice.id), now - 5 * hour),
            Blog("b4", alice.id,
                "Kotlin coroutines in 5 minutes",
                "Coroutines feel intimidating until you realize they're just lightweight threads with sugar on top. Launch one with `lifecycleScope.launch { }` and you've basically replaced an AsyncTask.",
                tech.id, mutableSetOf(admin.id), now - 1 * day),
            Blog("b5", bob.id,
                "Homemade pizza night",
                "Made pizza from scratch last weekend. Dough rose for 24 hours in the fridge. Topped it with fresh mozzarella, basil, and a little garlic oil. Restaurant-quality at home.",
                foodies.id, mutableSetOf(admin.id), now - 1 * day - 3 * hour),
            Blog("b6", alice.id,
                "ViewBinding vs findViewById",
                "If you're still using findViewById in 2024, please stop. ViewBinding gives you type safety, null safety, and zero boilerplate. Add `buildFeatures { viewBinding = true }` to your Gradle and never look back.",
                tech.id, mutableSetOf(charlie.id, admin.id), now - 2 * day),
            Blog("b7", admin.id,
                "Welcome to BlogSphere!",
                "Hi everyone — I'm the admin of BlogSphere. This is a space to share your thoughts, follow interesting writers, and join niche communities. Please be kind, be creative, and have fun!",
                null, mutableSetOf(alice.id, bob.id, charlie.id), now - 3 * day),
            Blog("b8", charlie.id,
                "Weekend photography tips",
                "Golden hour is your friend. Shoot in RAW if you can. Don't be afraid to take 100 photos to get 1 great one. And always, always back up your shots.",
                null, mutableSetOf(), now - 4 * day),
            Blog("b9", bob.id,
                "Street food crawl — part 1",
                "Started my street food tour yesterday. Pani puri first, then pav bhaji, and finally kulfi. My stomach was happy. My wallet was intact. 10/10.",
                foodies.id, mutableSetOf(admin.id), now - 5 * day),
            Blog("b10", alice.id,
                "Why I love open-source",
                "Open source taught me more than any course. Reading code written by strangers around the world is humbling and inspiring. Start small — fix a typo in a README. You'll be hooked.",
                null, mutableSetOf(charlie.id, bob.id), now - 6 * day),
            Blog("b11", charlie.id,
                "Book recs for this month",
                "Currently reading: Atomic Habits (again), Project Hail Mary, and Kotlin in Action. Mixed bag but each one's worth your time.",
                null, mutableSetOf(alice.id), now - 7 * day),
            Blog("b12", alice.id,
                "Debugging is 80% of the job",
                "Nobody warns you in tutorials that the actual job is reading stack traces. Embrace the logcat. Your future self will thank you.",
                tech.id, mutableSetOf(admin.id, charlie.id), now - 10 * day)
        )

        return SeedBundle(
            users = listOf(admin, alice, bob, charlie),
            groups = listOf(tech, foodies),
            blogs = seedBlogs
        )
    }
}
