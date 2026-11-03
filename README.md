# loi

loi is a Kotlin library for on-device model inference on Android.
You supply a model file and call the loi API. loi owns the engine, the worker thread, the file checks, and the cleanup.

Version 1 supports EmbeddingGemma 2 Text 270M on LiteRT-LM.
The library is in development. Read [DESIGN.md](DESIGN.md) for the API, the modules, and the build plan.

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

## Model files

The repo does not contain model files. `.gitignore` excludes `*.litertlm` and `*.tflite`.

## License

MIT. See [LICENSE](LICENSE).
