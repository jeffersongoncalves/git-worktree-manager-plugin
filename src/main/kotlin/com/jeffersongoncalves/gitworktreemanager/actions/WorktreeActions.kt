package com.jeffersongoncalves.gitworktreemanager.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jeffersongoncalves.gitworktreemanager.WorktreeFlows
import com.jeffersongoncalves.gitworktreemanager.service.GitWorktreeService

abstract class ProjectAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}

class CreateFromExistingAction : ProjectAction() {
    override fun actionPerformed(e: AnActionEvent) {
        WorktreeFlows.createFromExisting(e.project ?: return)
    }
}

class CreateNewBranchAction : ProjectAction() {
    override fun actionPerformed(e: AnActionEvent) {
        WorktreeFlows.createNewBranch(e.project ?: return)
    }
}

class PruneAction : ProjectAction() {
    override fun actionPerformed(e: AnActionEvent) {
        WorktreeFlows.prune(e.project ?: return)
    }
}

class RefreshAction : ProjectAction() {
    override fun actionPerformed(e: AnActionEvent) {
        GitWorktreeService.getInstance(e.project ?: return).notifyChanged()
    }
}
