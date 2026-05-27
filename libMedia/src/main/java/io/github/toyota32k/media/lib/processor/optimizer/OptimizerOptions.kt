package io.github.toyota32k.media.lib.processor.optimizer

import android.app.Application
import android.content.Context
import io.github.toyota32k.media.lib.processor.contract.IMultiPhaseProgress
import io.github.toyota32k.media.lib.processor.contract.IProgress

data class OptimizingProcessorPhase(override val description:String, override val valueUnit: IProgress.ValueUnit) : IMultiPhaseProgress.IPhase {
    companion object {
        val INITIAL: IMultiPhaseProgress.IPhase = OptimizingProcessorPhase("Preparing", IProgress.ValueUnit.BYTES)
        val SPLITTING: IMultiPhaseProgress.IPhase = OptimizingProcessorPhase("Splitting", IProgress.ValueUnit.US)
        val CONVERTING: IMultiPhaseProgress.IPhase = OptimizingProcessorPhase("Converting", IProgress.ValueUnit.US)
        val OPTIMIZING: IMultiPhaseProgress.IPhase = OptimizingProcessorPhase("Optimizing", IProgress.ValueUnit.BYTES)
    }
}

data class OptimizerOptions(
    val applicationContext: Context,       // 一時ファイルを作るため、コンテキストが必要
    val moveFreeAtom:Boolean,
    val onMultiPhaseProgress:((IMultiPhaseProgress)->Unit)?,
) {
    constructor(applicationContext: Context, onProgress:(IMultiPhaseProgress)->Unit):this(applicationContext, true,onProgress)
}