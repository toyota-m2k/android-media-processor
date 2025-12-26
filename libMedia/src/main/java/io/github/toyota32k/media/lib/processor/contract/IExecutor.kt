package io.github.toyota32k.media.lib.processor.contract

interface IExecutor : ICancellable {
    suspend fun execute() : IConvertResult
}
