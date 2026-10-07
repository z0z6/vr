package com.example.vrplayer

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.Matrix
import android.view.Surface

/**
 * Śledzenie ruchu głowy (żyroskop / wektor rotacji).
 * Zwraca macierz widoku w układzie OpenGL (oś Y w górę, kamera patrzy w -Z).
 */
class HeadTracker(
    context: Context,
    private val displayRotation: () -> Int
) : SensorEventListener {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? =
        sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val lock = Any()
    private val raw = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val tmp = FloatArray(16)
    private val remapped = FloatArray(16)

    val available: Boolean get() = sensor != null

    fun start() {
        sensor?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() = sm.unregisterListener(this)

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(tmp, event.values)
        val (ax, ay) = if (displayRotation() == Surface.ROTATION_270)
            SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
        else
            SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
        SensorManager.remapCoordinateSystem(tmp, ax, ay, remapped)
        synchronized(lock) { System.arraycopy(remapped, 0, raw, 0, 16) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /** Macierz widoku (kolumnowa, jak w android.opengl.Matrix). */
    fun getViewMatrix(out: FloatArray) {
        synchronized(lock) { System.arraycopy(raw, 0, out, 0, 16) }
        // świat czujnika (Z w górę) -> świat OpenGL (Y w górę)
        Matrix.rotateM(out, 0, 90f, 1f, 0f, 0f)
    }
}
