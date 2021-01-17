package com.example.arcorebase.renderer

import android.app.Activity
import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import android.util.Pair
import android.view.WindowManager
import com.google.ar.core.*
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.time.seconds

@Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
class GLRenderer : GLSurfaceView.Renderer {
    private val TAG: String = GLRenderer::class.java.simpleName
    private lateinit var _activity: Activity
    private var _session: Session? = null
    private lateinit var _backgroundRenderer: BackgroundRenderer
    private lateinit var _augmentedImageRenderer: AugmentedImageRenderer

    private var _viewportChanged = false
    private var _viewportWidth = 0
    private var _viewportHeight = 0
    private var _augmentedImageMap: MutableMap<Int, Pair<AugmentedImage, Anchor>> = HashMap()

    fun setSession(session: Session){
        _session = session
    }

    fun setActivity(activity: Activity){
        _activity = activity
    }

    override fun onSurfaceCreated(p0: GL10?, p1: EGLConfig?) {//初期化時
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)//背景色の指定。
        _backgroundRenderer = BackgroundRenderer(_activity)
        _augmentedImageRenderer = AugmentedImageRenderer(_activity)
        _augmentedImageRenderer.createOnGlThread()
    }

    override fun onSurfaceChanged(unuse: GL10?, width: Int, height: Int){
        GLES20.glViewport(0, 0, width, height)//表示領域設定
        _viewportWidth = width
        _viewportHeight = height
        _viewportChanged = true
    }

    override fun onDrawFrame(p0: GL10?) {
        //背景をglClearColorで指定した色でクリアする
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

//        GLES20.glClearColor(0.0f,0.0f,0.0f,1.0f)//背景色の指定

        if (_session == null) {
            return
        }

        if(_viewportChanged){

            val display = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                _activity.display
            } else {
                val windowManager = _activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager.defaultDisplay
            }
            val displayRotation = display?.rotation
            if (displayRotation != null) {
                _session!!.setDisplayGeometry(displayRotation, _viewportWidth, _viewportHeight)
            }
            _viewportChanged = false
        }

        try {

            _session!!.setCameraTextureName(_backgroundRenderer.getTextureId())
            val frame = _session!!.update()
            val camera = frame.camera
            _backgroundRenderer.draw(frame)

            // Get projection matrix.
            val projectionMatrix = FloatArray(16)
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)

            // Get camera matrix and draw.
            val viewMatrix = FloatArray(16)
            camera.getViewMatrix(viewMatrix, 0)

            // Compute lighting from average intensity of the image.
            val colorCorrectionRgba = FloatArray(4)
            frame.lightEstimate.getColorCorrection(colorCorrectionRgba, 0)

            // Visualize augmented images.
            drawAugmentedImages(frame, projectionMatrix, viewMatrix, colorCorrectionRgba)

        }catch (t: Throwable){
            Log.e(TAG, "Exception on the OpenGL thread", t)
        }
    }

    private fun drawAugmentedImages(
        frame: Frame?,
        projectionMatrix: FloatArray,
        viewMatrix: FloatArray,
        colorCorrectionRgba: FloatArray
    ) {
        val updatedAugmentedImages = frame!!.getUpdatedTrackables(
            AugmentedImage::class.java
        )

        for (augmentedImage: AugmentedImage in updatedAugmentedImages) {
            when (augmentedImage.trackingState) {
                TrackingState.PAUSED -> break
                TrackingState.TRACKING -> {
//                    _activity.runOnUiThread(
//                        Runnable {
//                            fun run() {
//
//                            }
//                        })
                    // Create a new anchor for newly found images.
                    if (!_augmentedImageMap.containsKey(augmentedImage.index)) {
                        val centerPoseAnchor =
                            augmentedImage.createAnchor(augmentedImage.centerPose)
                        _augmentedImageMap[augmentedImage.index] =
                            Pair.create(augmentedImage, centerPoseAnchor)
                    }
                    break
                }
                TrackingState.STOPPED -> {
                    _augmentedImageMap.remove(augmentedImage.index)
                    break
                }
            }
        }

        for(pair:Pair<AugmentedImage, Anchor> in _augmentedImageMap.values){
            val augmentedImage:AugmentedImage = pair.first
            val centerAnchor: Anchor? = _augmentedImageMap[augmentedImage.index]?.second
            when(augmentedImage.trackingState){
                TrackingState.TRACKING -> {
                    _augmentedImageRenderer.draw(viewMatrix, projectionMatrix, augmentedImage, centerAnchor, colorCorrectionRgba)
                    break
                }
                else -> break
            }
        }
    }


}