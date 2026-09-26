# 模型上下文窗口精确清单

> 数据源：`shared/providers/model-context-table.ts`（桌面端位于 `electron/`，Web 端位于 `server/host-core/` 目录下）。
> 每个条目均于 **2026-08-18** 逐个核对官方文档，匹配采用精确 ID 查表（无正则），未列入清单的模型走保守 fallback（200K，档位仅 128K/200K）。

## 档位规则

- 标准档位：`128K(131,072)` / `200K` / `256K(262,144)` / `400K` / `512K(524,288)` / `1M(1,048,576)` / `2M(2,097,152)`。
- 每个模型的可用档位 = 全部 ≤ 其最大上下文的标准档 + 特殊档（如 GPT-5.4 的 272K、GPT-5.6 Sol 的 1.5M）。
- 支持 1M 的模型必然可选 128K/200K/256K/400K/512K 等小档（省 token）；只支持 200K 的模型只出现 128K/200K 档。

## 模型清单

### 阿里 Qwen（来源：阿里云百炼官方文档 help.aliyun.com/zh/model-studio）

| 模型 ID | 上下文长度（官方精确值） | 备注 |
|---|---|---|
| qwen3.8-max | 1,000,000 | 最大输入 991,808 / 思考模式 983,616 |
| qwen3.7-max | 1,000,000 | |
| qwen3.7-plus | 1,000,000 | |
| qwen3.7-flash | 1,000,000 | |
| qwen3.6-plus | 1,000,000 | |
| qwen3.6-flash | 1,000,000 | |
| qwen3.5-plus | 1,000,000 | |
| qwen3.5-omni-plus | 262,144 | 最大输入 196,608 |
| qwen3.5-omni-flash | 262,144 | |
| qwen-plus | 1,000,000 | 2025-07-28 起 1M |
| qwen-max | 32,768 | 千问 2.5 系列 |
| qwen-turbo | 131,072 | Qwen3 系列 Turbo |
| qwen-long | 10,000,000 | HTTP 直连仅 1M，超 1M 需文件方式 |

### DeepSeek（来源：官方 API 文档 api-docs.deepseek.com）

| 模型 ID | 最大上下文 | 备注 |
|---|---|---|
| deepseek-v4-pro / deepseek-v4-pro-0813 | 1M | 最大输出 384K |
| deepseek-v4-flash / deepseek-v4-flash-0731 | 1M | 最大输出 384K |
| deepseek-chat | 1M | 端点现指向 V4 系列 |
| deepseek-reasoner | 1M | 端点现指向 V4 系列 |
| deepseek-v3.2 | 128K | |

### 智谱（来源：官方文档 docs.bigmodel.cn）

| 模型 ID | 最大上下文 | 备注 |
|---|---|---|
| glm-5.3 | 1M | 最大输出 128K，2026-08 发布 |
| glm-5.2 | 1M | 最大输出 128K |
| glm-4.7 | 200K | |
| glm-4.6 | 200K | |
| glm-4.5 / glm-4.5-air | 128K | |

### MiniMax（来源：官网 minimaxi.com）

| 模型 ID | 最大上下文 | 备注 |
|---|---|---|
| minimax-m3 | 1M | 官方保证至少 512K 可用，2026-06-01 发布 |
| minimax-m2.7 | 200K | |
| minimax-m2 | 200K | |

### OpenAI（来源：官方 openai.com / 平台规格页）

| 模型 ID | 最大上下文 | 备注 |
|---|---|---|
| gpt-5.6 / gpt-5.6-sol | 1.5M | Sol 标称 1.5M，API 逐步开放 |
| gpt-5.6-terra | 1.05M | |
| gpt-5.6-luna | 1.05M | |
| gpt-5.5 | 1.05M | |
| gpt-5.4 | 1M | 标准 272K，超出部分双倍计费 |
| gpt-5.2 / gpt-5.1 / gpt-5 | 400K | |
| gpt-4.1 / gpt-4.1-mini / gpt-4.1-nano | 1M（1,047,576） | |
| gpt-4o / gpt-4o-mini / o1 / o3 / o4-mini | 200K | |

### Anthropic（来源：官方 anthropic.com）

| 模型 ID | 最大上下文 | 备注 |
|---|---|---|
| claude-sonnet-5 | 1M | 最大输出 128K，2026-06 发布 |
| claude-opus-4-8 / claude-opus-4.8 | 1M | 最大输出 128K，2026-05-28 发布 |
| claude-opus-4.7 / claude-sonnet-4.7 | 1M | |
| claude-opus-4.6 / claude-sonnet-4.6 | 1M | 2026-03 全面开放 |
| claude-opus-4.5 / claude-sonnet-4.5 / claude-haiku-4.5 | 200K | |
| claude-3-7-sonnet / claude-3-5-sonnet / claude-3-5-haiku | 200K | |

### Google（来源：官方 ai.google.dev / cloud.google.com）

| 模型 ID | 最大上下文 | 备注 |
|---|---|---|
| gemini-3.5-flash | 1M | 2026-07 上线 |
| gemini-3-pro / gemini-3-flash | 1M | |
| gemini-2.5-pro / gemini-2.5-flash / gemini-2.5-flash-lite | 1M | |

### xAI（来源：官方文档 docs.x.ai）

| 模型 ID | 最大上下文 | 备注 |
|---|---|---|
| grok-4.6 | 500K | 2026-08 发布 |
| grok-4.5 | 500K | |
| grok-4.1-fast / grok-4-fast | 2M | |
| grok-4 | 2M | |
| grok-4.1 | 256K | 2025-11-17 发布 |

### Moonshot（来源：Kimi 平台官方文档 platform.kimi.com）

| 模型 ID | 最大上下文 | 备注 |
|---|---|---|
| kimi-k3 | 1M | 2026-07 发布，2.8T 参数 |
| kimi-k2.6 | 256K | |
| kimi-k2.5 | 256K | |

## 不收录清单（无法 100% 确认，走保守 fallback 200K）

- qwen3.5-max（存在性未确认）
- qwen2.5 系列（开源 128K/1M 变体混杂，口径不一）
- gemini-3.5-pro（截至 2026-08-12 仍未公开发布，传闻 2M）
- grok-4.2（公开信息不足）
- MiniMax M1（已停产，官方口径未统一）
