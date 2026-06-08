package com.o2.commit.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.components.JBTextArea
import com.o2.commit.prompt.PromptBuilder

/**
 * 设置页:Settings → Tools → o2commit。
 *
 * 应用级配置(applicationConfigurable),用 IntelliJ 原生 Kotlin UI DSL v2 实现。
 *
 * @author will
 * @since 2026-06-07
 */
class O2Configurable : BoundConfigurable("o2commit") {

    private val appState = O2Settings.getInstance().state

    override fun createPanel(): DialogPanel = panel {
        row {
            checkBox("在提交面板显示「生成提交信息」按钮")
                .bindSelected(appState::commitEnabled)
        }

        buttonsGroup("使用的本地 CLI") {
            row {
                radioButton("Claude", O2Settings.CliType.CLAUDE)
                radioButton("Codex", O2Settings.CliType.CODEX)
            }
        }.bind(
            MutableProperty({ appState.provider }, { appState.provider = it }),
            O2Settings.CliType::class.java,
        )

        buttonsGroup("输出语言") {
            row {
                radioButton("中文", "中文")
                radioButton("English", "English")
            }
        }.bind(
            MutableProperty({ appState.commitLanguage }, { appState.commitLanguage = it }),
            String::class.java,
        )

        row("CLI 路径(可选):") {
            textField()
                .bindText(appState::cliPathOverride)
                .columns(40)
                .comment("留空则经登录 shell 在 PATH 中查找;若 IDE 找不到命令,请填绝对路径,如 /Users/you/.local/bin/claude")
        }

        group("生成参数") {
            row("超时(秒):") {
                intTextField().bindIntText(appState::timeoutSeconds).columns(6)
            }
            row("diff 最大字符数:") {
                intTextField().bindIntText(appState::maxDiffChars).columns(8)
            }
            row("风格参考提交数:") {
                intTextField().bindIntText(appState::recentCommitCount).columns(4)
            }
        }

        group("自定义提示词") {
            lateinit var area: JBTextArea
            row {
                area = textArea()
                    .bindText(appState::promptTemplate)
                    .align(AlignX.FILL)
                    .applyToComponent { rows = 16 }
                    .comment("可用占位符:{language} 输出语言、{recent_commits} 风格参考、{staged_stat} 变更摘要、{staged_diff} 变更详情")
                    .component
            }
            row {
                button("恢复默认提示词") { area.text = PromptBuilder.DEFAULT_TEMPLATE }
            }
        }
    }
}
