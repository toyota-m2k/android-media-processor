package io.github.toyota32k.media.lib.processor.contract

import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.io.IOutputMediaFile
import io.github.toyota32k.media.lib.types.Rotation
import io.github.toyota32k.media.lib.strategy.IAudioStrategy
import io.github.toyota32k.media.lib.strategy.IVideoStrategy
import io.github.toyota32k.media.lib.internals.surface.RenderOption
import io.github.toyota32k.media.lib.types.RangeUs

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