package io.github.toyota32k.media.lib.processor.optimizer

import android.app.Application
import android.content.Context
import io.github.toyota32k.media.lib.processor.contract.IMultiPhaseProgress

enum class OptimizingProcessorPhase(override var description:String, override var index:Int) : IMultiPhaseProgress.IPhase {
    CONVERTING("Converting", 0),
    OPTIMIZING("Optimizing", 1),
}

data class OptimizerOptions(
    val applicationContext: Context,       // 一時ファイルを作るため、コンテキストが必要
    val moveFreeAtom:Boolean,
    val onMultiPhaseProgress:((IMultiPhaseProgress<OptimizingProcessorPhase>)->Unit)?,
) {
    constructor(applicationContext: Context, onProgress:(IMultiPhaseProgress<OptimizingProcessorPhase>)->Unit):this(applicationContext, true,onProgress)
}