package com.example.arcorebase

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.arcorebase.helper.CameraPermissionHelper
import com.example.arcorebase.renderer.BackgroundRenderer
import com.example.arcorebase.renderer.GLRenderer
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import java.io.IOException
import java.io.InputStream

class MainActivity : AppCompatActivity() {
    private val TAG = BackgroundRenderer::class.java.simpleName
    private var _installRequested = false
    private lateinit var _surfaceView: GLSurfaceView
    private var _fitToScanView: ImageView? = null
    private lateinit var _glRenderer: GLRenderer
    private var _session: Session? = null
    private var _arSceneView: ArSceneView? = null
    private var _isAttachedModel: Boolean = false
    private var _textViewRenderable: ViewRenderable? = null
    private var _sessionConfigured = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        _fitToScanView = findViewById(R.id.image_view_fit_to_scan)
        _fitToScanView!!.setImageResource(R.drawable.fit_to_scan)
        initializeGLSurfaceView()
        _installRequested = false
        trackingImage()
    }

    override fun onResume() {
        super.onResume()

        when (ArCoreApk.getInstance().requestInstall(
            this,
            !_installRequested
        )) {   //Initiates installation of ARCore when needed.
            // When your application launches or enters an AR mode,
            // it should call this method with userRequestedInstall = true.
            //If ARCore is installed and compatible,
            //this function will return InstallStatus.INSTALLED or INSTALL_REQUESTED
            //getInstance() : Returns the singleton of ArCoreApk.
            ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                _installRequested = true
                return
            }
            ArCoreApk.InstallStatus.INSTALLED -> {
            }
        }

        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this)
            return
        }

        if (_session == null) {
            createSession()
        }

        if (_sessionConfigured) {
            //事前 に 登録 し た 画像 データベース を、
            // AR の システム 状態 を 管理 し ライフサイクル を 担う Session クラス に 結びつける
            configureSession()
            _sessionConfigured = false
        }

        try {
            _session!!.resume()
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available. Try restarting the app.")
            _session = null
            return
        }
        _glRenderer.setSession(_session!!)

        _surfaceView.onResume()
        _fitToScanView?.visibility = View.VISIBLE
    }

    override fun onPause() {
        super.onPause()
        if (_session != null) {
            _surfaceView.onPause()
            _session!!.pause()
        }
    }

    private fun initializeGLSurfaceView() {
        _glRenderer = GLRenderer()//Rendererオブジェクトを生成
        _glRenderer.setActivity(this)
        _surfaceView = findViewById(R.id.surfaceView)//viewとしてGLSurfaceViewを実装したレイアウトを指定
        _surfaceView.preserveEGLContextOnPause = true
        //GLSurfaceViewが一時停止したときにEGLコンテキストが保持されるようにする
        _surfaceView.setEGLContextClientVersion(2)//OpengGLのバージョン指定 ver2.0
        _surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        //バッファー深度16bit, RGBA8888フォーマットの構成を設定する。
        //setRendererの前に呼び出す必要がある。
        _surfaceView.setRenderer(_glRenderer)//Rendererオブジェクトをセット。
        _surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        //シーンを再レンダリングするためにレンダラーが継続的に呼ばれるように設定。
        _surfaceView.setWillNotDraw(false)
    }

    private fun createSession() {
        try {
            _session = Session(this)
//            val config: Config = _session!!.config//現在の構成を取得する
//            //提供されたDepthModeが、このデバイスと選択したカメラでサポートされているか確認する。
//            if (_session!!.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
//                config.depthMode = Config.DepthMode.AUTOMATIC//画像内のすべてのピクセルの震度情報を取得する
//            } else {
//                config.depthMode = Config.DepthMode.DISABLED//震度情報は提供されない。
//            }
//            _session!!.configure(config)//セッションを構成する。
        } catch (e: UnavailableArcoreNotInstalledException) {
            Log.e(TAG, "Please install ARCore")
        } catch (e: UnavailableUserDeclinedInstallationException) {
            Log.e(TAG, "Please install ARCore")
        } catch (e: UnavailableApkTooOldException) {
            Log.e(TAG, "Please update ARCore")
        } catch (e: UnavailableSdkTooOldException) {
            Log.e(TAG, "Please update this app")
        } catch (e: UnavailableDeviceNotCompatibleException) {
            Log.e(TAG, "This device does not support AR")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AR session")
        }
        _sessionConfigured = true
    }

    private fun configureSession() {
        val config = Config(_session)
        setupAugmentedImageDatabase(config)
        _arSceneView?.setupSession(_session)
    }

    private fun setupAugmentedImageDatabase(config: Config) {
        val inputStream: InputStream
        try {
            inputStream = assets.open("sample_database.imgdb")
            //入力ストリームから新しい画像データベースを作成する。
            val imageDatabase = AugmentedImageDatabase.deserialize(_session, inputStream)
            with(config) {
                augmentedImageDatabase = imageDatabase
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                _session?.configure(this)
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Sessionを設定することができません", Toast.LENGTH_SHORT).show()
        }
    }

    private fun trackingImage() {
        //xmlからArFragmentを見つけてArFragmentとして定義する。
   //     val arFragment = supportFragmentManager.findFragmentById(R.id.ar_fragment) as? ArFragment

//        arFragment?.arSceneView?.scene?.addOnUpdateListener {//addOnUpdateListenerは1フレーム毎に呼ばれる。
//            val frame = arFragment.arSceneView?.arFrame
//            val updateAugmentedImages = frame?.getUpdatedTrackables(AugmentedImage::class.java)
//                ?: return@addOnUpdateListener

//            for (img in updateAugmentedImages) {
//                if (img.trackingState == TrackingState.TRACKING) {
//                    if (!isAttachedModel) {
//                        setUp3DModel(img.createAnchor(img.centerPose), arFragment)
//                    }
//                }
//            }
        }


    private fun setUp3DModel(anchor: Anchor?, arFragment: ArFragment) {
//        ViewRenderable.builder()
//            .build()
//            .thenAccept { renderable -> textViewRenderable = renderable }
//            .exceptionally {
//                Toast.makeText(this, "レンダリングできません", Toast.LENGTH_LONG).apply {
//                    setGravity(Gravity.CENTER, 0, 0)
//                    show()
//                }
//                null
//            }
//        if (textViewRenderable == null) {
//            return
//        }
//
//        val anchorNode = AnchorNode(anchor)
//        anchorNode.setParent(arFragment.arSceneView?.scene)
//        TransformableNode(arFragment.transformationSystem).apply {
//            setParent(anchorNode)
//            renderable = textViewRenderable
//            rotationController
//            scaleController
//            select()
//            //方向ベクトルを90度変えてARの向きを画像に対して垂直ではなく平行にするために追加する
////            localRotation = lookRotation(Vector3.down(), Vector3.up())
//        }
//        isAttachedModel = true
    }

}