package com.example.vrplayer

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2
import kotlin.math.tan

/**
 * Renderer wideo 360° (equirectangular, mono).
 *
 * Zamiast siatki sfery rysujemy pełnoekranowy quad na oko, a fragment shader
 * wylicza kierunek promienia dla każdego piksela i próbkuje teksturę wideo.
 * Dzięki temu korekcja zniekształceń soczewek Cardboard to kilka linijek w shaderze.
 */
class VrRenderer(
    private val tracker: HeadTracker,
    private val onVideoSurface: (Surface) -> Unit
) : GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {

    /** Tryb podzielonego ekranu (dwa oczy). */
    @Volatile var stereo = true
    /** Korekcja zniekształceń soczewek (tylko w trybie stereo). */
    @Volatile var lensCorrection = true
    @Volatile private var recenterRequested = false
    @Volatile private var frameReady = false

    fun recenter() { recenterRequested = true }

    private var program = 0
    private var texId = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var videoSurface: Surface? = null

    private val stMatrix = FloatArray(16).also { Matrix.setIdentityM(it, 0) }
    private val view = FloatArray(16)
    private val rot3 = FloatArray(9)
    private var yawOffset = 0f
    private var width = 1
    private var height = 1

    private val quad = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder())
        .asFloatBuffer().apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }

    // Locations
    private var aPos = 0
    private var uTex = 0; private var uST = 0; private var uRot = 0; private var uTan = 0; private var uK = 0

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram(VERTEX, FRAGMENT)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        uTex = GLES20.glGetUniformLocation(program, "uTex")
        uST = GLES20.glGetUniformLocation(program, "uST")
        uRot = GLES20.glGetUniformLocation(program, "uRot")
        uTan = GLES20.glGetUniformLocation(program, "uTan")
        uK = GLES20.glGetUniformLocation(program, "uK")

        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        texId = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        videoSurface?.release()
        surfaceTexture?.release()
        val st = SurfaceTexture(texId)
        st.setOnFrameAvailableListener(this)
        surfaceTexture = st
        val surface = Surface(st)
        videoSurface = surface
        onVideoSurface(surface)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w; height = h
    }

    override fun onFrameAvailable(st: SurfaceTexture?) { frameReady = true }

    override fun onDrawFrame(gl: GL10?) {
        val st = surfaceTexture ?: return
        if (frameReady) {
            frameReady = false
            st.updateTexImage()
            st.getTransformMatrix(stMatrix)
        }

        tracker.getViewMatrix(view)
        if (recenterRequested) {
            recenterRequested = false
            // bieżący kierunek patrzenia (yaw) staje się "przodem"
            yawOffset = Math.toDegrees(atan2(-view[2].toDouble(), view[10].toDouble())).toFloat()
        }
        Matrix.rotateM(view, 0, -yawOffset, 0f, 1f, 0f)

        // kamera -> świat = transpozycja części rotacyjnej macierzy widoku
        for (j in 0..2) for (i in 0..2) rot3[3 * j + i] = view[4 * i + j]

        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glUniformMatrix4fv(uST, 1, false, stMatrix, 0)
        GLES20.glUniformMatrix3fv(uRot, 1, false, rot3, 0)

        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quad)

        if (stereo) {
            val half = width / 2
            drawEye(0, half, FOV_STEREO, lensCorrection)
            drawEye(half, width - half, FOV_STEREO, lensCorrection)
        } else {
            drawEye(0, width, FOV_MONO, false)
        }
    }

    private fun drawEye(x: Int, w: Int, fovDeg: Float, lens: Boolean) {
        GLES20.glViewport(x, 0, w, height)
        val t = tan(Math.toRadians(fovDeg / 2.0)).toFloat()
        GLES20.glUniform2f(uTan, t * w / height, t)
        if (lens) GLES20.glUniform2f(uK, K1, K2) else GLES20.glUniform2f(uK, 0f, 0f)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun release() {
        videoSurface?.release(); videoSurface = null
        surfaceTexture?.release(); surfaceTexture = null
    }

    private fun buildProgram(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "Shader error: " + GLES20.glGetShaderInfoLog(s) }
            return s
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        return p
    }

    companion object {
        /** Pionowe FOV na oko w trybie gogli i w trybie "okna" na telefonie. */
        const val FOV_STEREO = 95f
        const val FOV_MONO = 80f
        /** Współczynniki dystorsji beczkowej (kompensacja soczewek). Dostosuj do swoich gogli. */
        const val K1 = 0.18f
        const val K2 = 0.06f

        private const val VERTEX = """
            attribute vec2 aPos;
            varying vec2 vPos;
            void main() {
                vPos = aPos;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
        """

        private const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision highp float;
            uniform samplerExternalOES uTex;
            uniform mat4 uST;
            uniform mat3 uRot;
            uniform vec2 uTan;
            uniform vec2 uK;
            varying vec2 vPos;
            const float PI = 3.14159265359;
            void main() {
                vec2 p = vPos;
                float r2 = dot(p, p);
                p *= 1.0 + uK.x * r2 + uK.y * r2 * r2;
                vec3 d = normalize(vec3(p.x * uTan.x, p.y * uTan.y, -1.0));
                vec3 w = uRot * d;
                float lon = atan(w.x, -w.z);
                float lat = asin(clamp(w.y, -1.0, 1.0));
                vec2 uv = vec2(0.5 + lon / (2.0 * PI), 0.5 + lat / PI);
                vec2 t = (uST * vec4(uv, 0.0, 1.0)).xy;
                gl_FragColor = texture2D(uTex, t);
            }
        """
    }
}
