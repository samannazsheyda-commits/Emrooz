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
import com.nameemrooz.journal.writing.DraftController

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    private val repo = JournalRepository(db.journalDao())
    private val draftPreferences = app.getSharedPreferences("emrooz_draft", android.content.Context.MODE_PRIVATE)
    val draft = DraftController(draftPreferences.getString("text", "").orEmpty()) {
        draftPreferences.edit().putString("text", it).apply()
    }
    suspend fun saveDraft(): Boolean = draft.save { repo.save(it.trim(), "") }

    val active = repo.active.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val archived = repo.archived.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun update(e: JournalEntry): Boolean = try {
        if (e.text.isBlank()) false else { repo.update(e.copy(text = e.text.trim())); true }
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (_: Exception) { false }

    suspend fun delete(e: JournalEntry): Boolean = try { repo.delete(e); true }
    catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (_: Exception) { false }
}
