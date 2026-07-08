package com.skysense.app.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.atan2

/**
 * Manages the device's hardware compass using the Rotation Vector sensor.
 */
class CompassManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVectorSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _heading = MutableStateFlow(0f)
    val heading: StateFlow<Float> = _heading.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var isListening = false

    private var lastAzimuth = -1f
    private var continuousAzimuth = 0f

    fun start() {
        if (isListening || rotationVectorSensor == null) return
        sensorManager.registerListener(
            this,
            rotationVectorSensor,
            SensorManager.SENSOR_DELAY_UI
        )
        isListening = true
    }

    fun stop() {
        if (!isListening) return
        sensorManager.unregisterListener(this)
        isListening = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            
            // orientationAngles[0] is azimuth in radians (-pi to pi)
            val azimuthRadians = orientationAngles[0]
            var azimuthDegrees = Math.toDegrees(azimuthRadians.toDouble()).toFloat()
            
            if (azimuthDegrees < 0) {
                azimuthDegrees += 360f
            }

            if (lastAzimuth == -1f) {
                lastAzimuth = azimuthDegrees
                continuousAzimuth = azimuthDegrees
            } else {
                var delta = azimuthDegrees - lastAzimuth
                if (delta > 180f) delta -= 360f
                else if (delta < -180f) delta += 360f
                continuousAzimuth += delta
                lastAzimuth = azimuthDegrees
            }
            
            _heading.value = continuousAzimuth
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No action needed
    }
}
