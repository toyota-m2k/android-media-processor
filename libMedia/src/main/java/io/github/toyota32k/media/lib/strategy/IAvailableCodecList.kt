package io.github.toyota32k.media.lib.strategy

/**
 * コーデックをサポートしているエンコーダー/デコーダーの情報を返すための i/f
 * VideoStrategy.availableEncoders/availableDecoders で使用する。
 */
interface IAvailableCodecList {
    val encoder:Boolean
    val default: String
    val hardwareAccelerated: List<String>
    val softwareOnly: List<String>
    val other: List<String>
}