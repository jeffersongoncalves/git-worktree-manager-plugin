# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Git Worktree Manager** is a JetBrains plugin (Kotlin) for `git worktree`: a tool window listing worktrees plus actions to create (from an existing or a new branch), open, copy the path of, remove and prune worktrees. It is the JetBrains port of the VS Code extension in `../git-worktree-manager` and keeps the same behaviour and settings.

- **Plugin ID**: `com.jeffersongoncalves.gitworktree`
- **Target IDE**: any JetBrains IDE 2024.3+ (builds 243–263.*), depends only on `com.intellij.modules.platform`
- **Language**: Kotlin (JVM 17), Gradle 8.13, IntelliJ Platform Gradle Plugin 2.x, dev platform IntelliJ IDEA Community 2024.3
- **Git**: runs the `git` executable from PATH (no Git4Idea dependency), like the VS Code extension

## Commands

```bash
./gradlew buildPlugin   # build/distributions/*.zip
./gradlew runIde        # sandbox IDE
./gradlew test          # JUnit 4 tests
./gradlew verifyPlugin
```

Locally on Windows set `JAVA_HOME` to a JDK 17 first (one lives in `~/.jdks/`).

## Architecture (`com.jeffersongoncalves.gitworktreemanager`)

| File | Purpose |
|------|---------|
| `model/Worktree.kt` | `Worktree`, `BranchList` and `WorktreeParser` — porcelain/branch parsing, folder sanitizing, path suggestion, force-remove detection. Pure, unit tested |
| `service/GitWorktreeService.kt` | Project service running `git` (blocking — never on the EDT). Publishes `TOPIC` after every change |
| `settings/` | App-level `WorktreeSettings` (defaultPath, openBehavior, confirmRemove) + `WorktreeConfigurable` (Settings → Tools) |
| `WorktreeFlows.kt` | User flows (dialogs, popups, modal progress, notifications) shared by menu actions and the tool window |
| `actions/WorktreeActions.kt` | Create from existing / new branch, prune, refresh — registered under VCS → Git Worktrees |
| `ui/WorktreeToolWindow.kt` | Tool window: list + toolbar; open/copy/remove act on the selection; refreshes on `TOPIC` |

Everything is registered in `src/main/resources/META-INF/plugin.xml`.

## Release

Bump `pluginVersion` in `gradle.properties`, then push a `v<version>` tag: `.github/workflows/release.yml` builds, tests, publishes to the JetBrains Marketplace (`PUBLISH_TOKEN` secret) and creates the GitHub release.
