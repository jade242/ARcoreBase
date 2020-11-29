package com.example.arcorebase.shader

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.RuntimeException
import java.lang.StringBuilder

class Shader {
    companion object {
        /**
         * Converts a raw text file, saved as a resource, into an OpenGL ES shader.
         *
         * @param type The type of shader we will be creating.
         * @param filename The filename of the asset file about to be turned into a shader.
         * @return The shader object handler.
         */
        fun loadGLShader(tag:String?, context: Context, type: Int, filename: String): Int {
            // Load shader source code.
            val code:String = readShaderFileFromAssets(context,filename)

            // Compiles shader code.
            var shader = GLES20.glCreateShader(type)//バーテックスシェーダかフラグメントシェーダtypeのshaderを作成
            GLES20.glShaderSource(shader, code)//作成したシェーダにソースを設定
            GLES20.glCompileShader(shader)//シェーダをコンパイル
            //コンパイルに成功したかどうかのチェック
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,compileStatus,0)
            if(compileStatus[0] == 0){
                Log.e(tag, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader))
                GLES20.glDeleteShader(shader)
                shader = 0
            }
            if(shader == 0){
                throw RuntimeException("Error creating shader.")
            }
            return shader
        }

        @Throws(IOException::class)
        private fun readShaderFileFromAssets(context: Context,filename: String):String{
//            val inputStream1 = context.assets.open(filename)
//            inputStream1.use{
//                val bufferedReader = BufferedReader(InputStreamReader(inputStream1))
//                val line1:String? = bufferedReader.readLine()
//                while(line1 != null){
//                    val splitLine1 = line1.split(" ")
//                    val dropLine1 = splitLine1.dropLastWhile {line1.isEmpty()  }
//                    val tokens = dropLine1.toTypedArray()
//                }
//            }
            context.assets.open(filename).use{inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use{reader ->
                    val stringBuilder = StringBuilder()
                    var line:String? = reader.readLine()
                    while(line != null){
                        val tokens =
                            line.split(" ").dropLastWhile { it.isEmpty() }.toTypedArray()
                        if(tokens.isNotEmpty() && tokens[0] == "#include"){
                            var includeFilename = tokens[1]
                            includeFilename = includeFilename.replace("\"","")
                            if(includeFilename == filename){
                                throw IOException("Do not include the calling file.")
                            }
                            stringBuilder.append(readShaderFileFromAssets(context, includeFilename))
                        }else{
                            stringBuilder.append(line).append("\n")
                        }
                        line = reader.readLine()
                    }
                    return stringBuilder.toString()
                }
            }
        }

        fun checkGLError(tag: String, label: String) {
            var lastError = GLES20.GL_NO_ERROR
            var error: Int
            while(GLES20.glGetError().also{error = it}!= GLES20.GL_NO_ERROR){
                Log.e(tag, "$label; glError $error")
                lastError = error
            }
            if(lastError != GLES20.GL_NO_ERROR){
                throw RuntimeException("$label: glError $lastError")
            }
        }
    }
}