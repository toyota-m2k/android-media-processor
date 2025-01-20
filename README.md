# Android Media Processor

## About This Library

This library is designed to re-encode (transcode) video files. It exclusively uses Android OS's standard APIs (MediaExtractor, MediaCodec, MediaMuxer), so no additional licenses are required, making it suitable for commercial use.

### Key Features

- Video file re-encoding (transcoding) <br>
    Re-encoding of video files using codecs supported by the OS (H.264, H.265, VP8, VP9, AAC, etc.). It can be used to compress file sizes by specifying resolution (number of pixels), bitrate, frame rate, etc.
- Trimming/editing video files <br>
    Specify start and end times to cut out (trim) a portion of a video file. You can also create a new video file by connecting only the necessary parts by specifying any time range (editing).
- FastStart (moving metadata to the beginning of the video file) <br>
    Moving metadata to the beginning of the video file speeds up video playback.

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

## Basic Usage

Create a `Converter` instance using a `Factory` and call the `execute()` method. In the following example, a `Converter` object is created by specifying the input/output files and encoding methods, and then the transcoding is executed.

```kotlin
    Converter.Factory()
        .input(inFile)          // Input file
        .output(outFile)        // Output file
        .audioStrategy(PresetAudioStrategies.AACDefault)            // Audio encoding settings
        .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)    // Video encoding settings
        .build()                // Create Converter object
        .execute()              // Execute conversion
```

### Input File
The input video file can be specified as a local file (File/Uri) or as a URL string from an HTTP server.

- fun input(path: File): Factory // Local file
- fun input(uri: Uri, context: Context): Factory // Uri of local file
- fun input(url: String, context: Context): Factory // URL from HTTP server

### Output File
Specify a writable local file (File/Uri).

- fun output(path: File): Factory // Local file
- fun output(uri: Uri, context: Context): Factory // Uri of local file

### Specifying Encoding
Specify the encoding method by passing classes that implement the `IAudioStrategy`/`IVideoStrategy` interfaces to the Factory's `audioStrategy()`/`videoStrategy()` methods. While you can implement these classes yourself, you generally use the `AudioStrategy`/`VideoStrategy` classes, which are basic implementations of these interfaces, by passing the necessary parameters (codec, profile, profile level, etc.).

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
        // iFrame interval (specify maximum and default values)
        iFrameInterval = MinDefault(1,1),
        // Color format
        colorFormat = ColorFormat.COLOR_FormatSurface,
        // Bitrate mode (CBR, VBR, etc.). Null uses the system default
        bitRateMode = null,
    )
```

Some typical settings are defined in the `PresetAudioStrategies`/`PresetVideoStrategies` classes, which you can also use.

### Creating a Converter Instance
Call the `build()` method of the Factory class to create a Converter object.

### Executing the Conversion
Execute the conversion by calling the `execute()` method of the Converter object. The return value is an object of the ConvertResult class, which provides information on the success or failure of the conversion, as well as error details. The `report` property of ConvertResult includes detailed information about the input and output files.

## Trimming

Specify the start and end times for trimming in milliseconds using the `trimmingStartFrom()` / `trimmingEndTo()` methods of the `Factory` class. For example, to trim from the 1-second mark to the 5-second mark, you would use the following code:

```kotlin
    Converter.Factory()
        .input(inFile)          // Input file
        .output(outFile)        // Output file
        .audioStrategy(PresetAudioStrategies.AACDefault)            // Audio encoding settings
        .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)    // Video encoding settings
        .trimmingStartFrom(1000)    // Start from 1 second
        .trimmingEndTo(5000)        // End at 5 seconds
        .build()                // Create Converter object
        .execute()              // Execute conversion
```

## Editing (Cutting and Recombining Multiple Ranges)

Create a `Converter` object by specifying the ranges to cut using the `addTrimmingRange()` method of the `Factory` class. For example, to cut from the 1-second mark to the 5-second mark, the 10-second mark to the 15-second mark, and the 20-second mark to the 25-second mark to create a new video file, use the following code:

```kotlin
    Converter.Factory()
        .input(inFile)          // Input file
        .output(outFile)        // Output file
        .audioStrategy(PresetAudioStrategies.AACDefault)            // Audio encoding settings
        .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)    // Video encoding settings
        .addTrimmingRange(1000, 5000)   // 1 second to 5 seconds
        .addTrimmingRange(10000, 15000) // 10 seconds to 15 seconds
        .addTrimmingRange(20000, 25000) // 20 seconds to 25 seconds
        .build()                // Create Converter object
        .execute()              // Execute conversion
```

Instead of the `addTrimmingRange` method, you can use the `addTrimmingRanges()` method to specify multiple ranges at once using an array of RangeMs objects. The example above, rewritten using the `addTrimmingRanges()` method, looks like this:

```kotlin
    val ranges = arrayOf(
        RangeMs(1000, 5000),    // 1 second to 5 seconds
        RangeMs(10000, 15000),  // 10 seconds to 15 seconds
        RangeMs(20000, 25000)   // 20 seconds to 25 seconds
    )
    Converter.Factory()
        .input(inFile)          // Input file
        .output(outFile)        // Output file
        .audioStrategy(PresetAudioStrategies.AACDefault)            // Audio encoding settings
        .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)    // Video encoding settings
        .addTrimmingRanges(ranges) // Specify multiple ranges
        .build()                // Create Converter object
        .execute()              // Execute conversion
```
If you have a list of valid ranges in a video editing interface, it can be more convenient to use this method rather than specifying each range individually.

## Notes on Editing

- Specify the editing ranges in chronological order. If the specified ranges overlap, the last specified range will be valid.
- If there are inconsistencies in the specified ranges, an `IllegalStateException` will be thrown.
- If there is no keyframe at the specified position, the exact position may not be cut. In such cases, you can check the actual cut range using the `adjustedTrimmingRangeList` property of the `ConvertResult`.

## Progress Notification and Cancellation

You can set a handler to receive progress notifications using the `setProgressHandler()` method of the `Factory`. Additionally, you can cancel the conversion using the `cancel()` method of the `Converter`. For example, if you want to display the progress and provide a cancel button in the `MainActivity`, you can do it as follows:

```kotlin
class MainActivity : AppCompatActivity() {
    ...
    private var converter: Converter? = null    // Currently running Converter object
    /**
     * Start the conversion.
     * Called from the convert start button.
     */
    fun startConvert() {
        val inFile = File("input.mp4")
        val outFile = File("output.mp4")
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        Converter.Factory()
            .input(inFile)          // Input file
            .output(outFile)        // Output file
            .audioStrategy(PresetAudioStrategies.AACDefault)            // Audio encoding settings
            .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)    // Video encoding settings
            .setProgressHandler { progress ->
                // Display progress
                // Since this is called from a sub-thread, switch to the UI thread when manipulating the UI
                runOnUiThread {
                    progressBar.max = 100
                    progressBar.progress = progress.percentage
                }
            }
            .build()                // Create Converter object
            .apply {
                converter = this
                // Converter#execute() is a suspend function, so execute it within a coroutine scope.
                CoroutineScope(Dispatchers.IO).launch {
                    execute()        // Execute conversion
                    converter = null
                }
            }
    }

    /**
     * Cancel the conversion.
     * Called when the cancel button is pressed.
     */
    fun cancelConvert() {
        converter?.cancel()
    }
}
```

## FastStart

By moving the metadata (MOOV Atom) to the beginning of the video file, video playback can be accelerated. First, check whether FastStart is needed using the `FastStart.check()` method. If necessary, execute the process with the `FastStart.process()` method.

```kotlin
    if (FastStart.check(inUri, context, removeFree: true)) {
        // If FastStart is needed
        FastStart.execute(inFile, outFile) { progress ->
            // Display progress
            ...
        }
    }
```

If you specify `true` for the third `removeFree` argument of FastStart.check, it will return `true` even if moving the MOOV Atom is unnecessary but there is a Free Atom. When `FastStart.process()` is executed with Free Atom present, it will remove the Free Atom, potentially reducing the file size slightly.

## Sample Program

In the MainActivity of the sample module, the following operations are possible:

- Select input video file
  - Display information of the selected input video file
  - Play the selected input video file and select trimming range
- Select output video file
- Select output video codec, choose software decoder/encoder, remove audio track
- Execute transcoding/trimming
  - Display information of the output video file
  - Play the output video file

The implementation of this sample uses the following libraries:
- [android-dialog](https://github.com/toyota-m2k/android-dialog)<br>Video file playback and trimming range selection
- [android-media-player](https://github.com/toyota-m2k/android-media-player)<br>File selection and dialog display
- [android-binding](https://github.com/toyota-m2k/android-binding)<br>View/ViewModel Binding
