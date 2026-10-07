package com.jeffersongoncalves.gitworktreemanager.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ListSpeedSearch
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.jeffersongoncalves.gitworktreemanager.WorktreeFlows
import com.jeffersongoncalves.gitworktreemanager.model.Worktree
import com.jeffersongoncalves.gitworktreemanager.service.GitWorktreeService
import com.jeffersongoncalves.gitworktreemanager.service.WorktreesListener
import java.awt.event.MouseEvent
import javax.swing.JList

class WorktreeToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = WorktreeToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}

class WorktreeToolWindowPanel(private val project: Project) : SimpleToolWindowPanel(true, true), Disposable {

    private val model = CollectionListModel<Worktree>()
    private val list = JBList(model).apply {
        emptyText.text = "No worktrees"
        cellRenderer = WorktreeRenderer()
    }

    init {
        ListSpeedSearch.installOn(list) { "${it.branch} ${it.path}" }
        object : DoubleClickListener() {
            override fun onDoubleClick(event: MouseEvent): Boolean {
                selected()?.let { WorktreeFlows.open(project, it.path) }
                return true
            }
        }.installOn(list)

        val actions = ActionManager.getInstance()
        val group = DefaultActionGroup().apply {
            add(actions.getAction("GitWorktreeManager.CreateFromExisting"))
            add(actions.getAction("GitWorktreeManager.CreateNewBranch"))
            add(actions.getAction("GitWorktreeManager.Refresh"))
            add(actions.getAction("GitWorktreeManager.Prune"))
            addSeparator()
            add(SelectionAction("Open Worktree", AllIcons.Actions.MenuOpen) { WorktreeFlows.open(project, it.path) })
            add(SelectionAction("Copy Path", AllIcons.Actions.Copy) { WorktreeFlows.copyPath(it) })
            add(SelectionAction("Remove Worktree", AllIcons.General.Remove, allowMain = false) { WorktreeFlows.remove(project, it) })
        }
        val toolbar = actions.createActionToolbar("GitWorktreeManager.Toolbar", group, true)
        toolbar.targetComponent = this

        setToolbar(toolbar.component)
        setContent(JBScrollPane(list))

        project.messageBus.connect(this).subscribe(GitWorktreeService.TOPIC, WorktreesListener { refresh() })
        refresh()
    }

    fun refresh() {
        list.setPaintBusy(true)
        ApplicationManager.getApplication().executeOnPooledThread {
            val service = GitWorktreeService.getInstance(project)
            val (worktrees, error) = try {
                val repo = service.repoRoot()
                (if (repo == null) emptyList() else service.list(repo)) to (if (repo == null) "Not a git repository" else null)
            } catch (e: Exception) {
                emptyList<Worktree>() to (e.message ?: "Failed to list worktrees")
            }

            ApplicationManager.getApplication().invokeLater({
                model.replaceAll(worktrees)
                list.emptyText.text = error ?: "No worktrees"
                list.setPaintBusy(false)
            }, project.disposed)
        }
    }

    private fun selected(): Worktree? = list.selectedValue

    override fun dispose() = Unit

    private inner class SelectionAction(
        text: String,
        icon: javax.swing.Icon,
        private val allowMain: Boolean = true,
        private val perform: (Worktree) -> Unit,
    ) : DumbAwareAction(text, null, icon) {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT

        override fun update(e: AnActionEvent) {
            val worktree = selected()
            e.presentation.isEnabled = worktree != null && (allowMain || !worktree.isMain)
        }

        override fun actionPerformed(e: AnActionEvent) {
            selected()?.let(perform)
        }
    }
}

private class WorktreeRenderer : ColoredListCellRenderer<Worktree>() {
    override fun customizeCellRenderer(list: JList<out Worktree>, value: Worktree, index: Int, selected: Boolean, hasFocus: Boolean) {
        icon = if (value.isMain) AllIcons.Nodes.HomeFolder else AllIcons.Vcs.Branch
        append(value.branch, if (value.isMain) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES)
        append("  ${value.path}", SimpleTextAttributes.GRAYED_ATTRIBUTES)

        val badges = listOfNotNull("locked".takeIf { value.isLocked }, "prunable".takeIf { value.isPrunable })
        if (badges.isNotEmpty()) append("  ${badges.joinToString(", ")}", SimpleTextAttributes.GRAYED_ITALIC_ATTRIBUTES)

        toolTipText = "${value.branch}\n${value.path}\n${value.head}"
    }
}
