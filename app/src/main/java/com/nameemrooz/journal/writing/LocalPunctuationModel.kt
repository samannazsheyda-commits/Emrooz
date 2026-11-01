package com.nameemrooz.journal.writing

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.sentencepiece.Model
import com.sentencepiece.Scoring
import com.sentencepiece.SentencePieceAlgorithm
import java.io.File

/**
 * Fully local punctuation restoration. The model is allowed to add punctuation only:
 * output is rejected if lexical tokens differ from the input.
 */
class LocalPunctuationModel(
    private val context: Context,
) : PunctuationRestorer, AutoCloseable {
    private data class Runtime(
        val env: OrtEnvironment,
        val session: OrtSession,
        val options: OrtSession.SessionOptions,
        val tokenizer: Model,
        val algorithm: SentencePieceAlgorithm,
    )

    @Volatile private var runtime: Runtime? = null
    @Volatile private var permanentlyUnavailable = false

    fun prepare(): Boolean = runCatching {
        ensureRuntime()
        true
    }.getOrElse {
        permanentlyUnavailable = true
        false
    }

    override suspend fun restore(text: String): String {
        if (text.isBlank() || permanentlyUnavailable) return text
        val candidate = runCatching { infer(text) }.getOrElse { return text }
        return if (LexicalSafety.isPunctuationOnlyChange(text, candidate)) candidate else text
    }

    @Synchronized
    private fun infer(text: String): String {
        val rt = ensureRuntime()
        val chunks = SourcePunctuationRenderer.plan(text) {
            rt.tokenizer.encodeNormalized(it, rt.algorithm)
        }
        if (chunks.isEmpty()) return text
        val bos = rt.tokenizer.getIdForToken("<s>")
        val eos = rt.tokenizer.getIdForToken("</s>")
        if (bos < 0 || eos < 0) return text
        val insertions = mutableListOf<SourcePunctuationRenderer.Insertion>()
        for (words in chunks) {
            val plainIds = words.flatMap { it.ids }
            val ids = LongArray(plainIds.size + 2)
            ids[0] = bos.toLong()
            plainIds.forEachIndexed { index, id -> ids[index + 1] = id.toLong() }
            ids[ids.lastIndex] = eos.toLong()
            OnnxTensor.createTensor(rt.env, arrayOf(ids)).use { input ->
                rt.session.run(mapOf(INPUT_NAME to input)).use { result ->
                    val predictions = result.get(POST_OUTPUT_NAME).orElse(null) ?: return text
                    insertions += SourcePunctuationRenderer.insertions(words, numericRow(predictions.value))
                }
            }
        }
        return SourcePunctuationRenderer.render(text, insertions)
    }

    private fun numericRow(value: Any?): IntArray {
        val row: Any? = when (value) {
            is Array<*> -> value.firstOrNull()
            else -> value
        }
        return when (row) {
            is LongArray -> IntArray(row.size) { row[it].toInt() }
            is IntArray -> row
            is ShortArray -> IntArray(row.size) { row[it].toInt() }
            is Array<*> -> IntArray(row.size) { index -> (row[index] as? Number)?.toInt() ?: 0 }
            else -> IntArray(0)
        }
    }

    @Synchronized
    private fun ensureRuntime(): Runtime {
        runtime?.let { return it }
        check(!permanentlyUnavailable) { "Punctuation model unavailable" }
        // Only these obsolete model-cache files are ours; never clear the app cache
        // recursively, since other components can keep user work there.
        val cache = File(context.cacheDir, "emrooz-punctuation")
        listOf("punctuation47.onnx", "punctuation47.onnx.part").forEach { name ->
            File(cache, name).takeIf { it.isFile }?.delete()
        }
        val modelFile = copyAssetToCache(MODEL_ASSET, "punctuation47-int8-v1.onnx")
        val tokenizerFile = copyAssetToCache(TOKENIZER_ASSET, "punctuation47.model")
        val tokenizer = Model.parseFrom(tokenizerFile.toPath())
        val algorithm = SentencePieceAlgorithm(true, Scoring.HIGHEST_SCORE)
        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(1)
            setInterOpNumThreads(1)
        }
        val session = try {
            env.createSession(modelFile.absolutePath, options)
        } catch (error: Throwable) {
            options.close()
            throw error
        }
        return Runtime(env, session, options, tokenizer, algorithm).also { runtime = it }
    }

    private fun copyAssetToCache(assetName: String, fileName: String): File {
        val dir = File(context.cacheDir, "emrooz-punctuation").apply { mkdirs() }
        val out = File(dir, fileName)
        if (out.exists() && out.length() > 1024L) return out
        val temp = File(dir, "$fileName.part")
        context.assets.open(assetName).use { input ->
            temp.outputStream().buffered(1024 * 1024).use { output -> input.copyTo(output, 1024 * 1024) }
        }
        if (!temp.renameTo(out)) {
            temp.copyTo(out, overwrite = true)
            temp.delete()
        }
        return out
    }

    override fun close() {
        synchronized(this) {
            permanentlyUnavailable = true
            runtime?.let {
                runCatching { it.session.close() }
                runCatching { it.options.close() }
            }
            runtime = null
        }
    }

    companion object {
        private const val INPUT_NAME = "input_ids"
        private const val POST_OUTPUT_NAME = "post_preds"
        private const val MODEL_ASSET = "models/punctuation47/punctuation-int8.onnx"
        private const val TOKENIZER_ASSET = "models/punctuation47/spe_unigram_64k_lowercase_47lang.model"
    }
}
