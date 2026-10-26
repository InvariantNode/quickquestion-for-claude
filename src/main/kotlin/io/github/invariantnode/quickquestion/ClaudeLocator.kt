package io.github.invariantnode.quickquestion

import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import java.io.File

@State(name = "QuickQuestionSettings", storages = [Storage("quickquestion-for-claude.xml")])
@Service(Service.Level.APP)
class QQSettings : PersistentStateComponent<QQSettings.State> {
    class State {
        var claudePath: String = ""
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    var claudePath: String
        get() = state.claudePath
        set(value) {
            state.claudePath = value.trim()
        }

    companion object {
        fun getInstance(): QQSettings = ApplicationManager.getApplication().getService(QQSettings::class.java)
    }
}

object ClaudeLocator {
    /** Settings page, then the login-shell PATH, then well-known install locations. */
    fun find(): File? {
        val configured = QQSettings.getInstance().claudePath
        if (configured.isNotBlank()) {
            return File(configured).takeIf { it.isFile }
        }
        return findOnPath() ?: knownLocations().firstOrNull { it.isFile && it.canExecute() }
    }

    /** The resolved path shown as a hint on the settings page, or null. */
    fun detected(): File? = findOnPath() ?: knownLocations().firstOrNull { it.isFile && it.canExecute() }

    private fun findOnPath(): File? {
        val path = EnvironmentUtil.getValue("PATH") ?: return null
        val names = if (SystemInfo.isWindows) listOf("claude.exe", "claude.cmd", "claude") else listOf("claude")
        for (name in names) {
            PathEnvironmentVariableUtil.findInPath(name, path, null)?.let { return it }
        }
        return null
    }

    private fun knownLocations(): List<File> {
        val home = System.getProperty("user.home")
        val dirs = buildList {
            add(File(home, ".local/bin"))
            add(File(home, ".claude/local"))
            if (SystemInfo.isWindows) {
                System.getenv("APPDATA")?.let { add(File(it, "npm")) }
                System.getenv("LOCALAPPDATA")?.let { add(File(it, "Programs/claude")) }
            } else {
                add(File("/opt/homebrew/bin"))
                add(File("/usr/local/bin"))
            }
        }
        val names = if (SystemInfo.isWindows) listOf("claude.exe", "claude.cmd") else listOf("claude")
        return dirs.flatMap { dir -> names.map { File(dir, it) } }
    }
}
