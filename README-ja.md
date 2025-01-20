# Android Media Processor

## このライブラリについて

このライブラリは、動画ファイルの再エンコード（トランスコード）を行うためのライブラリです。
Android OS の標準API（MediaExtractor, MediaCodec, MediaMuxer）だけを使用していますので、
追加のライセンスは不要で、商用利用も可能です。

### 主な機能

- 動画ファイルの再エンコード（トランスコード） <br>
    OSがサポートするコーデック（H.264, H.265, VP8, VP9, AAC, etc.）を使用して、動画ファイルの再エンコードを行います。
    解像度（ピクセル数）やビットレート、フレームレートなどを指定してファイルサイズの圧縮に利用できます。
- 動画ファイルのトリミング/編集<br>
    開始時間、終了時間を指定して、動画ファイルの一部を切り出す（トリミング）ことができます。 
    また、任意の時間範囲を指定して、必要な分だけを繋ぎ合わせて新しい動画ファイルを作成する（編集）も可能です。
- FastStart（動画ファイルの先頭にメタデータを移動）<br>
    動画ファイルの先頭にメタデータを移動することで、動画ファイルの再生を高速化します。

### Gradle

settings.gradle.kts で、mavenリポジトリ https://jitpack.io への参照を定義。

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

モジュールの build.gradle で、dependencies を追加。

```kotlin
dependencies {
    implementation("com.github.toyota-m2k:android-media-processor:Tag")
}
```
Tag には、最新のリリースバージョンを指定してください。
尚、最新バージョンの利用には、Androidの targetSdk 35以上が必要です。targetSdk 35未満の場合は、`2.16.2-LTS34` を指定してください。
ただし、このバージョンは、最新の機能やバグ修正が利用できませんので、targetSdkを35以上に変更することをお勧めします。

## 基本的な使い方

ファクトリ(Converter.Factory)を使って Converter インスタンスを作成し、execute()メソッドを呼び出します。次の例では、入/出力ファイルと、エンコード方法を指定して Converter オブジェクトを生成して、トランスコードを実行します。


```kotlin
    Converter.Factory()
        .input(inFile)          // 入力ファイル
        .output(outFile)        // 出力ファイル
        .audioStrategy(PresetAudioStrategies.AACDefault)            // 音声のエンコード設定
        .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)    // 映像のエンコード設定
        .build()                // Converter オブジェクトを生成
        .execute()              // 変換を実行
```

### 入力ファイル

入力動画ファイルには、ローカルファイル(File/Uri)または、HTTPサーバーのURL文字列が指定できます。
 - fun `input`(path: File): Factory   // ローカルファイル
 - fun `input`(uri: Uri, context: Context): Factory // ローカルファイルのUri
 - fun `input`(url: String, context: Context): Factory // HTTPサーバーのURL
 
### 出力ファイル

書き込み可能なローカルファイル(File/Uri)を指定します。
 - fun `output`(path: File): Factory  // ローカルファイル
 - fun `output`(uri: Uri, context: Context): Factory  // ローカルファイルのUri

### エンコードの指定

エンコード方法の指定には、`IAudioStrategy`/`IVideoStrategy` インターフェースを実装したクラスをFactoryの `audioStrategy()`/`videoStrategy()`メソッドに渡して指定します。
これらのクラスは自分で実装することもできますが、
通常、これらのインターフェースの基本実装である `AudioStrategy`/`VideoStrategy` クラスに必要なパラメータ(コーデック、プロファイル、プロファイルレベルなど)を渡して利用します。

例
```kotlin
    object AVC1080Profile : VideoStrategy(
        // コーデック：H.264
        codec = Codec.AVC,      
        // プロファイル: High Profile
        profile = Profile.AVCProfileHigh,
        // プロファイルレベル： 4.1
        level = Level.AVCLevel41,
        // 端末が上のProfile/Levelをサポートしていない場合に代替するProfile/Level
        fallbackProfiles = arrayOf(
            ProfileLv(Profile.AVCProfileMain,Level.AVCLevel41), 
            ProfileLv(Profile.AVCProfileBaseline,Level.AVCLevel41)),
        // 映像サイズ（解像度）の上限指定 : FullHD-1080p
        sizeCriteria = VideoStrategy.SizeCriteria(
            VideoStrategy.FHD1080_S_SIZE, 
            VideoStrategy.FHD1080_L_SIZE), 
        // ビットレート（最大値とデフォルト値を指定）
        bitRate = MaxDefault(4*1000*1000, 2*1000*1000),
        // フレームレート（最大値とデフォルト値を指定）
        frameRate = MaxDefault(30, 30),
        // iFrameインターバル（最大値とデフォルト値を指定）
        iFrameInterval = MinDefault(1,1),
        // カラーフォーマット（ColorFormat）
        colorFormat = ColorFormat.COLOR_FormatSurface,
        // ビットレートモード（CBR, VBRなど）を指定。null ならシステムのデフォルトを使用
        bitRateMode = null,
    )
```

尚、いくつかの典型的な設定は、`PresetAudioStrategies`/`PresetVideoStrategies` クラスに定義されているので、これを利用することもできます。

### コンバーター（Converterインスタンス）の作成

Converter オブジェクトを生成するために、Factory クラスの `build()` メソッドを呼び出します。

### 変換の実行

Converter オブジェクトの `execute()` メソッドを呼び出すことで、変換を実行します。
戻り値は、`ConvertResult`クラスのオブジェクトで、変換の成功・失敗、エラー情報が取得できます。
また、ConvertResultの `report` プロパティには、詳細な入出力ファイルの情報が含まれています。

## トリミング

Factory クラスの `trimmingStartFrom()` / `trimmingEndTo()` メソッドを使用して、トリミングの開始/終了時間をミリ秒単位で指定します。
例えば、開始１秒目から、5秒目までを切り出すには、次のようにします。

```kotlin
    Converter.Factory()
        .input(inFile)          // 入力ファイル
        .output(outFile)        // 出力ファイル
        .audioStrategy(PresetAudioStrategies.AACDefault)            // 音声のエンコード設定
        .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)    // 映像のエンコード設定
        .trimmingStartFrom(1000)    // 1秒から
        .trimmingEndTo(5000)        // 5秒まで
        .build()                // Converter オブジェクトを生成
        .execute()              // 変換を実行
```

## 編集（複数範囲の切り出しと再結合）

Factory クラスの `addTrimmingRange()`メソッドを使用して、切り出す範囲を指定してConverterオブジェクトを生成します。
例えば、開始1秒目から5秒目、10秒目から15秒目、20秒目から25秒目を切り出して、新しい動画ファイルを作成するには、次のようにします。

```kotlin
    Converter.Factory()
        .input(inFile)          // 入力ファイル
        .output(outFile)        // 出力ファイル
        .audioStrategy(PresetAudioStrategies.AACDefault)            // 音声のエンコード設定
        .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)    // 映像のエンコード設定
        .addTrimmingRange(1000, 5000)   // 1秒から5秒まで
        .addTrimmingRange(10000, 15000) // 10秒から15秒まで
        .addTrimmingRange(20000, 25000) // 20秒から25秒まで
        .build()                // Converter オブジェクトを生成
        .execute()              // 変換を実行
```

addTrimmingRangeメソッドの代わりに、`addTrimmingRanges()`メソッドを使用すると、`RangeMs` クラスの配列を使って、複数の範囲を一度に指定できます。
上の例を addTrimmingRanges()メソッドを使って書き換えると、次のようになります。

```kotlin
    val ranges = arrayOf(
        RangeMs(1000, 5000),    // 1秒から5秒まで
        RangeMs(10000, 15000),  // 10秒から15秒まで
        RangeMs(20000, 25000)   // 20秒から25秒まで
    )
    Converter.Factory()
        .input(inFile)          // 入力ファイル
        .output(outFile)        // 出力ファイル
        .audioStrategy(PresetAudioStrategies.AACDefault)            // 音声のエンコード設定
        .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)    // 映像のエンコード設定
        .addTrimmingRanges(ranges) // 複数の範囲を指定
        .build()                // Converter オブジェクトを生成
        .execute()              // 変換を実行
```
動画編集画面などで、有効範囲のリストを持っているような場合は、１つずつ指定するよりも、このメソッドの利用が便利です。

## 編集の注意点

- 編集範囲は、時間順に指定してください。指定した範囲が重複している場合、最後に指定した範囲が有効になります。
- 範囲指定に矛盾がある場合は、IllegalStateException がスローされます。
- 指定した位置にキーフレームがない場合、正確な位置で切り出されないことがあります。その場合は、実際に切り出された範囲を ConvertResultの adjustedTrimmingRangeList プロパティで確認できます。

## 進捗通知とキャンセル

Factory の setProgressHandler() メソッドを使用して、進捗通知を受け取るハンドラを設定できます。また Converter の cancel()メソッドで、変換を中止できます。
例えば、MainActivity に 進捗表示とキャンセルボタンを用意して、コンバートを行う場合は、次のようにします。

```kotlin
class MainActivity : AppCompatActivity() {
    ...
    private var converter: Converter? = null    // 実行中のConverter オブジェクト
    /**
     * コンバートを開始する。
     * 変換開始ボタンから呼び出される。
     */
    fun startConvert() {
        val inFile = File("input.mp4")
        val outFile = File("output.mp4")
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        Converter.Factory()
            .input(inFile)          // 入力ファイル
            .output(outFile)        // 出力ファイル
            .audioStrategy(PresetAudioStrategies.AACDefault)            // 音声のエンコード設定
            .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)    // 映像のエンコード設定
            .setProgressHandler { progress ->
                // 進捗表示
                // サブスレッドから呼ばれるのでUIを操作する場合はUIスレッドに切り替える
                runOnUiThread {
                    progressBar.max = 100
                    progressBar.progress = progress.percentage
                }
            }
            .build()                // Converter オブジェクトを生成
            .apply {
                converter = this
                // Converter#execute()は suspend関数なのでコルーチンスコープで実行する。
                CoroutineScope(Dispatchers.IO).launch {
                    execute()        // 変換を実行
                    converter = null
                }
            }
    }

    /**
     * 変換のキャンセル
     * キャンセルボタンが押されたときに呼び出される。
     */
    fun cancelConvert() {
        converter?.cancel()
    }
}
```

## Converter.Factory のその他のオプション

- fun `rotate`(rotation: Rotation):Factory<br>
  動画の回転を指定します。`Rotation` クラスの定数を指定しますが、現時点では、90度(`Rotation.right`)、180度(_Rotation.upsideDown_)、270度(`Rotation.left`) のみをサポートしています。
 
- fun `containerFormat`(format: ContainerFormat):Factory<br>
  出力ファイルのコンテナフォーマット(mp4,webm) を指定します。`ContainerFormat` クラスの定数を指定します。デフォルトは mp4 です。

- fun `preferSoftwareDecoder`(flag:Boolean):Factory<br>
  デコーダーの選択をソフトウェアデコーダーに優先するかどうかを指定します。デフォルトは false で、通常はハードウェアデコーダーを使用しますが、イケてないデバイスでデコードできない動画があったときに、ソフトウェアデコーダーなら処理できるかもしれません。
  尚、エンコーダーもソフトウェアエンコーダーを優先することができますが、エンコーダーは、Converterではなく、AudioStrategy/VideoStrategy の、preferSoftwareEncoder()メソッドで指定します。

- fun `deleteOutputOnError`(flag:Boolean):Factory<br>
  コンバートに失敗したとき、中途半端な状態となった出力ファイルを削除する(true)か、しない(false)かを設定します。デフォルトは true（削除する）です。

## FastStart

動画ファイルの先頭にメタデータ(MOOV Atom)を移動することで、動画ファイルの再生を高速化します。
まず、FastStart が必要かどうかを `FastStart.check()`メソッドで確認し、必要な場合は、`FastStart.process()`メソッドで処理を実行します。

```kotlin
    if (FastStart.check(inUri, context, removeFree: true)) {
        // FastStartが必要な場合
        FastStart.execute(inFile, outFile) { progress ->
            // 進捗表示
            ...
        }
    }
```

FastStart.checkの第３引数 (`removeFree`) に true を指定すると、MOOV Atom の移動が不要でも、Free Atom があれば trueを返します。
Free Atom がある場合、FastStart.process() を実行すると、Free Atom が削除され、ファイルサイズの削減が（わずかながら）期待できます。


## サンプルプログラム

sample module の MainActivity では、次の操作が可能です。

- 入力動画ファイルの選択
  - 選択された入力動画ファイルの情報表示
  - 選択された入力動画ファイルの再生、切り出し範囲選択
- 出力動画ファイルの選択
- 出力動画コーデックの選択、ソフトウェアデコーダー/エンコーダーの選択、Audioトラック削除
- トランスコード・トリミングの実行
  - 出力動画ファイルの情報表示
  - 出力動画ファイルの再生

尚、本サンプルの実装には、以下のライブラリを利用しています。
- [android-dialog](https://github.com/toyota-m2k/android-dialog)<br>動画ファイルの再生、切り出し範囲選択
- [android-media-player](https://github.com/toyota-m2k/android-media-player)<br>ファイル選択、ダイアログの表示
- [android-binding](https://github.com/toyota-m2k/android-binding)<br>View/ViewModel Binding
