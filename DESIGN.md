# loi — design

Status: draft for owner approval. Date: 2026-11-03.

## 1. Purpose

loi is a Kotlin library for on-device inference on Android.
A developer supplies a model file and calls the loi API. loi owns the engine, the worker thread, file checks, and cleanup.
Version 1 supports one model: EmbeddingGemma 2 Text 270M, on the LiteRT-LM runtime.

The forma repo is the source of the first code. forma is not a user of loi and does not control its design.

## 2. Scope

In scope for version 1:

- Text embeddings with EmbeddingGemma 2, for query and document tasks.
- A model file that the caller supplies as a `java.io.File`.
- SHA-256 file checks with three levels: strict, warn, and off.
- One serial worker for each embedder, with coroutine cancellation.
- A `release()` hook that the app calls on memory pressure.
- A sample Android app.

Out of scope for version 1:

- Text generation, GPU backends, and other models.
- iOS. iOS support is expected later, so the core avoids Android types where that costs little.
- React Native and Flutter adapters. They can come later as separate modules.
- Model download. The caller obtains the file.
- Vector storage and vector search. loi returns vectors and a similarity function only.
- Publication to a Maven repository. The repo is local only until the API is stable.

## 3. Decisions

| Topic | Decision |
|---|---|
| Language | Kotlin, Android library modules (AAR) |
| Runtime | LiteRT-LM Kotlin SDK `com.google.ai.edge.litertlm:litertlm-android:0.18.0` |
| First user | Native Android developers |
| Adapters | Flutter and React Native only if needed, as separate modules |
| File check | Strict, warn, or off. The default is strict. |
| Memory pressure | The app calls `release()`. loi does not register Android callbacks. |
| Distribution | Local only. Users include the repo as a Gradle included build. |
| Package and group | `dev.loi` |
| License | MIT, as in forma |
| Toolchain | Kotlin 2.4.0, Java 21, Gradle 9.4.1, minSdk 24, compileSdk 37 |

The toolchain values come from the forma Android build, which passed device acceptance with LiteRT-LM 0.18.0.
The SDK publishes Java 21 class files and Kotlin 2.4.0 metadata, so these values are minimums.

## 4. Public API

The user adds one dependency, `:loi-android`, and calls one factory.

```kotlin
val embedder: Embedder = Loi.embeddingGemma2(
    context = applicationContext,
    modelFile = File(filesDir, "embeddinggemma-2-text-270m.litertlm"),
    options = EmbeddingGemma2.Options(
        dimensions = 256,                     // 128, 256, 512, or 768
        maxInputTokens = 2048,                // 128, 256, 512, 1024, 2048, or 8192
        verification = Verification.STRICT,   // STRICT, WARN, or OFF
    ),
)

val query: Embedding = embedder.embed("knee pain after squats", EmbeddingTask.QUERY)
val docs: List<Embedding> = embedder.embedAll(listOf("note one", "note two"), EmbeddingTask.DOCUMENT)
val score: Float = query.cosine(docs[0])

embedder.release()   // On memory pressure. The next call loads the model again.
embedder.close()     // At the end. The embedder rejects calls after this.
```

`Loi.embeddingGemma2` does not read the file or load the model. The first `embed` call, or `load()`, does that on the worker thread.
`Loi.storageRoot(context)` returns `<noBackupFilesDir>/loi`, the folder for the hash cache and the engine caches. An app can delete it to clear all loi state.

`loi-android` exposes `core` and `models:embeddinggemma2` as API dependencies. `runtime-litertlm` is an implementation dependency, so app code cannot use LiteRT-LM types through loi.

The core interface does not use Android types:

```kotlin
interface Embedder : AutoCloseable {
    val model: ModelInfo                          // id, dimensions, verification result
    suspend fun load()                            // Optional. Loads the model before the first call.
    suspend fun embed(text: String, task: EmbeddingTask): Embedding
    suspend fun embedAll(texts: List<String>, task: EmbeddingTask): List<Embedding>
    suspend fun release()                         // Unloads the engine. The embedder stays usable.
    override fun close()                          // Unloads the engine and rejects later calls.
}

class Embedding(vector: FloatArray, val task: EmbeddingTask, val modelId: String) {
    val dimensions: Int
    operator fun get(index: Int): Float
    fun toFloatArray(): FloatArray                // A new copy each time
    fun cosine(other: Embedding): Float           // IllegalArgumentException if modelId or dimensions differ
}

data class ModelInfo(val id: String, val dimensions: Int, val verification: VerificationResult)
// VerificationResult: Pending (before the first load), Known(artifact), Unknown(sha256), or Skipped
```

`Embedding` keeps a private copy of the vector, so a caller cannot change it after construction.
Its `toString()` does not contain vector values.
`cosine` uses a programmer-error exception, not `LoiException`, because incompatible inputs are a bug in the caller.

loi adds the task prefix to the text. The caller sends plain text.
This rule reverses the forma rule, where the caller sent prefixed text and the native layer checked it.

## 5. Module layout

```
loi/
├── core/                      Kotlin/JVM. No Android, no LiteRT.
│   ├── api/                   Embedder, Embedding, EmbeddingTask, ModelInfo, Verification
│   ├── engine/                EmbeddingEngine port (interface) and EngineOptions
│   ├── runtime/               SerialEmbedder: worker, load state, cancellation, release, close
│   ├── verify/                SHA-256 file check and the three levels
│   ├── validate/              Input text rules and output vector rules
│   └── errors/                LoiException sealed hierarchy
├── models/embeddinggemma2/    Kotlin/JVM. Model spec: prefixes, allowed sizes, known artifacts.
├── runtime-litertlm/          Android. The only module that imports com.google.ai.edge.litertlm.
├── loi-android/               Android. The Loi factory. Wires the model spec, runtime, and cache directory.
├── testing/                   Kotlin/JVM. A fake engine for tests in other modules.
└── sample/                    Android app. Picks a model file, embeds two texts, shows the score.
```

Each module has one responsibility:

- `core` owns behavior that does not depend on a model or an engine.
- `models/*` owns facts about one model. A new model is a new module, and `core` does not change.
- `runtime-*` owns the translation to one engine SDK. A new engine is a new module.
- `loi-android` owns the composition and the Android-specific defaults.

Dependency rules:

| Module | Can depend on |
|---|---|
| `core` | `kotlinx-coroutines-core` only |
| `models/embeddinggemma2` | `core` |
| `runtime-litertlm` | `core`, LiteRT-LM |
| `loi-android` | `core`, `models/*`, `runtime-litertlm` |
| `testing` | `core` |
| `sample` | `loi-android` |

A build check fails if a module other than `runtime-litertlm` imports `com.google.ai.edge`.
A second check fails if `core` or `models/*` imports `android.`.

## 6. Engine port

`core` defines the engine port. `runtime-litertlm` implements it.

```kotlin
interface EmbeddingEngine : AutoCloseable {
    fun initialize()
    fun embed(prefixedText: String): FloatArray    // Synchronous. Called on the worker only.
}

// An engine throws this when the text has more tokens than maxInputTokens.
class TooManyTokensException(cause: Throwable?) : RuntimeException

data class EngineOptions(
    val modelPath: String,
    val cacheDir: String,
    val maxInputTokens: Int,
    val outputDimensions: Int,
    val normalize: Boolean,
    val insertSpecialTokens: Boolean,
    val backend: Backend,                           // CPU only in version 1
    val activationPrecision: Precision,             // FLOAT32 only in version 1
)

fun interface EmbeddingEngineFactory { fun create(options: EngineOptions): EmbeddingEngine }
```

The model spec supplies the values. The runtime maps them to `EmbeddingEngineConfig` and `EmbeddingOptions` of LiteRT-LM.
The port uses no SDK type, so a later iOS runtime can implement the same port.

`core` also defines two more contracts:

```kotlin
interface EmbeddingSpec {                           // Implemented by a model module
    val modelId: String                             // Identifies the vector space
    val dimensions: Int
    val normalized: Boolean                         // The embedder checks the L2 norm if true
    val maxInputCharacters: Int                     // Limit for the caller text, without the prefix
    fun prefix(task: EmbeddingTask): String
}

fun interface ModelPreparer {                       // Implemented by the file check (issue #4)
    fun prepare(): PreparedModel                    // Runs on the worker before each load
}
class PreparedModel(val options: EngineOptions, val verification: VerificationResult)
```

`SerialEmbedder(spec, preparer, factory)` connects the three contracts. It is the only `Embedder` implementation in `core`.

### LiteRT-LM runtime facts

These facts come from the 0.18.0 AAR:

- The native library is only for `arm64-v8a` and `x86_64`. loi does not run on `armeabi-v7a` devices.
- The AAR has no R8 keep rules. `runtime-litertlm` adds the consumer rule `-keep class com.google.ai.edge.litertlm.** { *; }`.
- The `EmbeddingEngine` constructor only stores the config. `initialize`, `computeEmbedding`, and `close` call the native library.
- `Engine.setNativeMinLogSeverity` is a process-wide setting. The last engine to initialize sets it.

## 7. Threading, cancellation, and lifetime

Each embedder owns one single-thread dispatcher. Only that thread touches the engine.
The embedder holds at most one engine handle.

Load is lazy. The first `embed` call, or an explicit `load()` call, verifies the file and initializes the engine.
`embedAll` runs the texts in sequence on the same worker, with one SDK call for each text.
LiteRT-LM 0.18.0 also has `computeEmbeddingBatch(List<List<InputData>>, EmbeddingOptions)`. Source: `javap` on the 0.18.0 AAR `classes.jar`.
loi does not use it in version 1: one call for each text keeps the cancel point between texts. A batch port method is a possible later speed change, after a device measurement.

Cancellation follows the coroutine rules:

- A cancel before the SDK call starts stops the call. loi does not call the SDK.
- A cancel during the SDK call cannot stop the native work. The caller resumes at once with `CancellationException`. The worker finishes the call and discards the result.
- A cancel during `embedAll` stops the remaining texts after the current SDK call.
- File verification and engine initialization cannot stop in the middle.

`release()` waits for the active call, closes the engine, and keeps the embedder open. The next call loads again.
`close()` waits for the active call, closes the engine, and stops the worker. Later calls fail with `LoiException.Closed`.

Warning: if the native engine fails to close, loi marks the embedder as poisoned. It does not load a second engine, because the first one can still hold memory. All later calls fail with `LoiException.Resource`.

These rules come from `InferenceCoordinator` and `SerialModelRuntime` in forma. loi removes the owner IDs, the request sequence numbers, and the process lease, because those rules protected the React Native bridge.

loi does not block calls until process restart after memory pressure. forma did that. A library must let the app decide when to load again.

## 8. Model file verification

The model module supplies a list of known artifacts:

| Artifact | Bytes | SHA-256 |
|---|---|---|
| `embeddinggemma-2-text-270m.litertlm` | 164,626,432 | `2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb` |

Source: `litert-community/embeddinggemma-2-text-270m-litert-lm`, Apache-2.0. loi does not include the file.

| Level | Behavior |
|---|---|
| `STRICT` | The size and SHA-256 must match a known artifact. If not, load fails with `LoiException.Verification`. |
| `WARN` | loi computes the SHA-256. An unknown file loads, and `ModelInfo.verification` reports `Unknown(sha256)`. |
| `OFF` | loi checks only that the file exists and can be read. `ModelInfo.verification` reports `Skipped`. |

The SHA-256 of a 165 MB file takes time on a phone, so loi caches the result.
After a full hash, loi writes a record with the canonical path, size, modification time, and SHA-256 to `<noBackupFilesDir>/loi/verify/`.
Before each load, loi compares the current path, size, and modification time with the record. If all three match, loi uses the recorded SHA-256. If one is different, loi hashes the file again.

Warning: a file with different bytes but the same path, size, and modification time passes the cached check. The owner accepted this risk on 2026-11-03 to make cold loads faster.

`FileModelPreparer` in `core/verify` implements this check. It takes a storage root and uses two folders under it:

- `verify/`: one hash record for each canonical path. The record file name is a hash of the path, so it does not contain the path.
- `cache/<key>/`: the engine cache for one model file. The key is the SHA-256. At level `OFF`, the key is `off-<size>-<modified>`.

`loi-android` uses `<noBackupFilesDir>/loi` as the storage root.

More rules:

- At level `STRICT`, loi compares the size first. A file with no known size fails at once, with no hash.
- If the size or modification time changes during the hash, the load fails with `ModelFile(CHANGED)`. loi does not write a record for that hash.
- A damaged, unreadable, or unwritable record is a cache miss. It never fails a load.
- The check uses `java.io.File` only, because `java.nio.file` needs Android API 26.

## 9. Errors and privacy

All errors extend `LoiException`, a sealed class:

| Error | Cause |
|---|---|
| `ModelFile` | The file is missing, is not a file, cannot be read, or changed during the check. |
| `Verification` | The size or hash does not match at level `STRICT`. |
| `Load` | The engine failed to initialize. |
| `Input` | The text is blank, is longer than the character limit, has more tokens than the token limit, contains NUL, or has an unpaired surrogate. |
| `Output` | The vector has the wrong size, a non-finite value, or the wrong L2 norm. |
| `Closed` | The embedder is closed. |
| `Resource` | The engine failed to close. The embedder is poisoned. |

Cancellation uses the standard `CancellationException`, not a `LoiException`.
`LoiException` turns off coroutine stack-trace recovery with `CopyableThrowable`. Without this, the caller can get a copy whose `cause` is the original `LoiException`, not the engine failure.

Error messages are fixed text. They do not contain input text, output values, or file paths.
loi attaches the SDK exception as `cause`, so the app can choose to log it. SDK messages can contain input text or paths.
loi sets the LiteRT-LM native log level to `INFINITY` by default, as forma does. `Options.nativeLogs = true` turns native logs back on for diagnosis.

## 10. EmbeddingGemma 2 spec

| Property | Value |
|---|---|
| Model ID | `embeddinggemma-2-text-270m` |
| Query prefix | `task: search query \| text: ` |
| Document prefix | `task: search result \| text: ` |
| Output dimensions | 128, 256, 512, or 768 (MRL truncation by the SDK) |
| Default dimensions | 256 |
| Input tokens | 128, 256, 512, 1024, 2048, or 8192 |
| Default input tokens | 2048 |
| Normalization | L2, by the SDK. loi checks that the norm is 1 ± 0.0001. |
| Backend | CPU |
| Activation precision | float32 |
| Special tokens | inserted by the SDK |

Source: forma `docs/model-policy.md` and `LiteRtInferenceConfig.kt`. The model card supplies the allowed sizes.
Warning: vectors with different dimensions, prefixes, or artifacts are not compatible. `Embedding.cosine` fails if the two `modelId` or `dimensions` values are different.

The model ID is `embeddinggemma-2-text-270m-mrl<dimensions>`, for example `embeddinggemma-2-text-270m-mrl256`.
It does not include the artifact. At level `STRICT`, only the known artifact loads, so the ID is exact.
At level `WARN` or `OFF`, a different file with the same output size gives the same ID. The caller must use `ModelInfo.verification` to keep such vectors apart.

The caller text limit is 16,384 characters, from the forma limits. The prefix does not count.
The SDK does not cut a text with more tokens than `maxInputTokens`. The SDK call fails, and loi throws `LoiException.Input(TOO_MANY_TOKENS)`.
A text in the 16,384-character limit can have more than 2,048 tokens, so loi finds this error only in the engine call. The engine stays loaded.

LiteRT-LM 0.18.0 has no error code. It throws `LiteRtLmJniException` with the message `ComputeEmbedding failed: INVALID_ARGUMENT: `.
`runtime-litertlm` changes this message to the port exception `TooManyTokensException`. `SerialEmbedder` changes that exception to `Input(TOO_MANY_TOKENS)`.
Issue #17 checked on a Nothing Phone (2a) that only a token overflow gives `INVALID_ARGUMENT`. Control characters, emoji, RTL text, BOM, private-use, and noncharacter text did not.
Warning: the match depends on the SDK message. After an SDK update, run the device test `inputLongerThanTheTokenLimit` again.

## 11. Extraction map

| forma source | loi target | Change |
|---|---|---|
| `InferenceCoordinator.kt` | `core/runtime/SerialEmbedder` | Keep the serial worker and the cancel hook. Remove owner ID, sequence, process lease, and restart block. Use coroutines in place of `CompletableFuture`. |
| `InferenceModels.kt` `SerialModelRuntime` | `core/runtime/SerialEmbedder` | Keep one handle and the poisoned state. Remove the model-kind switch. |
| `InferenceModels.kt` `validInferenceText` | `core/validate` | Keep as it is. |
| `InferenceModels.kt` `InferenceRequest` prefixes | `models/embeddinggemma2` | loi adds the prefix. It does not check for one. |
| `LiteRtInferenceBackend.kt` `LiteRtEmbeddingModel` | `runtime-litertlm` | Keep. Read size and options from `EngineOptions`. Remove the generation model and the 3 GiB memory guard. |
| `LiteRtInferenceConfig.kt` | `runtime-litertlm` | Keep the embedding part. Take values from `EngineOptions`. |
| `ModelArtifactStore.kt` `status`, `verifiedFile` | `core/verify` | Keep the chunked SHA-256. Add the three levels and the hash cache. Remove the fixed `forma-models-v1` directory. |
| `ModelArtifactStore.kt` `ApprovedModels` | `models/embeddinggemma2` | Becomes the known-artifact list. |
| `ModelArtifactStore.kt` `import` | Not in version 1 | A possible later `model-files` module for content `Uri` import. |
| `InferenceFailureCodes.kt` | `core/errors` | String codes become the sealed `LoiException`. |
| `packages/core/src/inference/validation.ts` `vector` | `core/validate` | Port the finite, size, and norm checks to Kotlin. |
| `FormaInferenceModule.kt`, `InferenceJson.kt`, `NativeFormaInference.ts`, `packages/adapters/src/android/inference.ts` | Not moved | React Native bridge code. A later RN adapter can use it as a reference. |
| `InferenceAcceptance.kt`, `InferenceObservations.kt`, `HostGateLaunchOptions.kt` | Not moved | forma acceptance harness. |
| `packages/core/src/inference/service.ts` | Not moved | forma note logic. |

Tests to port: `InferenceCoordinatorTest`, `InferenceModelsTest`, `ModelArtifactStoreTest`, `LiteRtInferenceConfigTest`, `InferenceFailureCodesTest`.

## 12. Tests and acceptance

- `core` and `models/*` tests run on the JVM with the fake engine from `testing`. They prove the worker, cancellation, release, close, poisoned state, verification levels, and validation.
- `runtime-litertlm` tests inspect the real SDK configuration objects without a native load, as forma `LiteRtInferenceConfigTest` does.
- A device test in `sample` loads the real file on the Nothing Phone (2a). It embeds a query and a document and checks the size and the norm.
- Device budgets from the forma model policy are targets, not gates: cold load 2 s or less, one query 300 ms or less.

## 13. Build plan

Each step ends with a green build and green tests.

1. Gradle skeleton with all modules, the toolchain, and the two import checks.
2. `core`: API types, errors, and validation, with tests.
3. `core`: `SerialEmbedder` with the fake engine, with tests.
4. `core`: file verification and the three levels, with tests.
5. `models/embeddinggemma2`: spec and known artifacts, with tests.
6. `runtime-litertlm`: LiteRT-LM engine and configuration tests.
7. `loi-android`: the `Loi` factory and the cache directory.
8. `sample` and the device test on the phone.

## 14. Open questions

1. Does `embedAll` need a progress callback for long lists?
2. Do we want an optional event listener for timings (load, verify, SDK call), as forma `InferenceObservations` did?

Closed on 2026-11-03:

- The hash result is cached by path, size, and modification time. See section 8.
- A text with too many tokens fails with `Input(TOO_MANY_TOKENS)`. loi reads the SDK error message. See section 10 and issue #17.
- Version 1 has no content `Uri` import module. The caller supplies a `File`, and the sample app copies the picked file. The forma `ModelArtifactStore.import` code stays as the reference for a later `model-files` module.
