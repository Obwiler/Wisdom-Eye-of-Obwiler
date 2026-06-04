package com.obwiler.weo.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

interface ImuProvider {
    val pitchDeg: Float
    val rollDeg: Float
    fun start()
    fun stop()
}

class ImuCollector(context: Context) : ImuProvider, SensorEventListener {

    companion object {
        private const val TAG = "WEO/IMU"
    }

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // RG_Glasses has Game Rotation Vector (QTI hardware) but NO magnetometer
    // and NO TYPE_ROTATION_VECTOR. Use Game Rotation Vector as primary source.
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    // Fallback: accelerometer-only tilt estimation (less accurate but always works)
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile
    override var pitchDeg: Float = 0f
        private set

    @Volatile
    override var rollDeg: Float = 0f
        private set

    @Volatile
    private var active = false

    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    // ---- fallback: accelerometer-only ----
    private val accelData = FloatArray(3)
    @Volatile private var hasAccel = false

    private val usingRotationSensor: Boolean get() = rotationSensor != null

    override fun start() {
        if (rotationSensor != null) {
            // Primary: Game Rotation Vector (accurate, no magnetometer needed)
            active = true
            sensorManager.registerListener(
                this, rotationSensor, SensorManager.SENSOR_DELAY_UI
            )
            Log.d(TAG, "IMU started (Game Rotation Vector)")
        } else if (accelerometer != null) {
            // Fallback: accelerometer-only tilt (less accurate, no heading)
            active = true
            hasAccel = false
            sensorManager.registerListener(
                this, accelerometer, SensorManager.SENSOR_DELAY_UI
            )
            Log.w(TAG, "IMU started (accelerometer-only fallback — no rotation sensor)")
        } else {
            Log.e(TAG, "No rotation sensor or accelerometer available — IMU disabled")
        }
    }

    override fun stop() {
        active = false
        sensorManager.unregisterListener(this)
        Log.d(TAG, "IMU stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !active) return

        when (event.sensor.type) {
            Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                // Convert rotation vector to matrix, then extract pitch/roll
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                // orientation[1] = pitch (radians), orientation[2] = roll (radians)
                pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
                rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                if (usingRotationSensor) return  // don't use fallback
                System.arraycopy(event.values, 0, accelData, 0, 3)
                hasAccel = true
                // Simple tilt from gravity vector
                val gx = accelData[0]
                val gy = accelData[1]
                val gz = accelData[2]
                pitchDeg = Math.toDegrees(Math.atan2(gy.toDouble(), gz.toDouble())).toFloat()
                rollDeg = Math.toDegrees(Math.atan2((-gx).toDouble(),
                    Math.sqrt((gy * gy + gz * gz).toDouble()))).toFloat()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
