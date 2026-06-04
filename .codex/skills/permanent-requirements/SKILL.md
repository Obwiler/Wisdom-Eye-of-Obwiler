---
name: permanent-requirements
description: "Stores the user's permanent behavioral requirements for Codex. When the user speaks with the pattern 以后…… (from now on...), treat this as an instruction to append or modify content in this skill file. Always read this skill at the start of every session. Use when the user says 以后…… or references permanent requirements."
---

# 永久要求

本文件存储用户对 Codex 的永久性行为要求。Codex 在每次对话中都要遵循此文件中的所有要求。

## 触发机制

当用户使用 "以后……" 句式发言时，该发言应被理解为对此文件的**追加或修改指令**，而非一次性回答。Codex 应：

1. 提取 "以后" 后面的具体要求
2. 将其追加（或修改对应条目）到下方要求列表中
3. 回复确认已记录
4. 后续所有对话中严格遵守

## 当前要求列表

| # | 要求 | 添加日期 |
|---|------|----------|
| 1 | 查询资料时，优先使用必应 (Bing) 搜索 | 2026-06-03 |

| 2 | PC工具配置（API Key/地址/模型等）必须本地持久化，应用重启后自动恢复 | 2026-06-03 |
| 3 | 每次完成涉及 APK 或 PC 工具代码修改的计划后，自动重新编译 APK 和重封 exe，保持分发包始终是最新版 | 2026-06-03 |