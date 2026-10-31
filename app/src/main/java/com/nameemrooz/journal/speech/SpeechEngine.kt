package com.nameemrooz.journal.speech

interface SpeechEngine : AutoCloseable {
    fun prepare()
    fun start()
    fun stop()
    override fun close()
}
