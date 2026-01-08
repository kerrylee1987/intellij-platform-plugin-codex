package com.codex.intellij

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

@Service(Service.Level.PROJECT)
class CodexToolWindowService(private val project: Project) {
    private var panel: CodexToolWindowPanel? = null
    private var pendingContext: CodexContext? = null

    fun attach(panel: CodexToolWindowPanel) {
        this.panel = panel
        pendingContext?.let {
            panel.setContext(it)
            pendingContext = null
        }
    }

    fun showToolWindow() {
        ToolWindowManager.getInstance(project).getToolWindow(CodexToolWindowPanel.TOOL_WINDOW_ID)?.show()
    }

    fun useContext(context: CodexContext) {
        val current = panel
        if (current == null) {
            pendingContext = context
        } else {
            current.setContext(context)
        }
    }
}
