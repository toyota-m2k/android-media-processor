package io.github.toyota32k.media.lib.types

/**
 * 動画結合時に、基準サイズと異なるサイズの動画をどのようにスケーリングするかを指定する。
 */
enum class ScaleMode {
    /**
     * アスペクト比を維持して基準サイズに内接させる。
     * 余白は黒帯（letterbox / pillarbox）となる。
     */
    FitInside,

    /**
     * アスペクト比を維持して基準サイズに外接させる。
     * 基準サイズからはみ出した部分はクロップされる。
     */
    FitOutside,

    /**
     * アスペクト比を無視して基準サイズいっぱいに引き伸ばす。
     */
    Stretch,
}
