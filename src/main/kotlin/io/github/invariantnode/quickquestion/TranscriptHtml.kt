package io.github.invariantnode.quickquestion

import com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color

/** Builds the HTML for the transcript pane. Swing's HTML only supports a CSS subset, so keep styles simple. */
object TranscriptHtml {
    const val SETTINGS_HREF = "qqsettings:"

    fun render(project: Project, session: ChatSession): String {
        val fg = UIUtil.getLabelForeground()
        val bg = UIUtil.getPanelBackground()
        val muted = JBColor.GRAY
        val tint = ColorUtil.mix(bg, JBColor.namedColor("Link.activeForeground", JBColor.BLUE), 0.12)
        val codeBg = ColorUtil.mix(bg, fg, 0.06)
        val font = UIUtil.getLabelFont()

        val body = StringBuilder()
        if (session.parts.isEmpty()) {
            body.append("<p style='color:${hex(muted)}'>Ask a question about the selected code. ")
            body.append("Enter sends, Shift+Enter adds a new line.</p>")
        }
        for (part in session.parts) {
            when (part) {
                is Part.User -> body.append(
                    "<table width='100%' cellspacing='0' cellpadding='6' border='0'><tr>" +
                        "<td bgcolor='${hex(tint)}'>${markdown(project, part.text)}</td></tr></table>",
                )
                is Part.Assistant -> {
                    if (part.text.isNotBlank()) body.append(assistantHtml(project, part))
                }
                is Part.Tool -> body.append(
                    "<div style='color:${hex(muted)}'>› ${escape(part.label)}</div>",
                )
                is Part.Error -> body.append(
                    "<div style='color:${hex(JBColor.RED)}'>${if (part.offerSettings) markdown(project, part.text) else escape(part.text)}</div>",
                )
            }
        }
        if (session.thinking) body.append("<p style='color:${hex(muted)}'><i>Thinking…</i></p>")

        return "<html><head><style>" +
            "body { font-family: '${font.family}'; font-size: ${font.size}pt; color: ${hex(fg)}; margin: 4px; }" +
            "p { margin-top: 4px; margin-bottom: 4px; }" +
            "pre { background-color: ${hex(codeBg)}; padding: 6px; margin-top: 4px; margin-bottom: 4px; }" +
            "code { font-family: monospaced; }" +
            "a { color: ${hex(JBColor.namedColor("Link.activeForeground", JBColor.BLUE))}; }" +
            "</style></head><body>$body</body></html>"
    }

    private fun assistantHtml(project: Project, part: Part.Assistant): String {
        part.cachedHtml?.let { return it }
        val html = markdown(project, part.text.toString())
        if (part.finished) part.cachedHtml = html
        return html
    }

    private fun markdown(project: Project, md: String): String {
        val html = try {
            runReadActionBlocking { DocMarkdownToHtmlConverter.convert(project, md) }
        } catch (e: Exception) {
            "<pre>${escape(md)}</pre>"
        }
        return FileLinks.linkify(html)
    }

    private fun hex(c: Color) = "#" + ColorUtil.toHex(c)

    fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}
