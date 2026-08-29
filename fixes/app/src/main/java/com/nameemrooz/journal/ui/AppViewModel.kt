package com.nameemrooz.journal.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nameemrooz.journal.data.AppDatabase
import com.nameemrooz.journal.data.JournalRepository
import com.nameemrooz.journal.model.JournalEntry
import com.nameemrooz.journal.util.TitleGenerator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val repo = JournalRepository(db.journalDao())
    val active = repo.active.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val archived = repo.archived.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(text: String, title: String = "") = viewModelScope.launch {
        // The final ASR cleanup happens before editing. Preserve the user's manual edits exactly.
        val edited = text.trim()
        if (edited.isNotBlank()) {
            repo.save(edited, TitleGenerator.resolve(title, edited))
        }
    }

    fun update(e: JournalEntry) = viewModelScope.launch {
        val edited = e.text.trim()
        if (edited.isNotBlank()) {
            repo.update(e.copy(text = edited, title = TitleGenerator.resolve(e.title, edited)))
        }
    }

    fun delete(e: JournalEntry) = viewModelScope.launch { repo.delete(e) }
}