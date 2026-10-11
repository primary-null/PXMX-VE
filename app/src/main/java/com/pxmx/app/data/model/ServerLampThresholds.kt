package com.pxmx.app.data.model

data class ServerLampThresholds(
    val activityPercent: Int = DEFAULT_ACTIVITY_PERCENT,
    val warnPercent: Int = DEFAULT_WARN_PERCENT,
) {
    fun slideActivity(activity: Int): ServerLampThresholds =
        clamp(activity = activity, warn = warnPercent, slidingActivity = true)

    fun slideWarn(warn: Int): ServerLampThresholds =
        clamp(activity = activityPercent, warn = warn, slidingActivity = false)

    companion object {
        const val DEFAULT_ACTIVITY_PERCENT = 5
        const val DEFAULT_WARN_PERCENT = 85

        fun clamp(
            activity: Int,
            warn: Int,
            slidingActivity: Boolean = true,
        ): ServerLampThresholds {
            val a = activity.coerceIn(0, 99)
            val w = warn.coerceIn(1, 100)
            return if (a < w) {
                ServerLampThresholds(activityPercent = a, warnPercent = w)
            } else if (slidingActivity) {
                val bumpedWarn = (a + 1).coerceAtMost(100)
                ServerLampThresholds(activityPercent = a, warnPercent = bumpedWarn)
            } else {
                val droppedActivity = (w - 1).coerceAtLeast(0)
                ServerLampThresholds(activityPercent = droppedActivity, warnPercent = w)
            }
        }
    }
}

fun clampServerLampThresholds(
    activity: Int,
    warn: Int,
    slidingActivity: Boolean = true,
): ServerLampThresholds = ServerLampThresholds.clamp(activity, warn, slidingActivity)
