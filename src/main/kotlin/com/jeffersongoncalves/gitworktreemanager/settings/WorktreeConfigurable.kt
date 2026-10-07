package com.jeffersongoncalves.gitworktreemanager.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

class WorktreeConfigurable : BoundConfigurable("Git Worktree Manager") {

    override fun createPanel(): DialogPanel {
        val state = WorktreeSettings.getInstance().state

        return panel {
            row("Default base directory:") {
                textField()
                    .bindText(state::defaultPath)
                    .comment("Where new worktrees are suggested, relative to the main repository (e.g. <code>../</code>)")
            }
            row("After creating a worktree:") {
                comboBox(OpenBehavior.entries)
                    .bindItem({ state.openBehavior }, { state.openBehavior = it ?: OpenBehavior.ASK })
            }
            row {
                checkBox("Ask for confirmation before removing a worktree")
                    .bindSelected(state::confirmRemove)
            }
        }
    }
}
