package io.github.anbarasandevops3.tempo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.anbarasandevops3.tempo.MainActivity
import io.github.anbarasandevops3.tempo.R
import io.github.anbarasandevops3.tempo.audio.BeepType
import io.github.anbarasandevops3.tempo.audio.ClipSource
import io.github.anbarasandevops3.tempo.audio.Cue
import io.github.anbarasandevops3.tempo.audio.NameVoices
import io.github.anbarasandevops3.tempo.audio.VoicePack
import io.github.anbarasandevops3.tempo.audio.buildCues
import io.github.anbarasandevops3.tempo.audio.renderTrack
import io.github.anbarasandevops3.tempo.model.EntryStatus
import io.github.anbarasandevops3.tempo.model.HistoryEntry
import io.github.anbarasandevops3.tempo.model.Step
import io.github.anbarasandevops3.tempo.model.Repeat
import io.github.anbarasandevops3.tempo.model.Timeline
import io.github.anbarasandevops3.tempo.model.Workout
import io.github.anbarasandevops3.tempo.model.WorkoutNode
import io.github.anbarasandevops3.tempo.model.compile
import io.github.anbarasandevops3.tempo.model.formatClock
import io.github.anbarasandevops3.tempo.model.newId
import io.github.anbarasandevops3.tempo.store.SavedSession
import io.github.anbarasandevops3.tempo.store.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Phase { PREPARING, PLAYING, PAUSED, DONE, ERROR }

/** What the run screen shows. Null when no workout is running. */
data class RunUi(
    val workout: Workout,
    val timeline: Timeline,
    val phase: Phase,
    val position: Double,
    val error: String? = null,
)

/**
 * Plays a workout (ADR-001 + ADR-002). A mediaPlayback foreground service, so Android keeps it
 * alive with the screen off and the app swiped away. The rendered WAV is the clock:
 * position = MediaPlayer.currentPosition. A 200 ms ticker drives vibration, the notification,
 * the UI state and saving progress.
 */
class WorkoutService : Service() {

    companion object {
        private const val ACTION_START = "start"
        private const val ACTION_TOGGLE = "toggle"
        private const val ACTION_NEXT = "next"
        private const val ACTION_PREV = "prev"
        private const val ACTION_STOP = "stop"
        private const val EXTRA_WORKOUT = "workout"
        private const val EXTRA_RESUME_AT = "resumeAt"
        private const val EXTRA_ENTRY = "entry"
        private const val CHANNEL = "workout"
        private const val NOTIFICATION_ID = 1
        private const val TICK_MS = 200L

        private val _state = MutableStateFlow<RunUi?>(null)
        val state: StateFlow<RunUi?> = _state.asStateFlow()

        fun start(context: Context, workoutId: String, resumeAt: Double? = null, entryId: String? = null) {
            val i = Intent(context, WorkoutService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_WORKOUT, workoutId)
            resumeAt?.let { i.putExtra(EXTRA_RESUME_AT, it) }
            entryId?.let { i.putExtra(EXTRA_ENTRY, it) }
            ContextCompat.startForegroundService(context, i)
        }

        fun toggle(context: Context) = send(context, ACTION_TOGGLE)
        fun next(context: Context) = send(context, ACTION_NEXT)
        fun previous(context: Context) = send(context, ACTION_PREV)
        fun stop(context: Context) = send(context, ACTION_STOP)

        /** Clear a finished run so the UI goes back home. */
        fun dismiss() { if (_state.value?.phase == Phase.DONE || _state.value?.phase == Phase.ERROR) _state.value = null }

        private fun send(context: Context, action: String) {
            if (_state.value == null) return
            context.startService(Intent(context, WorkoutService::class.java).setAction(action))
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var session: MediaSession? = null
    private var audioFocus: AudioFocusRequest? = null
    private var workout: Workout? = null
    private var timeline: Timeline? = null
    private var vibrations: List<Cue.Beep> = emptyList()
    private var entryId: String? = null
    private var baseDone = 0
    private var pausedByFocus = false
    private var lastTickPos = 0.0
    private var lastSegment = -1
    private var lastSave = 0L
    private var noisyRegistered = false
    // Played time = sum of the stretches between seeks (skipping ahead isn't training).
    private var playedBefore = 0.0
    private var stretchStart = 0.0
    private var generation = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val w = Store.workout(intent.getStringExtra(EXTRA_WORKOUT) ?: "")
                if (w == null) { stopSelf(); return START_NOT_STICKY }
                begin(w, if (intent.hasExtra(EXTRA_RESUME_AT)) intent.getDoubleExtra(EXTRA_RESUME_AT, 0.0) else null,
                    intent.getStringExtra(EXTRA_ENTRY))
            }
            ACTION_TOGGLE -> toggle()
            ACTION_NEXT -> skip(1)
            ACTION_PREV -> skip(-1)
            ACTION_STOP -> stopRun()
        }
        return START_NOT_STICKY
    }

    // --- lifecycle of a run

    private fun begin(w: Workout, resumeAt: Double?, resumeEntry: String?) {
        releasePlayer()
        val gen = ++generation
        val tl = compile(w)
        workout = w
        timeline = tl
        lastSegment = -1
        playedBefore = 0.0
        stretchStart = resumeAt ?: 0.0
        lastTickPos = resumeAt ?: 0.0
        _state.value = RunUi(w, tl, Phase.PREPARING, resumeAt ?: 0.0)
        goForeground(buildNotification())

        thread(name = "render") {
            try {
                val pack = VoicePack.load(this)
                val names = NameVoices(this)
                if (w.cues.speakNames) names.prepare(labels(w.nodes))
                val clips = ClipSource { key -> names.clip(key) ?: pack.clip(key) }
                val cues = buildCues(tl, w.cues) { key -> clips.clip(key) != null }
                val wav = renderTrack(cues, tl.total, clips)
                val file = File(cacheDir, "track.wav").apply { writeBytes(wav) }
                val vib = if (w.cues.vibrate) cues.filterIsInstance<Cue.Beep>() else emptyList()
                main.post { if (gen == generation) startPlayback(file, vib, resumeAt, resumeEntry) }
            } catch (t: Throwable) {
                main.post { if (gen == generation) fail("Couldn't prepare the audio: ${t.message}") }
            }
        }
    }

    private fun startPlayback(file: File, vib: List<Cue.Beep>, resumeAt: Double?, resumeEntry: String?) {
        val w = workout ?: return
        val tl = timeline ?: return
        vibrations = vib
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            if (resumeAt != null && resumeAt > 0) mp.seekTo((resumeAt * 1000).toInt())
        } catch (t: Throwable) {
            mp.release()
            fail("Couldn't play the audio: ${t.message}")
            return
        }
        mp.setOnCompletionListener { finishRun() }
        player = mp

        // History: continue the resumed entry, or start a new one.
        val existing = resumeEntry?.let { id -> Store.history.value.firstOrNull { it.id == id } }
        if (existing != null) {
            entryId = existing.id
            baseDone = existing.doneSec
        } else {
            val id = newId()
            Store.addHistory(HistoryEntry(id, w.id, w.name, System.currentTimeMillis(), tl.total, 0, EntryStatus.ACTIVE))
            entryId = id
            baseDone = 0
        }

        setupMediaSession()
        registerNoisy()
        if (requestFocus()) play() else { publish(Phase.PAUSED) }
        main.post(ticker)
    }

    private fun finishRun() {
        entryId?.let { Store.updateHistory(it, doneSec(), EntryStatus.COMPLETED) }
        Store.saveSession(null)
        vibrate(BeepType.END)
        val w = workout ?: return
        val tl = timeline ?: return
        _state.value = RunUi(w, tl, Phase.DONE, tl.total.toDouble())
        shutdown()
    }

    private fun stopRun() {
        if (player != null) entryId?.let { Store.finishStopped(it, doneSec()) }
        Store.saveSession(null)
        _state.value = null
        shutdown()
    }

    private fun fail(message: String) {
        val w = workout
        val tl = timeline
        _state.value = if (w != null && tl != null) RunUi(w, tl, Phase.ERROR, 0.0, message) else null
        shutdown()
    }

    private fun shutdown() {
        main.removeCallbacks(ticker)
        generation++
        releasePlayer()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        main.removeCallbacks(ticker)
        releasePlayer()
        super.onDestroy()
    }

    private fun releasePlayer() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        session?.let { it.isActive = false; it.release() }
        session = null
        audioFocus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        audioFocus = null
        if (noisyRegistered) { runCatching { unregisterReceiver(noisy) }; noisyRegistered = false }
    }

    // --- controls

    private fun play() {
        val mp = player ?: return
        if (!mp.isPlaying) mp.start()
        pausedByFocus = false
        publish(Phase.PLAYING)
        refreshNotification()
    }

    private fun pause() {
        val mp = player ?: return
        if (mp.isPlaying) mp.pause()
        publish(Phase.PAUSED)
        saveProgress()
        refreshNotification()
    }

    private fun toggle() {
        val mp = player ?: return
        if (mp.isPlaying) pause() else if (requestFocus()) play()
    }

    /** Next step, or (dir = -1) back to the start of this step / the previous one within 3 s. */
    private fun skip(dir: Int) {
        val mp = player ?: return
        val tl = timeline ?: return
        val pos = position()
        val seg = tl.segmentAt(pos) ?: return
        val target = if (dir > 0) seg.end.toDouble() else {
            val prev = tl.segments.getOrNull(seg.index - 1)
            if (pos - seg.start < 3 && prev != null) prev.start.toDouble() else seg.start.toDouble()
        }
        playedBefore += max(0.0, min(pos, tl.total.toDouble()) - stretchStart)
        val t = target.coerceIn(0.0, tl.total.toDouble())
        mp.seekTo((t * 1000).toInt())
        stretchStart = t
        lastTickPos = t
        publish(if (mp.isPlaying) Phase.PLAYING else Phase.PAUSED, t)
        refreshNotification(t)
    }

    // --- ticker

    private val ticker = object : Runnable {
        override fun run() {
            val mp = player ?: return
            val pos = position()
            if (mp.isPlaying) {
                // Vibrate for cues crossed since the last tick (but not a burst after a long gap).
                for (c in vibrations) if (c.at > lastTickPos && c.at <= pos && pos - c.at < 1.0) vibrate(c.type)
                lastTickPos = pos
                publish(Phase.PLAYING, pos)
                val seg = timeline?.segmentAt(pos)
                if (seg != null && seg.index != lastSegment) refreshNotification(pos)
                if (SystemClock.elapsedRealtime() - lastSave > 2000) saveProgress()
            }
            main.postDelayed(this, TICK_MS)
        }
    }

    private fun position(): Double = player?.let { runCatching { it.currentPosition / 1000.0 }.getOrNull() } ?: -1.0

    private fun doneSec(): Int {
        val tl = timeline ?: return baseDone
        val pos = min(max(position(), 0.0), tl.total.toDouble())
        return (baseDone + min(tl.total.toDouble(), playedBefore + max(0.0, pos - stretchStart))).roundToInt()
    }

    private fun saveProgress() {
        lastSave = SystemClock.elapsedRealtime()
        val w = workout ?: return
        val tl = timeline ?: return
        val pos = position()
        if (pos < 0 || pos >= tl.total) return
        Store.saveSession(SavedSession(w.id, pos, System.currentTimeMillis(), entryId))
        entryId?.let { Store.updateHistory(it, doneSec()) }
    }

    private fun publish(phase: Phase, pos: Double = position()) {
        val w = workout ?: return
        val tl = timeline ?: return
        _state.value = RunUi(w, tl, phase, max(0.0, pos))
        session?.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS)
            .setState(if (phase == Phase.PLAYING) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                (max(0.0, pos) * 1000).toLong(), if (phase == Phase.PLAYING) 1f else 0f)
            .build())
    }

    // --- vibration

    private fun vibrate(type: BeepType) {
        if (workout?.cues?.vibrate != true) return
        val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        val effect = when (type) {
            BeepType.START -> VibrationEffect.createOneShot(450, VibrationEffect.DEFAULT_AMPLITUDE)
            BeepType.TICK -> VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
            BeepType.END -> VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 300), -1)
        }
        @Suppress("DEPRECATION")
        v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
    }

    // --- audio focus: pause for calls, resume afterwards; pause if headphones are unplugged

    private fun requestFocus(): Boolean {
        val am = getSystemService(AudioManager::class.java)
        val req = audioFocus ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener({ change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        if (player?.isPlaying == true) { pause(); pausedByFocus = change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT }
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> if (pausedByFocus) play()
                }
            }, main)
            .setWillPauseWhenDucked(false)
            .build().also { audioFocus = it }
        return am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) pause()
        }
    }

    private fun registerNoisy() {
        ContextCompat.registerReceiver(this, noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        noisyRegistered = true
    }

    // --- media session (headphone buttons, lock-screen media controls)

    private fun setupMediaSession() {
        session = MediaSession(this, "Tempo").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { if (requestFocus()) play() }
                override fun onPause() = pause()
                override fun onSkipToNext() = skip(1)
                override fun onSkipToPrevious() = skip(-1)
                override fun onStop() = stopRun()
            })
            isActive = true
        }
    }

    // --- notification with a live countdown (system chronometer, so it ticks even while we sleep)

    private fun refreshNotification(pos: Double = position()) {
        val seg = timeline?.segmentAt(max(pos, 0.0))
        lastSegment = seg?.index ?: -1
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(pos))
        session?.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, seg?.title ?: workout?.name)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, workout?.name)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, (timeline?.total ?: 0) * 1000L)
            .build())
    }

    private fun buildNotification(pos: Double = 0.0): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Workout in progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live countdown and controls while a workout plays"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            })
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        fun action(icon: Int, title: String, action: String) = NotificationCompat.Action(icon, title,
            PendingIntent.getService(this, action.hashCode(), Intent(this, WorkoutService::class.java).setAction(action),
                PendingIntent.FLAG_IMMUTABLE))

        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        val tl = timeline
        val seg = tl?.segmentAt(max(pos, 0.0))
        val playing = player?.isPlaying == true
        if (player == null || tl == null || seg == null) {
            b.setContentTitle(workout?.name ?: "Tempo").setContentText("Preparing audio…")
            return b.build()
        }
        val next = tl.segments.getOrNull(seg.index + 1)
        val round = if (seg.rounds != null && seg.rounds > 1) " · ${seg.round}/${seg.rounds}" else ""
        b.setContentTitle(seg.title + round)
            .setContentText(next?.let { "Next: ${it.title} · ${formatClock(it.duration)}" } ?: "Last one!")
            .setSubText(workout?.name)
            .addAction(action(R.drawable.ic_prev, "Back", ACTION_PREV))
            .addAction(if (playing) action(R.drawable.ic_pause, "Pause", ACTION_TOGGLE) else action(R.drawable.ic_play, "Play", ACTION_TOGGLE))
            .addAction(action(R.drawable.ic_next, "Next", ACTION_NEXT))
        val left = seg.end - pos
        if (playing) {
            b.setUsesChronometer(true).setChronometerCountDown(true).setShowWhen(true)
                .setWhen(System.currentTimeMillis() + (left * 1000).toLong())
        } else {
            b.setContentText("Paused · ${formatClock(left)} left in this step")
        }
        return b.build()
    }

    private fun goForeground(n: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, type)
    }

    private fun labels(nodes: List<WorkoutNode>): List<String> = nodes.flatMap {
        when (it) {
            is Step -> listOfNotNull(it.label)
            is Repeat -> labels(it.children)
        }
    }
}
