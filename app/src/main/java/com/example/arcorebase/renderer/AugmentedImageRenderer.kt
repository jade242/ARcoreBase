package com.example.arcorebase.renderer

import android.content.Context
import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.AugmentedImage
import java.io.IOException

class AugmentedImageRenderer(context: Context) {
    private val TAG = AugmentedImageRenderer::class.java.simpleName
    private val _context = context
    private val _imageFrame: ObjectRenderer = ObjectRenderer()
    private val TINT_INTENSITY = 0.1f
    private val TINT_ALPHA = 1.0f
    private val TINT_COLORS_HEX = intArrayOf(
        0x000000, 0xF44336, 0xE91E63, 0x9C27B0, 0x673AB7, 0x3F51B5, 0x2196F3, 0x03A9F4, 0x00BCD4,
        0x009688, 0x4CAF50, 0x8BC34A, 0xCDDC39, 0xFFEB3B, 0xFFC107, 0xFF9800
    )

    fun createOnGlThread() {
        try {
            _imageFrame.createOnGlThread(_context, "models/Andy.obj", "Andy_Diffuse.png")
            _imageFrame.setBlendMode(ObjectRenderer.BlendMode.SourceAlpha)
            _imageFrame.setMaterialProperties(0.0f, 2.0f, 0.5f, 6.0f)
        }catch (e: IOException){
            Log.e(TAG, "Failed to read an asset file", e)
        }
    }

    fun draw(
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray,
        augmentedImage: AugmentedImage,
        centerAnchor: Anchor?,
        colorCorrectionRgba: FloatArray
    ) {
        val scaleFactor = 1.0f
        val modelMatrix = FloatArray(16)
        val tintColor = convertHexToColor(
            TINT_COLORS_HEX[augmentedImage.index % TINT_COLORS_HEX.size]
        )
        _imageFrame.updateModelMatrix(modelMatrix, scaleFactor)
        _imageFrame.draw(viewMatrix, projectionMatrix, colorCorrectionRgba,tintColor)
    }

    private fun convertHexToColor(colorHex: Int): FloatArray {
        // colorHex is in 0xRRGGBB format
        val red: Float =
            (colorHex and 0xFF0000 shr 16) / 255.0f * TINT_INTENSITY
        val green: Float =
            (colorHex and 0x00FF00 shr 8) / 255.0f * TINT_INTENSITY
        val blue: Float = (colorHex and 0x0000FF) / 255.0f * TINT_INTENSITY
        return floatArrayOf(red, green, blue, TINT_ALPHA)
    }
}