package com.nameemrooz.journal.speech

import java.util.concurrent.atomic.AtomicBoolean

class SpeechSessionGate {
    private val active = AtomicBoolean(false)

    fun tryBegin(): Boolean = active.compareAndSet(false, true)

    fun finish() {
        active.set(false)
    }

    fun isActive(): Boolean = active.get()
}
