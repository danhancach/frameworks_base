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
import android.view.Display
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.axdynamicbar.shared.AppTrackingFgApplyKind
import com.android.systemui.axdynamicbar.shared.AppTrackingFgOwnerState
import com.android.systemui.axdynamicbar.shared.AppTrackingFgTaskInput
import com.android.systemui.axdynamicbar.shared.statusBarAppFgApplyTask
import com.android.systemui.axdynamicbar.shared.statusBarAppFgDisplayEligible
import com.android.systemui.axdynamicbar.shared.statusBarAppPillLogFgOwner
import com.android.systemui.axdynamicbar.shared.statusBarOwnerForegroundIsHomeOrSystemUi
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
    /** Pkg cuoi da ghi switch/history — tach dedupe khoi FG ownership. */
    @Volatile private var lastRecordedSwitchPkg: String? = null

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

    private fun setForegroundPackage(
        pkg: String?,
        cause: String = "setFg",
        taskId: Int = -1,
        displayId: Int = Display.DEFAULT_DISPLAY,
    ) {
        currentForegroundPkg = pkg
        if (_foregroundPackage.value != pkg) {
            _foregroundPackage.value = pkg
            // Diag: callback→FG that — cung AxDbAppPill uptime (stage delay Home).
            statusBarAppPillLogFgOwner(
                cause = cause,
                kind = if (pkg == null) "CLEAR" else "SET",
                taskId = taskId,
                displayId = displayId,
                packageName = pkg,
            )
        }
    }

    private fun ownerState(): AppTrackingFgOwnerState =
        AppTrackingFgOwnerState(
            foregroundPkg = currentForegroundPkg,
            lastRecordedSwitchPkg = lastRecordedSwitchPkg,
        )

    /**
     * Home / wallpaper home-type / SystemUI → FG null (giu pill tren Home).
     * Khong broad-null moi SystemUI surface ngoai package SystemUI/launcher.
     */
    private fun isHomeTypeOrSystemUi(taskInfo: ActivityManager.RunningTaskInfo): Boolean =
        statusBarOwnerForegroundIsHomeOrSystemUi(
            topActivityType = taskInfo.topActivityType,
            packageName = taskInfo.topActivity?.packageName,
            launcherPackage = launcherPackage,
        )

    /**
     * Top task default display — khong getRunningTasks(1) (co the secondary top).
     */
    private fun defaultDisplayTopTask(): ActivityManager.RunningTaskInfo? =
        try {
            ActivityTaskManager.getInstance()
                .getTasks(
                    /* maxNum = */ 1,
                    /* filterOnlyVisibleRecents = */ false,
                    /* keepIntentExtra = */ false,
                    /* displayId = */ Display.DEFAULT_DISPLAY,
                )
                .firstOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query default-display tasks", e)
            null
        }

    /**
     * Ap dung top task → foregroundPackage (+ optional switch history).
     * @param recordAppSwitch true khi onTaskMovedToFront; false reconcile / seed ownership.
     */
    private fun applyForegroundFromTask(
        taskInfo: ActivityManager.RunningTaskInfo,
        recordAppSwitch: Boolean,
    ) {
        val pkgHint = taskInfo.topActivity?.packageName
        val result =
            statusBarAppFgApplyTask(
                state = ownerState(),
                task =
                    AppTrackingFgTaskInput(
                        displayId = taskInfo.displayId,
                        topActivityType = taskInfo.topActivityType,
                        packageName = pkgHint,
                        taskId = taskInfo.taskId,
                        recordAppSwitch = recordAppSwitch,
                    ),
                launcherPackage = launcherPackage,
                emitAppSwitch = emitAppSwitch,
                hasLauncherIntent = pkgHint?.let { hasLauncherIntent(it) } ?: false,
            )
        val applyCause =
            if (recordAppSwitch) "onTaskMovedToFront" else "reconcile"
        when (result.kind) {
            AppTrackingFgApplyKind.SKIP_DISPLAY -> {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "skip ineligible displayId=${taskInfo.displayId}")
                }
                return
            }
            AppTrackingFgApplyKind.SKIP_NO_PACKAGE -> return
            AppTrackingFgApplyKind.CLEAR_HOME_OR_SYSTEM_UI -> {
                setForegroundPackage(
                    pkg = null,
                    cause = "$applyCause/CLEAR_HOME",
                    taskId = taskInfo.taskId,
                    displayId = taskInfo.displayId,
                )
                lastRecordedSwitchPkg = result.nextState.lastRecordedSwitchPkg
                return
            }
            AppTrackingFgApplyKind.UPDATE_OWNER -> {
                setForegroundPackage(
                    pkg = result.nextState.foregroundPkg,
                    cause = "$applyCause/UPDATE_OWNER",
                    taskId = taskInfo.taskId,
                    displayId = taskInfo.displayId,
                )
                lastRecordedSwitchPkg = result.nextState.lastRecordedSwitchPkg
            }
            AppTrackingFgApplyKind.OWNER_UNCHANGED -> {
                lastRecordedSwitchPkg = result.nextState.lastRecordedSwitchPkg
                if (!result.shouldRecordSwitch) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(
                            TAG,
                            "skip duplicate front pkg=$pkgHint task=${taskInfo.taskId}",
                        )
                    }
                    return
                }
            }
        }
        if (!result.shouldRecordSwitch) return

        val pkg = result.nextState.foregroundPkg ?: return
        val leavingPkg = result.leavingPkgForSwitch

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

    /**
     * Event-driven reconcile (khong poll): top default-display 1 lan / callback.
     * Bu home-type null topActivity (wallpaper task) + stack doi truoc MovedToFront muon.
     */
    private fun reconcileForegroundFromRunningTask() {
        val task = defaultDisplayTopTask() ?: return
        applyForegroundFromTask(task, recordAppSwitch = false)
    }

    private val listener =
        object : TaskStackChangeListener {
            override fun onTaskMovedToFront(taskInfo: ActivityManager.RunningTaskInfo) {
                applyForegroundFromTask(taskInfo, recordAppSwitch = true)
            }

            /**
             * Home restart/TO_FRONT thuong log som (truoc remote transition finish ~1s).
             * Chi clear FG khi Home tren default display — khong clear secondary Home.
             */
            override fun onActivityRestartAttempt(
                task: ActivityManager.RunningTaskInfo,
                homeTaskVisible: Boolean,
                clearedTask: Boolean,
                wasVisible: Boolean,
            ) {
                if (!statusBarAppFgDisplayEligible(task.displayId)) return
                // Chi khi task dang restart la Home/SystemUI — khong clear theo homeTaskVisible le.
                if (!isHomeTypeOrSystemUi(task)) return
                setForegroundPackage(
                    pkg = null,
                    cause = "onActivityRestartAttempt/HOME",
                    taskId = task.taskId,
                    displayId = task.displayId,
                )
            }

            override fun onTaskStackChanged() {
                reconcileForegroundFromRunningTask()
            }

            override fun onTaskRemoved(taskId: Int) {
                synchronized(recentApps) {
                    val removed = recentApps.removeAll { it.taskId == taskId }
                    if (removed) emitEvent()
                }
                // Task owner bi remove — reconcile top con lai (co the ve Home).
                reconcileForegroundFromRunningTask()
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
        lastRecordedSwitchPkg = null
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
            val task = defaultDisplayTopTask() ?: return
            if (!statusBarAppFgDisplayEligible(task.displayId)) return
            if (isHomeTypeOrSystemUi(task) ||
                task.topActivity?.packageName?.let { !hasLauncherIntent(it) } == true
            ) {
                setForegroundPackage(
                    pkg = null,
                    cause = "seed/CLEAR_HOME",
                    taskId = task.taskId,
                    displayId = task.displayId,
                )
            } else {
                val pkg = task.topActivity?.packageName ?: return
                setForegroundPackage(
                    pkg = pkg,
                    cause = "seed/SET",
                    taskId = task.taskId,
                    displayId = task.displayId,
                )
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
        lastRecordedSwitchPkg = null
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
            if (pkg == lastRecordedSwitchPkg) lastRecordedSwitchPkg = null

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
