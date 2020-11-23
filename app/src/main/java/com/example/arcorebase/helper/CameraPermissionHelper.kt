package com.example.arcorebase.helper

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class CameraPermissionHelper {
    companion object {


        private val CAMERA_PERMISSION_CODE = 0
        private val CAMERA_PERMISSION = Manifest.permission.CAMERA

        /** Check to see we have the necessary permissions for this app.  */
        fun hasCameraPermission(activity: Activity?): Boolean {
            return (ContextCompat.checkSelfPermission(
                activity!!,
                CAMERA_PERMISSION
            )
                    == PackageManager.PERMISSION_GRANTED)
        }

        /** Check to see we have the necessary permissions for this app, and ask for them if we don't.  */
        fun requestCameraPermission(activity: Activity?) {
            ActivityCompat.requestPermissions(
                activity!!,
                arrayOf(CAMERA_PERMISSION),
                CAMERA_PERMISSION_CODE
            )
        }

    }
}