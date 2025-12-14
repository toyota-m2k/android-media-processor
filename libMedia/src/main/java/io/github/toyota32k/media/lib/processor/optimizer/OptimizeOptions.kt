package io.github.toyota32k.media.lib.processor.optimizer

import android.app.Application
import io.github.toyota32k.media.lib.processor.contract.IMultiPhaseProgress

enum class OptimizingProcessorPhase(override var description:String, override var index:Int) : IMultiPhaseProgress.IPhase {
    CONVERTING("Converting", 0),
    OPTIMIZING("Optimizing", 1),
}

data class OptimizeOptions(
    val application: Application,       // 一時ファイルを作るため、コンテキストが必要
    val moveFreeAtom:Boolean,
    val onMultiPhaseProgress:((IMultiPhaseProgress<OptimizingProcessorPhase>)->Unit)?,
) {
    constructor(application: Application, onProgress:(IMultiPhaseProgress<OptimizingProcessorPhase>)->Unit):this(application, true,onProgress)
}