package io.github.toyota32k.media.lib.processor.contract

import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.io.IOutputMediaFile
import io.github.toyota32k.media.lib.types.Rotation
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.internals.surface.RenderOption
import io.github.toyota32k.media.lib.processor.ConcatSource
import io.github.toyota32k.media.lib.processor.optimizer.OptimizerOptions
import io.github.toyota32k.media.lib.types.RangeUs
import io.github.toyota32k.media.lib.types.ScaleMode

interface IProcessorOptions {
    val outPath: IOutputMediaFile
    val optimizerOptions: OptimizerOptions?
    val deleteOutputOnError:Boolean
    val videoStrategy: IVideoStrategy
    val audioStrategy: IAudioStrategy
    // 出力ファイルを差し替える
    fun derive(outPath: IOutputMediaFile, optimizerOptions: OptimizerOptions?=null): IProcessorOptions
}

interface IConvertOptions : IProcessorOptions {
    val inPath: IInputMediaFile
    val rangesUs:List<RangeUs>
    val limitDurationUs:Long
    val rotation:Rotation?
    val renderOption:RenderOption?
}

interface IConcatOptions : IProcessorOptions {
    val sources: List<ConcatSource>
    val scaleMode: ScaleMode
}