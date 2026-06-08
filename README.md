# o2commit

使用本地已登录的 **claude** / **codex** CLI 自动生成 Git 提交信息的 JetBrains 插件。

## 为什么是它

市面同类插件大多直连 OpenAI/Claude API,需要你填 API Key、配代理、关心计费。
o2commit 不直连任何 LLM API,而是**复用你机器上已装好并登录的 AI CLI**:

```
读取勾选的变更 → 拼 prompt → 调本地 claude/codex 进程 → 把结果填回提交框
```

零密钥、零网络配置。

## 使用

1. 在 IDE 设置中安装插件(Settings → Plugins → 从磁盘安装 `build/distributions/o2commit-*.zip`)。
2. 确保本地已安装并登录 `claude` 或 `codex` CLI。
3. 在提交面板勾选要提交的文件,点击提交框旁的「✨ 生成提交信息」按钮。

## 配置

Settings → Tools → o2commit:

| 配置 | 说明 |
|---|---|
| 使用的 CLI | Claude / Codex |
| CLI 路径 | 可选;IDE 找不到命令时填绝对路径(如 `~/.local/bin/claude`) |
| 超时 / diff 上限 / 风格参考提交数 | 生成参数 |
| 自定义提示词 | 完整可编辑的提示词模板,默认内置 Conventional Commits 规范,支持 `{language}`/`{recent_commits}`/`{staged_stat}`/`{staged_diff}` 占位符,可点「恢复默认」还原 |

## 生成质量

- 喂给模型的上下文包含:最近提交(风格参考)、`--stat` 摘要、真实 `git diff` unified diff。
- 内置 Conventional Commits 规范,输出用 `<commit></commit>` 标签包裹以稳定提取。

## 构建

```bash
./gradlew buildPlugin   # 产物在 build/distributions/
./gradlew runIde        # 启动沙箱 IDE 调试
```

## 兼容性

JetBrains IDE 2023.3+(build 233+),支持 macOS / Linux / Windows。
- macOS / Linux:经登录 shell 自动解析完整 PATH,无需手填 CLI 路径。
- Windows:经 `cmd.exe` 调用,自动定位 npm 安装的 `claude.cmd` / `codex.cmd`;若 IDE 找不到命令,在设置里填 CLI 绝对路径即可。
