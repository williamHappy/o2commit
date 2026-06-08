package com.o2.commit.cli

import com.google.gson.JsonParser

/**
 * claude CLI 适配器。
 *
 * 调用:`claude -p --output-format json`,prompt 走 stdin。
 * 输出为单个 JSON 对象 {"type":"result","subtype":"success","result":"..."}
 * (兼容旧版数组格式 [{init},{assistant},{result}])。
 *
 * @author will
 * @since 2026-06-07
 */
object ClaudeProvider : CliProvider {

    override val command: String = "claude"

    override fun args(): List<String> = listOf("-p", "--output-format", "json")

    override fun parse(stdout: String): String {
        val resultText = try {
            extractResult(stdout)
        } catch (e: Exception) {
            throw CliException("无法解析 Claude 输出: ${e.message}")
        }
        return OutputCleaner.clean(resultText)
    }

    private fun extractResult(stdout: String): String {
        val element = JsonParser.parseString(stdout.trim())

        // 旧版:JSON 数组,取 type=result && subtype=success 的项
        if (element.isJsonArray) {
            val resultObj = element.asJsonArray
                .map { it.asJsonObject }
                .firstOrNull { it.get("type")?.asString == "result" }
                ?: throw CliException("Claude 输出中未找到 result")
            return resultObj.get("result")?.asString
                ?: throw CliException(resultObj.get("error")?.asString ?: "Claude 返回空结果")
        }

        // 新版:单个 JSON 对象
        val obj = element.asJsonObject
        if (obj.get("type")?.asString == "result") {
            return obj.get("result")?.asString
                ?: throw CliException(obj.get("error")?.asString ?: "Claude 返回空结果")
        }
        throw CliException("Claude 输出格式无法识别")
    }
}
