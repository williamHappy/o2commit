package com.o2.commit.git

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import git4idea.config.GitExecutableManager
import java.nio.charset.StandardCharsets

/**
 * 变更上下文:最近提交(风格参考)、变更摘要、unified diff。
 */
data class DiffContext(val recentCommits: String, val stat: String, val diff: String)

/**
 * 从用户在提交面板勾选的 [Change] 集合生成高质量上下文。
 *
 * 与 idea-claude-code-gui 的伪 diff 不同,这里调用真实 git 得到标准 unified diff:
 * - 已跟踪文件(beforeRevision != null):`git diff HEAD -- <paths>` 覆盖 staged+unstaged;
 * - 纯新增文件(beforeRevision == null):`git diff --no-index /dev/null <path>` 兜底,二者路径互斥不重复。
 * 并额外采集 `git log` 作为风格参考、`git diff --stat` 作为摘要(借鉴 EnsoAI)。
 *
 * @author will
 * @since 2026-06-07
 */
class DiffCollector(private val project: Project) {

    private val gitPath: String by lazy { GitExecutableManager.getInstance().getPathToGit(project) }
    private val workDir: String = project.basePath ?: "."

    /**
     * @param changes 用户勾选的变更
     * @param maxDiffChars diff 截断上限
     * @param recentCommitCount 风格参考的提交条数
     */
    fun collect(
        changes: Collection<Change>,
        unversionedPaths: List<String>,
        maxDiffChars: Int,
        recentCommitCount: Int,
    ): DiffContext {
        val existingPaths = mutableListOf<String>()
        val addedPaths = mutableListOf<String>()
        for (change in changes) {
            val path = ChangesUtil.getFilePath(change).path
            if (change.beforeRevision != null) existingPaths.add(path) else addedPaths.add(path)
        }
        // 未跟踪的新文件统一按新增处理(用 --no-index 与空文件对比)
        addedPaths.addAll(unversionedPaths)

        val recentCommits = runGit(listOf("--no-pager", "log", "-n", recentCommitCount.toString(), "--format=%s"))

        val stat = if (existingPaths.isEmpty()) ""
        else runGit(listOf("--no-pager", "diff", "HEAD", "--stat", "--") + existingPaths)

        val diffBuilder = StringBuilder()
        if (existingPaths.isNotEmpty()) {
            diffBuilder.append(runGit(listOf("--no-pager", "diff", "HEAD", "--") + existingPaths))
        }
        for (path in addedPaths) {
            // 新文件与空文件对比;有差异时 git 返回退出码 1,需忽略
            val added = runGit(
                listOf("--no-pager", "diff", "--no-index", "--", "/dev/null", path),
                ignoreExitCode = true,
            )
            if (added.isNotBlank()) diffBuilder.append('\n').append(added)
        }

        var diff = diffBuilder.toString().trim()
        if (diff.length > maxDiffChars) {
            diff = diff.substring(0, maxDiffChars) + "\n... (diff 已截断)"
        }
        return DiffContext(recentCommits.trim(), stat.trim(), diff)
    }

    private fun runGit(args: List<String>, ignoreExitCode: Boolean = false): String {
        return try {
            val cmd = GeneralCommandLine(listOf(gitPath) + args)
                .withWorkDirectory(workDir)
                .withCharset(StandardCharsets.UTF_8)
            val output = CapturingProcessHandler(cmd).runProcess(5000)
            if (output.exitCode != 0 && !ignoreExitCode) "" else output.stdout
        } catch (e: Exception) {
            LOG.warn("git 命令执行失败: ${args.joinToString(" ")}", e)
            ""
        }
    }

    companion object {
        private val LOG = logger<DiffCollector>()
    }
}
