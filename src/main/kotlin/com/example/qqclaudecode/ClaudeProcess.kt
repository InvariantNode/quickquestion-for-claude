package com.example.qqclaudecode

import com.google.gson.JsonObject
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * One headless `claude` process per chat. Multi-turn works by writing one JSON line per user message to
 * the same process's stdin; we never use -c/--continue/--resume.
 *
 * [onEvent] is invoked on a reader thread.
 */
class ClaudeProcess(
    private val executable: File,
    private val workingDir: String?,
    private val onEvent: (ClaudeEvent) -> Unit,
) {
    private val log = logger<ClaudeProcess>()

    @Volatile
    private var process: Process? = null

    @Volatile
    private var writer: BufferedWriter? = null

    @Volatile
    private var destroyed = false

    private val shutdownHook = Thread({
        process?.let { p ->
            p.toHandle().descendants().forEach { it.destroyForcibly() }
            p.destroyForcibly()
        }
    }, "QuickQuestion shutdown")

    private val stderrTail = StringBuilder()
    private val seenToolIds = HashSet<String>()

    /** Not on the EDT: spawning a process can block. */
    fun start() {
        val cmd = object : GeneralCommandLine(executable.path) {
            override fun setupEnvironment(environment: MutableMap<String, String>) {
                super.setupEnvironment(environment)
                // Keep the child from attaching to this IDE or any other running one.
                environment.remove("CLAUDE_CODE_SSE_PORT")
                environment.remove("ENABLE_IDE_INTEGRATION")
            }
        }
        cmd.withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
        cmd.withCharset(StandardCharsets.UTF_8)
        workingDir?.let { cmd.withWorkDirectory(it) }
        cmd.addParameters(
            "-p",
            "--input-format", "stream-json",
            "--output-format", "stream-json",
            "--verbose",
            "--include-partial-messages",
            "--no-session-persistence",
            "--strict-mcp-config",
            "--append-system-prompt", SYSTEM_PROMPT,
            "--allowedTools", "Read", "Grep", "Glob",
            "--tools", "Read", "Grep", "Glob",
        )

        val p = cmd.createProcess()
        process = p
        // Safety net for an IDE that is killed or exits without disposing its services.
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        writer = BufferedWriter(OutputStreamWriter(p.outputStream, StandardCharsets.UTF_8))
        if (destroyed) {
            destroy()
            return
        }
        thread("QuickQuestion stdout") { readStdout(p) }
        thread("QuickQuestion stderr") { readStderr(p) }
    }

    /** Writes one user message as a JSON line. Not on the EDT. */
    fun send(text: String) {
        val message = JsonObject().apply {
            addProperty("type", "user")
            add("message", JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", text)
            })
        }
        try {
            synchronized(this) {
                val w = writer ?: return
                w.write(message.toString())
                w.write("\n")
                w.flush()
            }
        } catch (e: Exception) {
            log.info("Could not write to claude stdin", e)
        }
    }

    fun destroy() {
        destroyed = true
        try {
            Runtime.getRuntime().removeShutdownHook(shutdownHook)
        } catch (_: IllegalStateException) {
            // Already shutting down.
        }
        val p = process ?: return
        // Closing stdin lets the CLI exit on its own; the rest is a safety net.
        try {
            writer?.close()
        } catch (_: Exception) {
        }
        val handle = p.toHandle()
        handle.descendants().forEach { it.destroy() }
        p.destroy()
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                if (!p.waitFor(2, TimeUnit.SECONDS)) {
                    handle.descendants().forEach { it.destroyForcibly() }
                    p.destroyForcibly()
                }
            } catch (_: InterruptedException) {
            }
        }
    }

    private fun readStdout(p: Process) {
        try {
            p.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                // useLines splits on newlines for us, so partial chunks are buffered until a full line arrives.
                for (line in lines) {
                    for (event in StreamJson.parseLine(line)) {
                        if (event is ClaudeEvent.ToolUse && !seenToolIds.add(event.id)) continue
                        if (!destroyed) onEvent(event)
                    }
                }
            }
        } catch (e: Exception) {
            if (!destroyed) log.info("claude stdout closed", e)
        }
        if (destroyed) return
        val code = try {
            if (p.waitFor(2, TimeUnit.SECONDS)) p.exitValue() else null
        } catch (_: InterruptedException) {
            null
        }
        onEvent(ClaudeEvent.Exited(code, synchronized(stderrTail) { stderrTail.toString().trim() }))
    }

    private fun readStderr(p: Process) {
        try {
            p.errorStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    synchronized(stderrTail) {
                        stderrTail.append(line).append('\n')
                        if (stderrTail.length > MAX_STDERR) stderrTail.delete(0, stderrTail.length - MAX_STDERR)
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun thread(name: String, body: () -> Unit) {
        Thread(body, name).apply { isDaemon = true }.start()
    }

    private companion object {
        const val SYSTEM_PROMPT = "Be concise; you're in a small IDE popup."
        const val MAX_STDERR = 4000
    }
}
