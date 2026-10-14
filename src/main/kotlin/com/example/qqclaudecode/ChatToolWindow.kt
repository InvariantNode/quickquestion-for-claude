package com.example.qqclaudecode

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener

/** Parent of every live chat, so closing the project ends all of its `claude` processes. */
@Service(Service.Level.PROJECT)
class QQProject : Disposable {
    override fun dispose() {}

    companion object {
        fun getInstance(project: Project): QQProject = project.service()
    }
}

/**
 * The right-anchored tool window that chats are moved into. It is declared in plugin.xml but stays
 * unavailable (no stripe button) until a chat is moved in, and goes away again when the last tab closes.
 */
class ChatToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun shouldBeAvailable(project: Project) = false

    override fun init(toolWindow: ToolWindow) {
        toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun contentRemoved(event: ContentManagerEvent) {
                if (toolWindow.contentManager.contentCount == 0) toolWindow.setAvailable(false)
            }
        })
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Content is added by ChatToolWindow.adopt when a chat is moved here.
    }
}

object ChatToolWindow {
    const val ID = "QuickQuestion for Claude"

    /** Takes over [panel]: from here on a tab disposes it, through the content's disposer. */
    fun adopt(project: Project, panel: ChatPanel, title: String) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ID) ?: return
        toolWindow.setAvailable(true)
        val content = ContentFactory.getInstance().createContent(panel, title, false).apply {
            isCloseable = true
            setDisposer(panel)
        }
        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)
        toolWindow.activate { panel.focusInput() }
    }
}
