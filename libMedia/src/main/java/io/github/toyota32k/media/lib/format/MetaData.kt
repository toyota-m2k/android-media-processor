package io.github.toyota32k.media.lib.format

import io.github.toyota32k.media.lib.io.IInputMediaFile

data class MetaData(
    val mime: String?,
    val width: Int?,
    val height: Int?,
    val duration: Long?,
    val frameRate: Int?,
    val bitRate: Int?,
    val rotation: Int?,
    val location: String?) {
    companion object {
        fun fromFile(inFile:IInputMediaFile):MetaData {
            inFile.openMetadataRetriever().useObj { meta->
                return MetaData(
                    mime = meta.getMime(),
                    width = meta.getWidth(),
                    height = meta.getHeight(),
                    duration = meta.getDuration(),
                    frameRate = meta.getFrameRate(),
                    bitRate = meta.getBitRate(),
                    rotation = meta.getRotation(),
                    location = meta.getLocation())
            }
        }
    }
    val durationUs:Long?
        get() = if (duration!=null) duration*1000 else null
}