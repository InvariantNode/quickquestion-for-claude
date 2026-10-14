package com.example.qqclaudecode

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.Timer
import javax.swing.event.HyperlinkEvent
import javax.swing.text.DefaultCaret
import javax.swing.text.DefaultEditorKit

/**
 * The chat transcript plus the input field. Owns its [ChatSession]; disposing the panel ends the chat and
 * kills the backing process.
 */
class ChatPanel(
    private val project: Project,
    val session: ChatSession,
) : JPanel(BorderLayout()), Disposable {

    private val transcript = JEditorPane().apply {
        editorKit = HTMLEditorKitBuilder().withWordWrapViewFactory().build()
        isEditable = false
        border = JBUI.Borders.empty(4)
        // Selectable, but no caret: it must neither paint nor drag the viewport around on setText.
        caret = object : DefaultCaret() {
            override fun paint(g: Graphics) {}
            override fun isVisible() = false
        }.also { it.updatePolicy = DefaultCaret.NEVER_UPDATE }
        addHyperlinkListener { e ->
            if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                val href = e.description ?: e.url?.toString() ?: return@addHyperlinkListener
                FileLinks.open(project, href)
            }
        }
    }

    private val scroll = JBScrollPane(
        transcript,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER,
    ).apply { border = JBUI.Borders.empty() }

    private val input = JBTextArea(2, 40).apply {
        lineWrap = true
        wrapStyleWord = true
        emptyText.text = "Ask Claude about the selected code…"
        border = JBUI.Borders.empty(4)
    }

    /** Coalesces re-renders: many stream deltas arrive per frame, rendering Markdown on each would be wasteful. */
    private val renderTimer = Timer(RENDER_DELAY_MS) { renderNow() }.apply { isRepeats = false }

    private var disposed = false

    init {
        installKeys()
        add(scroll, BorderLayout.CENTER)
        add(
            JBScrollPane(input).apply {
                border = JBUI.Borders.customLine(JBUI.CurrentTheme.Popup.separatorColor(), 1, 0, 0, 0)
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            },
            BorderLayout.SOUTH,
        )
        session.onChange = { scheduleRender() }
        renderNow()
    }

    val inputComponent: JComponent get() = input

    fun focusInput() = input.requestFocusInWindow()

    private fun installKeys() {
        val enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
        val shiftEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK)
        input.inputMap.put(enter, "qq.send")
        input.inputMap.put(shiftEnter, DefaultEditorKit.insertBreakAction)
        input.actionMap.put("qq.send", object : AbstractAction() {
            override fun actionPerformed(e: ActionEvent?) = submit()
        })
    }

    private fun submit() {
        val text = input.text.trim()
        if (text.isEmpty() || !session.canSend) return
        input.text = ""
        session.send(text)
    }

    private fun scheduleRender() {
        if (disposed) return
        if (!renderTimer.isRunning) renderTimer.start()
    }

    private fun renderNow() {
        if (disposed) return
        val bar = scroll.verticalScrollBar
        val wasAtBottom = bar.value + bar.visibleAmount >= bar.maximum - STICKY_PIXELS
        val viewPosition = scroll.viewport.viewPosition

        transcript.text = TranscriptHtml.render(project, session)

        SwingUtilities.invokeLater {
            if (disposed) return@invokeLater
            if (wasAtBottom) {
                bar.value = bar.maximum
            } else {
                scroll.viewport.viewPosition = viewPosition
            }
        }
    }

    override fun dispose() {
        disposed = true
        renderTimer.stop()
        Disposer.dispose(session)
    }

    private companion object {
        const val RENDER_DELAY_MS = 80
        const val STICKY_PIXELS = 8
    }
}
