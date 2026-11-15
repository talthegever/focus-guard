package com.focusguard.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Runs the real rule table against saved screen dumps in src/test/resources/screens.
 * Replace a dump with `python tools/dump_screen.py <name>` to re-check a rule against
 * the app version installed on the phone.
 */
class MatcherTest {
    private fun blockedBy(screenName: String, rules: List<Rule> = Rules.all): String? =
        Matcher.firstMatch(DumpScreen.load(screenName), rules)?.rule?.key

    @Test
    fun shortsPlayerIsBlocked() {
        assertEquals("youtube_shorts", blockedBy("youtube_shorts"))
    }

    @Test
    fun youtubeHomeIsNotBlocked() {
        assertNull(blockedBy("youtube_home"))
    }

    @Test
    fun normalYoutubeVideoIsNotBlocked() {
        assertNull(blockedBy("youtube_video"))
    }

    @Test
    fun instagramReelsTabIsBlocked() {
        assertEquals("instagram_reels_tab", blockedBy("instagram_reels_tab"))
    }

    @Test
    fun instagramFeedIsNotBlocked() {
        assertNull(blockedBy("instagram_feed"))
    }

    @Test
    fun instagramExploreIsNotBlocked() {
        assertNull(blockedBy("instagram_explore"))
    }

    @Test
    fun instagramDirectInboxIsNotBlocked() {
        assertNull(blockedBy("instagram_dm"))
    }

    @Test
    fun reelOpenedFromDirectMessageIsNotBlocked() {
        assertNull(blockedBy("instagram_dm_reel"))
    }

    @Test
    fun ruleThatIsSwitchedOffDoesNotBlock() {
        val enabled = Rules.all.filter { it.key != "youtube_shorts" }
        assertNull(blockedBy("youtube_shorts", enabled))
    }

    @Test
    fun ruleNeverAppliesToAnotherApp() {
        val shortsViewInsideInstagram = FakeScreen(
            appPackage = "com.instagram.android",
            views = mapOf("reel_recycler" to listOf(ViewState(selected = false, visible = true))),
        )
        assertNull(Matcher.firstMatch(shortsViewInsideInstagram, Rules.all))
    }

    @Test
    fun viewThatIsNotVisibleDoesNotBlock() {
        val offscreenShortsPage = FakeScreen(
            appPackage = "com.google.android.youtube",
            views = mapOf("reel_recycler" to listOf(ViewState(selected = false, visible = false))),
        )
        assertNull(Matcher.firstMatch(offscreenShortsPage, Rules.all))
    }

    @Test
    fun tabMustBeSelectedNotMerelyPresent() {
        val reelsTabShownButNotOpen = FakeScreen(
            appPackage = "com.instagram.android",
            views = mapOf("clips_tab" to listOf(ViewState(selected = false, visible = true))),
        )
        assertNull(Matcher.firstMatch(reelsTabShownButNotOpen, Rules.all))
    }

    private class FakeScreen(
        override val appPackage: String,
        private val views: Map<String, List<ViewState>>,
    ) : Screen {
        override fun views(viewId: String): List<ViewState> = views[viewId].orEmpty()
    }
}
