package io.github.toyota32k.media.lib.converter
import android.content.Context
import android.net.Uri
import io.github.toyota32k.logger.UtLog
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream


@Suppress("unused")
object FastStart {
    /**
     * Fast Start が必要かどうかをチェックする
     * @param inUri 入力（動画）ファイル
     * @param context  uri解決用コンテキスト（ApplicationContextでok）
     * @param removeFree true にすると、MOOV Atomが先頭にあっても（= Fast Start readyであっても）、Free Atom が存在すれば true を返す。
     * @return true: 必要 / false: 不要
     */
    fun check(inUri: Uri, context: Context, removeFree:Boolean=true):Boolean {
        val inFile = AndroidFile(inUri, context)
        return try {
            inFile.fileInputStream { inStream->
                processImpl(inStream, null, removeFree, null)
            }
        } catch(e:Throwable) {
            logger.error(e)
            false
        }
    }

    /**
     * Fast Start 化を実行する
     * @param inUri 入力（動画）ファイル
     * @param outUri 出力ファイル
     * @param context  uri解決用コンテキスト（ApplicationContextでok）
     * @return true: 処理した / false: 処理は不要だった、あるいはエラーのため処理できなかった
     */
    fun process(inUri:Uri, outUri:Uri, context:Context, progressCallback: ((IProgress) -> Unit)?):Boolean {
        return process(AndroidFile(inUri, context), AndroidFile(outUri, context), progressCallback)
    }
    fun process(inFile:AndroidFile, outFile:AndroidFile, progressCallback: ((IProgress) -> Unit)?):Boolean {
        var result = false
        try {
            inFile.fileInputStream { inStream->
                outFile.fileOutputStream { outStream ->
                    result = processImpl(inStream, outStream, true, progressCallback)
                }
            }
        } catch(e:Throwable) {
            logger.error(e)
            result = false
        } finally {
            if(!result) {
                outFile.safeDelete()
            }
        }
        return result
    }

    val logger = UtLog("FastStart2", Converter.logger)

    private const val CHUNK_SIZE = 8192

    private class ByteArrayInputStreamPosition(buf: ByteArray?) : ByteArrayInputStream(buf) {
        //    constructor(buf: ByteArray?, offset: Int, length: Int) : super(buf, offset, length)
        fun position(): Int {
            return pos
        }
    }

    private data class Atom(var type: String, var size: Long, var start:Long = 0) {
        override fun toString(): String {
            return "[$type,start=$start,size=$size]"
        }
    }

    class FastStartException(message:String) : Exception(message)

    private fun readInt(inputStream: InputStream): Int {
        val data = ByteArray(4)
        inputStream.read(data)
        var n = 0
        for (b in data) n = (n shl 8) + (b.toInt() and 0xFF)
        return n
    }

    private fun readLong(inputStream: InputStream): Long {
        val data = ByteArray(8)
        inputStream.read(data)
        var n: Long = 0
        for (b in data) n = (n shl 8) + (b.toInt() and 0xFF)
        return n
    }

    private fun readType(inputStream: InputStream): String {
        val data = ByteArray(4)
        inputStream.read(data)
        return String(data)
    }

    private fun readAtom(inputStream: InputStream): Atom {
        val size = readInt(inputStream)
        val type = readType(inputStream)
        return Atom(type, size.toLong())
    }

    private fun toByteArray(n: Int): ByteArray {
        val b = ByteArray(4)
        for (i in 0..3) b[3 - i] = (n ushr 8 * i).toByte()
        return b
    }

    private fun toByteArray(n: Long): ByteArray {
        val b = ByteArray(8)
        for (i in 0..7) b[7 - i] = (n ushr 8 * i).toByte()
        return b
    }

    private fun getIndex(inputStream: FileInputStream): ArrayList<Atom> {
        logger.debug("Getting index of top level atoms...")
        val index = ArrayList<Atom>()
        var seenMoov = false
        var seenMdat = false
        while (inputStream.available() > 0) {
            try {
                val atom = readAtom(inputStream)
                var skippedBytes = 8
                if (atom.size == 1L) {
                    atom.size = readLong(inputStream)
                    skippedBytes = 16
                }
                atom.start = inputStream.channel.position() - skippedBytes
                index.add(atom)
                logger.debug(atom.toString())
                if (atom.type.equals("moov", ignoreCase = true)) seenMoov = true
                if (atom.type.equals("mdat", ignoreCase = true)) seenMdat = true
                if (atom.size == 0L) break
                inputStream.skip(atom.size - skippedBytes)
            } catch (ex: IOException) {
                logger.error(ex)
                break
            }
        }
        if (!seenMoov) throw FastStartException("No moov atom type found!")
        if (!seenMdat) throw FastStartException("No mdat atom type found!")
        return index
    }

    private fun skipToNextTable(inputStream: InputStream): Atom? {
        while (inputStream.available() > 0) {
            val atom = readAtom(inputStream)
            if (isTableType(atom.type)) return atom else if (isKnownAncestorType(atom.type)) continue else inputStream.skip(atom.size - 8)
        }
        return null
    }

    @Suppress("SpellCheckingInspection")
    private fun isKnownAncestorType(type: String): Boolean {
        return type.equals("trak", ignoreCase = true) ||
                type.equals("mdia", ignoreCase = true) ||
                type.equals("minf", ignoreCase = true) ||
                type.equals("stbl", ignoreCase = true)
    }

    @Suppress("SpellCheckingInspection")
    private fun isTableType(type: String): Boolean {
        return type.equals("stco", ignoreCase = true) ||
                type.equals("co64", ignoreCase = true)
    }

    private class Progress(override var total: Long, val callback:((IProgress)->Unit)?) : IProgress {
        override var current: Long = 0L
        override val remainingTime: Long = 0L

        fun update(current:Long) {
            this.current = current
            callback?.invoke(this)
        }
    }

    private fun processImpl(inputStream: FileInputStream, outputStream: FileOutputStream?, removeFree:Boolean, progressCallback:((IProgress)->Unit)?): Boolean {
        val index: ArrayList<Atom> = try {
            getIndex(inputStream)
        } catch (ex: IOException) {
            logger.error(ex)
            throw FastStartException("IO Exception during indexing.")
        }
        var moov: Atom? = null
        var mdatStart = 999999L
        var freeSize = 0L

        //Check that moov is after mdat (they are both known to exist)
        for (atom in index) {
            if (atom.type.equals("moov", ignoreCase = true)) {
                moov = atom
            } else if (atom.type.equals("mdat", ignoreCase = true)) {
                mdatStart = atom.start
            } else if (atom.type.equals("free", ignoreCase = true) && atom.start < mdatStart) {
                //This free atom is before the mdat
                freeSize += atom.size
                logger.info("Removing free atom at ${atom.start} (${atom.size} bytes)")
            }
        }
        var offset = (moov!!.size - freeSize).toInt()
        if (moov.start < mdatStart) {
            offset -= moov.size.toInt()
            if (!removeFree || freeSize == 0L) {
                //good to go already!
                logger.info("File already suitable.")
                return false
            }
        }
        val moovContents = ByteArray(moov.size.toInt())
        try {
            inputStream.channel.position(moov.start)
            inputStream.read(moovContents)
        } catch (ex: IOException) {
            logger.error(ex)
            throw FastStartException("IO Exception reading moov contents.")
        }
        val moovIn = ByteArrayInputStreamPosition(moovContents)
        val moovOut = ByteArrayOutputStream(moovContents.size)

        //Skip type and size
        moovIn.skip(8)
        try {
            while (true) {
                val atom = skipToNextTable(moovIn) ?: break
                moovIn.skip(4) //skip version and flags
                val entryCount = readInt(moovIn)
                logger.info("Patching ${atom.type} with $entryCount entries.")
                val entriesStart: Int = moovIn.position()
                //write up to start of the entries
                moovOut.write(moovContents, moovOut.size(), entriesStart - moovOut.size())
                @Suppress("SpellCheckingInspection")
                if (atom.type.equals("stco", ignoreCase = true)) { //32 bit
                    var entry: ByteArray
                    for (i in 0 until entryCount) {
                        entry = toByteArray(readInt(moovIn) + offset)
                        moovOut.write(entry)
                    }
                } else { //64 bit
                    var entry: ByteArray
                    for (i in 0 until entryCount) {
                        entry = toByteArray(readLong(moovIn) + offset)
                        moovOut.write(entry)
                    }
                }
            }
            if (moovOut.size() < moovContents.size) //write the rest
                moovOut.write(moovContents, moovOut.size(), moovContents.size - moovOut.size())
        } catch (ex: IOException) {
            logger.error(ex)
            throw FastStartException("IO Exception while patching moov.")
        }
        if(outputStream==null) {
            return true
        }
        logger.debug("Writing output file:")

        //write FTYP
        for (atom in index) {
            if (atom.type.equals("ftyp", ignoreCase = true)) {
                logger.debug("Writing ftyp...")
                try {
                    inputStream.channel.position(atom.start)
                    val data = ByteArray(atom.size.toInt())
                    inputStream.read(data)
                    outputStream.write(data)
                } catch (ex: IOException) {
                    logger.error(ex)
                    throw FastStartException("IO Exception during writing ftyp.")
                }
            }
        }

        //write MOOV
        try {
            logger.debug("Writing moov...")
            //if(DEBUG) System.out.println("Modified moov contents:\n"+moovOut.toString());
            moovOut.writeTo(outputStream)
            moovIn.close()
            moovOut.close()
        } catch (ex: IOException) {
            logger.error(ex)
            throw FastStartException("IO Exception during writing moov.")
        }

        //write everything else!
        for (atom in index) {
            if (atom.type.equals("ftyp", ignoreCase = true) ||
                atom.type.equals("moov", ignoreCase = true) ||
                atom.type.equals("free", ignoreCase = true)
            ) {
                continue
            }
            logger.debug("Writing ${atom.type}...")
            val progress = Progress(atom.size, progressCallback)
            try {
                inputStream.channel.position(atom.start)
                val chunk = ByteArray(CHUNK_SIZE)
                for (i in 0 until atom.size / CHUNK_SIZE) {
                    inputStream.read(chunk)
                    outputStream.write(chunk)
                    progress.update(progress.current + CHUNK_SIZE)
                }
                val remainder = (atom.size % CHUNK_SIZE).toInt()
                if (remainder > 0) {
                    inputStream.read(chunk, 0, remainder)
                    outputStream.write(chunk, 0, remainder)
                }
                progress.update(atom.size)
            } catch (ex: IOException) {
                logger.error(ex)
                throw FastStartException("IO Exception during output writing.")
            }
        }
        logger.info("Write complete!")

        //cleanup
        try {
            inputStream.close()
            outputStream.flush()
            outputStream.close()
        } catch (e: IOException) { /* Intentionally empty */
        }
        return true
    }
}