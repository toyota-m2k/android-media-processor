package io.github.toyota32k.media.lib.internals.surface

import android.opengl.Matrix
import android.util.Size
import io.github.toyota32k.media.lib.types.ScaleMode
import kotlin.math.max
import kotlin.math.min

/**
 * 動画結合（concat）用の座標変換マトリックスプロバイダー。
 *
 * 入力動画（srcWidth x srcHeight, srcRotation）を、固定の出力サイズ（dstWidth x dstHeight）へ
 * ScaleMode にしたがってスケーリングして描画する。
 * - 入力の回転メタデータは行列に焼き込んで正規化する（出力に回転メタデータは持たせない）。
 * - FitInside で生じる余白は TextureRender のクリア色（黒）で塗られる。
 *
 * @param srcWidth      入力動画の幅（格納状態＝回転適用前のピクセル数）
 * @param srcHeight     入力動画の高さ（格納状態＝回転適用前のピクセル数）
 * @param srcRotation   入力動画の回転メタデータ (0/90/180/270)
 * @param dstWidth      出力動画の幅（UnifiedOutputFormatで決定された固定値）
 * @param dstHeight     出力動画の高さ（同上）
 * @param mode          スケーリングモード
 */
class ScaleMatrixProvider(
    val srcWidth: Int,
    val srcHeight: Int,
    val srcRotation: Int,
    val dstWidth: Int,
    val dstHeight: Int,
    val mode: ScaleMode,
) : IMatrixProvider {

    /** 回転適用後（表示状態）の入力動画の幅 */
    val rotatedWidth: Int get() = if (srcRotation % 180 == 0) srcWidth else srcHeight

    /** 回転適用後（表示状態）の入力動画の高さ */
    val rotatedHeight: Int get() = if (srcRotation % 180 == 0) srcHeight else srcWidth

    override fun createMatrix(output: FloatArray) {
        val sw = rotatedWidth.toFloat()
        val sh = rotatedHeight.toFloat()
        val dw = dstWidth.toFloat()
        val dh = dstHeight.toFloat()

        // 出力NDC空間でのクワッドのスケール係数
        val (scaleX, scaleY) = when (mode) {
            ScaleMode.Stretch -> 1f to 1f
            ScaleMode.FitInside -> {
                val s = min(dw / sw, dh / sh)
                (s * sw / dw) to (s * sh / dh)
            }
            ScaleMode.FitOutside -> {
                val s = max(dw / sw, dh / sh)
                (s * sw / dw) to (s * sh / dh)
            }
        }

        // gl_Position = M * p,  M = Scale * Rotate
        // → コンテンツをまず回転（正規化）し、そのあと出力空間でスケーリングする。
        Matrix.setIdentityM(output, 0)
        Matrix.scaleM(output, 0, scaleX, scaleY, 1f)
        if (srcRotation % 360 != 0) {
            // 回転メタデータは「表示時に時計回りに回す角度」。
            // NDC（y-up）では時計回り＝負角。Matrix.rotateM は正角で反時計回りに回すため符号を反転する。
            Matrix.rotateM(output, 0, -srcRotation.toFloat(), 0f, 0f, 1f)
        }
    }

    /**
     * 出力サイズは入力に依らず、常に UnifiedOutputFormat で決定された固定サイズ。
     */
    override fun getOutputVideoSize(inputWidth: Int, inputHeight: Int): Size {
        return Size(dstWidth, dstHeight)
    }

    override val scaleRatio: Float = 1f

    override fun toString(): String {
        return "scale($mode) ${srcWidth}x${srcHeight}(rot=$srcRotation) --> ${dstWidth}x${dstHeight}"
    }
}
