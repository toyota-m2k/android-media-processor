package io.github.toyota32k.media.lib.processor

import android.media.MediaFormat
import android.os.Build
import io.github.toyota32k.media.lib.format.MetaData
import io.github.toyota32k.media.lib.format.Profile
import io.github.toyota32k.media.lib.format.channelCount
import io.github.toyota32k.media.lib.format.frameRate
import io.github.toyota32k.media.lib.format.height
import io.github.toyota32k.media.lib.format.isHDR
import io.github.toyota32k.media.lib.format.sampleRate
import io.github.toyota32k.media.lib.format.width
import io.github.toyota32k.media.lib.internals.surface.RenderOption
import io.github.toyota32k.media.lib.misc.MediaConstants
import io.github.toyota32k.media.lib.processor.contract.IConcatOptions
import io.github.toyota32k.media.lib.strategy.PresetAudioStrategies
import io.github.toyota32k.media.lib.strategy.PresetVideoStrategies
import io.github.toyota32k.media.lib.strategy.VideoStrategy
import io.github.toyota32k.media.lib.types.Rotation

/**
 * 結合対象ソースの解析結果（1ソース分）
 *
 * @param metaData          MediaMetadataRetriever による解析結果
 * @param width             映像の幅（格納状態＝回転適用前）
 * @param height            映像の高さ（格納状態＝回転適用前）
 * @param rotation          回転メタデータ (0/90/180/270)
 * @param durationUs        再生時間 (us) 不明なら null
 * @param audioSampleRate   音声サンプルレート（音声トラックがなければ null）
 * @param isVideoHDR        映像がHDRプロファイルか
 */
internal class ConcatSourceInfo(
    val metaData: MetaData,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val durationUs: Long?,
    val audioSampleRate: Int?,
    val isVideoHDR: Boolean,
)

/**
 * 結合処理の実行計画（ConcatAnalyzer の解析結果）
 */
internal class ConcatPlan(
    val sourceInfos: List<ConcatSourceInfo>,
    val unified: UnifiedOutputFormat,
)

/**
 * 結合対象の全ソースを解析して、
 * - 結合可能かどうかのバリデーション
 * - 全ソースで共有する出力フォーマット (UnifiedOutputFormat) の決定
 * を行う。
 */
internal object ConcatAnalyzer {

    fun analyze(options: IConcatOptions): ConcatPlan {
        val dropAudio = options.audioStrategy is PresetAudioStrategies.NoAudio
        if (options.videoStrategy is PresetVideoStrategies.InvalidStrategy) {
            throw IllegalArgumentException("concat requires re-encoding: InvalidStrategy is not acceptable as videoStrategy.")
        }
        if (options.audioStrategy is PresetAudioStrategies.InvalidStrategy) {
            throw IllegalArgumentException("concat requires re-encoding: InvalidStrategy is not acceptable as audioStrategy. (use NoAudio to drop audio)")
        }
        if (options.sources.size < 2) {
            throw IllegalArgumentException("at least 2 input files are required.")
        }

        // 各ソースの解析
        var firstVideoFormat: MediaFormat? = null
        val audioOutChannels = mutableListOf<Int?>()    // ソース毎の出力チャネル数（音声なしなら null）
        val infos = options.sources.mapIndexed { index, source ->
            val extractorRef = source.input.openExtractor()
            try {
                val extractor = extractorRef.obj
                var videoFormat: MediaFormat? = null
                var audioFormat: MediaFormat? = null
                for (idx in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(idx)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (videoFormat == null && mime.startsWith("video/")) videoFormat = format
                    if (audioFormat == null && mime.startsWith("audio/")) audioFormat = format
                }
                if (videoFormat == null) {
                    throw IllegalArgumentException("source[$index] has no video track: ${source.input}")
                }
                if (index == 0) {
                    firstVideoFormat = videoFormat
                }
                val metaData = MetaData.fromFile(source.input)
                val width = videoFormat.width ?: metaData.width
                    ?: throw IllegalArgumentException("source[$index] has no video size information: ${source.input}")
                val height = videoFormat.height ?: metaData.height
                    ?: throw IllegalArgumentException("source[$index] has no video size information: ${source.input}")
                val rotation = if (videoFormat.containsKey(MediaConstants.KEY_ROTATION_DEGREES)) {
                    videoFormat.getInteger(MediaConstants.KEY_ROTATION_DEGREES)
                } else {
                    metaData.rotation ?: 0
                }
                // 音声の解析（NoAudioなら音声トラックは無視）
                val audioSampleRate: Int? = if (!dropAudio && audioFormat != null) {
                    val channels = audioFormat.channelCount ?: 0
                    if (channels != 1 && channels != 2) {
                        throw IllegalArgumentException("source[$index] audio channel count ($channels) is not supported.")
                    }
                    audioOutChannels.add(options.audioStrategy.resolveOutputChannelCount(audioFormat))
                    audioFormat.sampleRate
                        ?: throw IllegalArgumentException("source[$index] audio sample rate is unknown.")
                } else {
                    audioOutChannels.add(null)
                    null
                }
                ConcatSourceInfo(
                    metaData = metaData,
                    width = width,
                    height = height,
                    rotation = Rotation.normalize(rotation),
                    durationUs = metaData.durationUs,
                    audioSampleRate = audioSampleRate,
                    isVideoHDR = Profile.fromFormat(videoFormat)?.isHDR() == true,
                )
            } finally {
                extractorRef.close()
            }
        }

        // 音声の出力フォーマット決定
        // - 1つでも音声ありソースがあれば、出力に音声トラックを含める（音声なしソースの区間は無音挿入）。
        // - 出力サンプルレート: 最初の音声ありソースのレートに AudioStrategy の上限(sampleRate)を適用した値。
        //   異なるレートのソースは AudioChannel がリサンプリングする。
        // - 出力チャネル数: 最初の音声ありソースに対して AudioStrategy が解決した値。
        //   異なるチャネル数のソースは AudioChannel が up-mix / down-mix する。
        val hasAudio = !dropAudio && infos.any { it.audioSampleRate != null }
        val firstAudioIndex = infos.indexOfFirst { it.audioSampleRate != null }
        val audioSampleRate = if (hasAudio) {
            options.audioStrategy.sampleRate.value(infos[firstAudioIndex].audioSampleRate)
        } else 0
        val audioChannelCount = if (hasAudio) {
            audioOutChannels[firstAudioIndex] ?: 2
        } else 0

        // UnifiedOutputFormat の決定
        // 基準サイズ: sources[0] の回転適用後（表示状態）のサイズに SizeCriteria を適用したもの
        val first = infos[0]
        val displayWidth = if (first.rotation % 180 == 0) first.width else first.height
        val displayHeight = if (first.rotation % 180 == 0) first.height else first.width
        val unifiedSize = VideoStrategy.calcVideoSize(displayWidth, displayHeight, options.videoStrategy.sizeCriteria)

        // ビットレート・フレームレート・profile/level・HDR は sources[0] を基準に既存ロジックで決定
        val encoder = options.videoStrategy.createEncoder()
        val videoFormat = try {
            options.videoStrategy.createOutputFormat(firstVideoFormat!!, first.metaData, encoder, RenderOption.DEFAULT)
        } finally {
            encoder.release()
        }
        videoFormat.setInteger(MediaFormat.KEY_WIDTH, unifiedSize.width)
        videoFormat.setInteger(MediaFormat.KEY_HEIGHT, unifiedSize.height)

        // HDR: 全ソースがHDRの場合のみ引き継ぐ。SDRソースが混在する場合はHDR情報を除去してSDR出力とする。
        if (!infos.all { it.isVideoHDR }) {
            stripHdrInfo(videoFormat)
        }

        val frameRate = videoFormat.frameRate ?: first.metaData.frameRate ?: DEFAULT_FRAME_RATE
        val unified = UnifiedOutputFormat(
            videoFormat = videoFormat,
            width = unifiedSize.width,
            height = unifiedSize.height,
            frameRate = frameRate,
            hasAudio = hasAudio,
            audioSampleRate = audioSampleRate,
            audioChannelCount = audioChannelCount,
        )
        Processor.logger.info("concat: $unified")
        return ConcatPlan(infos, unified)
    }

    private fun stripHdrInfo(format: MediaFormat) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            format.removeKey(MediaFormat.KEY_COLOR_STANDARD)
            format.removeKey(MediaFormat.KEY_COLOR_RANGE)
            format.removeKey(MediaFormat.KEY_COLOR_TRANSFER)
            format.removeKey(MediaFormat.KEY_HDR_STATIC_INFO)
            format.removeKey(MediaFormat.KEY_HDR10_PLUS_INFO)
        } else if (format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) {
            // API 28 以下では MediaFormat.removeKey が使えない
            Processor.logger.warn("HDR/SDR mixed sources: cannot remove HDR keys on API ${Build.VERSION.SDK_INT}.")
        }
    }

    private const val DEFAULT_FRAME_RATE = 30
}
