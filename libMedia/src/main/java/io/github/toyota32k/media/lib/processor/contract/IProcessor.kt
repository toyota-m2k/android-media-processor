package io.github.toyota32k.media.lib.processor.contract

interface IProcessor : ICancellable {
    fun process(options: IProcessorOptions): IConvertResult
}