package com.jeffersongoncalves.gitworktreemanager

import com.intellij.ide.impl.ProjectUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.ThrowableComputable
import com.jeffersongoncalves.gitworktreemanager.model.Worktree
import com.jeffersongoncalves.gitworktreemanager.model.WorktreeParser
import com.jeffersongoncalves.gitworktreemanager.service.GitCommandException
import com.jeffersongoncalves.gitworktreemanager.service.GitWorktreeService
import com.jeffersongoncalves.gitworktreemanager.settings.OpenBehavior
import com.jeffersongoncalves.gitworktreemanager.settings.WorktreeSettings
import java.awt.datatransfer.StringSelection

/** User flows shared by the menu actions and the tool window toolbar. All entry points run on the EDT. */
object WorktreeFlows {

    private const val TITLE = "Git Worktree Manager"

    fun createFromExisting(project: Project) {
        val service = GitWorktreeService.getInstance(project)
        val repo = git(project, "Reading branches...") { service.repoRoot() } ?: return notGit(project)
        val branches = git(project, "Reading branches...") { service.branches(repo) } ?: return

        chooseBranch(project, "Branch for the New Worktree", branches.local + branches.remote) { branch ->
            val path = askPath(project, WorktreeParser.suggestWorktreePath(repo, branch, settings().defaultPath)) ?: return@chooseBranch
            git(project, "Creating worktree for $branch...") { service.add(repo, path, branch) } ?: return@chooseBranch
            created(project, path)
        }
    }

    fun createNewBranch(project: Project) {
        val service = GitWorktreeService.getInstance(project)
        val repo = git(project, "Reading branches...") { service.repoRoot() } ?: return notGit(project)

        val newBranch = Messages.showInputDialog(
            project, "New branch name:", TITLE, null, "", BranchNameValidator,
        )?.trim() ?: return

        val (current, branches) = git(project, "Reading branches...") { service.currentBranch(repo) to service.branches(repo) } ?: return
        val bases = listOf(current) + branches.local.filter { it != current } + branches.remote

        chooseBranch(project, "Base Branch for '$newBranch'", bases) { base ->
            val path = askPath(project, WorktreeParser.suggestWorktreePath(repo, newBranch, settings().defaultPath)) ?: return@chooseBranch
            git(project, "Creating worktree with branch $newBranch...") { service.addWithNewBranch(repo, path, newBranch, base) } ?: return@chooseBranch
            created(project, path)
        }
    }

    fun remove(project: Project, worktree: Worktree) {
        if (worktree.isMain) {
            Messages.showWarningDialog(project, "The main worktree cannot be removed.", TITLE)
            return
        }

        if (settings().confirmRemove &&
            Messages.showYesNoDialog(project, "Remove worktree \"${worktree.branch}\" at ${worktree.path}?", TITLE, "Remove", "Cancel", Messages.getWarningIcon()) != Messages.YES
        ) {
            return
        }

        val service = GitWorktreeService.getInstance(project)
        val repo = git(project, "Removing worktree...") { service.repoRoot() } ?: return notGit(project)

        try {
            ProgressManager.getInstance().runProcessWithProgressSynchronously(
                ThrowableComputable<Unit, GitCommandException> { service.remove(repo, worktree.path) },
                "Removing worktree...", false, project,
            )
            notify(project, "Worktree removed: ${worktree.path}", NotificationType.INFORMATION)
        } catch (e: GitCommandException) {
            val message = e.message.orEmpty()
            if (!WorktreeParser.needsForce(message)) {
                notify(project, "Failed to remove worktree: $message", NotificationType.ERROR)
                return
            }
            val force = Messages.showYesNoDialog(
                project, "Worktree has uncommitted changes or is locked. Force remove \"${worktree.branch}\"?",
                TITLE, "Force Remove", "Cancel", Messages.getWarningIcon(),
            )
            if (force == Messages.YES) {
                git(project, "Removing worktree...") { service.remove(repo, worktree.path, force = true) } ?: return
                notify(project, "Worktree removed: ${worktree.path}", NotificationType.INFORMATION)
            }
        }
    }

    fun prune(project: Project) {
        val service = GitWorktreeService.getInstance(project)
        val repo = git(project, "Pruning worktrees...") { service.repoRoot() } ?: return notGit(project)
        git(project, "Pruning worktrees...") { service.prune(repo) } ?: return
        notify(project, "Stale worktree references pruned.", NotificationType.INFORMATION)
    }

    fun open(project: Project, path: String, behavior: OpenBehavior = OpenBehavior.ASK) {
        val effective = if (behavior == OpenBehavior.ASK) askOpenBehavior(project, path) ?: return else behavior

        when (effective) {
            OpenBehavior.NEW_WINDOW -> ProjectUtil.openOrImport(path, null, true)
            OpenBehavior.SAME_WINDOW -> ProjectUtil.openOrImport(path, project, false)
            else -> Unit
        }
    }

    fun copyPath(worktree: Worktree) {
        CopyPasteManager.getInstance().setContents(StringSelection(worktree.path))
    }

    private fun created(project: Project, path: String) {
        notify(project, "Worktree created at $path", NotificationType.INFORMATION)
        open(project, path, settings().openBehavior)
    }

    private fun askOpenBehavior(project: Project, path: String): OpenBehavior? {
        val options = arrayOf("New Window", "This Window", "Don't Open")
        return when (Messages.showDialog(project, "Open worktree $path?", TITLE, options, 0, Messages.getQuestionIcon())) {
            0 -> OpenBehavior.NEW_WINDOW
            1 -> OpenBehavior.SAME_WINDOW
            2 -> OpenBehavior.NONE
            else -> null
        }
    }

    private fun askPath(project: Project, suggested: String): String? =
        Messages.showInputDialog(project, "Worktree path:", TITLE, null, suggested, null)?.trim()?.ifEmpty { null }

    private fun chooseBranch(project: Project, title: String, branches: List<String>, onChosen: (String) -> Unit) {
        if (branches.isEmpty()) {
            notify(project, "No branches found.", NotificationType.WARNING)
            return
        }

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(branches)
            .setTitle(title)
            .setNamerForFiltering { it }
            .setItemChosenCallback(onChosen)
            .createPopup()
            .showCenteredInCurrentWindow(project)
    }

    /** Runs a blocking git call under a modal progress; shows the error and returns null when it fails. */
    private fun <T> git(project: Project, title: String, block: () -> T): T? = try {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(
            ThrowableComputable<T, GitCommandException> { block() }, title, false, project,
        )
    } catch (e: GitCommandException) {
        notify(project, e.message.orEmpty(), NotificationType.ERROR)
        null
    }

    private fun notGit(project: Project) = notify(project, "The project is not inside a git repository.", NotificationType.WARNING)

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(TITLE)
            .createNotification(TITLE, content, type)
            .notify(project)
    }

    private fun settings() = WorktreeSettings.getInstance().state

    private object BranchNameValidator : InputValidator {
        override fun checkInput(inputString: String?): Boolean {
            val name = inputString?.trim().orEmpty()
            return name.isNotEmpty() && !name.contains(' ') && !name.startsWith('-')
        }

        override fun canClose(inputString: String?) = checkInput(inputString)
    }
}
