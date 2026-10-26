package io.github.invariantnode.quickquestion

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.AlignX

class QQConfigurable : BoundSearchableConfigurable("QuickQuestion for Claude", "io.github.invariantnode.quickquestion.settings") {
    override fun createPanel(): DialogPanel {
        val settings = QQSettings.getInstance()
        return panel {
            row("Claude executable:") {
                textFieldWithBrowseButton(FileChooserDescriptorFactory.singleFile())
                    .bindText(settings::claudePath)
                    .align(AlignX.FILL)
                    .comment(
                        ClaudeLocator.detected()?.let { "Leave empty to use the detected CLI: ${it.path}" }
                            ?: "Leave empty to search PATH and common install locations. No CLI was found yet.",
                    )
            }
        }
    }
}
