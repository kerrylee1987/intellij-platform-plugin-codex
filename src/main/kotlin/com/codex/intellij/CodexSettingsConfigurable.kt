package com.codex.intellij

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class CodexSettingsConfigurable : SearchableConfigurable {
    private var component: JComponent? = null

    override fun getId(): String = "com.codex.intellij.settings"

    override fun getDisplayName(): String = "Codex"

    override fun createComponent(): JComponent {
        val settings = CodexSettingsState.getInstance().state
        val panel = panel {
            row("Codex CLI path:") {
                textField()
                    .bindText(settings::cliPath)
                    .comment("Leave blank to use codex from PATH.")
            }
            row("Timeout (seconds):") {
                intTextField(10..600)
                    .bindIntText(settings::timeoutSeconds)
                    .comment("Default 120 seconds.")
            }
            row("Max context size (chars):") {
                intTextField(512..50000)
                    .bindIntText(settings::maxContextChars)
                    .comment("Trims selection or file context before sending.")
            }
        }
        component = panel
        return panel
    }

    override fun isModified(): Boolean {
        // The DSL panel handles isModified automatically if we rely on it, 
        // but SearchableConfigurable requires us to implement it if we don't use BoundConfigurable.
        // However, with 'bindText' and 'bindIntText', the panel can check for modifications.
        // But since we are storing the panel in 'component', we can't easily access the inner cells 
        // without keeping references.
        // For simplicity in this DSL version, the best practice is to extend BoundConfigurable.
        // But to keep it compatible with the current interface SearchableConfigurable:
        // We can just return true (always apply) or implement properly.
        // Let's implement properly by creating the panel and letting it handle apply/reset.
        // Wait, 'apply' is called on the configurable.
        // If we use `bindText(settings::cliPath)`, the `panel.apply()` needs to be called.
        // SearchableConfigurable doesn't automatically call panel.apply().
        // We should use `BoundConfigurable` if possible, or manually handle it.
        // Given the constraints, I will switch to BoundConfigurable pattern if I can change the superclass.
        // But let's stick to SearchableConfigurable and use the panel's apply/isModified if available.
        // In recent Platform versions, `panel.isModified()` and `panel.apply()` exist.
        val p = component as? com.intellij.openapi.ui.DialogPanel
        return p?.isModified() ?: false
    }

    override fun apply() {
        val p = component as? com.intellij.openapi.ui.DialogPanel
        p?.apply()
    }

    override fun reset() {
        val p = component as? com.intellij.openapi.ui.DialogPanel
        p?.reset()
    }
}
