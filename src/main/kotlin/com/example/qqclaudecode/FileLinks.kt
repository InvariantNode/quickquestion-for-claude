package com.example.qqclaudecode

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.concurrency.AppExecutorUtil
import java.nio.file.Path
import java.nio.file.Paths

/** Turning `Foo.kt:42`, `path/to/Foo.kt` and `path#L42` into clickable links that open files in the editor. */
object FileLinks {
    private const val SCHEME = "qqfile:"

    private val KNOWN_EXTENSIONS = listOf(
        "kt", "kts", "java", "scala", "groovy", "gradle", "xml", "json", "yml", "yaml", "toml", "properties",
        "md", "txt", "py", "js", "jsx", "ts", "tsx", "go", "rs", "c", "h", "cpp", "hpp", "cs", "rb", "php",
        "swift", "html", "css", "scss", "sql", "sh",
    ).joinToString("|")

    private val FILE_REF = Regex(
        "(?<![\\w./\\\\@-])" +
            "((?:[\\w.-]+/)+[\\w-]+(?:\\.[\\w-]+)*\\.[A-Za-z]\\w{0,9}|[\\w-]+(?:\\.[\\w-]+)*\\.(?:$KNOWN_EXTENSIONS))" +
            "(?::(\\d+)(?::\\d+)?)?(?![\\w])",
    )

    private val TAG = Regex("<[^>]*>")
    private val LINE_SUFFIX = Regex("(?:#L|:)(\\d+)(?:-L?\\d+)?$")

    /** Links file references in the text parts of [html], skipping `<pre>` blocks and existing `<a>` links. */
    fun linkify(html: String): String {
        val out = StringBuilder(html.length + 64)
        var pre = 0
        var anchor = 0
        var last = 0
        fun text(segment: String) {
            if (pre > 0 || anchor > 0) out.append(segment) else out.append(replaceRefs(segment))
        }
        for (tag in TAG.findAll(html)) {
            text(html.substring(last, tag.range.first))
            out.append(tag.value)
            last = tag.range.last + 1
            val name = tag.value.trim('<', '>', '/', ' ').substringBefore(' ').lowercase()
            val closing = tag.value.startsWith("</")
            val selfClosing = tag.value.endsWith("/>")
            when (name) {
                "pre" -> pre = if (closing) maxOf(0, pre - 1) else if (selfClosing) pre else pre + 1
                "a" -> anchor = if (closing) maxOf(0, anchor - 1) else if (selfClosing) anchor else anchor + 1
            }
        }
        text(html.substring(last))
        return out.toString()
    }

    private fun replaceRefs(text: String): String = FILE_REF.replace(text) { m ->
        val path = m.groupValues[1]
        val line = m.groupValues[2]
        val href = SCHEME + path + if (line.isNotEmpty()) "#L$line" else ""
        "<a href=\"$href\">${m.value}</a>"
    }

    /** Handles a clicked href from the transcript. */
    fun open(project: Project, href: String) {
        when {
            href.startsWith("http://") || href.startsWith("https://") -> BrowserUtil.browse(href)
            href == TranscriptHtml.SETTINGS_HREF ->
                ShowSettingsUtil.getInstance().showSettingsDialog(project, QQConfigurable::class.java)
            href.startsWith("mailto:") -> BrowserUtil.browse(href)
            else -> openFile(project, href.removePrefix(SCHEME))
        }
    }

    private fun openFile(project: Project, ref: String) {
        val line = LINE_SUFFIX.find(ref)?.groupValues?.get(1)?.toIntOrNull()
        val path = LINE_SUFFIX.replace(ref, "").removePrefix("file://")
        ReadAction.nonBlocking<VirtualFile?> { resolve(project, path) }
            .inSmartMode(project)
            .expireWith(project)
            .finishOnUiThread(ModalityState.defaultModalityState()) { file ->
                if (file == null) {
                    NotificationGroupManager.getInstance().getNotificationGroup("QuickQuestion for Claude")
                        .createNotification("Could not find $path in this project", NotificationType.WARNING)
                        .notify(project)
                } else {
                    OpenFileDescriptor(project, file, ((line ?: 1) - 1).coerceAtLeast(0), 0).navigate(true)
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    /** Project root first, then the filename index (matching on the trailing path segments). */
    private fun resolve(project: Project, path: String): VirtualFile? {
        val base = project.basePath
        val fs = LocalFileSystem.getInstance()
        val candidate = runCatching { Paths.get(path) }.getOrNull()
        if (candidate != null) {
            val absolute: Path? = when {
                candidate.isAbsolute -> candidate
                base != null -> Paths.get(base).resolve(candidate)
                else -> null
            }
            absolute?.let { fs.findFileByNioFile(it.normalize()) }?.takeIf { !it.isDirectory }?.let { return it }
        }
        val normalized = path.replace('\\', '/')
        val name = normalized.substringAfterLast('/')
        return FilenameIndex.getVirtualFilesByName(name, GlobalSearchScope.projectScope(project))
            .filter { it.path.endsWith("/$normalized") || it.path == normalized || !normalized.contains('/') }
            .minByOrNull { it.path.length }
    }
}
