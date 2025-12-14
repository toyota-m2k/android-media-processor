package io.github.toyota32k.media.lib.processor.contract

import io.github.toyota32k.media.lib.converter.IInputMediaFile
import io.github.toyota32k.media.lib.converter.IOutputMediaFile
import io.github.toyota32k.media.lib.converter.Rotation
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.surface.RenderOption
import io.github.toyota32k.media.lib.utils.RangeUs

interface IProcessorOptions {
    val inPath: IInputMediaFile
    val outPath: IOutputMediaFile
    val videoStrategy: IVideoStrategy
    val audioStrategy: IAudioStrategy
    val rangesUs:List<RangeUs>
    val limitDurationUs:Long
    val rotation:Rotation?
    val renderOption:RenderOption?
    val onProgress: ((IProgress)->Unit)?
}