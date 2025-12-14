package io.github.toyota32k.media.lib.processor.optimizer

import io.github.toyota32k.media.lib.converter.AndroidFile
import io.github.toyota32k.media.lib.converter.ConvertResult
import io.github.toyota32k.media.lib.converter.FastStart
import io.github.toyota32k.media.lib.converter.toAndroidFile
import io.github.toyota32k.media.lib.processor.DerivedProcessorOptions.Companion.derive
import io.github.toyota32k.media.lib.processor.contract.IProgress
import io.github.toyota32k.media.lib.processor.Processor
import io.github.toyota32k.media.lib.processor.contract.IMultiPhaseProgress
import io.github.toyota32k.media.lib.processor.contract.IProcessorOptions
import java.io.File

object Optimizer {
    val logger = Processor.logger

    private class MultiPhaseProgress(override val phaseCount: Int) : IMultiPhaseProgress<OptimizingProcessorPhase> {
        override var phase = OptimizingProcessorPhase.CONVERTING
        override var total: Long = 0L
        override var current: Long = 0L
        override var remainingTime: Long = 0L

        fun updatePhase(phase: OptimizingProcessorPhase) = apply {
            this.phase = phase
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

    fun optimize(processor:Processor, processorOptions: IProcessorOptions, optimizeOptions: OptimizeOptions): Processor.Result {
        val workFile:AndroidFile = File.createTempFile("ame", ".tmp", optimizeOptions.application.cacheDir).toAndroidFile()
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
            val result = FastStart.process(workFile, outputFile, optimizeOptions.moveFreeAtom) { p: IProgress ->
                progressCallback?.invoke(multiProgress.updateProgress(p))
            }
            if (!result) {
                // Fast Start が処理しなかった（すでに最適化されている）場合は、作業ファイルをoutputにコピーする。
                try {
                    outputFile.copyFrom(workFile)
                } catch (e: Throwable) {
                    logger.error(e)
                    ConvertResult.error(e)
                }
            }
            return processorResult
        } catch(e:Throwable) {
            throw e
        } finally {
            workFile.safeDelete()
        }
    }

}