package com.o2.commit.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.CommitMessageI
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.o2.commit.cli.ClaudeProvider
import com.o2.commit.cli.CodexProvider
import com.o2.commit.cli.ShellExecutor
import com.o2.commit.git.DiffCollector
import com.o2.commit.prompt.PromptBuilder
import com.o2.commit.settings.O2Settings
import com.o2.commit.util.O2Notifier

/**
 * 提交面板「生成提交信息」入口。
 *
 * 流程:取勾选变更 → 占位「生成中」→ 后台采集 diff、拼 prompt、调 CLI → 写回提交框。
 * VCS 接入(取提交框 / 取勾选变更的多级回退)借鉴 idea-claude-code-gui。
 *
 * @author will
 * @since 2026-06-07
 */
class GenerateCommitMessageAction : AnAction(), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val enabled = e.project != null && O2Settings.getInstance().state.commitEnabled
        e.presentation.isEnabledAndVisible = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val panel = getCommitPanel(e)
        if (panel == null) {
            Messages.showWarningDialog(project, "无法访问提交信息输入框", "o2commit")
            return
        }

        val changes = getIncludedChanges(e, project)
        val unversioned = getIncludedUnversioned(e, project)
        if (changes.isEmpty() && unversioned.isEmpty()) {
            Messages.showWarningDialog(project, "请先在提交面板勾选要提交的文件", "o2commit")
            return
        }

        panel.setCommitMessage("⏳ 正在生成提交信息…")

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val settings = O2Settings.getInstance().state

                val context = DiffCollector(project)
                    .collect(changes, unversioned, settings.maxDiffChars, settings.recentCommitCount)
                if (context.diff.isBlank()) {
                    throw IllegalStateException("没有可用于生成的变更内容(纯未跟踪文件请先加入版本控制)")
                }

                val prompt = PromptBuilder.build(settings.promptTemplate, settings.commitLanguage, context)
                val provider = when (settings.provider) {
                    O2Settings.CliType.CLAUDE -> ClaudeProvider
                    O2Settings.CliType.CODEX -> CodexProvider
                }
                val exe = settings.cliPathOverride.ifBlank { provider.command }

                val rawOutput = ShellExecutor.execute(
                    exe = exe,
                    args = provider.args(),
                    stdin = prompt,
                    workDir = project.basePath ?: ".",
                    timeoutSeconds = settings.timeoutSeconds,
                )
                val message = provider.parse(rawOutput)

                ApplicationManager.getApplication().invokeLater {
                    panel.setCommitMessage(message)
                    O2Notifier.info(project, "提交信息已生成")
                }
            } catch (ex: Exception) {
                LOG.warn("生成提交信息失败", ex)
                ApplicationManager.getApplication().invokeLater {
                    panel.setCommitMessage("")
                    Messages.showErrorDialog(project, ex.message ?: "未知错误", "o2commit 生成失败")
                }
            }
        }
    }

    /** 取提交框句柄:新版 COMMIT_WORKFLOW_HANDLER → 旧版 COMMIT_MESSAGE_CONTROL */
    private fun getCommitPanel(e: AnActionEvent): CommitMessageI? {
        (e.getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER) as? CommitMessageI)?.let { return it }
        return e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL)
    }

    /**
     * 取用户勾选的变更,四级回退:
     * 1. COMMIT_WORKFLOW_HANDLER.ui.getIncludedChanges()(反射,只取勾选文件)
     * 2. CheckinProjectPanel.getSelectedChanges()
     * 3. VcsDataKeys.CHANGES
     * 4. ChangeListManager.getAllChanges()
     *
     * 关键:前两级是「已勾选」的**权威**来源,返回非 null(哪怕空集合)即代表用户的真实勾选,
     * 必须直接返回——空集合意味着「没勾选」,绝不能再回退到「全部变更」。
     * 只有反射不可用(返回 null,旧版 IDE)时才继续向下回退。
     */
    private fun getIncludedChanges(e: AnActionEvent, project: Project): Collection<Change> {
        e.getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER)?.let { handler ->
            includedChangesViaReflection(handler)?.let { return it }
        }

        (e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as? CheckinProjectPanel)
            ?.let { return it.selectedChanges }

        e.getData(VcsDataKeys.CHANGES)?.takeIf { it.isNotEmpty() }?.let { return it.toList() }

        return ChangeListManager.getInstance(project).allChanges
    }

    /**
     * 取用户勾选的未跟踪文件路径。
     * 全新仓库里所有文件都是 unversioned,不会出现在 [getIncludedChanges] 中,必须单独处理。
     * 反射 ui.getIncludedUnversionedFiles();失败则兜底为仓库内全部未跟踪文件。
     */
    private fun getIncludedUnversioned(e: AnActionEvent, project: Project): List<String> {
        e.getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER)?.let { handler ->
            includedUnversionedViaReflection(handler)?.let { return it.map(FilePath::getPath) }
        }
        return ChangeListManager.getInstance(project).unversionedFilesPaths.map(FilePath::getPath)
    }

    /** 经反射调用 handler.getUi().getIncludedUnversionedFiles() */
    private fun includedUnversionedViaReflection(handler: Any): Collection<FilePath>? {
        return try {
            val ui = handler.javaClass.getMethod("getUi").invoke(handler) ?: return null
            val result = ui.javaClass.getMethod("getIncludedUnversionedFiles").invoke(ui)
            (result as? Collection<*>)?.filterIsInstance<FilePath>()
        } catch (e: NoSuchMethodException) {
            null
        } catch (e: Exception) {
            LOG.debug("反射获取 includedUnversionedFiles 失败: ${e.message}")
            null
        }
    }

    /**
     * 经反射调用 handler.getUi().getIncludedChanges()。
     * 该 API 仅新版 IDEA 提供,反射可在旧版优雅降级到其他回退。
     */
    private fun includedChangesViaReflection(handler: Any): Collection<Change>? {
        return try {
            val ui = handler.javaClass.getMethod("getUi").invoke(handler) ?: return null
            val result = ui.javaClass.getMethod("getIncludedChanges").invoke(ui)
            (result as? Collection<*>)?.filterIsInstance<Change>()
        } catch (e: NoSuchMethodException) {
            null
        } catch (e: Exception) {
            LOG.debug("反射获取 includedChanges 失败: ${e.message}")
            null
        }
    }

    companion object {
        private val LOG = logger<GenerateCommitMessageAction>()
    }
}
