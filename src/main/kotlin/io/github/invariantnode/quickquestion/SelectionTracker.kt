package io.github.invariantnode.quickquestion

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.project.Project
import javax.swing.Timer

/**
 * Follows the selection in the project's editors, so an open chat can answer about the lines that are
 * selected now rather than the ones selected when it was opened. Clearing a selection keeps the last one.
 */
class SelectionTracker(
    private val project: Project,
    initial: SelectionContext?,
    private val onChange: (SelectionContext) -> Unit,
) : Disposable {
    var current: SelectionContext? = initial
        private set

    /** Dragging a selection fires many events; capture once things settle. */
    private val debounce = Timer(DEBOUNCE_MS) { captureLatest() }.apply { isRepeats = false }

    private var lastEditor: com.intellij.openapi.editor.Editor? = null

    init {
        EditorFactory.getInstance().eventMulticaster.addSelectionListener(object : SelectionListener {
            override fun selectionChanged(e: SelectionEvent) {
                val editor = e.editor
                if (editor.project != project || editor.editorKind != EditorKind.MAIN_EDITOR) return
                if (e.newRange.isEmpty) return
                lastEditor = editor
                debounce.restart()
            }
        }, this)
    }

    /** Sets the selection directly, for example when "Ask Claude" is invoked on a sidebar chat. */
    fun set(selection: SelectionContext) {
        debounce.stop()
        update(selection)
    }

    private fun captureLatest() {
        val editor = lastEditor?.takeIf { !it.isDisposed } ?: return
        SelectionContext.capture(project, editor)?.let(::update)
    }

    private fun update(selection: SelectionContext) {
        if (selection == current) return
        current = selection
        onChange(selection)
    }

    override fun dispose() {
        debounce.stop()
        lastEditor = null
    }

    private companion object {
        const val DEBOUNCE_MS = 200
    }
}
