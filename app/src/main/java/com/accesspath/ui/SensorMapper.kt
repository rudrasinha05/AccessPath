package com.accesspath.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.accesspath.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class SensorMapper(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val rotSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val _session = MutableStateFlow(MappingSession(id = UUID.randomUUID().toString()))
    val session = _session.asStateFlow()

    private var currentHeadingRad = 0.0
    private var confidence = 0.95f

    fun startSession(floorId: String, consent: MappingConsent) {
        _session.value = MappingSession(
            id = UUID.randomUUID().toString(),
            floorId = floorId,
            status = MappingStatus.RECORDING,
            consent = consent,
            currentConfidence = confidence
        )
        stepSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        rotSensor?.let { sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun pauseSession() {
        if (_session.value.status == MappingStatus.RECORDING) {
            _session.value = _session.value.copy(status = MappingStatus.PAUSED)
        }
    }

    fun resumeSession() {
        if (_session.value.status == MappingStatus.PAUSED) {
            _session.value = _session.value.copy(status = MappingStatus.RECORDING)
        }
    }

    fun stopSession() {
        sensorManager?.unregisterListener(this)
        _session.value = _session.value.copy(status = MappingStatus.STOPPED)
    }

    fun recordStep(stepLength: Double = 0.75) {
        _session.value = _session.value.addStep(
            stepLength = stepLength,
            headingRad = currentHeadingRad,
            confidence = confidence,
            now = System.currentTimeMillis()
        )
    }

    fun recordObstacle(distance: Double = 1.0) {
        _session.value = _session.value.recordObstacle(distance, currentHeadingRad)
    }

    fun setSimulatedConfidence(newConfidence: Float) {
        confidence = newConfidence.coerceIn(0.0f, 1.0f)
        _session.value = _session.value.copy(
            currentConfidence = confidence,
            trackingLost = confidence < 0.45f
        )
    }

    fun setHeadingDegrees(deg: Double) {
        currentHeadingRad = Math.toRadians(deg)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || _session.value.status != MappingStatus.RECORDING) return
        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                recordStep(0.75)
            }
            Sensor.TYPE_ROTATION_VECTOR -> {
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                currentHeadingRad = orientation[0].toDouble()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        confidence = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> 0.98f
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> 0.85f
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> 0.60f
            else -> 0.35f
        }
        _session.value = _session.value.copy(
            currentConfidence = confidence,
            trackingLost = confidence < 0.45f
        )
    }
}
