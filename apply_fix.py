path = "innertube/src/main/kotlin/com/nikhil/yt/innertube/YouTube.kt"

old = """        // 2. Extract Framework-based comments
        val mutations = response.frameworkUpdates?.entityBatchUpdate?.mutations.orEmpty()
        val commentsFromFramework = mutations.mapNotNull { it.payload?.commentEntityPayload }
"""

new = """        // 2. Extract Framework-based comments
        val mutations = response.frameworkUpdates?.entityBatchUpdate?.mutations.orEmpty()
        val toolbarMap = mutations.mapNotNull { it.payload?.engagementToolbarStateEntityPayload }.associateBy { it.key }
        val surfaceMap = mutations.mapNotNull { it.payload?.engagementToolbarSurfaceEntityPayload }.associateBy { it.key }

        val commentsFromFramework = mutations.mapNotNull { mutation ->
            mutation.payload?.commentEntityPayload?.let { payload ->
                val toolbarKey = payload.properties?.toolbarStateKey
                val surfaceKey = payload.properties?.toolbarSurfaceKey
                val toolbarState = toolbarMap[toolbarKey]
                val surface = surfaceMap[surfaceKey]
                val likeCount = payload.toolbar?.likeCountNotliked
                    ?: surface?.toolbar?.likeCountNotliked
                    ?: "0"
                val replyCount = payload.toolbar?.replyCount
                    ?: surface?.toolbar?.replyCount
                    ?: "0"

                val commentId = payload.properties?.commentId ?: "framework-${mutation.hashCode()}"
                val legacyMatch = legacyCommentsMap[commentId]

                CommentThreadRenderer(
                    comment = CommentThreadRenderer.Comment(
                        commentRenderer = com.nikhil.yt.innertube.models.comment.CommentRenderer(
                            authorText = com.nikhil.yt.innertube.models.Runs(
                                runs = listOf(com.nikhil.yt.innertube.models.Run(text = payload.author?.displayName ?: "Unknown", navigationEndpoint = null))
                            ),
                            authorThumbnail = com.nikhil.yt.innertube.models.Thumbnails(
                                thumbnails = listOf(com.nikhil.yt.innertube.models.Thumbnail(url = payload.author?.avatarThumbnailUrl ?: "", width = 0, height = 0))
                            ),
                            contentText = com.nikhil.yt.innertube.models.Runs(
                                runs = listOf(com.nikhil.yt.innertube.models.Run(text = payload.properties?.content?.content ?: "", navigationEndpoint = null))
                            ),
                            publishedTimeText = com.nikhil.yt.innertube.models.Runs(
                                runs = listOf(com.nikhil.yt.innertube.models.Run(text = payload.properties?.publishedTime ?: "", navigationEndpoint = null))
                            ),
                            commentId = commentId,
                            voteCount = com.nikhil.yt.innertube.models.Runs(
                                runs = listOf(com.nikhil.yt.innertube.models.Run(text = likeCount, navigationEndpoint = null))
                            ),
                            voteStatus = when (toolbarState?.likeState) {
                                "TOOLBAR_LIKE_STATE_LIKE" -> "UPVOTE"
                                "TOOLBAR_LIKE_STATE_INDIFFERENT" -> "INDIFFERENT"
                                else -> "INDIFFERENT"
                            },
                            replyCount = replyCount.toIntOrNull() ?: 0
                        )
                    ),
                    replies = legacyMatch?.replies
                )
            }
        }
"""

with open(path, "r") as f:
    content = f.read()

count = content.count(old)
if count != 1:
    print(f"ERROR: expected exactly 1 match of the old block, found {count}. Aborting -- no changes made.")
    raise SystemExit(1)

content = content.replace(old, new)
with open(path, "w") as f:
    f.write(content)

print("Patch applied successfully.")
