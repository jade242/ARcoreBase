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

class BackgroundRenderer(context: Context) {
    private val TAG = BackgroundRenderer::class.java.simpleName

    //backGroundの頂点情報をfloat型の配列として定義する。
    private val QUAD_COORDS = floatArrayOf(
        -1.0f, -1.0f,
        1.0f, -1.0f,
        -1.0f, 1.0f,
        1.0f, 1.0f
    )

    private val CAMERA_VERTEX_SHADER_NAME = "shaders/screenquad.vert"
    private val CAMERA_FRAGMENT_SHADER_NAME = "shaders/screenquad.frag"
    private val COORDS_PER_VERTEX = 2
    private val TEXCOORDS_PER_VERTEX = 2
    private val FLOAT_SIZE = 4

    private var _program = 0
    private var _quadCords: FloatBuffer
    private var _quadTexCords: FloatBuffer
    private var _cameraPositionAttrib = 0
    private var _cameraTexCoordAttrib = 0
    private var _cameraTextureUniform = 0
    private var _cameraTextureId = -1

    init {
        //テクスチャを作成
        val textures = IntArray(1)//1つのテクスチャを作成
        GLES20.glGenTextures(1, textures, 0)//空きテクスチャIDを1つtexturesに代入してtexturesというテクスチャオブジェクトを生成
        _cameraTextureId = textures[0]//空きテクスチャIDをcameraTextureIdに代入。
        val textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES//たぶんカメラから取得するためのtargetType
        GLES20.glBindTexture(textureTarget, _cameraTextureId)//カメラのtargetTypeでテクスチャの次元を指定。
        // cameraTexutreIdというテクスチャIDの操作を行うことをOpenGLに伝える
        //テクスチャの各種設定
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        //縮小時の補間設定
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        //拡大時の補間設定
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        //描画用の座標を準備する
        val numVertices = FLOAT_SIZE
        val bbCords =
            ByteBuffer.allocateDirect(QUAD_COORDS.size * FLOAT_SIZE)
        bbCords.order(ByteOrder.nativeOrder())
        _quadCords = bbCords.asFloatBuffer().apply {
            put(QUAD_COORDS)
            position(0)
        }

        val bbTexCordsTransformed =
            ByteBuffer.allocateDirect(numVertices * TEXCOORDS_PER_VERTEX * FLOAT_SIZE)
        bbTexCordsTransformed.order(ByteOrder.nativeOrder())
        _quadTexCords = bbTexCordsTransformed.asFloatBuffer()

        //シェーダプログラムを作成
        val vertexShader: Int = Shader.loadGLShader(
            TAG, context,
            GLES20.GL_VERTEX_SHADER, CAMERA_VERTEX_SHADER_NAME
        )//バーテックスシェーダのコンパイル

        val fragmentShader: Int = Shader.loadGLShader(
            TAG, context,
            GLES20.GL_FRAGMENT_SHADER, CAMERA_FRAGMENT_SHADER_NAME
        )//フラグメントシェーダのコンパイル

        //プログラムオブジェクトの作成
        _program = GLES20.glCreateProgram()
        GLES20.glAttachShader(_program, vertexShader)
        GLES20.glAttachShader(_program, fragmentShader)

        //リンク
        GLES20.glLinkProgram(_program)
        GLES20.glUseProgram(_program)

        //"a_Positionというattribute変数がバーテックスシェーダプログラムで上から何番目に定義されているかを取得する。
        _cameraPositionAttrib = GLES20.glGetAttribLocation(_program, "a_Position")
        //"a_TexCoord"というattribute変数がバーテックスシェーダプログラムで上から何番目に定義されているかを取得する。
        _cameraTexCoordAttrib = GLES20.glGetAttribLocation(_program, "a_TexCoord")
        Shader.checkGLError(TAG, "Program creation")
        //"sTexture"というuniform型の変数がフラグメントシェーダプログラムで上から何番目に定義されているかを取得する。
        _cameraTextureUniform = GLES20.glGetUniformLocation(_program, "sTexture")
        Shader.checkGLError(TAG, "Program parameters")
    }

    fun getTextureId(): Int {
        return _cameraTextureId
    }

    //描画処理：GLRendererのonDrawFrameが呼ばれたときに行う描画処理
    fun draw(frame: Frame) {
        if (frame.hasDisplayGeometryChanged()) {
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                _quadCords,
                Coordinates2d.TEXTURE_NORMALIZED,
                _quadTexCords
            )
        }

        if (frame.timestamp == 0L) {
            return
        }

        _quadTexCords.position(0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)//テクスチャ0を有効化する。

        //カメラのtargetTypeでテクスチャの次元を指定。指定した名前のテクスチャを有効にする。
        //_cameraTextureIdはinit()で空きテクスチャID(テクスチャ0)を代入されている。
        //つまり、テクスチャ0に_cameraTextureIdをバインドして、このテクスチャIDの操作を行うことをOpenGLに伝える。
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, _cameraTextureId)
        GLES20.glUseProgram(_program)//シェーダープログラムを利用する
        GLES20.glUniform1i(_cameraTextureUniform, 0)//シェーダにuniform変数の番号とテクスチャIDを渡す。
        // 描画時にテクスチャユニット0番のテクスチャ(カメラ映像)を参照させるように設定

        GLES20.glEnableVertexAttribArray(_cameraPositionAttrib)//attribute属性を有効にする
        GLES20.glEnableVertexAttribArray(_cameraTexCoordAttrib)//attribute属性を有効にする

        GLES20.glVertexAttribPointer(//OpenGLからシェーダに頂点情報を渡す
            _cameraPositionAttrib,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            _quadCords
        )
        GLES20.glVertexAttribPointer(//OpenGLからシェーダに頂点情報を渡す。
            _cameraTexCoordAttrib,
            TEXCOORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            0,
            _quadTexCords
        )

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, FLOAT_SIZE)//モデルの描画

        GLES20.glDisableVertexAttribArray(_cameraPositionAttrib)//attribute属性を無効化
        GLES20.glDisableVertexAttribArray(_cameraTexCoordAttrib)//attribute属性を無効化
    }
}