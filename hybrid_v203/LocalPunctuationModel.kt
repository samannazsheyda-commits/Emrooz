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

    private fun infer(text: String): String {
        val rt = ensureRuntime()
        val plainIds = rt.tokenizer.encodeNormalized(text, rt.algorithm).take(MAX_CONTENT_TOKENS)
        if (plainIds.isEmpty()) return text

        val bos = rt.tokenizer.getIdForToken("<s>")
        val eos = rt.tokenizer.getIdForToken("</s>")
        if (bos < 0 || eos < 0) return text

        val ids = LongArray(plainIds.size + 2)
        ids[0] = bos.toLong()
        plainIds.forEachIndexed { index, id -> ids[index + 1] = id.toLong() }
        ids[ids.lastIndex] = eos.toLong()

        OnnxTensor.createTensor(rt.env, arrayOf(ids)).use { input ->
            rt.session.run(mapOf(INPUT_NAME to input)).use { result ->
                if (result.size() < 2) return text
                val postPreds = numericRow(result[1].value)
                if (postPreds.size < plainIds.size + 2) return text
                val rendered = render(rt.tokenizer, plainIds, postPreds)
                return if (rendered.isBlank()) text else rendered
            }
        }
    }

    private fun render(tokenizer: Model, ids: List<Int>, postPreds: IntArray): String {
        val out = StringBuilder()
        ids.forEachIndexed { index, id ->
            val piece = tokenizer.tokenById(id)
            if (piece == "<unk>" || piece == "<s>" || piece == "</s>") return@forEachIndexed
            if (piece.startsWith("▁")) {
                if (out.isNotEmpty()) out.append(' ')
                out.append(piece.substring(1))
            } else {
                out.append(piece)
            }
            val prediction = postPreds[index + 1]
            POST_LABELS.getOrNull(prediction)?.takeIf { it.isNotEmpty() }?.let(out::append)
        }
        return out.toString().trim()
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
        val modelFile = copyAssetToCache(MODEL_ASSET, "punctuation47.onnx")
        val tokenizerFile = copyAssetToCache(TOKENIZER_ASSET, "punctuation47.model")
        val env = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(1)
            setInterOpNumThreads(1)
        }
        val session = env.createSession(modelFile.absolutePath, options)
        val tokenizer = Model.parseFrom(tokenizerFile.toPath())
        val algorithm = SentencePieceAlgorithm(true, Scoring.HIGHEST_SCORE)
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
            runtime?.let {
                runCatching { it.session.close() }
                runCatching { it.options.close() }
            }
            runtime = null
        }
    }

    companion object {
        private const val MAX_CONTENT_TOKENS = 126
        private const val INPUT_NAME = "input_ids"
        private const val MODEL_ASSET = "models/punctuation47/punct_cap_seg_47lang.onnx"
        private const val TOKENIZER_ASSET = "models/punctuation47/spe_unigram_64k_lowercase_47lang.model"
        private val POST_LABELS = listOf(
            "", ".", ",", "?", "？", "，", "。", "、", "・", "।", "؟", "،", ";", "።", "፣", "፧"
        )
    }
}
