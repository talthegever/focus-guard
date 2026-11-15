package com.focusguard.app

/**
 * The whole blocking policy. This is the file to edit when an app update renames a view
 * and a rule stops firing: take a fresh dump with tools/dump_screen.py, find the new id,
 * change it here, and run the unit tests.
 *
 * If a rule targets a new app, also add its package to res/xml/blocker_service.xml and to
 * the <queries> block in AndroidManifest.xml.
 */
object Rules {
    const val YOUTUBE = "com.google.android.youtube"
    const val INSTAGRAM = "com.instagram.android"

    val all: List<Rule> = listOf(
        Rule(
            key = "youtube_shorts",
            title = "YouTube Shorts",
            appPackage = YOUTUBE,
            // The full-screen Shorts player, whether opened from the Shorts tab or a Short.
            anyOf = listOf(Signal("reel_progress_bar"), Signal("reel_recycler")),
            reaction = Reaction.Back,
        ),
        Rule(
            key = "instagram_reels_tab",
            title = "Instagram Reels tab",
            appPackage = INSTAGRAM,
            // Only the bottom-bar Reels tab. A reel opened from a DM, the feed, or Explore
            // leaves another tab selected (or hides the bar), so it is not matched.
            anyOf = listOf(Signal("clips_tab", mustBeSelected = true)),
            reaction = Reaction.Tap("feed_tab"),
        ),
    )
}
