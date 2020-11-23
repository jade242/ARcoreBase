package com.example.arcorebase.renderer

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.example.arcorebase.shader.Shader
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class BackgroundRenderer {
    private val TAG = BackgroundRenderer::class.java.simpleName
    private val QUAD_COORDS = floatArrayOf(
        -1.0f,-1.0f,
        1.0f,-1.0f,
        -1.0f,1.0f,
        1.0f,1.0f
    )

    private val CAMERA_VERTEX_SHADER_NAME = "shaders/screenquad.vert"
    private val CAMERA_FRAGMENT_SHADER_NAME = "shaders/screenquad.frag"
    private val COORDS_PER_VERTEX = 2
    private val TEXCOORDS_PER_VERTEX = 2
    private val FLOAT_SIZE = 4

    private var program = 0
    private var quadCords: FloatBuffer
    private var quadTexCords: FloatBuffer
    private var cameraPositionAttrib = 0
    private var cameraTexCoordAttrib = 0
    private var cameraTextureUniform = 0
    private var cameraTextureId = -1

    constructor(context: Context){
        //テクスチャを作成
        val textures = IntArray(1)
        GLES20.glGenTextures(1,textures,0)
        cameraTextureId = textures[0]
        val textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        GLES20.glBindTexture(textureTarget, cameraTextureId)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        //描画用の座標を準備する
        val numVertices = FLOAT_SIZE
        val bbCords =
            ByteBuffer.allocateDirect(QUAD_COORDS.size * FLOAT_SIZE)
        bbCords.order(ByteOrder.nativeOrder())
        quadCords = bbCords.asFloatBuffer().apply {
            put(QUAD_COORDS)
            position(0)
        }

        val bbTexCordsTransformed =
            ByteBuffer.allocateDirect(numVertices * TEXCOORDS_PER_VERTEX * FLOAT_SIZE)
        bbTexCordsTransformed.order(ByteOrder.nativeOrder())
        quadTexCords = bbTexCordsTransformed.asFloatBuffer()

        //シェーダプログラムを作成
        val vertexShader: Int = Shader.loadGLShader(TAG, context,
            GLES20.GL_VERTEX_SHADER, CAMERA_VERTEX_SHADER_NAME)

        val fragmentShader: Int = Shader.loadGLShader(TAG, context,
            GLES20.GL_FRAGMENT_SHADER, CAMERA_FRAGMENT_SHADER_NAME)

        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        GLES20.glUseProgram(program)
        cameraPositionAttrib = GLES20.glGetAttribLocation(program, "a_Position")
        cameraTexCoordAttrib = GLES20.glGetAttribLocation(program, "a_TexCoord")
        Shader.checkGLError(TAG, "Program creation")
        cameraTextureUniform = GLES20.glGetUniformLocation(program, "sTexture")
        Shader.checkGLError(TAG, "Program parameters")
    }

    fun getTextureId(): Int {
        return cameraTextureId
    }

//描画処理：GLRendererのonDrawFrameが呼ばれたときに行う描画処理
    fun draw(frame: Frame) {
        if (frame.hasDisplayGeometryChanged()) {
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                quadCords,
                Coordinates2d.TEXTURE_NORMALIZED,
                quadTexCords
            )
        }

        if (frame.timestamp == 0L) {
            return
        }

        quadTexCords.position(0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES20.glUseProgram(program)
        GLES20.glUniform1i(cameraTextureUniform, 0)
        GLES20.glVertexAttribPointer(
            cameraPositionAttrib,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            quadCords
        )
        GLES20.glVertexAttribPointer(
            cameraTexCoordAttrib,
            TEXCOORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            quadTexCords
        )
        GLES20.glEnableVertexAttribArray(cameraPositionAttrib)
        GLES20.glEnableVertexAttribArray(cameraTexCoordAttrib)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, FLOAT_SIZE)

        GLES20.glDisableVertexAttribArray(cameraPositionAttrib)
        GLES20.glDisableVertexAttribArray(cameraTexCoordAttrib)
    }
}