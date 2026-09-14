package com.nameemrooz.journal.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nameemrooz.journal.data.*
import com.nameemrooz.journal.model.JournalEntry
import com.nameemrooz.journal.util.PersianText
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val repo = JournalRepository(db.journalDao())
    val active = repo.active.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val archived = repo.archived.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(text: String) = viewModelScope.launch {
        val clean = PersianText.clean(text, final = true)
        if (clean.isNotBlank()) repo.save(clean, "")
    }
    fun update(e: JournalEntry) = viewModelScope.launch {
        val clean = PersianText.clean(e.text, final = true)
        if (clean.isNotBlank()) repo.update(e.copy(text = clean))
    }
    fun delete(e: JournalEntry) = viewModelScope.launch { repo.delete(e) }
}
