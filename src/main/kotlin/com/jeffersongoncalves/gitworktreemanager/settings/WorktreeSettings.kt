package com.jeffersongoncalves.gitworktreemanager.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

enum class OpenBehavior(private val label: String) {
    ASK("Ask every time"),
    NEW_WINDOW("Open in a new window"),
    SAME_WINDOW("Open in this window"),
    NONE("Don't open");

    override fun toString() = label
}

@Service(Service.Level.APP)
@State(name = "GitWorktreeManagerSettings", storages = [Storage("git-worktree-manager.xml")])
class WorktreeSettings : PersistentStateComponent<WorktreeSettings.SettingsState> {

    class SettingsState {
        /** Base directory for new worktrees, relative to the main repository. */
        var defaultPath: String = "../"
        var openBehavior: OpenBehavior = OpenBehavior.ASK
        var confirmRemove: Boolean = true
    }

    private var state = SettingsState()

    override fun getState(): SettingsState = state

    override fun loadState(state: SettingsState) {
        this.state = state
    }

    companion object {
        fun getInstance(): WorktreeSettings = ApplicationManager.getApplication().getService(WorktreeSettings::class.java)
    }
}
