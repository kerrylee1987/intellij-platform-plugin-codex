package com.codex.intellij

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class CodexSendSelectionAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val editor = event.getData(CommonDataKeys.EDITOR)
        val selection = editor?.selectionModel?.selectedText
        if (selection.isNullOrBlank()) {
            CodexNotifications.notifyWarning(project, "No selection found in the editor.")
            return
        }
        val service = project.getService(CodexToolWindowService::class.java)
        service.useContext(CodexContext("selection", selection))
        service.showToolWindow()
    }
}
