package dev.loi.sample

import android.app.Activity
import android.content.ComponentCallbacks2
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.loi.android.Loi
import dev.loi.core.api.Embedder
import dev.loi.core.errors.LoiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A minimal loi user. It picks a model file, makes an embedder, and shows one similarity score.
 *
 * Start with `--ez loi.autorun true` to run once and write `LOI_SAMPLE_RESULT <json>` to logcat.
 */
class MainActivity : Activity() {
    private val scope = MainScope()
    private var embedder: Embedder? = null
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { setPadding(32, 32, 32, 32) }
        val pick = Button(this).apply { text = "Pick model file"; setOnClickListener { pickModel() } }
        val run = Button(this).apply { text = "Run"; setOnClickListener { run(autorun = false) } }
        val release = Button(this).apply { text = "Release"; setOnClickListener { scope.launch { embedder?.release(); show("Released.") } } }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(pick); addView(run); addView(release); addView(status)
        })
        show(if (modelFile(this).isFile) "Model file found. Press Run." else "Pick the model file first.")
        if (intent.getBooleanExtra(AUTORUN, false)) run(autorun = true)
    }

    private fun pickModel() {
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), PICK)
    }

    @Deprecated("Activity result API of the platform Activity.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != PICK || resultCode != RESULT_OK || uri == null) return
        scope.launch {
            show("Copying the model file...")
            try {
                withContext(Dispatchers.IO) { copy(uri, modelFile(this@MainActivity)) }
                show("Model file copied. Press Run.")
            } catch (error: Exception) {
                show("Copy failed: ${error::class.java.simpleName}")
            }
        }
    }

    private fun copy(uri: Uri, target: File) {
        val temporary = File(target.parentFile, target.name + ".part")
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "The picked file cannot be opened." }
            temporary.outputStream().use { output -> input.copyTo(output, 65536) }
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            error("The model file cannot be moved into place.")
        }
    }

    private fun run(autorun: Boolean) {
        scope.launch {
            show("Loading...")
            try {
                val current = embedder ?: Loi.embeddingGemma2(this@MainActivity, modelFile(this@MainActivity)).also { embedder = it }
                val result = runSample(current)
                show(
                    "${result.verification}\nload ${result.loadMs} ms, query ${result.queryMs} ms, document ${result.documentMs} ms\n" +
                        "related ${"%.3f".format(result.related)}, unrelated ${"%.3f".format(result.unrelated)}",
                )
                if (autorun) Log.i(TAG, "LOI_SAMPLE_RESULT " + result.toJson())
            } catch (error: CancellationException) {
                throw error
            } catch (error: LoiException) {
                show("${error::class.java.simpleName}: ${error.message}")
                if (autorun) Log.i(TAG, """LOI_SAMPLE_RESULT {"ok":false,"error":"${error::class.java.simpleName}"}""")
            }
        }
    }

    private fun show(text: String) {
        status.text = text
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW && level != ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            scope.launch { embedder?.release() }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        // close() waits for the active engine call, so it must not block the main thread.
        embedder?.let { current -> Thread { runCatching { current.close() } }.start() }
        embedder = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "LoiSample"
        const val AUTORUN = "loi.autorun"
        const val PICK = 1
    }
}
