package com.example.arcorebase

import android.opengl.GLSurfaceView
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.example.arcorebase.helper.CameraPermissionHelper
import com.example.arcorebase.renderer.BackgroundRenderer
import com.example.arcorebase.renderer.GLRenderer
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.*

class MainActivity : AppCompatActivity() {
    private val TAG = BackgroundRenderer::class.java.simpleName
    private var _installRequested = false
    private lateinit var _surfaceView:GLSurfaceView
    private lateinit var _glRenderer: GLRenderer
    private var _session : Session? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeGLSurfaceView()
        _installRequested = false
    }

    override fun onResume() {
        super.onResume()

        when(ArCoreApk.getInstance().requestInstall(this,!_installRequested))
        {   //Initiates installation of ARCore when needed.
            // When your application launches or enters an AR mode,
            // it should call this method with userRequestedInstall = true.
            //If ARCore is installed and compatible,
            //this function will return InstallStatus.INSTALLED or INSTALL_REQUESTED
            //getInstance() : Returns the singleton of ArCoreApk.
            ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                _installRequested = true
                return
            }
            ArCoreApk.InstallStatus.INSTALLED -> {}
        }

        if(!CameraPermissionHelper.hasCameraPermission(this)){
            CameraPermissionHelper.requestCameraPermission(this);
            return;
        }

        createSession()
        try {
            _session!!.resume()
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available. Try restarting the app.")
            _session = null
            return
        }
        _glRenderer.setSession(_session!!)

        _surfaceView.onResume()
    }

    private fun createSession() {
        try{
            _session = Session(this)
            val config: Config = _session!!.config//現在の構成を取得する
            //提供されたDepthModeが、このデバイスと選択したカメラでサポートされているか確認する。
            if(_session!!.isDepthModeSupported(Config.DepthMode.AUTOMATIC)){
                config.depthMode = Config.DepthMode.AUTOMATIC//画像内のすべてのピクセルの震度情報を取得する
            }else {
                config.depthMode = Config.DepthMode.DISABLED//震度情報は提供されない。
            }
            _session!!.configure(config)//セッションを構成する。
        }catch (e: UnavailableArcoreNotInstalledException){
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
    }

    private fun initializeGLSurfaceView(){
        _glRenderer = GLRenderer()//Rendererオブジェクトを生成
        _glRenderer.setActivity(this)
        _surfaceView = findViewById(R.id.surfaceView)//viewとしてGLSurfaceViewを実装したレイアウトを指定
        _surfaceView.preserveEGLContextOnPause = true
        //GLSurfaceViewが一時停止したときにEGLコンテキストが保持されるようにする
        _surfaceView.setEGLContextClientVersion(2)//OpengGLのバージョン指定 ver2.0
        _surfaceView.setEGLConfigChooser(8,8,8,8,16,0)
        //バッファー深度16bit, RGBA8888フォーマットの構成を設定する。
        //setRendererの前に呼び出す必要がある。
        _surfaceView.setRenderer(_glRenderer)//Rendererオブジェクトをセット。
        _surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        //シーンを再レンダリングするためにレンダラーが継続的に呼ばれるように設定。
        _surfaceView.setWillNotDraw(false)
    }

}