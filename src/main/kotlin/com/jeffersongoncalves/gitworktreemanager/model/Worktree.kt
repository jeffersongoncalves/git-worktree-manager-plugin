package com.jeffersongoncalves.gitworktreemanager.model

import java.nio.file.Path

data class Worktree(
    val path: String,
    val branch: String,
    val head: String,
    val isMain: Boolean,
    val isLocked: Boolean,
    val isPrunable: Boolean,
)

data class BranchList(val local: List<String>, val remote: List<String>)

/** Pure parsing/path helpers, kept free of IDE APIs so they can be unit tested. */
object WorktreeParser {

    /** Parses `git worktree list --porcelain`. The first block is always the main worktree. */
    fun parsePorcelain(output: String): List<Worktree> {
        if (output.isBlank()) return emptyList()

        return output.split(Regex("\\r?\\n\\r?\\n"))
            .filter { it.isNotBlank() }
            .mapIndexed { index, block ->
                var path = ""
                var head = ""
                var branch = ""
                var locked = false
                var prunable = false

                for (line in block.lines()) {
                    when {
                        line.startsWith("worktree ") -> path = line.removePrefix("worktree ").trim()
                        line.startsWith("HEAD ") -> head = line.removePrefix("HEAD ").trim()
                        line.startsWith("branch ") -> branch = line.removePrefix("branch ").trim().removePrefix("refs/heads/")
                        line == "detached" -> branch = DETACHED
                        line == "locked" || line.startsWith("locked ") -> locked = true
                        line == "prunable" || line.startsWith("prunable ") -> prunable = true
                    }
                }

                Worktree(path, branch.ifEmpty { DETACHED }, head, index == 0, locked, prunable)
            }
    }

    /** Parses `git branch -a --format=%(refname:short)` into local and `origin/` remote branches. */
    fun parseBranches(output: String): BranchList {
        val local = mutableListOf<String>()
        val remote = mutableListOf<String>()

        for (name in output.lines().map { it.trim() }.filter { it.isNotEmpty() }) {
            when {
                name.endsWith("/HEAD") || name == "origin" -> Unit
                name.startsWith("origin/") -> remote += name
                else -> local += name
            }
        }

        return BranchList(local, remote)
    }

    /** `feature/login` -> `login`; strips refs prefixes and characters Windows rejects in folder names. */
    fun sanitizeBranchForFolder(branch: String): String {
        val stripped = branch.replace(Regex("^refs/(heads|remotes)/"), "")
        return stripped.substringAfterLast('/').replace(Regex("[\\\\:]+"), "-")
    }

    /** `<repo>/<defaultBase>/<repoName>-<branch folder>`, normalized. */
    fun suggestWorktreePath(repoPath: String, branch: String, defaultBase: String): String {
        val repo = Path.of(repoPath)
        val folder = "${repo.fileName}-${sanitizeBranchForFolder(branch)}"
        return repo.resolve(defaultBase).resolve(folder).normalize().toString()
    }

    /** Errors `git worktree remove` raises when `--force` would succeed. */
    fun needsForce(message: String): Boolean =
        Regex("uncommitted|dirty|not clean|locked|modified or untracked", RegexOption.IGNORE_CASE).containsMatchIn(message)

    const val DETACHED = "(detached)"
}
