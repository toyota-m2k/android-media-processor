package io.github.toyota32k.media.lib.surface

import android.graphics.Rect

data class RenderOption(val matrixProvider:IMatrixProvider, val brightness:Float) {
    companion object {
        @JvmField
        val DEFAULT = RenderOption(IdentityMatrixProvider, 1f)
        fun create(videoWidth:Int, videoHeight:Int, rect: Rect, brightness: Float): RenderOption {
            return if (rect.width()==videoWidth && rect.height()==videoHeight) {
                RenderOption(IdentityMatrixProvider, brightness)
            } else {
                RenderOption(MatrixProvider(videoWidth, videoHeight,rect.left, rect.top, rect.width(), rect.height()), brightness)
            }
        }
        fun create(brightness: Float): RenderOption {
            return RenderOption(IdentityMatrixProvider, brightness)
        }
    }
}