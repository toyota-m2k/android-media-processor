package io.github.toyota32k.media.lib.processor.contract

interface IProcessor : ICancellable {
    suspend fun process(options: IProcessorOptions, onProgress:((IProgress)->Unit)?): IConvertResult
}