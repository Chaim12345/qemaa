package com.example.ui

/**
 * State for the in-app nano-style editor.
 */
data class NanoEditorState(
    val isOpen: Boolean = false,
    val filePath: String = "",
    val content: String = "",
    val isModified: Boolean = false
)
