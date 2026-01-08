package com.codex.intellij

object CodexTextParsers {
    private val diffHeaderRegex = Regex("^@@ -\\d+(?:,\\d+)? \\+\\d+(?:,\\d+)? @@")

    fun extractFirstCodeBlock(text: String): String? {
        val start = text.indexOf("```")
        if (start == -1) return null
        val afterFence = text.indexOf('\n', start + 3).let { if (it == -1) start + 3 else it + 1 }
        val end = text.indexOf("```", afterFence)
        if (end == -1) return null
        return text.substring(afterFence, end).trimEnd()
    }

    fun looksLikeUnifiedDiff(text: String): Boolean {
        return text.lineSequence().any { line ->
            line.startsWith("diff --git") ||
                line.startsWith("--- ") ||
                line.startsWith("+++ ") ||
                diffHeaderRegex.containsMatchIn(line)
        }
    }
}
