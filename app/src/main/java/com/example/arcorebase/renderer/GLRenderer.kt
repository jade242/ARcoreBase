package com.example.arcorebase.renderer

import android.app.Activity
import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.WindowManager
import com.google.ar.core.Session
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLRenderer : GLSurfaceView.Renderer {
    private lateinit var _activity: Activity
    private var _session: Session? = null
    private lateinit var _backgroundRenderer: BackgroundRenderer

    private var _viewportChanged = false
    private var _viewportWidth = 0
    private var _viewportHeight = 0

    fun setSession(session:Session){
        _session = session
    }

    fun setActivity(activity: Activity){
        _activity = activity
    }

    override fun onSurfaceCreated(p0: GL10?, p1: EGLConfig?) {//初期化時
        GLES20.glClearColor(0.0f,0.0f,0.0f,1.0f)//背景色の指定。
        _backgroundRenderer = BackgroundRenderer(_activity)
    }

    override fun onSurfaceChanged(unuse: GL10?, width: Int, height: Int){
        GLES20.glViewport(0,0, width,height)//表示領域設定
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
                _session!!.setDisplayGeometry(displayRotation,_viewportWidth,_viewportHeight)
            }
            _viewportChanged = false
        }

        _session!!.setCameraTextureName(_backgroundRenderer.getTextureId())
        val frame = _session!!.update()
        _backgroundRenderer.draw(frame)
    }
}