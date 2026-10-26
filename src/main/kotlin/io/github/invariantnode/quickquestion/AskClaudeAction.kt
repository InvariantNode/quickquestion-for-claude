package io.github.invariantnode.quickquestion

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware

class AskClaudeAction : AnAction(), DumbAware {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = e.project != null && editor != null && editor.selectionModel.hasSelection()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        // With a sidebar chat open, point that chat at the new selection instead of opening a second one.
        ChatToolWindow.activeChat(project)?.let { (toolWindow, chat) ->
            SelectionContext.capture(project, editor)?.let(chat::useSelection)
            toolWindow.activate { chat.focusInput() }
            return
        }
        ChatPopup.show(project, editor)
    }
}
