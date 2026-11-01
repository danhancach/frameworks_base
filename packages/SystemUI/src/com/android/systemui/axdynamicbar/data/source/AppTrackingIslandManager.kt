package com.android.systemui.axdynamicbar.data.source

import android.app.ActivityManager
import android.app.ActivityTaskManager
import android.app.IActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.RemoteException
import android.os.UserHandle
import android.util.Log
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.shared.system.TaskStackChangeListener
import com.android.systemui.shared.system.TaskStackChangeListeners
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@SysUISingleton
class AppTrackingIslandManager @Inject constructor(@Application private val context: Context) {
    companion object {
        private const val TAG = "AppTrackingIslandManager"
        private const val MAX_RECENT = 8
    }

    private val _appSwitchEvent = MutableStateFlow<IslandEvent.AppSwitch?>(null)
    val appSwitchEvent: StateFlow<IslandEvent.AppSwitch?> = _appSwitchEvent.asStateFlow()

    /** Package user app dang foreground; null = launcher / SystemUI / khong co app. */
    private val _foregroundPackage = MutableStateFlow<String?>(null)
    val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()

    private val recentApps = mutableListOf<IslandEvent.RecentApp>()
    /** Theo doi task stack (media hide +/hoac app_switch). */
    private var stackListening = false
    /** Co emit IslandEvent.AppSwitch khi doi app. */
    private var emitAppSwitch = false
    private var launcherPackage: String? = null
    private val activityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
    private var switchesSincePrune = 0

    @Volatile private var currentForegroundPkg: String? = null

    private fun emitEvent() {
        if (!emitAppSwitch) return
        val event =
            if (recentApps.isNotEmpty())
                IslandEvent.AppSwitch(
                    recentApps = recentApps.toList(),
                    previousApp = _appSwitchEvent.value?.previousApp,
                )
            else null
        logEvent("emit", event)
        _appSwitchEvent.value = event
    }

    private fun setForegroundPackage(pkg: String?) {
        currentForegroundPkg = pkg
        if (_foregroundPackage.value != pkg) {
            _foregroundPackage.value = pkg
        }
    }

    private val listener =
        object : TaskStackChangeListener {
            override fun onTaskMovedToFront(taskInfo: ActivityManager.RunningTaskInfo) {
                val topActivity = taskInfo.topActivity ?: return
                val pkg = topActivity.packageName
                if (isHomeOrSystemUi(pkg)) {
                    setForegroundPackage(null)
                    return
                }
                // Track moi app foreground cho media ownership, ke ca khong co launcher intent.
                if (pkg == currentForegroundPkg) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "skip duplicate front pkg=$pkg task=${taskInfo.taskId}")
                    }
                    return
                }

                val leavingPkg = currentForegroundPkg
                setForegroundPackage(pkg)

                // Chi app co the launch moi vao app-switch pill.
                if (!emitAppSwitch || !hasLauncherIntent(pkg)) return

                val appName =
                    try {
                        context.packageManager
                            .getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0))
                            .toString()
                    } catch (_: Exception) {
                        pkg
                    }

                val appIcon =
                    try {
                        context.packageManager.getApplicationIcon(pkg)
                    } catch (_: Exception) {
                        null
                    }

                val app =
                    IslandEvent.RecentApp(
                        packageName = pkg,
                        appName = appName,
                        appIcon = appIcon,
                        taskId = taskInfo.taskId,
                        lastActiveTime = System.currentTimeMillis(),
                    )

                synchronized(recentApps) {
                    recentApps.removeAll { it.packageName == pkg }
                    recentApps.add(0, app)
                    while (recentApps.size > MAX_RECENT) recentApps.removeLast()

                    if (++switchesSincePrune >= 3) {
                        switchesSincePrune = 0
                        val runningTaskIds =
                            activityManager.getRunningTasks(MAX_RECENT).map { it.taskId }.toSet()
                        recentApps.removeAll { it.taskId !in runningTaskIds }
                    }

                    val newPreviousApp =
                        if (leavingPkg != null && leavingPkg != pkg) {
                            recentApps.find { it.packageName == leavingPkg }
                        } else {
                            _appSwitchEvent.value?.previousApp
                        }

                    val event =
                        if (recentApps.isNotEmpty())
                            IslandEvent.AppSwitch(
                                recentApps = recentApps.toList(),
                                previousApp = newPreviousApp,
                            )
                        else null
                    logEvent("front pkg=$pkg leaving=$leavingPkg", event)
                    _appSwitchEvent.value = event
                }
            }

            override fun onTaskRemoved(taskId: Int) {
                synchronized(recentApps) {
                    val removed = recentApps.removeAll { it.taskId == taskId }
                    if (removed) emitEvent()
                }
            }
        }

    /** Theo doi foreground (an media pill khi app mo) — khong can bat event app_switch. */
    fun startForegroundTracking() {
        ensureStackListening()
    }

    fun stopForegroundTracking() {
        // Giữ listener neu app_switch van bat.
        if (!emitAppSwitch) {
            tearDownStackListening()
        }
    }

    fun startListening() {
        emitAppSwitch = true
        ensureStackListening()
    }

    fun stopListening() {
        emitAppSwitch = false
        synchronized(recentApps) {
            recentApps.clear()
            _appSwitchEvent.value = null
        }
        // Van theo doi foreground neu media can.
        // stopForegroundTracking goi rieng khi tat media.
        if (!stackListening) return
        // Neu chi app_switch tat ma foreground tracking van can — repository quyet.
    }

    /** Tat het (media + app_switch). */
    fun stopAll() {
        emitAppSwitch = false
        tearDownStackListening()
        synchronized(recentApps) {
            recentApps.clear()
            _appSwitchEvent.value = null
        }
        setForegroundPackage(null)
    }

    private fun ensureStackListening() {
        if (stackListening) return
        stackListening = true
        launcherPackage = resolveLauncherPackage()
        TaskStackChangeListeners.getInstance().registerTaskStackListener(listener)
        seedForegroundFromRunningTask()
    }

    /** Dong bo foreground hien tai (tranh pill media le khi da o trong app). */
    private fun seedForegroundFromRunningTask() {
        try {
            val task = activityManager.getRunningTasks(1).firstOrNull() ?: return
            val pkg = task.topActivity?.packageName ?: return
            if (isHomeOrSystemUi(pkg) || !hasLauncherIntent(pkg)) {
                setForegroundPackage(null)
            } else {
                setForegroundPackage(pkg)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to seed foreground package", e)
        }
    }

    private fun tearDownStackListening() {
        if (!stackListening) return
        stackListening = false
        launcherIntentCache.clear()
        TaskStackChangeListeners.getInstance().unregisterTaskStackListener(listener)
        setForegroundPackage(null)
    }

    fun clear() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "clear")
        }
        _appSwitchEvent.value = null
    }

    fun refreshRecentApps() {
        synchronized(recentApps) {
            val runningTaskIds = activityManager.getRunningTasks(MAX_RECENT + 4).map { it.taskId }.toSet()
            val removed = recentApps.removeAll { it.taskId !in runningTaskIds }
            if (removed) emitEvent()
        }
    }

    fun switchToApp(taskId: Int) {
        try {
            activityManager.moveTaskToFront(taskId, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to switch to task $taskId", e)
            synchronized(recentApps) {
                recentApps.removeAll { it.taskId == taskId }
                emitEvent()
            }
        }
    }

    fun killApp(taskId: Int) {
        synchronized(recentApps) {
            val app = recentApps.find { it.taskId == taskId } ?: return
            val pkg = app.packageName

            try {
                val atm = ActivityTaskManager.getService()
                if (atm.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_PINNED) {
                    Log.w(TAG, "Cannot kill app while in pinned lock task mode")
                    return
                }

                val am = ActivityManager.getService() as IActivityManager
                am.forceStopPackage(pkg, UserHandle.myUserId())
                am.removeTask(taskId)
            } catch (e: RemoteException) {
                Log.w(TAG, "Failed to kill app $pkg (task $taskId)", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "Failed to kill app $pkg (task $taskId)", e)
            }

            recentApps.removeAll { it.taskId == taskId }
            if (pkg == currentForegroundPkg) setForegroundPackage(null)

            val newPreviousApp =
                if (_appSwitchEvent.value?.previousApp?.taskId == taskId) null
                else _appSwitchEvent.value?.previousApp

            if (!emitAppSwitch) return

            val event =
                if (recentApps.isNotEmpty())
                    IslandEvent.AppSwitch(
                        recentApps = recentApps.toList(),
                        previousApp = newPreviousApp,
                    )
                else null
            logEvent("kill task=$taskId pkg=$pkg", event)
            _appSwitchEvent.value = event
        }
    }

    private val launcherIntentCache = HashMap<String, Boolean>()

    /** Launcher / SystemUI — khong coi la app foreground (giu pill media tren home). */
    private fun isHomeOrSystemUi(pkg: String): Boolean =
        pkg == launcherPackage ||
            pkg == "com.android.systemui" ||
            pkg == "com.android.launcher3" ||
            pkg == "com.google.android.apps.nexuslauncher"

    private fun hasLauncherIntent(pkg: String): Boolean =
        launcherIntentCache.getOrPut(pkg) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
            try {
                context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
            } catch (_: Exception) {
                false
            }
        }

    private fun resolveLauncherPackage(): String? =
        try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo =
                context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            resolveInfo?.activityInfo?.packageName
        } catch (_: Exception) {
            null
        }

    private fun logEvent(reason: String, event: IslandEvent.AppSwitch?) {
        if (!Log.isLoggable(TAG, Log.DEBUG)) return
        Log.d(
            TAG,
            "$reason emit=${event != null} " +
                "count=${event?.recentApps?.size ?: 0} " +
                "top=${event?.recentApps?.firstOrNull()?.packageName} " +
                "prev=${event?.previousApp?.packageName}",
        )
    }
}
