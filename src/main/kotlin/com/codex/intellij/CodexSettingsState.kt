package com.codex.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "CodexSettings", storages = [Storage("codex.xml")])
@Service(Service.Level.APP)
class CodexSettingsState : PersistentStateComponent<CodexSettingsState.State> {
    data class State(
        var cliPath: String = "",
        var timeoutSeconds: Int = 120,
        var maxContextChars: Int = 8000,
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): CodexSettingsState =
            ApplicationManager.getApplication().getService(CodexSettingsState::class.java)
    }
}
