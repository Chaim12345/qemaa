package com.example.engine

sealed class TerminalLine {
    abstract val text: String

    data class Output(override val text: String) : TerminalLine()
    data class Error(override val text: String) : TerminalLine()
    data class Command(override val text: String) : TerminalLine()
    data class System(override val text: String) : TerminalLine()
}
