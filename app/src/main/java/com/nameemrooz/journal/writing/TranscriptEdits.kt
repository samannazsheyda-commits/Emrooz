package com.nameemrooz.journal.writing

/** Session/revision leases keep slow neural results from replacing newer text. */
class TranscriptEdits {
    data class Ticket(val session: Long, val revision: Long, val raw: String)
    private var session = 0L
    private var revision = 0L
    private var appliedRevision = -1L
    private var current = ""
    private var stableRaw = ""
    private var stableDisplay = ""

    @Synchronized fun reset() {
        session++
        revision = 0
        appliedRevision = -1
        current = ""
        stableRaw = ""
        stableDisplay = ""
    }

    @Synchronized fun partial(text: String): String {
        current = text
        return display()
    }

    @Synchronized fun ticket(text: String): Ticket {
        current = text
        return Ticket(session, ++revision, text)
    }

    @Synchronized fun apply(ticket: Ticket, edited: String): String? {
        if (ticket.session != session || ticket.revision < appliedRevision) return null
        appliedRevision = ticket.revision
        stableRaw = ticket.raw
        stableDisplay = edited
        return display()
    }

    @Synchronized fun display(): String =
        StableDisplayComposer.compose(stableRaw, stableDisplay, current)
}
