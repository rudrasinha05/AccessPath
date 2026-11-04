package com.accesspath.ui.mapping

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.accesspath.domain.TrailPoint
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.hypot

/** Collects the walked trail from ARCore camera poses. Written on the GL thread, read on the main thread. */
class TrailRecorder {
    private val lock = Any()
    private val points = mutableListOf<TrailPoint>()
    private val places = mutableListOf<Pair<Int, Pair<String, String>>>()
    @Volatile var paused = false
    @Volatile var tracking = false; private set
    @Volatile var distance = 0.0; private set

    fun onPose(x: Double, z: Double, isTracking: Boolean) {
        tracking = isTracking
        if (!isTracking || paused) return
        synchronized(lock) {
            val last = points.lastOrNull()
            if (last == null) { points += TrailPoint(x, z); return }
            val step = hypot(x - last.x, z - last.z)
            // Ignore jitter below 30 cm and jumps above 3 m (tracking glitches).
            if (step in 0.3..3.0) { points += TrailPoint(x, z); distance += step }
        }
    }

    fun addPlace(name: String, type: String) = synchronized(lock) { if (points.isNotEmpty()) places += (points.lastIndex to (name to type)) }

    fun snapshot(): Triple<List<TrailPoint>, List<Pair<Int, Pair<String, String>>>, Double> =
        synchronized(lock) { Triple(points.toList(), places.toList(), distance) }
}

/** Draws the camera image and feeds the camera position to [recorder] every frame. */
class ArCameraRenderer(
    private val session: Session,
    private val rotation: () -> Int,
    private val recorder: TrailRecorder
) : GLSurfaceView.Renderer {
    private var textureId = -1
    private var program = 0
    private var positionAttr = 0
    private var texCoordAttr = 0
    private var width = 0
    private var height = 0
    private var geometryChanged = false

    private fun buffer(n: Int): FloatBuffer = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val quad: FloatBuffer = buffer(8).apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }
    private val texCoords: FloatBuffer = buffer(8)

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        textureId = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        program = link(
            "attribute vec4 a_Position; attribute vec2 a_TexCoord; varying vec2 v_TexCoord;" +
                "void main() { gl_Position = a_Position; v_TexCoord = a_TexCoord; }",
            "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float; varying vec2 v_TexCoord; uniform samplerExternalOES sTexture;" +
                "void main() { gl_FragColor = texture2D(sTexture, v_TexCoord); }"
        )
        positionAttr = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordAttr = GLES20.glGetAttribLocation(program, "a_TexCoord")
        session.setCameraTextureName(textureId)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
        width = w; height = h; geometryChanged = true
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (geometryChanged) { session.setDisplayGeometry(rotation(), width, height); geometryChanged = false }
        val frame = try { session.update() } catch (e: Exception) { Log.w(TAG, "AR update failed", e); return }
        if (frame.hasDisplayGeometryChanged()) {
            quad.position(0); texCoords.position(0)
            frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quad, Coordinates2d.TEXTURE_NORMALIZED, texCoords)
            quad.position(0); texCoords.position(0)
        }
        if (frame.timestamp != 0L) {
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glVertexAttribPointer(positionAttr, 2, GLES20.GL_FLOAT, false, 0, quad)
            GLES20.glVertexAttribPointer(texCoordAttr, 2, GLES20.GL_FLOAT, false, 0, texCoords)
            GLES20.glEnableVertexAttribArray(positionAttr)
            GLES20.glEnableVertexAttribArray(texCoordAttr)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(positionAttr)
            GLES20.glDisableVertexAttribArray(texCoordAttr)
        }
        val camera = frame.camera
        val pose = camera.pose
        recorder.onPose(pose.tx().toDouble(), pose.tz().toDouble(), camera.trackingState == TrackingState.TRACKING)
    }

    private fun link(vs: String, fs: String): Int {
        fun compile(type: Int, src: String) = GLES20.glCreateShader(type).also { GLES20.glShaderSource(it, src); GLES20.glCompileShader(it) }
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, compile(GLES20.GL_VERTEX_SHADER, vs))
            GLES20.glAttachShader(it, compile(GLES20.GL_FRAGMENT_SHADER, fs))
            GLES20.glLinkProgram(it)
        }
    }

    private companion object { const val TAG = "ArCameraRenderer" }
}
