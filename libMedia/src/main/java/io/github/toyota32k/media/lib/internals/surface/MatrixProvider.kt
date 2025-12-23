package io.github.toyota32k.media.lib.internals.surface

import android.opengl.Matrix
import android.util.Size

// TextureRender が座標変換用マトリックスを取得するためのi/f定義
interface IMatrixProvider {
    fun createMatrix(output:FloatArray)
    fun getOutputVideoSize(inputWidth:Int, inputHeight:Int): Size
}

// 動画全体をそのまま出力するための単位マトリックス
object IdentityMatrixProvider : IMatrixProvider {
    override fun createMatrix(output:FloatArray) {
        Matrix.setIdentityM(output, 0)
    }
    override fun getOutputVideoSize(inputWidth:Int, inputHeight:Int): Size {
        return Size(inputWidth, inputHeight)
    }

    override fun toString(): String {
        return "NoCrop"
    }
}

// 動画の一部分を切り抜くためのマトリックス
class MatrixProvider(val videoWidth:Int, val videoHeight:Int, val cropX: Int, val cropY: Int, val cropWidth: Int, val cropHeight: Int):IMatrixProvider {
    companion object {
        const val MIN_OUTPUT_VIDEO_WIDTH = 100
        const val MIN_OUTPUT_VIDEO_HEIGHT = 100
    }

    override fun createMatrix(output:FloatArray) {
        // スケール
        val scaleX: Float = cropWidth.toFloat() / videoWidth
        val scaleY: Float = cropHeight.toFloat() / videoHeight

        // 中心座標（NDC 左上[-1,1]基準）
        val centerX: Float = (cropX.toFloat() + cropWidth / 2) / videoWidth * 2 - 1
        val centerY: Float = 1 - (cropY.toFloat() + cropHeight / 2) / videoHeight * 2 // MVP行列設定


        Matrix.setIdentityM(output, 0)
        Matrix.scaleM(output, 0, 1/scaleX, 1/scaleY, 1f) // 以降は通常通り描画
        Matrix.translateM(output, 0, -centerX, -centerY, 0f)
    }

    override fun getOutputVideoSize(inputWidth: Int, inputHeight: Int): Size {
        var w = cropWidth
        var h = cropHeight

        if (w < MIN_OUTPUT_VIDEO_WIDTH) {
            h = (MIN_OUTPUT_VIDEO_WIDTH.toFloat() / w * h).toInt()
            w = MIN_OUTPUT_VIDEO_WIDTH
        }
        if (h < MIN_OUTPUT_VIDEO_HEIGHT) {
            w = (MIN_OUTPUT_VIDEO_HEIGHT.toFloat() / h * w).toInt()
            h = MIN_OUTPUT_VIDEO_HEIGHT
        }
        // ８の倍数化 ... VideoStrategy#calcVideoSize()で実行されるので不要
//        val wo = ((w + 7) / 8) * 8
//        h = (wo.toFloat() / w * h).toInt()
        return Size(w, h)
    }

    override fun toString(): String {
        return "crop (${cropX},${cropY})-(${cropX+cropWidth},${cropY+cropHeight}) ${cropWidth}x$cropHeight} in ${videoWidth}x${videoHeight}"
    }
}