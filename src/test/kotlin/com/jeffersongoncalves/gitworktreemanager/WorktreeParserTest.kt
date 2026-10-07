package com.jeffersongoncalves.gitworktreemanager

import com.jeffersongoncalves.gitworktreemanager.model.WorktreeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Path

class WorktreeParserTest {

    @Test
    fun `parses porcelain output with main, branch, detached, locked and prunable worktrees`() {
        val output = listOf(
            "worktree /repo\nHEAD aaa\nbranch refs/heads/main",
            "worktree /repo-feature\nHEAD bbb\nbranch refs/heads/feature/login\nlocked reason",
            "worktree /repo-detached\nHEAD ccc\ndetached\nprunable gitdir file points to non-existent location",
        ).joinToString("\r\n\r\n") + "\n"

        val worktrees = WorktreeParser.parsePorcelain(output)

        assertEquals(3, worktrees.size)
        assertEquals("main", worktrees[0].branch)
        assertTrue(worktrees[0].isMain)
        assertEquals("feature/login", worktrees[1].branch)
        assertTrue(worktrees[1].isLocked)
        assertFalse(worktrees[1].isMain)
        assertEquals(WorktreeParser.DETACHED, worktrees[2].branch)
        assertTrue(worktrees[2].isPrunable)
        assertEquals("ccc", worktrees[2].head)
    }

    @Test
    fun `empty output has no worktrees`() {
        assertTrue(WorktreeParser.parsePorcelain("  \n").isEmpty())
    }

    @Test
    fun `splits local and origin branches and drops HEAD refs`() {
        val branches = WorktreeParser.parseBranches("main\nfeature/x\norigin/HEAD\norigin\norigin/main\norigin/feature/y\n")

        assertEquals(listOf("main", "feature/x"), branches.local)
        assertEquals(listOf("origin/main", "origin/feature/y"), branches.remote)
    }

    @Test
    fun `sanitizes branch names into folder names`() {
        assertEquals("login", WorktreeParser.sanitizeBranchForFolder("refs/heads/feature/login"))
        assertEquals("fix-1", WorktreeParser.sanitizeBranchForFolder("origin/fix:1"))
    }

    @Test
    fun `suggests a sibling folder named after repo and branch`() {
        val repo = Path.of("projects", "app").toAbsolutePath().toString()
        val expected = Path.of("projects", "app-login").toAbsolutePath().normalize().toString()

        assertEquals(expected, WorktreeParser.suggestWorktreePath(repo, "feature/login", "../"))
    }

    @Test
    fun `detects errors that a forced remove can fix`() {
        assertTrue(WorktreeParser.needsForce("fatal: '/x' contains modified or untracked files, use --force to delete it"))
        assertTrue(WorktreeParser.needsForce("fatal: cannot remove a locked working tree"))
        assertFalse(WorktreeParser.needsForce("fatal: '/x' is not a working tree"))
    }
}
