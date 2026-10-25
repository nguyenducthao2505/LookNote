package com.looknote.cam.ui

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.looknote.cam.lut.LutBuilder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Ve khung ngam camera qua 3D LUT cua cong thuc (GPU), de thay mau truc tiep.
 */
class LutPreviewRenderer(
    private val view: GLSurfaceView,
    private val onTexture: (SurfaceTexture) -> Unit,
) : GLSurfaceView.Renderer {

    private var program = 0
    private var camTex = 0
    private var lutTex = 0
    private var st: SurfaceTexture? = null
    private val texMatrix = FloatArray(16)
    @Volatile private var pendingLut: IntArray? = LutBuilder.build(null)
    @Volatile private var lutEnabled = 0f
    @Volatile private var currentLut: IntArray? = null

    private val quad: FloatBuffer = ByteBuffer.allocateDirect(4 * 16).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f,
        )); position(0)
    }

    private val vs = """
        attribute vec4 aPos;
        attribute vec2 aTex;
        uniform mat4 uTexMatrix;
        varying vec2 vTex;
        void main() {
            gl_Position = vec4(aPos.xy, 0.0, 1.0);
            vTex = (uTexMatrix * vec4(aTex, 0.0, 1.0)).xy;
        }
    """.trimIndent()

    private val fs = """
        #extension GL_OES_EGL_image_external : require
        precision highp float;
        varying vec2 vTex;
        uniform samplerExternalOES sCam;
        uniform sampler2D sLut;
        uniform float uN;
        uniform float uUse;
        vec3 lut(vec3 c) {
            float n = uN;
            float b = clamp(c.b, 0.0, 1.0) * (n - 1.0);
            float b0 = floor(b);
            float b1 = min(b0 + 1.0, n - 1.0);
            float f = b - b0;
            float x = (clamp(c.r, 0.0, 1.0) * (n - 1.0) + 0.5) / (n * n);
            float y = (clamp(c.g, 0.0, 1.0) * (n - 1.0) + 0.5) / n;
            vec3 a = texture2D(sLut, vec2(x + b0 / n, y)).rgb;
            vec3 d = texture2D(sLut, vec2(x + b1 / n, y)).rgb;
            return mix(a, d, f);
        }
        void main() {
            vec4 c = texture2D(sCam, vTex);
            gl_FragColor = vec4(mix(c.rgb, lut(c.rgb), uUse), 1.0);
        }
    """.trimIndent()

    /** Doi LUT (null = khong ap mau). Goi tu bat ky luong nao. */
    fun setLut(lut: IntArray?) {
        if (lut == null) { lutEnabled = 0f } else { currentLut = lut; pendingLut = lut; lutEnabled = 1f }
        view.requestRender()
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("Shader: " + GLES20.glGetShaderInfoLog(s))
        return s
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, compile(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(program, compile(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(program)

        val t = IntArray(2)
        GLES20.glGenTextures(2, t, 0)
        camTex = t[0]; lutTex = t[1]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, camTex)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTex)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        pendingLut = currentLut ?: LutBuilder.build(null)

        val surfaceTexture = SurfaceTexture(camTex)
        surfaceTexture.setOnFrameAvailableListener { view.requestRender() }
        st = surfaceTexture
        view.post { onTexture(surfaceTexture) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    private fun uploadLut(lut: IntArray) {
        val n = LutBuilder.SIZE
        val w = n * n
        val buf = ByteBuffer.allocateDirect(w * n * 4).order(ByteOrder.nativeOrder())
        for (p in lut) {
            buf.put(((p shr 16) and 255).toByte()); buf.put(((p shr 8) and 255).toByte())
            buf.put((p and 255).toByte()); buf.put(255.toByte())
        }
        buf.position(0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTex)
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, n, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
    }

    override fun onDrawFrame(gl: GL10?) {
        val s = st ?: return
        runCatching { s.updateTexImage() }
        s.getTransformMatrix(texMatrix)
        pendingLut?.let { uploadLut(it); pendingLut = null }

        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)
        val aPos = GLES20.glGetAttribLocation(program, "aPos")
        val aTex = GLES20.glGetAttribLocation(program, "aTex")
        quad.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(aPos)
        quad.position(2)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 16, quad)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program, "uTexMatrix"), 1, false, texMatrix, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, camTex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sCam"), 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTex)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sLut"), 1)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uN"), LutBuilder.SIZE.toFloat())
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program, "uUse"), lutEnabled)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun release() {
        st?.release(); st = null
    }
}
