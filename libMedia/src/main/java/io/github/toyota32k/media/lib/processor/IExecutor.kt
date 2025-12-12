package io.github.toyota32k.media.lib.processor

import io.github.toyota32k.media.lib.converter.IConvertResult

interface IExecutor : ICancellable {
    suspend fun execute() : IConvertResult
}
