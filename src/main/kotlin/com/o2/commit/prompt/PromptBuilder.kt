package com.o2.commit.prompt

import com.o2.commit.git.DiffContext

/**
 * 组装最终发送给 CLI 的 prompt。
 *
 * 采用 EnsoAI 风格的「单一可编辑模板 + 占位符」:模板存于设置,用户可整体改写;
 * 运行时把 {language} / {recent_commits} / {staged_stat} / {staged_diff} 替换为真实上下文。
 *
 * @author will
 * @since 2026-06-07
 */
object PromptBuilder {

    /**
     * 默认模板,也是设置页「自定义提示词」的初始值。
     * 关键约束(改写时建议保留):① 禁止调用任何工具(实测 codex 会跑无关 skill 流程,既慢又费);
     * ② 必须用 <commit></commit> 包裹,便于稳定提取。
     */
    val DEFAULT_TEMPLATE = """
你是一名资深工程师,负责生成规范、高质量的 Git commit message。

## 重要约束
- 你**无法也无需调用任何工具**:下方已提供生成所需的全部信息,请直接据此作答。
- 只输出提交信息本身、无需任何解释,**必须用 `<commit></commit>` 标签包裹,标签外不要有任何内容**。
- 不要添加签名、`Generated with`、`Co-Authored-By` 等元信息,不使用 emoji。

## 规范(Conventional Commits)
- 格式:`<type>(<scope>): <description>`,必要时空一行后接 body,再空一行接 footer。
- type:feat 新功能 / fix 修复 / docs 文档 / style 格式 / refactor 重构 / perf 性能 / test 测试 / chore 杂务 / ci CI / build 构建 / revert 回滚。
- scope 可选,表示影响范围(模块 / 目录 / 功能)。
- description:使用「{language}」,祈使、现在时("add" 而非 "added"),首字母小写,不超过 72 字符,结尾不加句号。
- 单一改动用单行主题即可;变更较复杂或需说明动机时,空一行后用要点列出 body(讲"是什么 / 为什么",而非"如何")。

## 最近提交(风格参考)
{recent_commits}

## 变更摘要
{staged_stat}

## 变更详情(diff)
{staged_diff}
""".trim()

    /**
     * 用真实上下文渲染模板。模板为空时回退到 [DEFAULT_TEMPLATE]。
     *
     * @param template 用户自定义模板(含占位符)
     * @param language 输出语言(如「中文」「English」)
     * @param context 变更上下文(风格参考 / 摘要 / diff)
     */
    fun build(template: String, language: String, context: DiffContext): String =
        template.ifBlank { DEFAULT_TEMPLATE }
            .replace("{language}", language)
            .replace("{recent_commits}", context.recentCommits.ifBlank { "(无历史提交)" })
            .replace("{staged_stat}", context.stat.ifBlank { "(无摘要)" })
            .replace("{staged_diff}", context.diff)
}
