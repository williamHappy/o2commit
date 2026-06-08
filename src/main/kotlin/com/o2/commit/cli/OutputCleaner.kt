package com.o2.commit.cli

/**
 * 清洗 CLI 输出,提取纯净的 commit message。
 *
 * 处理链:去 ANSI → 提取 <commit></commit> 标签内内容 → 去 markdown 代码围栏 → trim。
 * 借鉴 EnsoAI 的 stripAnsi/stripCodeFence 与 idea 的标签提取。
 *
 * @author will
 * @since 2026-06-07
 */
object OutputCleaner {

    private val ANSI_REGEX = Regex("\\[[0-9;]*[a-zA-Z]")
    private val COMMIT_TAG_REGEX = Regex("(?s)<commit>(.*?)</commit>")
    private val FENCE_FULL_REGEX = Regex("(?s)```\\w*\\s*[\\r\\n]+(.*?)[\\r\\n]+\\s*```\\s*$")
    private val FENCE_LEAD_REGEX = Regex("^```\\w*\\s*[\\r\\n]*")
    private val FENCE_TAIL_REGEX = Regex("[\\r\\n]*\\s*```\\s*$")

    fun clean(raw: String): String {
        val text = ANSI_REGEX.replace(raw, "").trim()

        // 优先提取 <commit> 标签内内容,可隔离模型多余的前言/思考
        COMMIT_TAG_REGEX.find(text)?.let { return it.groupValues[1].trim() }

        // 回退:去掉完整 markdown 围栏
        FENCE_FULL_REGEX.find(text)?.let { return it.groupValues[1].trim() }

        // 回退:去掉单侧残留围栏
        return text
            .replace(FENCE_LEAD_REGEX, "")
            .replace(FENCE_TAIL_REGEX, "")
            .trim()
    }
}
