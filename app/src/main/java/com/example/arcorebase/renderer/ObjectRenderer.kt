package com.example.arcorebase.renderer

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import com.example.arcorebase.shader.Shader
import de.javagl.obj.Obj
import de.javagl.obj.ObjData
import de.javagl.obj.ObjReader
import de.javagl.obj.ObjUtils
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import java.nio.ShortBuffer


class ObjectRenderer {
    private val TAG = ObjectRenderer::class.java.simpleName

    // Object vertex buffer variables.
    private var _vertexBufferId = 0
    private var _verticesBaseAddress = 0
    private var _texCoordsBaseAddress = 0
    private var _normalsBaseAddress = 0
    private var _indexBufferId = 0
    private var _indexCount = 0

    private var _program = 0
    private var _textures = IntArray(1)
    // Shader location: model view projection matrix.
    private var _modelViewUniform = 0
    private var _modelViewProjectionUniform = 0

    // Shader location: object attributes.
    private var _positionAttribute = 0
    private var _normalAttribute = 0
    private var _texCoordAttribute = 0

    // Shader location: texture sampler.
    private var _textureUniform = 0

    // Shader location: environment properties.
    private var _lightingParametersUniform = 0

    // Shader location: material properties.
    private var _materialParametersUniform = 0

    // Shader location: color correction property
    private var _colorCorrectionParameterUniform = 0

    // Shader location: color tinting
    private var _colorTintParameterUniform = 0

    // Shader names.
    private val VERTEX_SHADER_NAME = "shaders/object.vert"
    private val FRAGMENT_SHADER_NAME = "shaders/object.frag"

    private lateinit var _blendMode:BlendMode

    // Temporary matrices allocated here to reduce number of allocations for each frame.
    private val _modelMatrix = FloatArray(16)
    private val _modelViewMatrix = FloatArray(16)
    private val _modelViewProjectionMatrix = FloatArray(16)

    // Set some default material properties to use for lighting.
    private var _ambient = 0.3f
    private var _diffuse = 1.0f
    private var _specular = 1.0f
    private var _specularPower = 6.0f

    enum class BlendMode {
        /** Multiplies the destination color by the source alpha.  */
        Shadow,

        /** Normal alpha blending.  */
        SourceAlpha
    }

    private val COORDS_PER_VERTEX = 3

    // Note: the last component must be zero to avoid applying the translational part of the matrix.
    private val LIGHT_DIRECTION = floatArrayOf(0.250f, 0.866f, 0.433f, 0.0f)

    // No tint color
    private val ZERO_COLOR_TINT = floatArrayOf(0.0f, 0.0f, 0.0f, 0.0f) // No tinting by default

    private val _viewLightDirection = FloatArray(4)

    /**
     * modelのレンダリングに必要なOpenGLリソースの初期化
     */
    fun createOnGlThread(context: Context, objAssetName: String, diffuseTextrureAssetName: String)
    {
        try {
            val textureBitmap =
                    BitmapFactory.decodeStream(context.assets.open(diffuseTextrureAssetName))
            _textures = IntArray(1)//1つのテクスチャを作成
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)//テクスチャ0を有効化する。
            GLES20.glGenTextures(_textures.size, _textures, 0)//空きテクスチャIDを1つtexturesに代入してtexturesというテクスチャオブジェクトを生成
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _textures[0])//2DのtargetTypeでテクスチャの次元を指定。
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR
            )
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textureBitmap, 0)
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

            textureBitmap.recycle()
            Shader.checkGLError(TAG, "Texture loading")

            val obj = readObjFile(context, objAssetName)
            //直接バッファとして、OBJからデータを取得する。
            val wideIndices = ObjData.getFaceVertexIndices(obj, 3)
            val vertices = ObjData.getVertices(obj)
            val texCoords = ObjData.getTexCoords(obj, 2)
            val normals = ObjData.getNormals(obj)
            val indices = convertIndicesToShorts(wideIndices)
            val buffers = IntArray(2)
            GLES20.glGenBuffers(2, buffers, 0)
            _vertexBufferId = buffers[0]
            _indexBufferId = buffers[1]

            // Load vertex buffer

            // Load vertex buffer
            _verticesBaseAddress = 0
            _texCoordsBaseAddress = _verticesBaseAddress + 4 * vertices.limit()
            _normalsBaseAddress = _texCoordsBaseAddress + 4 * texCoords.limit()
            val totalBytes = _normalsBaseAddress + 4 * normals.limit()

            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, _vertexBufferId)
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, totalBytes, null, GLES20.GL_STATIC_DRAW)
            GLES20.glBufferSubData(
                GLES20.GL_ARRAY_BUFFER, _verticesBaseAddress, 4 * vertices.limit(), vertices
            )
            GLES20.glBufferSubData(
                GLES20.GL_ARRAY_BUFFER, _texCoordsBaseAddress, 4 * texCoords.limit(), texCoords
            )
            GLES20.glBufferSubData(
                GLES20.GL_ARRAY_BUFFER, _normalsBaseAddress, 4 * normals.limit(), normals
            )
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

            // Load index buffer

            // Load index buffer
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, _indexBufferId)
            _indexCount = indices.limit()
            GLES20.glBufferData(
                GLES20.GL_ELEMENT_ARRAY_BUFFER, 2 * _indexCount, indices, GLES20.GL_STATIC_DRAW
            )
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
            Shader.checkGLError(TAG, "OBJ buffer load")
            val vertexShader: Int = Shader.loadGLShader(
                TAG,
                context,
                GLES20.GL_VERTEX_SHADER,
                VERTEX_SHADER_NAME
            )
            val fragmentShader: Int = Shader.loadGLShader(
                TAG,
                context,
                GLES20.GL_FRAGMENT_SHADER,
                FRAGMENT_SHADER_NAME
            )

            _program = GLES20.glCreateProgram()
            GLES20.glAttachShader(_program, vertexShader)
            GLES20.glAttachShader(_program, fragmentShader)
            GLES20.glLinkProgram(_program)
            GLES20.glUseProgram(_program)

            Shader.checkGLError(TAG, "Program creation")

            _modelViewUniform = GLES20.glGetUniformLocation(_program, "u_ModelView")
            _modelViewProjectionUniform =
                GLES20.glGetUniformLocation(_program, "u_ModelViewProjection")

            _positionAttribute = GLES20.glGetAttribLocation(_program, "a_Position")
            _normalAttribute = GLES20.glGetAttribLocation(_program, "a_Normal")
            _texCoordAttribute = GLES20.glGetAttribLocation(_program, "a_TexCoord")

            _textureUniform = GLES20.glGetUniformLocation(_program, "u_Texture")

            _lightingParametersUniform = GLES20.glGetUniformLocation(
                _program,
                "u_LightingParameters"
            )
            _materialParametersUniform = GLES20.glGetUniformLocation(
                _program,
                "u_MaterialParameters"
            )
            _colorCorrectionParameterUniform =
                GLES20.glGetUniformLocation(_program, "u_ColorCorrectionParameters")
            _colorTintParameterUniform =
                GLES20.glGetUniformLocation(_program, "u_ColorTintParameters")

            Shader.checkGLError(TAG, "Program parameters")

            Matrix.setIdentityM(_modelMatrix, 0)

        }catch (e: IOException){}
    }

    private fun convertIndicesToShorts(wideIndices: IntBuffer?): ShortBuffer {
        val indices = ByteBuffer.allocateDirect(2 * wideIndices!!.limit())
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
        while (wideIndices.hasRemaining()) {
            indices.put(wideIndices.get().toShort())
        }
        indices.rewind()
        return indices
    }


    private fun readObjFile(context: Context, objAssetName: String): Obj? {
        val objInputStream = context.assets.open(objAssetName)
        val obj = ObjReader.read(objInputStream)
        //構造がOpenGLでのレンダリングに適しているようにObjを準備する。
        return ObjUtils.convertToRenderable(obj)

    }
    fun setBlendMode(blendMode: BlendMode) {
        _blendMode = blendMode
    }

    /**
     * Updates the object model matrix and applies scaling.
     *
     * @param modelMatrix A 4x4 model-to-world transformation matrix, stored in column-major order.
     * @param scaleFactor A separate scaling factor to apply before the `modelMatrix`.
     * @see android.opengl.Matrix
     */
    fun updateModelMatrix(modelMatrix: FloatArray?, scaleFactor: Float) {
        val scaleMatrix = FloatArray(16)
        Matrix.setIdentityM(scaleMatrix, 0)
        scaleMatrix[0] = scaleFactor
        scaleMatrix[5] = scaleFactor
        scaleMatrix[10] = scaleFactor
        Matrix.multiplyMM(modelMatrix, 0, modelMatrix, 0, scaleMatrix, 0)
    }


    fun setMaterialProperties(
        ambient: Float, diffuse: Float, specular: Float, specularPower: Float
    ) {
        _ambient = ambient
        _diffuse = diffuse
        _specular = specular
        _specularPower = specularPower
    }

    fun draw(
        cameraView: FloatArray,
        cameraPerspective: FloatArray,
        colorCorrectionRgba: FloatArray,
        colorTintRgba: FloatArray
    )
    {
        Shader.checkGLError(TAG, "Before draw")
        //オブジェクトの位置と光を計算するためのModelView行列とModelViewProjection行列を作成する。

        // Build the ModelView and ModelViewProjection matrices
        // for calculating object position and light.
        Matrix.multiplyMM(_modelViewMatrix, 0, cameraView, 0, _modelMatrix, 0)
        Matrix.multiplyMM(_modelViewProjectionMatrix, 0, cameraPerspective, 0, _modelViewMatrix, 0)

        GLES20.glUseProgram(_program)

        //ライティング環境プロパティを設定する

        // Set the lighting environment properties.
        Matrix.multiplyMV(
            _viewLightDirection,
            0,
            _modelViewMatrix,
            0,
            LIGHT_DIRECTION,
            0
        )
        normalizeVec3(_viewLightDirection)
        GLES20.glUniform4f(
            _lightingParametersUniform,
            _viewLightDirection.get(0),
            _viewLightDirection.get(1),
            _viewLightDirection.get(2),
            1f
        )

        GLES20.glUniform4f(
            _colorCorrectionParameterUniform,
            colorCorrectionRgba[0],
            colorCorrectionRgba[1],
            colorCorrectionRgba[2],
            colorCorrectionRgba[3]
        )

        GLES20.glUniform4f(
            _colorTintParameterUniform,
            colorTintRgba[0],
            colorTintRgba[1],
            colorTintRgba[2],
            colorTintRgba[3]
        )

        //object materialプロパティの設定

        // Set the object material properties.
        GLES20.glUniform4f(
            _materialParametersUniform,
            _ambient,
            _diffuse,
            _specular,
            _specularPower
        )

        //Attach the object texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, _textures[0])
        GLES20.glUniform1i(_textureUniform, 0)

        //Set the vertex attributes
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, _vertexBufferId)

        GLES20.glVertexAttribPointer(
            _positionAttribute, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, _verticesBaseAddress
        )
        GLES20.glVertexAttribPointer(
            _normalAttribute,
            3,
            GLES20.GL_FLOAT,
            false,
            0,
            _normalsBaseAddress
        )
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        // Set the ModelViewProjection matrix in the shader.
        GLES20.glUniformMatrix4fv(_modelViewUniform, 1, false, _modelViewMatrix, 0)
        GLES20.glUniformMatrix4fv(
            _modelViewProjectionUniform,
            1,
            false,
            _modelViewProjectionMatrix,
            0
        )

        //Enable vertex arrays
        GLES20.glEnableVertexAttribArray(_positionAttribute)
        GLES20.glEnableVertexAttribArray(_normalAttribute)
        GLES20.glEnableVertexAttribArray(_texCoordAttribute)

        GLES20.glDepthMask(false)
        GLES20.glEnable(GLES20.GL_BLEND)
        when(_blendMode){
            BlendMode.Shadow -> {
                //Multiplicative blending function for Shadow.
                GLES20.glBlendFunc(GLES20.GL_ZERO, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            }
            BlendMode.SourceAlpha -> {
                //SourceAlpha, additive blending function.
                GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            }
        }

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, _indexBufferId)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, _indexCount, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)

        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)

        //Disable vertex arrays
        GLES20.glDisableVertexAttribArray(_positionAttribute)
        GLES20.glDisableVertexAttribArray(_normalAttribute)
        GLES20.glDisableVertexAttribArray(_texCoordAttribute)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        Shader.checkGLError(TAG, "AFTER draw")

    }


    fun draw(cameraView: FloatArray, cameraPerspective: FloatArray, colorCorrectionRbga: FloatArray){
        draw(cameraView, cameraPerspective, colorCorrectionRbga, ZERO_COLOR_TINT)
    }

    private fun normalizeVec3(v: FloatArray) {
        val reciprocalLength =
            1.0f / Math.sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble())
                .toFloat()
        v[0] *= reciprocalLength
        v[1] *= reciprocalLength
        v[2] *= reciprocalLength
    }

}