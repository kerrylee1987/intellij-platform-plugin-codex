package com.codex.intellij

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class CodexSettingsConfigurable : SearchableConfigurable {
    private var cliPathField: JBTextField? = null
    private var timeoutField: JBTextField? = null
    private var maxContextField: JBTextField? = null
    private var component: JComponent? = null

    override fun getId(): String = "com.codex.intellij.settings"

    override fun getDisplayName(): String = "Codex"

    override fun createComponent(): JComponent {
        val settings = CodexSettingsState.getInstance().state
        val panel = panel {
            row("Codex CLI path") {
                cliPathField = textField()
                    .bindText(settings::cliPath)
                    .comment("Leave blank to use codex from PATH.")
                    .component
            }
            row("Timeout (seconds)") {
                timeoutField = textField()
                    .bindIntText(settings::timeoutSeconds, min = 10, max = 600)
                    .comment("Default 120 seconds.")
                    .component
            }
            row("Max context size (chars)") {
                maxContextField = textField()
                    .bindIntText(settings::maxContextChars, min = 512, max = 50000)
                    .comment("Trims selection or file context before sending.")
                    .component
            }
        }
        component = panel
        return panel
    }

    override fun isModified(): Boolean {
        val settings = CodexSettingsState.getInstance().state
        return cliPathField?.text != settings.cliPath ||
            timeoutField?.text?.toIntOrNull() != settings.timeoutSeconds ||
            maxContextField?.text?.toIntOrNull() != settings.maxContextChars
    }

    override fun apply() {
        val settings = CodexSettingsState.getInstance().state
        settings.cliPath = cliPathField?.text?.trim().orEmpty()
        settings.timeoutSeconds = timeoutField?.text?.toIntOrNull() ?: settings.timeoutSeconds
        settings.maxContextChars = maxContextField?.text?.toIntOrNull() ?: settings.maxContextChars
    }

    override fun reset() {
        val settings = CodexSettingsState.getInstance().state
        cliPathField?.text = settings.cliPath
        timeoutField?.text = settings.timeoutSeconds.toString()
        maxContextField?.text = settings.maxContextChars.toString()
    }
}
