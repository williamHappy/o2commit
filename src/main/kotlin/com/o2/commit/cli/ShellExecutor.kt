package com.o2.commit.cli

import com.intellij.openapi.diagnostic.logger
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * 执行本地 AI CLI 进程。
 *
 * 跨平台差异:
 * - macOS/Linux:GUI 启动的 IDE 进程 PATH 极简(无 ~/.local/bin、~/.nvm/.../bin),
 *   直接调用会 command not found。解法(与 uTools/Electron 应用的 fix-path/shell-env 同思路):
 *   用**交互式登录 shell**(`zsh -ilc`)解析出用户的完整 PATH,再注入子进程环境;
 *   命令经 `shell -c` 执行。
 * - Windows:GUI 进程默认继承用户完整 PATH(来自注册表),无需解析;命令经 `cmd.exe /c`
 *   执行,由 cmd 按 PATHEXT 自动定位 npm 安装的 `claude.cmd` / `codex.cmd`。
 *
 * @author will
 * @since 2026-06-07
 */
object ShellExecutor {

    private val LOG = logger<ShellExecutor>()

    /** 是否运行在 Windows */
    private val isWindows: Boolean =
        System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** 用户完整 PATH(经交互式登录 shell 解析,含 .zshrc 里的 nvm/.local/bin 等),懒加载并缓存。仅 Unix 使用 */
    private val userPath: String by lazy { resolveUserPath() }

    /**
     * 执行并返回原始 stdout。
     *
     * @param exe 可执行命令(命令名或绝对路径;命令名时靠注入的完整 PATH 查找)
     * @param args 固定参数
     * @param stdin 写入子进程标准输入的内容(即完整 prompt)
     * @param workDir 工作目录(项目根)
     * @param timeoutSeconds 超时秒数
     * @throws CliException 超时或非 0 退出
     */
    fun execute(
        exe: String,
        args: List<String>,
        stdin: String,
        workDir: String,
        timeoutSeconds: Int,
    ): String {
        val quotedExe = if (exe.contains(' ')) "\"$exe\"" else exe
        val fullCommand = (listOf(quotedExe) + args).joinToString(" ")

        LOG.info("执行: $fullCommand (cwd=$workDir)")

        val process = buildProcess(fullCommand, workDir).start()
        // 先启动异步读流,再写 stdin,避免大输入/输出时管道缓冲死锁
        val stdoutFuture = readAsync(process.inputStream)
        val stderrFuture = readAsync(process.errorStream)

        process.outputStream.use { it.write(stdin.toByteArray(StandardCharsets.UTF_8)) }

        val finished = process.waitFor(timeoutSeconds.toLong(), TimeUnit.SECONDS)
        if (!finished) {
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
            throw CliException("CLI 执行超时($timeoutSeconds 秒)")
        }

        val stdout = stdoutFuture.get()
        val stderr = stderrFuture.get()
        if (process.exitValue() != 0) {
            // 同时带上 stderr 与 stdout —— CLI 失败原因常常打在 stdout
            val detail = sequenceOf(stderr.trim(), stdout.trim())
                .filter { it.isNotBlank() }
                .joinToString("\n")
                .take(800)
            throw CliException(detail.ifBlank { "CLI 退出码 ${process.exitValue()}" })
        }
        return stdout
    }

    /**
     * 按平台构造子进程:Windows 走 `cmd.exe /c`(继承环境 PATH);
     * Unix 走 `shell -c`,并注入解析得到的完整 PATH。
     */
    private fun buildProcess(fullCommand: String, workDir: String): ProcessBuilder {
        val pb = if (isWindows) {
            ProcessBuilder("cmd.exe", "/c", fullCommand)
        } else {
            ProcessBuilder(loginShell(), "-c", fullCommand).apply {
                if (userPath.isNotBlank()) environment()["PATH"] = userPath
            }
        }
        return pb.directory(File(workDir))
    }

    private fun loginShell(): String =
        System.getenv("SHELL")?.takeIf { it.isNotBlank() } ?: "/bin/zsh"

    /**
     * 解析用户的完整 PATH:跑一次交互式登录 shell(加载 .zshrc 等)打印 $PATH。
     * 交互式 shell 可能夹带其它输出,故取包含路径分隔符的最长一行作为 PATH。
     */
    private fun resolveUserPath(): String {
        return try {
            val process = ProcessBuilder(loginShell(), "-ilc", "printf '%s' \"\$PATH\"").start()
            process.outputStream.close()
            val out = process.inputStream.readBytes().toString(StandardCharsets.UTF_8)
            process.errorStream.readBytes() // 排空,避免阻塞
            process.waitFor(10, TimeUnit.SECONDS)
            val path = out.lineSequence()
                .map { it.trim() }
                .filter { it.contains('/') && it.contains(':') }
                .maxByOrNull { it.length }
            LOG.info("解析到用户 PATH: ${path?.take(200)}")
            path ?: (System.getenv("PATH") ?: "")
        } catch (e: Exception) {
            LOG.warn("解析用户 PATH 失败,回退默认 PATH", e)
            System.getenv("PATH") ?: ""
        }
    }

    private fun readAsync(stream: InputStream): CompletableFuture<String> =
        CompletableFuture.supplyAsync {
            stream.readBytes().toString(StandardCharsets.UTF_8)
        }
}
