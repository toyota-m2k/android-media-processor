package io.github.toyota32k.media.lib.processor.optimizer

import io.github.toyota32k.media.lib.io.AndroidFile
import io.github.toyota32k.media.lib.io.toAndroidFile
import io.github.toyota32k.media.lib.processor.DerivedProcessorOptions.Companion.derive
import io.github.toyota32k.media.lib.processor.contract.IProgress
import io.github.toyota32k.media.lib.processor.Processor
import io.github.toyota32k.media.lib.processor.contract.IMultiPhaseProgress
import io.github.toyota32k.media.lib.processor.contract.IProcessor
import io.github.toyota32k.media.lib.processor.contract.IProcessorOptions
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * FastStartを呼ぶための作業ファイル関連の処理を隠蔽するヘルパークラス
 * 通常は Processor.execute() 内で利用され、直接このクラスを利用することはない。
 */
object Optimizer {
    val logger = Processor.logger

    private class MultiPhaseProgress(override val phaseCount: Int) : IMultiPhaseProgress<OptimizingProcessorPhase> {
        override var phase = OptimizingProcessorPhase.CONVERTING
        override var total: Long = 0L
        override var current: Long = 0L
        override var remainingTime: Long = 0L
        override var valueUnit: IProgress.ValueUnit = IProgress.ValueUnit.US

        fun updatePhase(phase: OptimizingProcessorPhase) = apply {
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

    fun optimize(processor: IProcessor, processorOptions: IProcessorOptions, optimizeOptions: OptimizerOptions): Processor.Result {
        val workFile:AndroidFile = File.createTempFile("ame", ".tmp", optimizeOptions.applicationContext.cacheDir).toAndroidFile()
        try {
            val outputFile: AndroidFile = processorOptions.outPath as? AndroidFile ?: throw IllegalStateException("output file must be AndroidFile.")
            val multiProgress = MultiPhaseProgress(2)
            val progressCallback = optimizeOptions.onMultiPhaseProgress
            val derivedProcessorOptions = processorOptions.derive(workFile) {
                 progressCallback?.invoke(multiProgress.updateProgress(it))
            }
            // Convert
            progressCallback?.invoke(multiProgress.updatePhase(OptimizingProcessorPhase.CONVERTING))
            val processorResult = processor.process(derivedProcessorOptions)

            // Fast Start
            progressCallback?.invoke(multiProgress.updatePhase(OptimizingProcessorPhase.OPTIMIZING))
            val result = FastStart.process(workFile, outputFile, optimizeOptions.moveFreeAtom) { p: IProgress ->
                progressCallback?.invoke(multiProgress.updateProgress(p))
            }
            if (!result) {
                // Fast Start が処理しなかった（すでに最適化されている）場合は、作業ファイルをoutputにコピーする。
                outputFile.copyFrom(workFile)
            }
            return Processor.Result(processorResult as Processor.Result, outputFile = outputFile)
        } catch(e:Throwable) {
            if (e !is CancellationException) {
                logger.error(e)
            }
            throw e
        } finally {
            workFile.safeDelete()
        }
    }

}