# Android Media Processor

[English](README.md) | [日本語](README-ja.md)

## About This Library

This library is designed for re-encoding (transcoding), trimming/editing, and concatenating video files.
It exclusively uses Android OS's standard APIs (MediaExtractor, MediaCodec, MediaMuxer), so no additional licenses are required, making it suitable for commercial use.

### Key Features

- Video file re-encoding (transcoding) <br>
    Re-encodes video files using codecs supported by the OS (H.264, H.265, VP8, VP9, AAC, etc.).
    It can be used to compress file sizes by specifying resolution (number of pixels), bitrate, frame rate, etc.
    When re-encoding is unnecessary (i.e., the input file already meets the specified criteria), the file is processed without re-encoding automatically.
- Trimming/editing video files <br>
    Specify start and end times to cut out (trim) a portion of a video file.
    You can also create a new video file by specifying multiple time ranges and connecting only the necessary parts (editing).
- Concatenating video files <br>
    Joins multiple video files into a single video file.
    Files with different resolutions, rotations, audio sample rates, etc. can be concatenated.
- FastStart (moving metadata to the beginning of the video file) <br>
    Moving the metadata (MOOV Atom) to the beginning of the video file speeds up the start of video playback.
    It can be executed as an option of the conversion/concatenation process, or as a standalone operation.

### Gradle

Define a reference to the maven repository https://jitpack.io in `settings.gradle.kts`.

```kotlin
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        mavenLocal()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Add dependencies in the module's build.gradle.

```kotlin
dependencies {
    implementation("com.github.toyota-m2k:android-media-processor:Tag")
}
```
Please specify the latest release version for Tag. Note that using the latest version requires Android targetSdk 35 or higher. If your targetSdk is below 35, specify `2.16.2-LTS34`. However, this version does not include the latest features and bug fixes, so it is recommended to change targetSdk to 35 or higher.

### What's New in v5 (Migration from Older Versions)

- Up to v3, the `Converter` class (`Converter.Factory`) was used. In v4, the conversion engine was redesigned around the `Processor` class.
  In v5, the video concatenation (concat) feature has been added to `Processor`, and the API has been cleaned up.
- Conversion parameters are constructed with `ConvertOptions.Builder` (for conversion/trimming) or `ConcatOptions.Builder` (for concatenation), and passed to `Processor#process()`.
- The old `Converter` class remains in the `legacy` package (`io.github.toyota32k.media.lib.legacy.converter`) for verification purposes,
  but it may be removed in a future version. Use `Processor` in new code.

## Basic Usage

Construct the conversion parameters (`ConvertOptions`) with `ConvertOptions.Builder`, and pass them to the `process()` method of `Processor` to execute the conversion.
The following example executes transcoding by specifying the input/output files and encoding methods.

```kotlin
    val options = ConvertOptions.Builder()
        .input(inFile)          // Input file
        .output(outFile)        // Output file
        .audioStrategy(PresetAudioStrategies.AACDefault)            // Audio encoding settings
        .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)    // Video encoding settings
        .build()                // Create ConvertOptions

    // process() is a suspend function (it switches to Dispatchers.IO internally)
    val result = Processor().process(options, null)     // pass null if progress notification is not needed
    if (result.succeeded) {
        // Conversion succeeded
    }
```

### Input File

The input video file can be specified as a local file (File/Uri) or as a URL string from an HTTP server.
 - fun `input`(path: File): Builder   // Local file
 - fun `input`(uri: Uri, context: Context): Builder // Uri of local file
 - fun `input`(url: String, context: Context): Builder // URL from HTTP server
 
### Output File

Specify a writable local file (File/Uri).
 - fun `output`(path: File): Builder  // Local file
 - fun `output`(uri: Uri, context: Context): Builder  // Uri of local file

### Specifying Encoding

Specify the encoding method by passing classes that implement the `IAudioStrategy`/`IVideoStrategy` interfaces to the Builder's `audioStrategy()`/`videoStrategy()` methods.
While you can implement these classes yourself, you generally use the `AudioStrategy`/`VideoStrategy` classes, which are basic implementations of these interfaces, by passing the necessary parameters (codec, profile, profile level, etc.).

Example
```kotlin
    object AVC1080Profile : VideoStrategy(
        // Codec: H.264
        codec = Codec.AVC,      
        // Profile: High Profile
        profile = Profile.AVCProfileHigh,
        // Profile level: 4.1
        level = Level.AVCLevel41,
        // Alternative Profile/Level if the device doesn't support the above Profile/Level
        fallbackProfiles = arrayOf(
            ProfileLv(Profile.AVCProfileMain,Level.AVCLevel41), 
            ProfileLv(Profile.AVCProfileBaseline,Level.AVCLevel41)),
        // Maximum resolution: FullHD-1080p
        sizeCriteria = VideoStrategy.SizeCriteria(
            VideoStrategy.FHD1080_S_SIZE, 
            VideoStrategy.FHD1080_L_SIZE), 
        // Bitrate (specify maximum and default values)
        bitRate = MaxDefault(4*1000*1000, 2*1000*1000),
        // Frame rate (specify maximum and default values)
        frameRate = MaxDefault(30, 30),
        // iFrame interval (specify minimum and default values)
        iFrameInterval = MinDefault(1,1),
        // Color format
        colorFormat = ColorFormat.COLOR_FormatSurface,
        // Bitrate mode (CBR, VBR, etc.). Null uses the system default
        bitRateMode = null,
    )
```

Some typical settings are defined in the `PresetAudioStrategies`/`PresetVideoStrategies` classes, which you can also use.
- PresetVideoStrategies: profiles for AVC (HD720/FullHD) and HEVC (HD720/FullHD/4K)
- PresetAudioStrategies: `AACDefault`, `AACMono`, `AACLowHEv2`, `NoAudio` (removes the audio track)

#### No Re-encoding (Suppressing Unnecessary Re-encoding)

- Tracks for which `videoStrategy()`/`audioStrategy()` are not specified are copied as-is from the input without re-encoding.
  This avoids quality degradation caused by re-encoding and is processed quickly, so it is useful when you only want to trim a file.
- Even if `videoStrategy()` is specified, when the input file already meets its criteria (codec, bitrate, and resolution),
  the track is automatically processed without re-encoding (copied). To disable this behavior and always re-encode,
  specify `forceReEncodeDespiteOfNecessity(true)`.
- To remove the audio track, specify `audioStrategy(PresetAudioStrategies.NoAudio)`.

#### Keeping the Profile of the Input File

- fun `keepVideoProfile`(flag: Boolean): Builder<br>
  When re-encoding with the same codec, matches the Profile/Level to the input file.
- fun `keepHDR`(flag: Boolean): Builder<br>
  When re-encoding an HDR video, selects a Profile that preserves HDR if the output codec supports it.

### Executing the Conversion and Getting the Result

`Processor#process()` is a suspend function. It switches to Dispatchers.IO internally, so it can be called from any coroutine scope.
The return value is an `IConvertResult`, which provides the following information:

- `succeeded`: whether the conversion succeeded
- `cancelled`: whether the conversion was cancelled
- `exception` / `errorMessage`: error information
- `report`: detailed information about the input/output files (codec, resolution, bitrate, processing time, etc.). Call `toString()` to get a formatted string.
- `soughtMap`: information about the positions actually sought during trimming (described later)

## Trimming

Pass a lambda that constructs the ranges to cut out (a scope function of `RangeUsListBuilder`) to the Builder's `trimming()` method.
For example, to cut out from the 1-second mark to the 5-second mark, use the following code:

```kotlin
    val options = ConvertOptions.Builder()
        .input(inFile)          // Input file
        .output(outFile)        // Output file
        .audioStrategy(PresetAudioStrategies.AACDefault)            // Audio encoding settings
        .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)    // Video encoding settings
        .trimming {
            addRangeMs(1000, 5000)  // From 1 second to 5 seconds
        }
        .build()
    val result = Processor().process(options, null)
```

Specifying 0 as the end time means "to the end of the file". For example, to cut out from the 1-second mark to the end, use `addRangeMs(1000, 0)`.

## Editing (Cutting and Recombining Multiple Ranges)

By specifying multiple ranges inside `trimming { }`, a video file is created by connecting them.
For example, to cut from the 1-second mark to the 5-second mark, the 10-second mark to the 15-second mark, and the 20-second mark to the 25-second mark to create a new video file, use the following code:

```kotlin
    val options = ConvertOptions.Builder()
        .input(inFile)          // Input file
        .output(outFile)        // Output file
        .audioStrategy(PresetAudioStrategies.AACDefault)            // Audio encoding settings
        .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)    // Video encoding settings
        .trimming {
            addRangeMs(1000, 5000)      // From 1 second to 5 seconds
            addRangeMs(10000, 15000)    // From 10 seconds to 15 seconds
            addRangeMs(20000, 25000)    // From 20 seconds to 25 seconds
        }
        .build()
```

If you have a list of `RangeMs` (or `RangeUs`) objects, you can specify them all at once with `addRangesMs()`/`addRangesUs()`.

```kotlin
    val ranges = listOf(
        RangeMs(1000, 5000),    // From 1 second to 5 seconds
        RangeMs(10000, 15000),  // From 10 seconds to 15 seconds
        RangeMs(20000, 25000)   // From 20 seconds to 25 seconds
    )
    val options = ConvertOptions.Builder()
        .input(inFile)
        .output(outFile)
        .audioStrategy(PresetAudioStrategies.AACDefault)
        .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)
        .trimming {
            addRangesMs(ranges) // Specify multiple ranges at once
        }
        .build()
```
If you have a list of valid ranges in a video editing interface, it can be more convenient to use this method rather than specifying each range individually.

### Notes on Editing

- Specify the editing ranges in chronological order without overlapping. Invalid ranges (start >= end, overlapping with the previous range, etc.) are ignored with an error log.
- If there is no keyframe at the specified position, the exact position may not be cut. 
  The positions actually sought can be checked with the `soughtMap` property of `IConvertResult`.
  Using `soughtMap.correctPositionUs(timeUs)`, you can convert a time on the source video to the corresponding time on the output video.

## Concatenating Videos

To join multiple video files into a single video file, construct the parameters with `ConcatOptions.Builder` and pass them to `Processor#process()`.

```kotlin
    val options = ConcatOptions.Builder()
        .addInput(inFile1)                      // 1st input file
        .addInput(inFile2) {                    // 2nd input file
            addRangeMs(1000, 5000)              // Trimming ranges can be specified per input file
        }
        .addInput(inFile3)                      // 3rd input file
        .output(outFile)                        // Output file
        .videoStrategy(PresetVideoStrategies.HEVC1080Profile)   // Video encoding settings (required)
        .audioStrategy(PresetAudioStrategies.AACDefault)        // Audio encoding settings (required)
        .scaleMode(ScaleMode.FitInside)         // Scaling mode for inputs with different sizes (optional)
        .build()
    val result = Processor().process(options, null)
```

- Specify two or more input files. They are concatenated in the order they are added by `addInput()`.
  The same variations of input types (File/Uri/URL) as `ConvertOptions.Builder#input()` are available.
- Concatenation always re-encodes. Specifying `videoStrategy`/`audioStrategy` is mandatory (concatenation without re-encoding is not supported).
  If you don't want an audio track in the output, specify `audioStrategy(PresetAudioStrategies.NoAudio)`.

### Concatenating Files with Different Formats

Files with different resolutions, rotations, and audio formats can be concatenated.

- **Different resolutions (pixel counts)**<br>
  The reference size of the output is determined by applying the sizeCriteria of the videoStrategy to the size of the first input video (after applying its rotation).
  Videos whose size differs from the reference size are scaled according to the mode specified by `scaleMode()`.
    - `ScaleMode.FitInside` (default): fits inside the reference size while maintaining the aspect ratio. The margins become black bars (letterbox/pillarbox).
    - `ScaleMode.FitOutside`: fits outside the reference size while maintaining the aspect ratio. The protruding parts are cropped.
    - `ScaleMode.Stretch`: stretches to fill the reference size, ignoring the aspect ratio.
- **Different rotations (portrait/landscape)**<br>
  The rotation metadata of each input is normalized by baking it into the video frames at rendering time (no rotation metadata is set on the output file).
- **Different audio formats**<br>
  Inputs with different sample rates or channel counts are automatically resampled/remixed.
  If some input files have no audio track, silence is inserted for those sections to maintain A/V synchronization.

### Limitations of Concatenation

- All input files must have a video track (audio-only files cannot be concatenated).
- When HDR and SDR sources are mixed, the output will be SDR. Colors may vary slightly between sources.

## Progress Notification and Cancellation

You can pass a handler (`(IProgress)->Unit`) to the second argument of `process()` to receive progress notifications.
Additionally, you can cancel the running process using the `cancel()` method of the `Processor`.
For example, if you want to display the progress and provide a cancel button in the `MainActivity`, you can do it as follows:

```kotlin
class MainActivity : AppCompatActivity() {
    ...
    private var processor: Processor? = null    // Currently running Processor
    /**
     * Start the conversion.
     * Called from the convert start button.
     */
    fun startConvert() {
        val options = ConvertOptions.Builder()
            .input(File("input.mp4"))
            .output(File("output.mp4"))
            .audioStrategy(PresetAudioStrategies.AACDefault)
            .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)
            .build()
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        processor = Processor()
        lifecycleScope.launch {
            val result = processor?.process(options) { progress ->
                // Display progress
                // Since this is called from a sub-thread, switch to the UI thread when manipulating the UI
                runOnUiThread {
                    progressBar.max = 100
                    progressBar.progress = progress.percentage
                }
            }
            processor = null
        }
    }

    /**
     * Cancel the conversion.
     * Called when the cancel button is pressed.
     */
    fun cancelConvert() {
        processor?.cancel()
    }
}
```

The following information is available from `IProgress`:

- `percentage` / `permillage` / `permyriad`: progress ratio (percent / permille / permyriad)
- `current` / `total`: current position and total length
- `remainingTime`: estimated remaining time (ms)
- `format()`: a formatted string of the progress ratio, position, and remaining time

Note that when `optimize()` (FastStart, described below) is enabled, an `IMultiPhaseProgress` is passed to the handler,
and the current processing phase (Converting / Optimizing, etc.) can be identified by its `phase` property.

## FastStart

Moving the metadata (MOOV Atom) to the beginning of the video file speeds up the start of video playback.

### Executing Together with Conversion/Concatenation

Using the `optimize()` method of `ConvertOptions.Builder` / `ConcatOptions.Builder`, the FastStart optimization is executed automatically after the conversion/concatenation process.

```kotlin
    val options = ConvertOptions.Builder()
        .input(inFile)
        .output(outFile)
        .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)
        .audioStrategy(PresetAudioStrategies.AACDefault)
        .optimize(applicationContext, removeFreeAtom = true)   // Enable FastStart optimization
        .build()
```

- The Context of the first argument is used to create a temporary working file (ApplicationContext is fine).
- If `removeFreeAtom` is true, Free Atoms are removed, which can be expected to (slightly) reduce the file size.

### Executing as a Standalone Operation

You can also perform the FastStart optimization alone on an existing file.
First, check whether the optimization is needed using the `FastStart.check()` method, and if necessary, execute the process with the `FastStart.process()` method.

```kotlin
    if (FastStart.check(inUri, context).optimizable) {
        // If FastStart optimization is needed (or a Free Atom can be removed)
        FastStart.process(inUri, outUri, context, removeFree = true) { progress ->
            // Display progress
            ...
        }
    }
```

`FastStart.check()` returns a `CheckResult`:

- `AlreadyOptimized`: already optimized (no processing needed)
- `HasFreeAtom`: a Free Atom exists (removing it can reduce the file size)
- `MoovAtomAtEnd`: the MOOV Atom is at the end (FastStart optimization is needed)
- `Error`: analysis failed

The `optimizable` property indicates whether the file is worth processing.

## Other Options

### Options of ConvertOptions.Builder

- fun `rotate`(rotation: Rotation?): Builder<br>
  Specifies the rotation of the video. In addition to `Rotation.right` (90°), `Rotation.upsideDown` (180°), and `Rotation.left` (270°),
  `Rotation.relative(degree)` (rotation relative to the current orientation) and `Rotation.absolute(degree)` (absolute) are available.
  Specify the angle in units of 90 degrees.

- fun `crop`(rect: Rect?): Builder / fun `crop`(x: Int, y: Int, cx: Int, cy: Int): Builder<br>
  Specifies the cropping area of the video (in pixels). When crop is specified, re-encoding is performed even if videoStrategy is not specified.

- fun `limitDuration`(durationMs: Long): Builder<br>
  Specifies the maximum length of the output video. Zero or a negative value means no limit (default).

- fun `preferSoftwareDecoder`(flag: Boolean): Builder<br>
  Specifies whether to prefer a software decoder. The default is false, and a hardware decoder is normally used, but when a video cannot be decoded on some devices, a software decoder might be able to process it.
  Note that a software encoder can also be preferred, but the encoder is specified not on the options but by the preferSoftwareEncoder() method of AudioStrategy/VideoStrategy.

- fun `deleteOutputOnError`(flag: Boolean): Builder<br>
  Sets whether to delete (true) or keep (false) the output file left in an incomplete state when the conversion fails. The default is true (delete).

### Options of Processor

The following parameters can be specified in the constructor of `Processor`:

- `containerFormat`: the container format of the output file (`ContainerFormat.MPEG_4` / `ContainerFormat.WEBM`). The default is MPEG_4.
- `bufferSize`: the size of the working buffer. Normally there is no need to specify it.

## Getting Media Information

Using `Analyzer.analyze()`, you can obtain information about a video file (codec, resolution, bitrate, duration, etc.) as a `Summary`.

```kotlin
    val summary = Analyzer.analyze(AndroidFile(uri, context))
    Log.d(TAG, summary.toString())
```

Also, `DeviceCapabilities.availableCodecs()` provides a list of encoders/decoders supported by the device.

```kotlin
    val encoders = DeviceCapabilities.availableCodecs(Codec.HEVC, encoder = true)
    val decoders = DeviceCapabilities.availableCodecs(Codec.HEVC, encoder = false)
```

## Sample Program

In the MainActivity of the sample module, the following operations are possible:

- Select input video file (a second input file can also be selected for concatenation)
  - Display information of the selected input video file
  - Play the selected input video file and select trimming ranges
- Select output video file
- Select output video codec, choose software decoder/encoder, remove audio track
- Execute transcoding/trimming
- Concatenate two video files
- Enable/disable FastStart optimization (optimize)
  - Display information of the output video file
  - Play the output video file

The implementation of this sample uses the following libraries:
- [android-media-player](https://github.com/toyota-m2k/android-media-player)<br>Video file playback and trimming range selection
- [android-dialog](https://github.com/toyota-m2k/android-dialog)<br>File selection and dialog display
- [android-binding](https://github.com/toyota-m2k/android-binding)<br>View/ViewModel Binding
