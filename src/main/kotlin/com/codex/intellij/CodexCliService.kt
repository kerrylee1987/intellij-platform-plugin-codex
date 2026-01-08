package com.codex.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

enum class CodexFailureReason(val userMessage: String) {
    MISSING_CLI(
        "Codex CLI not found. Install it with your preferred package manager " +
            "and ensure `codex` is on PATH, or set the absolute path in Settings | Tools | Codex.",
    ),
    INVALID_PATH("Configured Codex CLI path does not point to a codex executable."),
    NOT_LOGGED_IN("Codex CLI is not logged in. Run `codex login` in a terminal to authenticate."),
    TIMED_OUT("Codex CLI timed out. Increase timeout in Settings | Tools | Codex if needed."),
}

data class CodexCliResult(
    val output: String,
    val errorOutput: String,
    val exitCode: Int,
    val timedOut: Boolean,
    val failureReason: CodexFailureReason? = null,
)

class CodexCliExecution(
    private val handler: OSProcessHandler,
    private val timeoutFuture: ScheduledFuture<*>?,
    private val onDispose: () -> Unit,
) {
    fun cancel() {
        timeoutFuture?.cancel(false)
        handler.destroyProcess()
        onDispose()
    }
}

@Service(Service.Level.PROJECT)
class CodexCliService(private val project: Project) {
    private val logger = Logger.getInstance(CodexCliService::class.java)
    @Volatile
    private var runningExecution: CodexCliExecution? = null

    fun execute(
        prompt: String,
        onChunk: (String) -> Unit,
        onComplete: (CodexCliResult) -> Unit,
    ): CodexCliExecution? {
        if (runningExecution != null) {
            onComplete(
                CodexCliResult(
                    output = "",
                    errorOutput = "A request is already running.",
                    exitCode = -1,
                    timedOut = false,
                ),
            )
            return null
        }

        val settings = CodexSettingsState.getInstance().state
        val cliPath = resolveCliPath(settings.cliPath)
            ?: return handleFailure(onComplete, CodexFailureReason.INVALID_PATH)

        val commandLine = GeneralCommandLine(cliPath)
            .withParameters(listOf("chat"))
            .withWorkDirectory(project.basePath)
            .withCharset(StandardCharsets.UTF_8)

        val handler = try {
            OSProcessHandler(commandLine)
        } catch (ex: Exception) {
            logger.warn("Failed to start codex CLI", ex)
            return handleFailure(onComplete, CodexFailureReason.MISSING_CLI)
        }

        val outputBuffer = StringBuilder()
        val errorBuffer = StringBuilder()
        val timedOutFlag = AtomicBoolean(false)
        handler.addProcessListener(object : ProcessAdapter() {
            override fun onTextAvailable(event: ProcessEvent, outputType: com.intellij.execution.process.Key<*>) {
                val text = event.text
                if (outputType === ProcessOutputType.STDOUT) {
                    outputBuffer.append(text)
                    onChunk(outputBuffer.toString())
                } else if (outputType === ProcessOutputType.STDERR) {
                    errorBuffer.append(text)
                }
            }

            override fun processTerminated(event: ProcessEvent) {
                val timedOut = timedOutFlag.get()
                val failureReason = when {
                    timedOut -> CodexFailureReason.TIMED_OUT
                    errorBuffer.containsAuthFailure() -> CodexFailureReason.NOT_LOGGED_IN
                    else -> null
                }
                val result = CodexCliResult(
                    output = outputBuffer.toString().trim(),
                    errorOutput = errorBuffer.toString().trim(),
                    exitCode = event.exitCode,
                    timedOut = timedOut,
                    failureReason = failureReason,
                )
                runningExecution = null
                onComplete(result)
            }
        })

        handler.startNotify()
        handler.processInput?.writer(StandardCharsets.UTF_8)?.use { writer ->
            writer.write(prompt)
            writer.write("\n")
            writer.flush()
        }

        val timeoutFuture = if (settings.timeoutSeconds > 0) {
            AppExecutorUtil.getAppScheduledExecutorService().schedule({
                if (!handler.isProcessTerminated) {
                    timedOutFlag.set(true)
                    handler.destroyProcess()
                }
            }, settings.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        } else {
            null
        }

        val execution = CodexCliExecution(handler, timeoutFuture) { runningExecution = null }
        runningExecution = execution
        return execution
    }

    private fun resolveCliPath(configuredPath: String): String? {
        val trimmed = configuredPath.trim()
        if (trimmed.isEmpty()) {
            return "codex"
        }
        val fileName = Paths.get(trimmed).fileName.toString()
        if (fileName != "codex" && fileName != "codex.exe") {
            return null
        }
        if (!Files.exists(Paths.get(trimmed))) {
            return null
        }
        return trimmed
    }

    private fun handleFailure(
        onComplete: (CodexCliResult) -> Unit,
        reason: CodexFailureReason,
    ): CodexCliExecution? {
        onComplete(
            CodexCliResult(
                output = "",
                errorOutput = "",
                exitCode = -1,
                timedOut = false,
                failureReason = reason,
            ),
        )
        return null
    }

    private fun StringBuilder.containsAuthFailure(): Boolean {
        val text = toString().lowercase()
        return text.contains("login") || text.contains("authenticate") || text.contains("unauthorized")
    }

    companion object
}
