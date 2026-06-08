package com.o2.commit.cli

/** CLI 调用失败异常 */
class CliException(message: String) : RuntimeException(message)

/**
 * 本地 AI CLI 适配器。
 *
 * 隔离 claude / codex 在命令行参数与输出格式上的差异:
 * prompt 统一经 stdin 传入,各实现只需声明固定参数并解析自己的输出。
 *
 * @author will
 * @since 2026-06-07
 */
interface CliProvider {

    /** 默认可执行名(当用户未配置绝对路径时,经登录 shell 的 PATH 查找) */
    val command: String

    /** 固定命令行参数(不含用户内容,用户内容走 stdin) */
    fun args(): List<String>

    /**
     * 从原始 stdout 解析出纯净的 commit message。
     * @throws CliException 解析失败
     */
    fun parse(stdout: String): String
}
