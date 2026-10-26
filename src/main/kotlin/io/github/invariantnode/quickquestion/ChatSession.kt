package io.github.invariantnode.quickquestion

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project

sealed class Part {
    class User(val text: String) : Part()
    class Assistant : Part() {
        val text = StringBuilder()
        var finished = false

        /** Rendered HTML, cached once the part is finished. */
        var cachedHtml: String? = null
    }

    class Tool(val label: String) : Part()
    class Error(val text: String, val offerSettings: Boolean = false) : Part()
}

/**
 * The transcript plus the backing process of one chat. All state is touched on the EDT only; process
 * callbacks are marshalled over and dropped once the session is disposed.
 */
class ChatSession(
    private val project: Project,
    /** The selection as a fenced code block, sent together with the first question. */
    private val selectionBlock: String?,
    val title: String,
) : Disposable {
    val parts = ArrayList<Part>()

    var busy = false
        private set

    /** True from sending until the first text token of the turn arrives. */
    var thinking = false
        private set

    var onChange: (() -> Unit)? = null

    private var process: ClaudeProcess? = null
    private var disposed = false
    private var firstMessageSent = false
    private var turnGotText = false

    /** Starts the process when the popup opens, off the EDT. */
    fun start() {
        val executable = ClaudeLocator.find()
        if (executable == null) {
            parts += Part.Error(
                "Claude Code CLI not found. Install it, or set its path in " +
                    "[Settings → Tools → QuickQuestion for Claude](qqsettings:).",
                offerSettings = true,
            )
            changed()
            return
        }
        val proc = ClaudeProcess(executable, project.basePath, ::onProcessEvent)
        process = proc
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                proc.start()
            } catch (e: Exception) {
                post {
                    parts += Part.Error("Could not start `${executable.path}`: ${e.message}")
                    busy = false
                    thinking = false
                    changed()
                }
            }
        }
    }

    val canSend: Boolean get() = !busy && process != null

    fun send(question: String) {
        val proc = process ?: return
        if (busy || question.isBlank()) return
        val text = if (!firstMessageSent && selectionBlock != null) "$selectionBlock\n\n$question" else question
        firstMessageSent = true
        finishOpenAssistant()
        parts += Part.User(text)
        busy = true
        thinking = true
        turnGotText = false
        changed()
        ApplicationManager.getApplication().executeOnPooledThread { proc.send(text) }
    }

    private fun onProcessEvent(event: ClaudeEvent) = post {
        when (event) {
            is ClaudeEvent.TextBlockStart -> {
                finishOpenAssistant()
                parts += Part.Assistant()
            }
            is ClaudeEvent.TextDelta -> {
                if (event.text.isEmpty()) return@post
                turnGotText = true
                thinking = false
                val last = parts.lastOrNull()
                val target = if (last is Part.Assistant && !last.finished) last else Part.Assistant().also {
                    finishOpenAssistant()
                    parts += it
                }
                target.text.append(event.text)
            }
            is ClaudeEvent.ToolUse -> {
                finishOpenAssistant()
                parts += Part.Tool(event.label)
            }
            is ClaudeEvent.TurnEnd -> {
                finishOpenAssistant()
                if (event.isError) parts += Part.Error(event.message?.takeIf { it.isNotBlank() } ?: "The request failed.")
                busy = false
                thinking = false
            }
            is ClaudeEvent.Exited -> {
                finishOpenAssistant()
                if (!disposed) {
                    val detail = event.stderr.takeIf { it.isNotBlank() }?.let { ": $it" } ?: ""
                    parts += Part.Error("The Claude process exited (code ${event.exitCode ?: "unknown"})$detail")
                }
                process = null
                busy = false
                thinking = false
            }
        }
        changed()
    }

    private fun finishOpenAssistant() {
        (parts.lastOrNull() as? Part.Assistant)?.finished = true
    }

    private fun post(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater({
            if (!disposed) action()
        }, ModalityState.any())
    }

    private fun changed() {
        if (!disposed) onChange?.invoke()
    }

    override fun dispose() {
        disposed = true
        onChange = null
        process?.destroy()
        process = null
    }
}
