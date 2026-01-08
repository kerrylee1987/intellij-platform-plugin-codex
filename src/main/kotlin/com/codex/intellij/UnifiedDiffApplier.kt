package com.codex.intellij

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil

object UnifiedDiffApplier {
    private val hunkHeaderRegex = Regex("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@")

    data class ApplyResult(val isFailure: Boolean, val message: String) {
        companion object {
            fun success() = ApplyResult(false, "Patch applied.")
            fun failure(message: String) = ApplyResult(true, message)
        }
    }

    fun applyPatch(editor: Editor, diffText: String, project: Project): ApplyResult {
        val fileName = editor.virtualFile?.name ?: "current file"
        val patchTarget = extractTargetFileName(diffText)
        if (patchTarget != null && patchTarget != fileName) {
            return ApplyResult.failure("Patch targets $patchTarget, but the current file is $fileName.")
        }

        val originalText = editor.document.text
        val result = applyUnifiedDiff(originalText, diffText)
        if (result.isFailure) {
            return result
        }
        WriteCommandAction.runWriteCommandAction(project) {
            editor.document.setText(result.message)
        }
        return ApplyResult.success()
    }

    private fun applyUnifiedDiff(originalText: String, diffText: String): ApplyResult {
        val originalLines = originalText.split('\n').toMutableList()
        val hunks = parseHunks(diffText)
        if (hunks.isEmpty()) {
            return ApplyResult.failure("No hunks found in patch.")
        }
        var lineOffset = 0
        for (hunk in hunks) {
            var index = hunk.oldStart - 1 + lineOffset
            for (line in hunk.lines) {
                if (line.startsWith("\\ No newline")) continue
                when (line.firstOrNull()) {
                    ' ' -> {
                        val expected = line.drop(1)
                        val actual = originalLines.getOrNull(index)
                        if (actual != expected) {
                            return ApplyResult.failure("Patch context mismatch near: ${StringUtil.trimLog(expected, 120)}")
                        }
                        index++
                    }
                    '-' -> {
                        val expected = line.drop(1)
                        val actual = originalLines.getOrNull(index)
                        if (actual != expected) {
                            return ApplyResult.failure("Patch removal mismatch near: ${StringUtil.trimLog(expected, 120)}")
                        }
                        originalLines.removeAt(index)
                        lineOffset--
                    }
                    '+' -> {
                        originalLines.add(index, line.drop(1))
                        index++
                        lineOffset++
                    }
                }
            }
        }
        val newText = originalLines.joinToString("\n")
        return ApplyResult(false, newText)
    }

    private data class Hunk(val oldStart: Int, val lines: List<String>)

    private fun parseHunks(diffText: String): List<Hunk> {
        val lines = diffText.lines()
        val hunks = mutableListOf<Hunk>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val match = hunkHeaderRegex.find(line)
            if (match != null) {
                val oldStart = match.groupValues[1].toInt()
                val hunkLines = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].startsWith("@@")) {
                    val current = lines[index]
                    if (current.startsWith("--- ") || current.startsWith("+++ ") || current.startsWith("diff --git")) {
                        index++
                        continue
                    }
                    if (current.isNotEmpty()) {
                        hunkLines.add(current)
                    }
                    index++
                }
                hunks.add(Hunk(oldStart, hunkLines))
                continue
            }
            index++
        }
        return hunks
    }

    private fun extractTargetFileName(diffText: String): String? {
        val lines = diffText.lineSequence()
        val fileLine = lines.firstOrNull { it.startsWith("+++ ") || it.startsWith("--- ") }
        val raw = fileLine?.substringAfter(' ')?.trim() ?: return null
        val sanitized = raw.removePrefix("a/").removePrefix("b/").removePrefix("i/").removePrefix("w/")
        return sanitized.substringAfterLast('/').ifBlank { null }
    }
}
