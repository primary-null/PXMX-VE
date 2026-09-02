package com.pxmx.app.data

/**
 * Shared live-refresh intervals so home / guest / node don't invent their own magic numbers.
 */
object LivePoll {
    const val HOME_MS = 3_500L
    const val GUEST_MS = 2_500L
    const val NODE_MS = 4_000L
}
