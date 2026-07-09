# Android Media Processor

[English](README.md) | [日本語](README-ja.md)

## このライブラリについて

このライブラリは、動画ファイルの再エンコード（トランスコード）、トリミング・編集、結合などを行うためのライブラリです。
Android OS の標準API（MediaExtractor, MediaCodec, MediaMuxer）だけを使用していますので、
追加のライセンスは不要で、商用利用も可能です。

### 主な機能

- 動画ファイルの再エンコード（トランスコード） <br>
    OSがサポートするコーデック（H.264, H.265, VP8, VP9, AAC, etc.）を使用して、動画ファイルの再エンコードを行います。
    解像度（ピクセル数）やビットレート、フレームレートなどを指定してファイルサイズの圧縮に利用できます。
    再エンコードが不要な場合（入力ファイルがすでに指定条件を満たしている場合）は、自動的に無変換で処理します。
- 動画ファイルのトリミング/編集<br>
    開始時間、終了時間を指定して、動画ファイルの一部を切り出す（トリミング）ことができます。 
    また、任意の時間範囲を複数指定して、必要な分だけを繋ぎ合わせて新しい動画ファイルを作成する（編集）も可能です。
- 動画ファイルの結合（Concatenation）<br>
    複数の動画ファイルを連結して、1つの動画ファイルを作成します。
    解像度・回転・音声サンプルレートなどが異なるファイル同士でも結合できます。
- FastStart（動画ファイルの先頭にメタデータを移動）<br>
    動画ファイルの先頭にメタデータ(MOOV Atom)を移動することで、動画ファイルの再生開始を高速化します。
    変換・結合処理のオプションとして実行できるほか、単体でも実行可能です。

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

### v5 での変更点（旧バージョンからの移行）

- v3 までは `Converter` クラス（`Converter.Factory`）を使用していましたが、v4 で、変換エンジンを `Processor` クラスに刷新しました。
  v5 では、`Processor` に動画結合（concat）機能を追加するとともに、APIを整理しました。
- 変換のパラメータは、`ConvertOptions.Builder`（変換・トリミング用）または `ConcatOptions.Builder`（結合用）で構築し、`Processor#process()` に渡します。
- 旧 `Converter` クラスは、検証用に `legacy` パッケージ (`io.github.toyota32k.media.lib.legacy.converter`) に残していますが、
  将来のバージョンで削除される可能性があるため、新規のコードでは `Processor` を使用してください。

## 基本的な使い方

`ConvertOptions.Builder` で変換パラメータ（`ConvertOptions`）を構築し、`Processor` の `process()` メソッドに渡して変換を実行します。
次の例では、入/出力ファイルとエンコード方法を指定してトランスコードを実行します。

```kotlin
    val options = ConvertOptions.Builder()
        .input(inFile)          // 入力ファイル
        .output(outFile)        // 出力ファイル
        .audioStrategy(PresetAudioStrategies.AACDefault)            // 音声のエンコード設定
        .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)    // 映像のエンコード設定
        .build()                // ConvertOptions を生成

    // process() は suspend 関数（内部で Dispatchers.IO に切り替えて実行される）
    val result = Processor().process(options, null)     // 進捗通知が不要なら null
    if (result.succeeded) {
        // 変換成功
    }
```

### 入力ファイル

入力動画ファイルには、ローカルファイル(File/Uri)または、HTTPサーバーのURL文字列が指定できます。
 - fun `input`(path: File): Builder   // ローカルファイル
 - fun `input`(uri: Uri, context: Context): Builder // ローカルファイルのUri
 - fun `input`(url: String, context: Context): Builder // HTTPサーバーのURL
 
### 出力ファイル

書き込み可能なローカルファイル(File/Uri)を指定します。
 - fun `output`(path: File): Builder  // ローカルファイル
 - fun `output`(uri: Uri, context: Context): Builder  // ローカルファイルのUri

### エンコードの指定

エンコード方法の指定には、`IAudioStrategy`/`IVideoStrategy` インターフェースを実装したクラスをBuilderの `audioStrategy()`/`videoStrategy()`メソッドに渡して指定します。
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
        // iFrameインターバル（最小値とデフォルト値を指定）
        iFrameInterval = MinDefault(1,1),
        // カラーフォーマット（ColorFormat）
        colorFormat = ColorFormat.COLOR_FormatSurface,
        // ビットレートモード（CBR, VBRなど）を指定。null ならシステムのデフォルトを使用
        bitRateMode = null,
    )
```

尚、いくつかの典型的な設定は、`PresetAudioStrategies`/`PresetVideoStrategies` クラスに定義されているので、これを利用することもできます。
- PresetVideoStrategies: AVC (HD720/FullHD)、HEVC (HD720/FullHD/4K) の各プロファイル
- PresetAudioStrategies: `AACDefault`, `AACMono`, `AACLowHEv2`, `NoAudio`（音声トラックを削除）

#### 無変換（再エンコードの抑制）

- `videoStrategy()`/`audioStrategy()` を指定しなかったトラックは、再エンコードせず、入力のトラックをそのままコピーします。
  再エンコードによる画質・音質の劣化がなく、高速に処理されるので、トリミングだけを行いたい場合に有効です。
- `videoStrategy()` を指定していても、入力ファイルがその条件（コーデック・ビットレート・解像度）をすでに満たしている場合は、
  自動的に無変換（コピー）で処理されます。この動作を無効にして、常に再エンコードさせたい場合は、
  `forceReEncodeDespiteOfNecessity(true)` を指定してください。
- 音声トラックを削除したい場合は、`audioStrategy(PresetAudioStrategies.NoAudio)` を指定します。

#### 入力ファイルのプロファイルの維持

- fun `keepVideoProfile`(flag: Boolean): Builder<br>
  同じコーデックで再エンコードするとき、Profile/Level を入力ファイルに合わせます。
- fun `keepHDR`(flag: Boolean): Builder<br>
  HDRが有効な動画を再エンコードするとき、出力コーデックで可能なら、HDRを維持するProfileを選択します。

### 変換の実行と結果

`Processor#process()` は suspend 関数です。内部で Dispatchers.IO に切り替えて実行されるので、任意のコルーチンスコープから呼び出せます。
戻り値は `IConvertResult` で、次の情報が取得できます。

- `succeeded`: 変換に成功したかどうか
- `cancelled`: キャンセルされたかどうか
- `exception` / `errorMessage`: エラー情報
- `report`: 入出力ファイルの詳細情報（コーデック、解像度、ビットレート、処理時間など）。`toString()` で整形された文字列を取得できます。
- `soughtMap`: トリミング時に、実際にシークされた位置の情報（後述）

## トリミング

Builderの `trimming()` メソッドに、切り出す範囲を構築するラムダ（`RangeUsListBuilder` のスコープ関数）を渡します。
例えば、開始１秒目から、5秒目までを切り出すには、次のようにします。

```kotlin
    val options = ConvertOptions.Builder()
        .input(inFile)          // 入力ファイル
        .output(outFile)        // 出力ファイル
        .audioStrategy(PresetAudioStrategies.AACDefault)            // 音声のエンコード設定
        .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)    // 映像のエンコード設定
        .trimming {
            addRangeMs(1000, 5000)  // 1秒から5秒まで
        }
        .build()
    val result = Processor().process(options, null)
```

終了時刻に 0 を指定すると「ファイルの末尾まで」を意味します。例えば、1秒目から末尾までを切り出すには `addRangeMs(1000, 0)` とします。

## 編集（複数範囲の切り出しと再結合）

`trimming { }` 内で複数の範囲を指定すると、それらを繋ぎ合わせた動画ファイルが作成されます。
例えば、開始1秒目から5秒目、10秒目から15秒目、20秒目から25秒目を切り出して、新しい動画ファイルを作成するには、次のようにします。

```kotlin
    val options = ConvertOptions.Builder()
        .input(inFile)          // 入力ファイル
        .output(outFile)        // 出力ファイル
        .audioStrategy(PresetAudioStrategies.AACDefault)            // 音声のエンコード設定
        .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)    // 映像のエンコード設定
        .trimming {
            addRangeMs(1000, 5000)      // 1秒から5秒まで
            addRangeMs(10000, 15000)    // 10秒から15秒まで
            addRangeMs(20000, 25000)    // 20秒から25秒まで
        }
        .build()
```

`RangeMs`（または `RangeUs`）のリストを持っている場合は、`addRangesMs()`/`addRangesUs()` で一括指定できます。

```kotlin
    val ranges = listOf(
        RangeMs(1000, 5000),    // 1秒から5秒まで
        RangeMs(10000, 15000),  // 10秒から15秒まで
        RangeMs(20000, 25000)   // 20秒から25秒まで
    )
    val options = ConvertOptions.Builder()
        .input(inFile)
        .output(outFile)
        .audioStrategy(PresetAudioStrategies.AACDefault)
        .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)
        .trimming {
            addRangesMs(ranges) // 複数の範囲を一括指定
        }
        .build()
```
動画編集画面などで、有効範囲のリストを持っているような場合は、１つずつ指定するよりも、このメソッドの利用が便利です。

### 編集の注意点

- 編集範囲は、時間順に、重複しないように指定してください。不正な範囲（開始 >= 終了、前の範囲との重複など）は、エラーログを出力して無視されます。
- 指定した位置にキーフレームがない場合、正確な位置で切り出されないことがあります。
  実際にシークされた位置は、`IConvertResult` の `soughtMap` プロパティで確認できます。
  `soughtMap.correctPositionUs(timeUs)` を使うと、元動画上の時刻を出力動画上の時刻に補正できます。

## 動画の結合（Concatenation）

複数の動画ファイルを連結して1つの動画ファイルを作成するには、`ConcatOptions.Builder` でパラメータを構築し、`Processor#process()` に渡します。

```kotlin
    val options = ConcatOptions.Builder()
        .addInput(inFile1)                      // 1番目の入力ファイル
        .addInput(inFile2) {                    // 2番目の入力ファイル
            addRangeMs(1000, 5000)              // 入力ファイル毎にトリミング範囲を指定できる
        }
        .addInput(inFile3)                      // 3番目の入力ファイル
        .output(outFile)                        // 出力ファイル
        .videoStrategy(PresetVideoStrategies.HEVC1080Profile)   // 映像のエンコード設定（必須）
        .audioStrategy(PresetAudioStrategies.AACDefault)        // 音声のエンコード設定（必須）
        .scaleMode(ScaleMode.FitInside)         // サイズが異なる場合のスケーリング方法（省略可）
        .build()
    val result = Processor().process(options, null)
```

- 入力ファイルは2つ以上指定します。`addInput()` した順に結合されます。
  入力ファイルの型（File/Uri/URL）は、`ConvertOptions.Builder#input()` と同じバリエーションが使用できます。
- 結合は再エンコード前提です。`videoStrategy`/`audioStrategy` の指定は必須です（無変換での結合はできません）。
  音声を出力しない場合は、`audioStrategy(PresetAudioStrategies.NoAudio)` を指定してください。

### 異なるフォーマットのファイルの結合

解像度・回転・音声フォーマットが異なるファイル同士でも結合できます。

- **解像度（画素数）が異なる場合**<br>
  1番目の入力動画のサイズ（回転適用後）に videoStrategy の sizeCriteria を適用したものが、出力の基準サイズになります。
  基準サイズと異なるサイズの動画は、`scaleMode()` で指定した方法でスケーリングされます。
    - `ScaleMode.FitInside`（デフォルト）: アスペクト比を維持して基準サイズに内接。余白は黒帯になります。
    - `ScaleMode.FitOutside`: アスペクト比を維持して基準サイズに外接。はみ出した部分はクロップされます。
    - `ScaleMode.Stretch`: アスペクト比を無視して基準サイズいっぱいに引き伸ばします。
- **回転（縦撮り・横撮り）が異なる場合**<br>
  各入力の回転メタデータは、レンダリング時に映像に焼き込んで正規化されます（出力ファイルに回転メタデータは設定されません）。
- **音声フォーマットが異なる場合**<br>
  サンプルレートやチャネル数が異なる入力が混在しても、自動的にリサンプリング/リミックスされます。
  音声トラックを持たないファイルが混在する場合は、その区間に無音が挿入され、A/V同期が維持されます。

### 結合の制限

- すべての入力ファイルに映像トラックが必要です（音声のみのファイルは結合できません）。
- HDR/SDR が混在する場合、出力は SDR になります。ソース間で色味が僅かに変わる可能性があります。

## 進捗通知とキャンセル

`process()` の第2引数に、進捗通知を受け取るハンドラ（`(IProgress)->Unit`）を渡すことができます。
また、`Processor` の `cancel()` メソッドで、実行中の処理を中止できます。
例えば、MainActivity に進捗表示とキャンセルボタンを用意して、変換を行う場合は、次のようにします。

```kotlin
class MainActivity : AppCompatActivity() {
    ...
    private var processor: Processor? = null    // 実行中のProcessor
    /**
     * 変換を開始する。
     * 変換開始ボタンから呼び出される。
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
                // 進捗表示
                // サブスレッドから呼ばれるのでUIを操作する場合はUIスレッドに切り替える
                runOnUiThread {
                    progressBar.max = 100
                    progressBar.progress = progress.percentage
                }
            }
            processor = null
        }
    }

    /**
     * 変換のキャンセル
     * キャンセルボタンが押されたときに呼び出される。
     */
    fun cancelConvert() {
        processor?.cancel()
    }
}
```

`IProgress` からは、次の情報が取得できます。

- `percentage` / `permillage` / `permyriad`: 進捗率（%/‰/万分率）
- `current` / `total`: 現在位置と全体の長さ
- `remainingTime`: 推定残り時間 (ms)
- `format()`: 進捗率・位置・残り時間を整形した文字列

尚、後述の `optimize()`（FastStart）を有効にしている場合、ハンドラには `IMultiPhaseProgress` が渡され、
`phase` プロパティで現在の処理フェーズ（Converting / Optimizing など）を識別できます。

## FastStart

動画ファイルの先頭にメタデータ(MOOV Atom)を移動することで、動画ファイルの再生開始を高速化します。

### 変換・結合と同時に実行する

`ConvertOptions.Builder` / `ConcatOptions.Builder` の `optimize()` メソッドを使用すると、変換・結合処理に続けて、自動的に FastStart 化が実行されます。

```kotlin
    val options = ConvertOptions.Builder()
        .input(inFile)
        .output(outFile)
        .videoStrategy(PresetVideoStrategies.HEVC1080LowProfile)
        .audioStrategy(PresetAudioStrategies.AACDefault)
        .optimize(applicationContext, removeFreeAtom = true)   // FastStart 化を有効にする
        .build()
```

- 第1引数の Context は、作業用一時ファイルの作成に使用します（ApplicationContext でokです）。
- `removeFreeAtom` に true を指定すると、Free Atom を削除して、ファイルサイズの削減が（わずかながら）期待できます。

### 単体で実行する

既存のファイルに対して FastStart 化だけを行うこともできます。
まず、`FastStart.check()` メソッドで FastStart 化が必要かどうかを確認し、必要なら `FastStart.process()` メソッドで処理を実行します。

```kotlin
    if (FastStart.check(inUri, context).optimizable) {
        // FastStart化が必要（または Free Atom の削除が可能）な場合
        FastStart.process(inUri, outUri, context, removeFree = true) { progress ->
            // 進捗表示
            ...
        }
    }
```

`FastStart.check()` は `CheckResult` を返します。

- `AlreadyOptimized`: 最適化済み（処理不要）
- `HasFreeAtom`: Free Atom が存在する（削除すればファイルサイズを削減できる）
- `MoovAtomAtEnd`: MOOV Atom が末尾にある（FastStart化が必要）
- `Error`: 解析に失敗

`optimizable` プロパティで、処理する価値があるかどうかを判定できます。

## その他のオプション

### ConvertOptions.Builder のオプション

- fun `rotate`(rotation: Rotation?): Builder<br>
  動画の回転を指定します。`Rotation.right`（90度）、`Rotation.upsideDown`（180度）、`Rotation.left`（270度）のほか、
  `Rotation.relative(degree)`（現在の向きからの相対回転）、`Rotation.absolute(degree)`（絶対指定）が使用できます。
  角度は90度単位で指定してください。

- fun `crop`(rect: Rect?): Builder / fun `crop`(x: Int, y: Int, cx: Int, cy: Int): Builder<br>
  映像の切り抜き範囲（ピクセル単位）を指定します。crop を指定すると、videoStrategy が未指定でも再エンコードが実行されます。

- fun `limitDuration`(durationMs: Long): Builder<br>
  出力動画の最大長を指定します。0以下なら制限なし（デフォルト）。

- fun `preferSoftwareDecoder`(flag: Boolean): Builder<br>
  デコーダーの選択をソフトウェアデコーダーに優先するかどうかを指定します。デフォルトは false で、通常はハードウェアデコーダーを使用しますが、イケてないデバイスでデコードできない動画があったときに、ソフトウェアデコーダーなら処理できるかもしれません。
  尚、エンコーダーもソフトウェアエンコーダーを優先することができますが、エンコーダーは、AudioStrategy/VideoStrategy の、preferSoftwareEncoder()メソッドで指定します。

- fun `deleteOutputOnError`(flag: Boolean): Builder<br>
  変換に失敗したとき、中途半端な状態となった出力ファイルを削除する(true)か、しない(false)かを設定します。デフォルトは true（削除する）です。

### Processor のオプション

`Processor` のコンストラクタで、次のパラメータを指定できます。

- `containerFormat`: 出力ファイルのコンテナフォーマット（`ContainerFormat.MPEG_4` / `ContainerFormat.WEBM`）。デフォルトは MPEG_4 です。
- `bufferSize`: 作業バッファのサイズ。通常は指定不要です。

## メディア情報の取得

`Analyzer.analyze()` を使用すると、動画ファイルの情報（コーデック、解像度、ビットレート、再生時間など）を `Summary` として取得できます。

```kotlin
    val summary = Analyzer.analyze(AndroidFile(uri, context))
    Log.d(TAG, summary.toString())
```

また、`DeviceCapabilities.availableCodecs()` で、デバイスがサポートするエンコーダー/デコーダーの一覧を取得できます。

```kotlin
    val encoders = DeviceCapabilities.availableCodecs(Codec.HEVC, encoder = true)
    val decoders = DeviceCapabilities.availableCodecs(Codec.HEVC, encoder = false)
```

## サンプルプログラム

sample module の MainActivity では、次の操作が可能です。

- 入力動画ファイルの選択（結合用に2つ目の入力ファイルも選択可能）
  - 選択された入力動画ファイルの情報表示
  - 選択された入力動画ファイルの再生、切り出し範囲選択
- 出力動画ファイルの選択
- 出力動画コーデックの選択、ソフトウェアデコーダー/エンコーダーの選択、Audioトラック削除
- トランスコード・トリミングの実行
- ２つの動画ファイルの結合
- FastStart化（optimize）の有効/無効切り替え
  - 出力動画ファイルの情報表示
  - 出力動画ファイルの再生

尚、本サンプルの実装には、以下のライブラリを利用しています。
- [android-media-player](https://github.com/toyota-m2k/android-media-player)<br>動画ファイルの再生、切り出し範囲選択
- [android-dialog](https://github.com/toyota-m2k/android-dialog)<br>ファイル選択、ダイアログの表示
- [android-binding](https://github.com/toyota-m2k/android-binding)<br>View/ViewModel Binding
