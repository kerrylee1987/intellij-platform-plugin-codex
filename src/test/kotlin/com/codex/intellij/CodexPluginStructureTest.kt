package com.codex.intellij

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

class CodexPluginStructureTest {
    @Test
    fun `plugin xml declares tool window and action`() {
        val xml = Files.readString(Paths.get("src/main/resources/META-INF/plugin.xml"))
        assertTrue(xml.contains("toolWindow") && xml.contains("Codex"), "Codex ToolWindow missing")
        assertTrue(xml.contains("Codex.SendSelection"), "Codex action missing")
    }

    @Test
    fun `core classes load`() {
        Class.forName("com.codex.intellij.CodexToolWindowFactory")
        Class.forName("com.codex.intellij.CodexSendSelectionAction")
        Class.forName("com.codex.intellij.CodexCliService")
    }
}
