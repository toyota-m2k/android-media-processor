package io.github.toyota32k.media.lib.processor.contract

interface IMultiPhaseProgress : IProgress {
    interface IPhase {
        val description: String
        val valueUnit: IProgress.ValueUnit
    }

    val phaseCount: Int
    val phase: IPhase
}