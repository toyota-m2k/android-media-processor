package io.github.toyota32k.media.lib.processor.optimizer

import io.github.toyota32k.media.lib.io.AndroidFile
import io.github.toyota32k.media.lib.io.toAndroidFile
import io.github.toyota32k.media.lib.processor.Processor
import io.github.toyota32k.media.lib.processor.contract.IConcatOptions
import io.github.toyota32k.media.lib.processor.contract.IConvertResult
import io.github.toyota32k.media.lib.processor.contract.IMultiPhaseProgress
import io.github.toyota32k.media.lib.processor.contract.IProcessorOptions
import io.github.toyota32k.media.lib.processor.contract.IProcessorResult
import io.github.toyota32k.media.lib.processor.contract.IProgress
import io.github.toyota32k.media.lib.strategy.PresetVideoStrategies
import java.io.File

/**
 * FastStartを呼ぶための作業ファイル関連の処理を隠蔽するヘルパークラス
 * 通常は Processor.execute() 内で利用され、直接このクラスを利用することはない。
 */
object Optimizer {
    val logger = Processor.logger

    private class MultiPhaseProgress(override val phaseCount: Int) : IMultiPhaseProgress {
        override var phase = OptimizingProcessorPhase.CONVERTING
        override var total: Long = 0L
        override var current: Long = 0L
        override var remainingTime: Long = 0L
        override var valueUnit: IProgress.ValueUnit = IProgress.ValueUnit.US

        fun updatePhase(phase: IMultiPhaseProgress.IPhase) = apply {
            this.phase = phase
            valueUnit = if (phase == OptimizingProcessorPhase.OPTIMIZING) IProgress.ValueUnit.BYTES else IProgress.ValueUnit.US
            total = 0L
            current = 0L
            remainingTime = 0L
        }

        fun updateProgress(progress:IProgress) = apply {
            total = progress.total
            current = progress.current
            remainingTime = progress.remainingTime
        }
    }

    fun process(processorOptions: IProcessorOptions, onProgress: ((IProgress) -> Unit)?, firstPhaseProcess:(IProcessorOptions)-> IProcessorResult): IProcessorResult {
        val optimizeOptions = processorOptions.optimizerOptions ?: return firstPhaseProcess(processorOptions)

        val outputFile: AndroidFile = processorOptions.outPath as? AndroidFile ?: throw IllegalStateException("output file must be AndroidFile.")
        val workFile: AndroidFile = File.createTempFile("ame", ".tmp", optimizeOptions.applicationContext.cacheDir).toAndroidFile()
        try {
            val derivedOptions = processorOptions.derive(workFile)
            val multiProgress = MultiPhaseProgress(2)
            val firstPhase = when {
                processorOptions is IConcatOptions -> OptimizingProcessorPhase.MERGING
                derivedOptions.videoStrategy == PresetVideoStrategies.InvalidStrategy -> OptimizingProcessorPhase.SPLITTING
                else -> OptimizingProcessorPhase.CONVERTING
            }
            onProgress?.invoke(multiProgress.updatePhase(firstPhase))
            val firstResult = firstPhaseProcess(derivedOptions)
            if (!firstResult.succeeded) {
                return firstResult
            }
            onProgress?.invoke(multiProgress.updatePhase(OptimizingProcessorPhase.OPTIMIZING))
            val result = FastStart.process(workFile, outputFile, optimizeOptions.removeFreeAtom) { p: IProgress ->
                onProgress?.invoke(multiProgress.updateProgress(p))
            }
            if (!result) {
                // Fast Start が処理しなかった（すでに最適化されている）場合は、作業ファイルをoutputにコピーする。
                outputFile.copyFrom(workFile)
            }
            return firstResult.derive(outputFile)
        } finally {
            workFile.safeDelete()
        }
    }
}