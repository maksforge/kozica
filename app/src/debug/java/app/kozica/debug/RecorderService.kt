package app.kozica.debug

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import app.kozica.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Phase 6's gate apparatus (docs/phase-6.md § 1): a `location` foreground service that stores every
 * GPS fix, with no distance or accuracy filter, so `scripts/track-gaps.py` can tell whether the
 * service lived through a pocket. Started from Settings' debug section, never from the background:
 * a foreground service started from the background holds no while-in-use location (API 30+).
 *
 * Pause stops GPS and arms the significant-motion sensor, which resumes the recording when it fires.
 * Every start, pause, resume, motion, provider change and stop is an event row with the battery
 * level, which is where the battery-per-hour reading comes from. While recording, a `gnss` event at
 * most once a minute, with the satellites seen and used in the fix, tells a live service with no fix
 * from a dead or frozen one: the status callback reaches only a process that runs.
 *
 * Settings' Start row asks for precise location before it starts the service, hence the lint
 * suppression; `ServiceCompat` drops the service type below API 29, where it does not exist.
 */
@SuppressLint("MissingPermission", "InlinedApi")
class RecorderService : Service() {
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var database: GateDatabase
    private lateinit var locations: LocationManager
    private lateinit var sensors: SensorManager
    private var segment = 0
    private var points = 0
    private var lastFix: Location? = null
    private var lastHeartbeat = 0L

    private val listener =
        object : LocationListenerCompat {
            override fun onLocationChanged(location: Location) {
                points++
                lastFix = location
                database.point(location, segment)
                if (points % NOTIFY_EVERY == 0) notifyState()
                publish()
            }

            override fun onProviderEnabled(provider: String) = event("provider_on")

            override fun onProviderDisabled(provider: String) = event("provider_off")
        }

    private val gnss =
        object : GnssStatusCompat.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatusCompat) {
                val now = SystemClock.elapsedRealtime()
                if (state.value.paused || now - lastHeartbeat < HEARTBEAT_MS) return
                lastHeartbeat = now
                val used = (0 until status.satelliteCount).count { status.usedInFix(it) }
                event("gnss", status.satelliteCount, used)
            }
        }

    private val motion =
        object : TriggerEventListener() {
            override fun onTrigger(trigger: TriggerEvent?) {
                Log.i(TAG, "motion")
                event("motion")
                worker.execute { resume() }
            }
        }

    override fun onCreate() {
        super.onCreate()
        database = GateDatabase(this)
        locations = getSystemService(LocationManager::class.java)
        sensors = getSystemService(SensorManager::class.java)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action != ACTION_START && !state.value.recording) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_START -> start()
            ACTION_PAUSE -> worker.execute { pause() }
            ACTION_RESUME -> worker.execute { resume() }
            ACTION_STOP -> stop()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        LocationManagerCompat.removeUpdates(locations, listener)
        LocationManagerCompat.unregisterGnssStatusCallback(locations, gnss)
        significantMotion()?.let { sensors.cancelTriggerSensor(motion, it) }
        worker.execute { database.close() }
        worker.shutdown()
        state.value = GateState()
        super.onDestroy()
    }

    private fun start() {
        if (state.value.recording) return
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        state.value = GateState(recording = true, sensor = significantMotion() != null)
        segment = database.lastSegment() + 1
        event("start")
        worker.execute {
            requestFixes()
            LocationManagerCompat.registerGnssStatusCallback(locations, worker, gnss)
        }
    }

    private fun requestFixes() {
        LocationManagerCompat.requestLocationUpdates(
            locations,
            LocationManager.GPS_PROVIDER,
            LocationRequestCompat
                .Builder(INTERVAL_MS)
                .setQuality(LocationRequestCompat.QUALITY_HIGH_ACCURACY)
                .setMinUpdateDistanceMeters(0f)
                .build(),
            worker,
            listener,
        )
    }

    private fun pause() {
        if (state.value.paused || !state.value.recording) return
        LocationManagerCompat.removeUpdates(locations, listener)
        val armed = significantMotion()?.let { sensors.requestTriggerSensor(motion, it) } ?: false
        event(if (armed) "pause" else "pause_no_sensor")
        state.value = state.value.copy(paused = true)
        notifyState()
    }

    private fun resume() {
        if (!state.value.paused) return
        significantMotion()?.let { sensors.cancelTriggerSensor(motion, it) }
        segment++
        event("resume")
        state.value = state.value.copy(paused = false)
        requestFixes()
        notifyState()
    }

    private fun stop() {
        event("stop")
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun event(
        kind: String,
        satellites: Int? = null,
        usedInFix: Int? = null,
    ) {
        val battery =
            getSystemService(
                BatteryManager::class.java,
            ).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val detail = satellites?.let { ", $usedInFix of $it satellites in the fix" } ?: ""
        Log.i(TAG, "$kind, segment $segment, $points points, battery $battery %$detail")
        database.event(kind, segment, battery, satellites, usedInFix)
    }

    private fun significantMotion(): Sensor? = sensors.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

    private fun publish() {
        state.value = state.value.copy(points = points, lastFixMillis = lastFix?.time)
    }

    private fun notifyState() {
        publish()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_ID, NotificationManager.IMPORTANCE_LOW),
        )
        val paused = state.value.paused
        val toggle = if (paused) ACTION_RESUME to "Resume" else ACTION_PAUSE to "Pause"
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (paused) "Gate recorder: paused" else "Gate recorder: recording")
            .setContentText("$points points, segment $segment")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, toggle.second, action(toggle.first))
            .addAction(0, "Stop", action(ACTION_STOP))
            .build()
    }

    private fun action(name: String): PendingIntent =
        PendingIntent.getService(
            this,
            name.hashCode(),
            Intent(this, RecorderService::class.java).setAction(name),
            PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val TAG = "KozicaRecord"
        private const val CHANNEL_ID = "Gate recorder"
        private const val NOTIFICATION_ID = 6
        private const val INTERVAL_MS = 5_000L
        private const val NOTIFY_EVERY = 12
        private const val HEARTBEAT_MS = 60_000L
        private const val ACTION_START = "start"
        private const val ACTION_PAUSE = "pause"
        private const val ACTION_RESUME = "resume"
        private const val ACTION_STOP = "stop"

        private val state = MutableStateFlow(GateState())
        val gateState: StateFlow<GateState> = state

        fun send(
            context: Context,
            action: String,
        ) {
            val intent = Intent(context, RecorderService::class.java).setAction(action)
            if (action ==
                ACTION_START
            ) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }

        fun start(context: Context) = send(context, ACTION_START)

        fun pause(context: Context) = send(context, ACTION_PAUSE)

        fun resume(context: Context) = send(context, ACTION_RESUME)

        fun stop(context: Context) = send(context, ACTION_STOP)
    }
}

data class GateState(
    val recording: Boolean = false,
    val paused: Boolean = false,
    val sensor: Boolean = false,
    val points: Int = 0,
    val lastFixMillis: Long? = null,
)

/**
 * `gate.db`, apart from the app's database so the gate never touches its schema. Rollback journal,
 * not WAL, so a pulled copy is one file.
 */
private class GateDatabase(
    context: Context,
) : SQLiteOpenHelper(context, "gate.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE gate_points (fix_time INTEGER, stored_at INTEGER, segment INTEGER, " +
                "latitude REAL, longitude REAL, accuracy REAL, altitude REAL, speed REAL)",
        )
        db.execSQL(
            "CREATE TABLE gate_events (at INTEGER, kind TEXT, segment INTEGER, battery INTEGER, " +
                "satellites INTEGER, used_in_fix INTEGER)",
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int,
    ) {
        db.execSQL("ALTER TABLE gate_events ADD COLUMN satellites INTEGER")
        db.execSQL("ALTER TABLE gate_events ADD COLUMN used_in_fix INTEGER")
    }

    fun point(
        fix: Location,
        segment: Int,
    ) {
        writableDatabase.insert(
            "gate_points",
            null,
            ContentValues().apply {
                put("fix_time", fix.time)
                put("stored_at", System.currentTimeMillis())
                put("segment", segment)
                put("latitude", fix.latitude)
                put("longitude", fix.longitude)
                put("accuracy", if (fix.hasAccuracy()) fix.accuracy else null)
                put("altitude", if (fix.hasAltitude()) fix.altitude else null)
                put("speed", if (fix.hasSpeed()) fix.speed else null)
            },
        )
    }

    fun event(
        kind: String,
        segment: Int,
        battery: Int,
        satellites: Int?,
        usedInFix: Int?,
    ) {
        writableDatabase.insert(
            "gate_events",
            null,
            ContentValues().apply {
                put("at", System.currentTimeMillis())
                put("kind", kind)
                put("segment", segment)
                put("battery", battery)
                put("satellites", satellites)
                put("used_in_fix", usedInFix)
            },
        )
    }

    fun lastSegment(): Int =
        readableDatabase.rawQuery("SELECT COALESCE(MAX(segment), 0) FROM gate_events", null).use {
            it.moveToFirst()
            it.getInt(0)
        }
}
