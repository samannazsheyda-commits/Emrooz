package com.nameemrooz.journal.data

import com.nameemrooz.journal.model.JournalEntry

class JournalRepository(private val dao: JournalDao) {
    val active = dao.active()
    val archived = dao.archived()
    suspend fun save(text: String, title: String) = dao.insert(JournalEntry(title = title, text = text))
    suspend fun update(e: JournalEntry) = dao.update(e.copy(updatedAt = System.currentTimeMillis()))
    suspend fun delete(e: JournalEntry) = dao.delete(e)
}
