package com.o2.commit.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.o2.commit.prompt.PromptBuilder

/**
 * 应用级设置:CLI 选择、可选路径覆盖、全局附加 prompt、超时与截断参数。
 *
 * 采用平台 PersistentStateComponent 持久化,存储在 o2commit.xml。
 *
 * @author will
 * @since 2026-06-07
 */
@Service(Service.Level.APP)
@State(name = "O2CommitSettings", storages = [Storage("o2commit.xml")])
class O2Settings : PersistentStateComponent<O2Settings.State> {

    /** 可用的本地 AI CLI 类型 */
    enum class CliType { CLAUDE, CODEX }

    /** 持久化状态容器,字段需为可变以供 XML 序列化 */
    class State {
        /** 是否在提交面板显示生成按钮 */
        var commitEnabled: Boolean = true

        /** 使用哪个本地 CLI */
        var provider: CliType = CliType.CLAUDE

        /** CLI 绝对路径覆盖;留空则走登录 shell 的 PATH 自动查找 */
        var cliPathOverride: String = ""

        /** 生成 commit message 使用的语言 */
        var commitLanguage: String = "中文"

        /** 自定义提示词模板(含 {language}/{recent_commits}/{staged_stat}/{staged_diff} 占位符) */
        var promptTemplate: String = PromptBuilder.DEFAULT_TEMPLATE

        /** 进程超时秒数 */
        var timeoutSeconds: Int = 60

        /** 喂给模型的 diff 最大字符数,超出截断 */
        var maxDiffChars: Int = 16000

        /** 作为风格参考的最近提交条数 */
        var recentCommitCount: Int = 5
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(newState: State) {
        state = newState
    }

    companion object {
        fun getInstance(): O2Settings =
            ApplicationManager.getApplication().getService(O2Settings::class.java)
    }
}
