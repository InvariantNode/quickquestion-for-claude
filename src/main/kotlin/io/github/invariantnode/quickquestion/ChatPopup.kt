package io.github.invariantnode.quickquestion

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel

object ChatPopup {
    private const val DIMENSION_KEY = "QuickQuestion.Popup"

    fun show(project: Project, editor: Editor) {
        val selection = SelectionContext.capture(project, editor) ?: return

        val session = ChatSession(project, selection.fencedBlock, selection.label)
        val panel = ChatPanel(project, session)
        Disposer.register(QQProject.getInstance(project), panel)

        var moved = false
        lateinit var popup: JBPopup

        val moveLink = ActionLink("Move to sidebar") {
            // Hand ownership over before the popup closes: onClosed only disposes an unmoved panel.
            moved = true
            ChatToolWindow.adopt(project, panel, selection.label)
            popup.cancel()
        }
        val header = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(2, 8)
            add(moveLink, BorderLayout.EAST)
        }
        val content = JPanel(BorderLayout()).apply {
            add(header, BorderLayout.NORTH)
            add(panel, BorderLayout.CENTER)
            preferredSize = Dimension(JBUI.scale(520), JBUI.scale(380))
        }

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(content, panel.inputComponent)
            .setTitle("QuickQuestion for Claude · ${selection.label}")
            .setMovable(true)
            .setResizable(true)
            .setRequestFocus(true)
            .setFocusable(true)
            // Stay open while the user looks back at the code: only Esc (or the close button) closes it.
            .setCancelOnClickOutside(false)
            .setCancelOnWindowDeactivation(false)
            .setCancelOnOtherWindowOpen(false)
            .setCancelKeyEnabled(true)
            .setCancelButton(com.intellij.openapi.ui.popup.IconButton("Close", AllIcons.Actions.Close))
            .setDimensionServiceKey(project, DIMENSION_KEY, false)
            .setMinSize(Dimension(JBUI.scale(320), JBUI.scale(220)))
            .createPopup()

        popup.addListener(object : JBPopupListener {
            override fun onClosed(event: LightweightWindowEvent) {
                if (!moved) Disposer.dispose(panel)
            }
        })

        session.start()
        popup.showInBestPositionFor(editor)
    }
}

/** The selected code as a fenced block: project-relative path, 1-based line range, extension as language. */
class SelectionContext(val fencedBlock: String, val label: String) {
    companion object {
        fun capture(project: Project, editor: Editor): SelectionContext? {
            val model = editor.selectionModel
            if (!model.hasSelection()) return null
            val text = model.selectedText ?: return null
            val doc = editor.document
            val startLine = doc.getLineNumber(model.selectionStart) + 1
            var endOffset = model.selectionEnd
            // A selection that ends at column 0 of the next line does not really include that line.
            if (endOffset > model.selectionStart && doc.charsSequence[endOffset - 1] == '\n') endOffset--
            val endLine = doc.getLineNumber(endOffset) + 1

            val file = FileDocumentManager.getInstance().getFile(doc)
            val root = project.basePath?.let { com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(it) }
            val path = when {
                file == null -> "selection"
                root != null -> VfsUtilCore.getRelativePath(file, root) ?: file.name
                else -> file.name
            }
            val language = file?.extension.orEmpty()
            val range = if (startLine == endLine) "$startLine" else "$startLine-$endLine"

            // A fence longer than any backtick run in the selection keeps the block intact.
            val longestRun = Regex("`+").findAll(text).maxOfOrNull { it.value.length } ?: 0
            val fence = "`".repeat(maxOf(3, longestRun + 1))
            val block = "$fence$language $path:$range\n$text\n$fence"
            val short = file?.name ?: "selection"
            return SelectionContext(block, "$short:$range")
        }
    }
}
