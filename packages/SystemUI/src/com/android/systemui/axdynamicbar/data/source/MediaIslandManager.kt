package com.android.systemui.axdynamicbar.data.source

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import com.android.systemui.axdynamicbar.model.IslandEvent
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.media.controls.domain.pipeline.MediaDataManager
import com.android.systemui.media.controls.shared.model.MediaData
import com.android.systemui.media.dialog.MediaOutputDialogManager
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.util.concurrency.RepeatableExecutor
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Cac media pill lay cung danh sach the tu QS MediaDataManager.
 * Moi pill giu key va MediaSession rieng, khong chon mot controller global.
 */
@SysUISingleton
class MediaIslandManager
@Inject
constructor(
    @Application private val context: Context,
    @Main private val mainHandler: Handler,
    @Background private val backgroundExecutor: RepeatableExecutor,
    private val mediaDataManager: MediaDataManager,
    private val mediaOutputDialogManager: MediaOutputDialogManager,
    private val activityStarter: ActivityStarter,
) {
    companion object {
        private const val TAG = "MediaIslandManager"
        private const val POSITION_UPDATE_INTERVAL_MS = 1000L
    }

    private val entries = LinkedHashMap<String, MediaData>()
    private val controllers = mutableMapOf<String, MediaController>()
    private val callbacks = mutableMapOf<String, MediaController.Callback>()
    private val artworkIcons = mutableMapOf<String, Icon?>()
    private val appIcons = mutableMapOf<String, Icon?>()

    private val _mediaEvents = MutableStateFlow<List<IslandEvent.Media>>(emptyList())
    val mediaEvents: StateFlow<List<IslandEvent.Media>> = _mediaEvents.asStateFlow()

    /** Tap hop package dang co pill — de NotificationIslandManager loc CATEGORY_TRANSPORT. */
    val activeMediaPackages: Set<String>
        get() = buildSet {
            for (event in _mediaEvents.value) {
                if (event.packageName.isNotEmpty()) add(event.packageName)
            }
        }

    @Volatile private var listening = false
    private var cancelProgressPolling: Runnable? = null

    private val listener =
        object : MediaDataManager.Listener {
            override fun onMediaDataLoaded(
                key: String,
                oldKey: String?,
                data: MediaData,
                immediately: Boolean,
            ) {
                mainHandler.post {
                    if (!listening) return@post
                    if (oldKey != null && oldKey != key) removeEntry(oldKey)
                    entries[key] = data
                    syncController(key, data.token)
                    publish()
                }
            }

            override fun onMediaDataRemoved(key: String, userInitiated: Boolean) {
                mainHandler.post {
                    if (!listening) return@post
                    removeEntry(key)
                    publish()
                }
            }
        }

    private fun syncController(key: String, token: MediaSession.Token?) {
        val current = controllers[key]
        if (current?.sessionToken == token) return
        releaseController(key)
        if (token == null) return
        try {
            val controller = MediaController(context, token)
            val callback =
                object : MediaController.Callback() {
                    private fun refresh() {
                        mainHandler.post {
                            if (listening && controllers[key]?.sessionToken == token) publish()
                        }
                    }

                    override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
                    override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
                    override fun onAudioInfoChanged(info: MediaController.PlaybackInfo) = refresh()
                    override fun onSessionDestroyed() = refresh()
                }
            controller.registerCallback(callback, mainHandler)
            controllers[key] = controller
            callbacks[key] = callback
        } catch (e: Exception) {
            Log.w(TAG, "Failed to bind QS media key=$key", e)
        }
    }

    private fun releaseController(key: String) {
        val controller = controllers.remove(key)
        val callback = callbacks.remove(key)
        if (controller != null && callback != null) {
            try {
                controller.unregisterCallback(callback)
            } catch (_: Exception) {}
        }
    }

    private fun removeEntry(key: String) {
        entries.remove(key)
        releaseController(key)
        artworkIcons.remove(key)
        appIcons.remove(key)
    }

    private fun clearAllEntries() {
        val keys = entries.keys.toList()
        for (key in keys) releaseController(key)
        entries.clear()
        artworkIcons.clear()
        appIcons.clear()
    }

    private fun isPlaying(state: PlaybackState?): Boolean =
        state?.state == PlaybackState.STATE_PLAYING ||
            state?.state == PlaybackState.STATE_FAST_FORWARDING ||
            state?.state == PlaybackState.STATE_REWINDING

    private fun position(state: PlaybackState?, duration: Long): Long {
        if (state == null) return 0L
        val base = state.position.coerceAtLeast(0L)
        val elapsed =
            if (isPlaying(state) && state.lastPositionUpdateTime > 0L) {
                (SystemClock.elapsedRealtime() - state.lastPositionUpdateTime).coerceAtLeast(0L)
            } else {
                0L
            }
        val speed = state.playbackSpeed.takeIf { it > 0f } ?: 1f
        val result = base + (elapsed * speed).toLong()
        return if (duration > 0L) result.coerceIn(0L, duration) else result
    }

    /** Tai dung Drawable khi Icon instance khong doi (tranh nhay cover/app icon). */
    private fun loadCachedDrawable(
        cache: MutableMap<String, Icon?>,
        key: String,
        icon: Icon?,
        previous: Drawable?,
    ): Drawable? {
        val previousIcon = cache.put(key, icon)
        if (previous != null && icon != null && icon == previousIcon) return previous
        return try {
            icon?.loadDrawable(context)
        } catch (_: Exception) {
            null
        }
    }

    private fun toEvent(key: String, data: MediaData, old: IslandEvent.Media?): IslandEvent.Media {
        val controller = controllers[key]
        val metadata = controller?.metadata
        val state = controller?.playbackState
        val track =
            data.song?.toString()?.takeIf { it.isNotBlank() }
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: ""
        val artist =
            data.artist?.toString()
                ?: metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: ""
        val duration =
            metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.takeIf { it > 0L }
                ?: if (old?.track == track) old.duration else 0L
        val currentPosition =
            if (state != null) position(state, duration)
            else if (old?.track == track) old.position
            else 0L
        // Session callbacks can arrive before the cached QS MediaData is refreshed.
        val playing = if (state != null) isPlaying(state) else data.isPlaying ?: false
        val actions =
            state?.customActions?.take(2)?.mapNotNull { action ->
                val label = action.name?.toString()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val name = action.action?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val icon =
                    try {
                        if (action.icon != 0) {
                            Icon.createWithResource(data.packageName, action.icon).loadDrawable(context)
                        } else {
                            null
                        }
                    } catch (_: Exception) {
                        null
                    }
                IslandEvent.MediaCustomAction(label = label, action = name, icon = icon)
            }.orEmpty()

        return IslandEvent.Media(
            mediaKey = key,
            track = track,
            artist = artist,
            isPlaying = playing,
            albumArt = loadCachedDrawable(artworkIcons, key, data.artwork, old?.albumArt),
            progress =
                if (duration > 0L) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            duration = duration,
            position = currentPosition,
            playbackSpeed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            positionUpdateTime = SystemClock.elapsedRealtime(),
            outputDeviceName = data.device?.name?.toString().orEmpty(),
            customActions = actions,
            appIcon = loadCachedDrawable(appIcons, key, data.appIcon, old?.appIcon),
            packageName = data.packageName,
        )
    }

    private fun publish() {
        val oldByKey = _mediaEvents.value.associateBy { it.mediaKey }
        _mediaEvents.value = entries.map { (key, data) -> toEvent(key, data, oldByKey[key]) }
    }

    fun startListening() {
        if (listening) return
        listening = true
        // Chi nhan event moi — MDM khong replay khi addListener (xem unresolved seed).
        mediaDataManager.addListener(listener)
    }

    fun stopListening() {
        if (!listening) return
        listening = false
        mediaDataManager.removeListener(listener)
        stopProgressPolling()
        clearAllEntries()
        _mediaEvents.value = emptyList()
    }

    fun clear(key: String) {
        // Dong bo dismiss nguoc ve QS; khong xoa rieng pill khi QS van giu the.
        if (entries.containsKey(key)) {
            mediaDataManager.dismissMediaData(key, 0L, true)
        }
    }

    fun startProgressPolling() {
        if (cancelProgressPolling != null) return
        cancelProgressPolling =
            backgroundExecutor.executeRepeatedly(
                ::tickProgress,
                0L,
                POSITION_UPDATE_INTERVAL_MS,
            )
    }

    fun stopProgressPolling() {
        cancelProgressPolling?.run()
        cancelProgressPolling = null
    }

    private fun tickProgress() {
        val current = _mediaEvents.value
        if (current.none { it.isPlaying && it.duration > 0L }) return
        val now = SystemClock.elapsedRealtime()
        _mediaEvents.update { events ->
            events.map { event ->
                if (!event.isPlaying || event.duration <= 0L) return@map event
                val elapsed = (now - event.positionUpdateTime).coerceAtLeast(0L)
                val value =
                    (event.position + (elapsed * event.playbackSpeed).toLong())
                        .coerceIn(0L, event.duration)
                event.copy(
                    position = value,
                    progress = (value.toFloat() / event.duration).coerceIn(0f, 1f),
                    positionUpdateTime = now,
                )
            }
        }
    }

    private fun controller(key: String): MediaController? = controllers[key]

    private fun mediaData(key: String): MediaData? = entries[key]

    /** UU tien MediaController theo key; khong co session thi semanticActions cua QS card. */
    private fun withTransportOrSemantic(
        key: String,
        onController: (MediaController) -> Unit,
        onSemantic: (MediaData) -> Unit,
    ) {
        val c = controller(key)
        if (c != null) onController(c) else mediaData(key)?.let(onSemantic)
    }

    fun togglePlayPause(key: String) {
        withTransportOrSemantic(
            key,
            onController = { c ->
                if (isPlaying(c.playbackState)) c.transportControls.pause()
                else c.transportControls.play()
            },
            onSemantic = { it.semanticActions?.playOrPause?.action?.run() },
        )
    }

    fun skipNext(key: String) {
        withTransportOrSemantic(
            key,
            onController = { it.transportControls.skipToNext() },
            onSemantic = { it.semanticActions?.nextOrCustom?.action?.run() },
        )
    }

    fun skipPrev(key: String) {
        withTransportOrSemantic(
            key,
            onController = { it.transportControls.skipToPrevious() },
            onSemantic = { it.semanticActions?.prevOrCustom?.action?.run() },
        )
    }

    fun seekTo(key: String, position: Long) {
        controller(key)?.transportControls?.seekTo(position.coerceAtLeast(0L))
    }

    fun sendCustomAction(key: String, action: String) {
        controller(key)?.transportControls?.sendCustomAction(action, null)
    }

    fun openMediaApp(key: String) {
        val data = mediaData(key) ?: return
        val intent = data.clickIntent ?: controller(key)?.sessionActivity
        if (intent != null) {
            try {
                activityStarter.postStartActivityDismissingKeyguard(intent)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open QS media card", e)
            }
        }
        val launch = context.packageManager.getLaunchIntentForPackage(data.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            activityStarter.postStartActivityDismissingKeyguard(launch, 0)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open media app", e)
        }
    }

    fun openMediaOutputSwitcher(key: String) {
        val pkg = mediaData(key)?.packageName ?: return
        mainHandler.post {
            try {
                mediaOutputDialogManager.createAndShow(
                    packageName = pkg,
                    aboveStatusBar = true,
                    useSystemColors = true,
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open media output switcher", e)
            }
        }
    }
}
