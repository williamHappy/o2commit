package com.o2.commit.cli

/**
 * codex CLI 适配器。
 *
 * 调用:`codex exec --sandbox read-only --skip-git-repo-check`,prompt 走 stdin。
 * 实测最终结果直接打到 stdout(运行日志/思考在 stderr),无需 --output-last-message。
 * - --sandbox read-only:确保只生成文本,绝不改动工作区;
 * - --skip-git-repo-check:避免 codex 对受信任目录的校验中断。
 *
 * @author will
 * @since 2026-06-07
 */
object CodexProvider : CliProvider {

    override val command: String = "codex"

    override fun args(): List<String> =
        listOf("exec", "--sandbox", "read-only", "--skip-git-repo-check")

    override fun parse(stdout: String): String {
        val result = OutputCleaner.clean(stdout)
        if (result.isBlank()) throw CliException("Codex 返回空结果")
        return result
    }
}
