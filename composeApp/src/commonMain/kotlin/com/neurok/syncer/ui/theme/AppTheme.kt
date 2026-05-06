package com.neurok.syncer.ui.theme

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App-wide theme state singleton. Avoids ViewModel scoping issues when the theme
 * needs to be read at the root (MainActivity) and written from a nested screen (Settings).
 */
object AppTheme {
    private val _mode = MutableStateFlow("dark")
    val mode: StateFlow<String> = _mode

    fun set(mode: String) {
        _mode.value = mode
    }
}
