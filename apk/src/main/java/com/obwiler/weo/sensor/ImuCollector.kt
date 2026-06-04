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

    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    @Volatile
    override var pitchDeg: Float = 0f
        private set

    @Volatile
    override var rollDeg: Float = 0f
        private set

    @Volatile
    private var active = false

    private val accelData = FloatArray(3)
    private val magnetData = FloatArray(3)
    @Volatile private var hasAccel = false
    @Volatile private var hasMagnet = false

    override fun start() {
        if (accelerometer == null || magnetometer == null) {
            Log.e(TAG, "Accelerometer or magnetometer not available")
            return
        }
        active = true
        hasAccel = false
        hasMagnet = false
        sensorManager.registerListener(
            this, accelerometer, SensorManager.SENSOR_DELAY_UI
        )
        sensorManager.registerListener(
            this, magnetometer, SensorManager.SENSOR_DELAY_UI
        )
        Log.d(TAG, "IMU started (accelerometer + magnetometer)")
    }

    override fun stop() {
        active = false
        sensorManager.unregisterListener(this)
        Log.d(TAG, "IMU stopped")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !active) return

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelData, 0, 3)
                hasAccel = true
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magnetData, 0, 3)
                hasMagnet = true
            }
        }

        if (hasAccel && hasMagnet) {
            val rotationMatrix = FloatArray(9)
            val inclinationMatrix = FloatArray(9)
            if (SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, accelData, magnetData)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                pitchDeg = Math.toDegrees(orientation[1].toDouble()).toFloat()
                rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}