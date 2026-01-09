package com.codex.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

data class CodexContext(
    val source: String,
    val text: String,
)

class CodexToolWindowPanel(private val project: Project) {
    private val logger = Logger.getInstance(CodexToolWindowPanel::class.java)
    private val cliService = project.getService(CodexCliService::class.java)

    private val statusLabel = JBLabel("Status: Idle").apply {
        horizontalAlignment = SwingConstants.LEFT
    }
    private val contextLabel = JBLabel("Context: none")
    private val messageListPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = JBUI.Borders.empty(8)
    }
    private val messageScrollPane = ScrollPaneFactory.createScrollPane(messageListPanel, true) as JBScrollPane
    private val inputArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        minimumSize = Dimension(200, 80)
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(event: KeyEvent) {
                if (event.isControlDown && event.keyCode == KeyEvent.VK_ENTER) {
                    sendMessage()
                }
            }
        })
    }
    private val sendButton = JButton("Send")
    private val cancelButton = JButton("Cancel").apply { isEnabled = false }
    private val useSelectionButton = JButton("Use Selection")
    private val useFileButton = JButton("Use File")
    private val clearContextButton = JButton("Clear Context")

    private var currentExecution: CodexCliExecution? = null
    private var context: CodexContext? = null
    private var streamingPanel: MessagePanel? = null

    val component: JComponent = JPanel(BorderLayout()).apply {
        add(buildHeader(), BorderLayout.NORTH)
        add(messageScrollPane, BorderLayout.CENTER)
        add(buildInputArea(), BorderLayout.SOUTH)
    }

    init {
        sendButton.addActionListener { sendMessage() }
        cancelButton.addActionListener { cancelExecution() }
        useSelectionButton.addActionListener { useEditorSelection() }
        useFileButton.addActionListener { useEditorFile() }
        clearContextButton.addActionListener { clearContext() }
    }

    fun setContext(context: CodexContext) {
        this.context = context
        updateContextLabel(context)
    }

    private fun buildHeader(): JComponent {
        val header = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.compound(
                JBUI.Borders.empty(8, 8, 4, 8),
                JBUI.Borders.customLineBottom(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()),
            )
        }
        val statusPanel = JPanel(HorizontalLayout(8)).apply {
            add(statusLabel)
            add(Box.createHorizontalStrut(8))
            add(contextLabel)
        }
        val buttonPanel = JPanel(HorizontalLayout(4)).apply {
            add(useSelectionButton)
            add(useFileButton)
            add(clearContextButton)
        }
        header.add(statusPanel, BorderLayout.WEST)
        header.add(buttonPanel, BorderLayout.EAST)
        return header
    }

    private fun buildInputArea(): JComponent {
        val inputPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
        }
        val actionsPanel = JPanel(HorizontalLayout(8)).apply {
            add(sendButton)
            add(cancelButton)
        }
        inputPanel.add(ScrollPaneFactory.createScrollPane(inputArea, true), BorderLayout.CENTER)
        inputPanel.add(actionsPanel, BorderLayout.EAST)
        return inputPanel
    }

    private fun updateContextLabel(context: CodexContext?) {
        if (context == null) {
            contextLabel.text = "Context: none"
        } else {
            contextLabel.text = "Context: ${context.source} (${context.text.length} chars)"
        }
    }

    private fun useEditorSelection() {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val selection = editor?.selectionModel?.selectedText
        if (selection.isNullOrBlank()) {
            CodexNotifications.notifyWarning(project, "No selection found in the current editor.")
            return
        }
        setContext(CodexContext("selection", selection))
    }

    private fun useEditorFile() {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor
        val text = editor?.document?.text
        if (text.isNullOrBlank()) {
            CodexNotifications.notifyWarning(project, "No active editor file to use as context.")
            return
        }
        setContext(CodexContext("file", text))
    }

    private fun clearContext() {
        context = null
        updateContextLabel(null)
    }

    private fun sendMessage() {
        val userText = inputArea.text.trim()
        if (userText.isEmpty()) {
            CodexNotifications.notifyWarning(project, "Enter a message to send.")
            return
        }
        if (currentExecution != null) {
            CodexNotifications.notifyWarning(project, "A request is already running.")
            return
        }

        appendMessage("User", userText)
        streamingPanel = appendMessage("Codex", "")
        inputArea.text = ""
        setStatus("Running")
        sendButton.isEnabled = false
        cancelButton.isEnabled = true

        val prompt = buildPrompt(userText)
        currentExecution = cliService.execute(prompt, { chunk ->
            ApplicationManager.getApplication().invokeLater {
                streamingPanel?.updateContent(chunk)
            }
        }) { result ->
            ApplicationManager.getApplication().invokeLater {
                handleCliResult(result)
            }
        }
    }

    private fun cancelExecution() {
        currentExecution?.cancel()
        currentExecution = null
        setStatus("Cancelled")
        sendButton.isEnabled = true
        cancelButton.isEnabled = false
    }

    private fun handleCliResult(result: CodexCliResult) {
        currentExecution = null
        streamingPanel = null
        sendButton.isEnabled = true
        cancelButton.isEnabled = false

        if (result.failureReason != null) {
            appendMessage("Codex", result.failureReason.userMessage)
            CodexNotifications.notifyError(project, result.failureReason.userMessage)
            setStatus("Failed")
            return
        }

        if (result.exitCode != 0) {
            val summary = buildString {
                append("Codex CLI failed (exit ${result.exitCode}).")
                if (result.errorOutput.isNotBlank()) {
                    append("\n\n")
                    append(result.errorOutput.trim())
                }
            }
            appendMessage("Codex", summary)
            setStatus("Failed")
            return
        }

        if (result.output.isBlank()) {
            appendMessage("Codex", "Codex returned no output.")
            setStatus("Failed")
            return
        }

        appendMessage("Codex", result.output)
        setStatus("Done")
    }

    private fun buildPrompt(userText: String): String {
        val settings = CodexSettingsState.getInstance().state
        val contextText = context?.text
        if (contextText.isNullOrBlank()) {
            return userText
        }
        val limitedContext = StringUtil.trimLog(contextText, settings.maxContextChars)
        val trimmed = limitedContext != contextText
        if (trimmed) {
            CodexNotifications.notifyWarning(
                project,
                "Context truncated to ${settings.maxContextChars} characters.",
            )
        }
        return buildString {
            append("Context (${context?.source}):\n")
            append(limitedContext)
            append("\n\nUser:\n")
            append(userText)
        }
    }

    private fun appendMessage(
        @NlsContexts.Label author: String,
        content: String,
    ): MessagePanel {
        val panel = MessagePanel(project, author, content)
        messageListPanel.add(panel)
        messageListPanel.add(Box.createVerticalStrut(8))
        messageListPanel.revalidate()
        messageListPanel.repaint()
        messageScrollPane.verticalScrollBar.value = messageScrollPane.verticalScrollBar.maximum
        return panel
    }

    private fun setStatus(text: String) {
        statusLabel.text = "Status: $text"
    }

    private class MessagePanel(
        private val project: Project,
        val author: String,
        content: String,
    ) : JPanel(BorderLayout()) {
        private val contentArea = JBTextArea(content).apply {
            lineWrap = true
            wrapStyleWord = true
            isEditable = false
            border = JBUI.Borders.empty(4)
        }
        private val actionsPanel = JPanel(HorizontalLayout(8))

        init {
            border = JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground())
            add(JBLabel(author).apply { border = JBUI.Borders.empty(4) }, BorderLayout.NORTH)
            add(contentArea, BorderLayout.CENTER)
            add(actionsPanel, BorderLayout.SOUTH)
            updateActions(content)
        }

        fun updateContent(newContent: String) {
            contentArea.text = newContent
            updateActions(newContent)
        }

        private fun updateActions(content: String) {
            actionsPanel.removeAll()
            val codeBlock = CodexTextParsers.extractFirstCodeBlock(content)
            if (codeBlock != null) {
                val insertButton = JButton("Insert to Editor")
                insertButton.addActionListener { insertToEditor(codeBlock) }
                actionsPanel.add(insertButton)
            }
            if (CodexTextParsers.looksLikeUnifiedDiff(content)) {
                val applyButton = JButton("Apply Patch")
                applyButton.addActionListener { applyPatch(content) }
                actionsPanel.add(applyButton)
            }
            actionsPanel.revalidate()
            actionsPanel.repaint()
        }

        private fun insertToEditor(text: String) {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            if (editor == null) {
                CodexNotifications.notifyWarning(project, "No active editor to insert into.")
                return
            }
            WriteCommandAction.runWriteCommandAction(project) {
                editor.document.insertString(editor.caretModel.offset, text)
            }
        }

        private fun applyPatch(diffText: String) {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            if (editor == null) {
                CodexNotifications.notifyWarning(project, "No active editor to apply patch.")
                return
            }
            val result = UnifiedDiffApplier.applyPatch(editor, diffText, project)
            if (result.isFailure) {
                CodexNotifications.notifyError(project, result.message)
            }
        }
    }

    companion object {
        const val TOOL_WINDOW_ID = "Codex"
    }
}
