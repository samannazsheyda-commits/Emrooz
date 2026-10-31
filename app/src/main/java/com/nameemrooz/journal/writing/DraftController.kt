package com.nameemrooz.journal.writing

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns the draft across screens; only a successful insert may clear its saved revision. */
class DraftController(initial: String, private val persist: (String) -> Unit) {
    private val mutableText = MutableStateFlow(initial)
    val text = mutableText.asStateFlow()
    private var generation = 0L
    private var prefix = ""
    private var revision = 0L

    fun beginRecording(): Long {
        generation++
        prefix = mutableText.value.trimEnd()
        return generation
    }

    fun acceptSpeech(token: Long, transcript: String) {
        if (token != generation || transcript.isBlank()) return
        update(if (prefix.isBlank()) transcript else "$prefix ${transcript.trimStart()}")
    }

    fun edit(text: String) {
        generation++
        update(text)
    }

    private fun update(text: String) {
        revision++
        mutableText.value = text
        persist(text)
    }

    suspend fun save(insert: suspend (String) -> Unit): Boolean {
        val snapshot = mutableText.value
        val savedRevision = revision
        if (snapshot.isBlank()) return false
        return try {
            insert(snapshot)
            if (revision == savedRevision) edit("")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
}
