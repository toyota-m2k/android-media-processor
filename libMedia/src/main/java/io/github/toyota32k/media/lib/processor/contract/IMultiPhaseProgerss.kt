package io.github.toyota32k.media.lib.processor.contract

interface IMultiPhaseProgress<T: IMultiPhaseProgress.IPhase> : IProgress {
    val phaseIndex: Int get() = phase.index
    val phaseCount: Int
    val phase: T

    interface IPhase {
        val index: Int                  // 0,1,2,...phaseCount
        val description: String
    }
}