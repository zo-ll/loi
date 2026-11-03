# loi

loi is a Kotlin library for on-device model inference on Android.
You supply a model file and call the loi API. loi owns the engine, the worker thread, the file checks, and the cleanup.

Version 1 supports EmbeddingGemma 2 Text 270M on LiteRT-LM.
The current version is 0.1.0. The API can change before version 1.0.
Read [DESIGN.md](DESIGN.md) for the API, the modules, and the build plan.

## Device support

loi runs on Android 7.0 (API 24) or later, on `arm64-v8a` and `x86_64` devices only.
LiteRT-LM 0.18.0 does not include a native library for 32-bit ARM (`armeabi-v7a`).

## Requirements

- JDK 21.
- The Android SDK with platform 37. Set `ANDROID_HOME` or write `sdk.dir` in `local.properties`.

## Check

Run all checks with one command:

```sh
./gradlew check
```

This command compiles all modules and runs the unit tests and Android lint.
It also runs the build-logic tests and the import-boundary check.

## Modules

| Module | Type | Responsibility |
|---|---|---|
| `core` | Kotlin/JVM | API, errors, worker, file checks, validation |
| `models/embeddinggemma2` | Kotlin/JVM | EmbeddingGemma 2 facts |
| `runtime-litertlm` | Android library | The LiteRT-LM engine adapter |
| `loi-android` | Android library | The `Loi` factory. Users add this module. |
| `testing` | Kotlin/JVM | A fake engine for tests |
| `sample` | Android app | A minimal app that uses `loi-android` |
| `build-logic` | Gradle build | Convention plugins and the import-boundary check |

## Import boundaries

`./gradlew check` fails if a module breaks one of these rules:

- Only `runtime-litertlm` can refer to `com.google.ai.edge`.
- `core`, `models/embeddinggemma2`, and `testing` cannot import `android` or `androidx` packages.

The rules are in the root `build.gradle.kts`. The check writes its report to `build/reports/import-boundaries.txt`.

## Sample app and device test

The `sample` app picks a model file, makes an embedder, and shows the similarity of one query to two documents.
The device test in `sample/src/androidTest` loads the real model file.

Warning: the device test needs a phone with USB debugging. Do not use `connectedDebugAndroidTest`. It uninstalls the app after the run, and that deletes the model file.

1. Download `embeddinggemma-2-text-270m.litertlm` from the Hugging Face repo `litert-community/embeddinggemma-2-text-270m-litert-lm`.
2. Make sure that the file is the known artifact:

   ```sh
   sha256sum embeddinggemma-2-text-270m.litertlm
   # 2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb
   ```

3. Install the sample app and its test APK:

   ```sh
   ./gradlew :sample:installDebug :sample:installDebugAndroidTest
   ```

4. Push the model file to the app-specific storage of the sample:

   ```sh
   adb shell mkdir -p /sdcard/Android/data/dev.loi.sample/files
   adb push embeddinggemma-2-text-270m.litertlm /sdcard/Android/data/dev.loi.sample/files/
   ```

5. Run the device test. The `-r` output shows the `loi.*` measurements:

   ```sh
   adb shell am instrument -w -r dev.loi.sample.test/androidx.test.runner.AndroidJUnitRunner
   ```

To check the minified build, install it over the debug build and run it once:

```sh
./gradlew :sample:installRelease
adb shell am start -n dev.loi.sample/.MainActivity --ez loi.autorun true
adb logcat -s LoiSample
```

The app writes one `LOI_SAMPLE_RESULT <json>` line.

### Measurements

The targets come from the forma model policy. They are targets, not gates.
The values come from one run on 2026-11-03: Nothing Phone (2a), Android 16 (API 36), `arm64-v8a`, CPU backend, 256 dimensions.

| Measure | Target | Nothing Phone (2a) |
|---|---|---|
| Cold load (`load()` with an empty cache) | 2 s or less | 984 ms (debug build) |
| Warm load (hash cache and engine cache) | none | 313 ms in the same process (debug). 763–793 ms in a new process (minified). |
| One query | 300 ms or less | 227 ms (debug). 180–193 ms (minified). The first minified run after the install took 369 ms. |
| One document | none | 128–161 ms |
| Text longer than `maxInputTokens` | none | Fails with `LoiException.Input(TOO_MANY_TOKENS)`. The SDK does not cut the text. |

The cold load includes the SHA-256 pass and the engine cache build. The OS file cache had the model file, so a load after a phone restart can be slower.
The similarity values were the same in the debug and minified builds: related 0.9314, unrelated 0.5944.

## Model files

The repo does not contain model files. `.gitignore` excludes `*.litertlm` and `*.tflite`.

## License

MIT. See [LICENSE](LICENSE).
