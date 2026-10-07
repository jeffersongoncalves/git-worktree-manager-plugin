package com.jeffersongoncalves.gitworktreemanager.service

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import com.jeffersongoncalves.gitworktreemanager.model.BranchList
import com.jeffersongoncalves.gitworktreemanager.model.Worktree
import com.jeffersongoncalves.gitworktreemanager.model.WorktreeParser

class GitCommandException(message: String) : Exception(message)

fun interface WorktreesListener {
    fun worktreesChanged()
}

/**
 * Runs the `git` executable from PATH, like the VS Code extension does. Every method blocks:
 * call it from a background thread or a progress task, never from the EDT.
 */
@Service(Service.Level.PROJECT)
class GitWorktreeService(private val project: Project) {

    /** Top-level directory of the repository that contains the project, or null when it is not a git repository. */
    fun repoRoot(): String? {
        val base = project.basePath ?: return null
        return try {
            run(base, "rev-parse", "--show-toplevel").trim().ifEmpty { null }
        } catch (_: GitCommandException) {
            null
        }
    }

    fun list(repo: String): List<Worktree> = WorktreeParser.parsePorcelain(run(repo, "worktree", "list", "--porcelain"))

    fun branches(repo: String): BranchList = WorktreeParser.parseBranches(run(repo, "branch", "-a", "--format=%(refname:short)"))

    fun currentBranch(repo: String): String = run(repo, "rev-parse", "--abbrev-ref", "HEAD").trim()

    fun add(repo: String, path: String, branch: String) {
        run(repo, "worktree", "add", path, branch)
        notifyChanged()
    }

    fun addWithNewBranch(repo: String, path: String, newBranch: String, base: String) {
        run(repo, "worktree", "add", "-b", newBranch, path, base)
        notifyChanged()
    }

    fun remove(repo: String, path: String, force: Boolean = false) {
        if (force) run(repo, "worktree", "remove", "--force", path) else run(repo, "worktree", "remove", path)
        notifyChanged()
    }

    fun prune(repo: String) {
        run(repo, "worktree", "prune")
        notifyChanged()
    }

    fun notifyChanged() {
        project.messageBus.syncPublisher(TOPIC).worktreesChanged()
    }

    private fun run(workDir: String, vararg args: String): String {
        val commandLine = GeneralCommandLine("git", *args)
            .withWorkDirectory(workDir)
            .withCharset(Charsets.UTF_8)

        val result = try {
            CapturingProcessHandler(commandLine).runProcess(60_000)
        } catch (e: Exception) {
            throw GitCommandException("Could not run git: ${e.message}")
        }

        if (result.isTimeout) throw GitCommandException("git ${args.joinToString(" ")} timed out")
        if (result.exitCode != 0) {
            throw GitCommandException(result.stderr.trim().ifEmpty { result.stdout.trim() }.ifEmpty { "git exited with ${result.exitCode}" })
        }

        return result.stdout
    }

    companion object {
        @Topic.ProjectLevel
        val TOPIC: Topic<WorktreesListener> = Topic.create("Git worktrees changed", WorktreesListener::class.java)

        fun getInstance(project: Project): GitWorktreeService = project.getService(GitWorktreeService::class.java)
    }
}
