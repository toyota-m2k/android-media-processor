package io.github.toyota32k.media.lib.processor.contract

import io.github.toyota32k.media.lib.converter.IConvertResult

interface IExecutor : ICancellable {
    suspend fun execute() : IConvertResult
}
