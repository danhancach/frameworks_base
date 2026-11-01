package com.android.systemui.axdynamicbar.domain

import android.app.Notification
import android.service.notification.StatusBarNotification

/**
 * Nhan dien thẻ notif Clock timer / stopwatch (dong bo logic NotificationIslandManager).
 * Dung de an khoi shade/QS khi Dynamic Bar chronometer dang bat.
 */
object ClockChronometerNotif {
    private val CLOCK_PACKAGES = setOf("com.google.android.deskclock", "com.android.deskclock")
    private val ACTION_PREFIXES =
        listOf(
            "com.android.deskclock.action.",
            "com.google.android.deskclock.action.",
        )

    enum class Kind {
        TIMER,
        STOPWATCH,
    }

    fun kindOf(sbn: StatusBarNotification): Kind? {
        val pkg = sbn.packageName ?: return null
        if (pkg !in CLOCK_PACKAGES) return null
        val notification = sbn.notification ?: return null
        val extras = notification.extras ?: return null
        val channelId = notification.channelId?.lowercase() ?: ""
        val actionLabels =
            notification.actions?.map { it.title?.toString()?.lowercase() ?: "" } ?: emptyList()
        val actionIntents =
            notification.actions?.map { actionIntentString(it) ?: "" } ?: emptyList()

        val hasLap =
            actionLabels.any {
                it.contains("lap") || it.contains("vòng") || it.contains("vong")
            }
        val isStopwatch =
            actionIntents.any { isStopwatchAction(it) } ||
                channelId.contains("stopwatch") ||
                hasLap
        if (isStopwatch) return Kind.STOPWATCH

        val isCountDown = extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false) ||
            extras.getBoolean("android.chronometerCountDown", false)
        val isTimer =
            actionIntents.any { isTimerAction(it) } ||
                channelId.contains("timer") ||
                channelId.contains("firing") ||
                isCountDown ||
                actionLabels.any { it.contains("+1") || it.contains("add") }
        // Group summary Timers+Stopwatch: van an khi bat timer (tranh the "Timers" trong shade).
        if (isTimer) return Kind.TIMER
        if (
            (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0 &&
                (channelId.contains("timer") || channelId == "timers")
        ) {
            return Kind.TIMER
        }
        return null
    }

    fun shouldHideFromShade(
        sbn: StatusBarNotification,
        hideTimer: Boolean,
        hideStopwatch: Boolean,
    ): Boolean {
        if (!hideTimer && !hideStopwatch) return false
        return when (kindOf(sbn)) {
            Kind.TIMER -> hideTimer
            Kind.STOPWATCH -> hideStopwatch
            null -> false
        }
    }

    private fun actionIntentString(action: Notification.Action): String? {
        val pi = action.actionIntent ?: return null
        return try {
            pi.intent?.action
        } catch (_: Exception) {
            null
        }
    }

    private fun matches(action: String, name: String): Boolean =
        ACTION_PREFIXES.any { action == it + name } || action.endsWith(".action.$name")

    private fun isTimerAction(action: String): Boolean =
        matches(action, "START_TIMER") ||
            matches(action, "PAUSE_TIMER") ||
            matches(action, "RESET_TIMER") ||
            matches(action, "ADD_MINUTE_TIMER") ||
            matches(action, "SHOW_TIMER")

    private fun isStopwatchAction(action: String): Boolean =
        matches(action, "START_STOPWATCH") ||
            matches(action, "PAUSE_STOPWATCH") ||
            matches(action, "RESET_STOPWATCH") ||
            matches(action, "LAP_STOPWATCH") ||
            matches(action, "SHOW_STOPWATCH")
}
