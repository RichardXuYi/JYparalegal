# 模型切换 & Gateway 连接弹窗优化方案

> 状态：已被 [`切模型免重启方案-2026-09-25.md`](切模型免重启方案-2026-09-25.md) 取代（2026-09-25 按 OpenClaw **2026.9.6** 修订）。
> 本文方案 A（删掉 reload、继续直写 `openclaw.json`）和方案 C（`x-openclaw-model` / `chat.send.model`）不要实施。本机 `D:\Projects\openclaw` 是 2026.9.6：`agents.list` 会迁到 `agents.entries`，之后再写的 `list` 被删掉；`SIGUSR1` 不再是热重载。细节以选定方案为准。
>
> 问题：每次 Studio 切换模型 / Gateway 连接时弹出全屏阻断遮罩，用户体验差。
> 根因：旧版 OpenClaw 不支持进程内热加载，Studio 只能通过重启 Gateway 来应用配置变更。

---

## 现状问题链路

```
用户切换模型
  → ChatInput.handleSelectModel()
    → beginModelSwitch(label)            // 弹窗1: "正在切换模型…" 全屏遮罩
    → updateAgentModel(agentId, modelRef)
      → hostApi.agents.updateModel()
        → agents-api.ts: updateModel()
          → 写 agent 配置 + openclaw.json
          → syncAgentModelOverrideToRuntime()
          → scheduleGatewayReload()      // 触发 Gateway reload
            → gatewayManager.debouncedReload()
              → reload()
                → Windows: SIGUSR1 不支持 → fallback restart()  // 弹窗2: "正在重新连接…"
                → macOS/Linux: SIGUSR1 → 可能也 fallback restart
    → endModelSwitch()                   // 关闭弹窗1
```

**两条弹窗链路叠加：**
1. `ConnectionStatusModal` 的 `model` 模式 —— `beginModelSwitch`/`endModelSwitch` 触发
2. `ConnectionStatusModal` 的 `reconnecting` 模式 —— Gateway 重启触发

---

## 方案 A（推荐，改动最小）：移除模型切换的强制 reload + 轻量化弹窗

### 核心思路

1. OpenClaw Gateway 的 hybrid 模式已内置文件监听，会自动检测并应用 `openclaw.json` 的变更
2. Studio 不需要手动触发 reload/restart —— 写配置后等 Gateway 自行热加载即可
3. 模型切换是毫秒级的 API 调用，不需要全屏阻断遮罩

### 涉及文件

| 文件 | 改动 |
|------|------|
| `studio-web/server/host-core/services/agents-api.ts` | 删除 `updateModel` 中的 `scheduleGatewayReload()` |
| `studio-frontend/electron/services/agents-api.ts` | 同上 |
| `studio-web/src/pages/Chat/ChatInput.tsx` | 移除 `beginModelSwitch`/`endModelSwitch`，改用轻量 spinner |
| `studio-frontend/src/pages/Chat/ChatInput.tsx` | 同上 |
| `studio-web/src/components/common/ConnectionStatusModal.tsx` | 可选：移除 `model` reason 分支 |
| `studio-frontend/src/components/common/ConnectionStatusModal.tsx` | 同上 |
| `studio-web/src/stores/connection-status.ts` | 可选：移除 modelSwitch 相关 state |
| `studio-frontend/src/stores/connection-status.ts` | 同上 |
| `studio-web/src/lib/connection-status.ts` | 可选：移除 `modelSwitchActive` 参数 |
| `studio-frontend/src/lib/connection-status.ts` | 同上 |

### 具体改动

#### 1. `agents-api.ts` — 移除 `scheduleGatewayReload`

```diff
  updateModel: async (payload) => {
    const agentId = requireString(payload, 'id');
    const modelRef = isRecord(payload) && typeof payload.modelRef === 'string' ? payload.modelRef : null;
    const snapshot = await updateAgentModel(agentId, modelRef);
    try {
      await syncAllProviderAuthToRuntime();
      await syncAgentModelOverrideToRuntime(agentId);
    } catch (syncError) {
      console.warn('[agents] Failed to sync runtime after updating agent model:', syncError);
    }
-   // Agent model changes must be picked up by the running Gateway before
-   // the next send; otherwise the UI can show the new selection while the
-   // active runtime still answers with the previous model.
-   scheduleGatewayReload(ctx, 'update-agent-model');
+   // Gateway hybrid mode watches openclaw.json and applies model changes
+   // automatically in-process — no explicit reload/restart needed.
    return { success: true, ...snapshot };
  },
```

#### 2. `ChatInput.tsx` — 轻量化模型切换指示

模型切换改为内联 loading（模型选择器旁边显示小 spinner），不再阻断整个 UI：

```diff
  const handleSelectModel = useCallback(async (modelRef: string) => {
    if (!currentAgent || switchingModelRef) return;
    if (modelRef === effectiveModelRef) {
      setModelPickerOpen(false);
      textareaRef.current?.focus();
      return;
    }

    const previousModelRef = effectiveModelRef;
    const desiredOverride = modelRef === (defaultModelRef || '').trim() ? null : modelRef;
    const targetLabel = modelOptions.find((option) => option.modelRef === modelRef)?.label
      ?? formatModelRefLabel(modelRef);
    setSwitchingModelRef(modelRef);
    setOptimisticModelRef(modelRef);
    setModelPickerOpen(false);
-   beginModelSwitch(targetLabel);
    try {
      await updateAgentModel(currentAgent.id, desiredOverride);
    } catch (error) {
      setOptimisticModelRef(previousModelRef);
      toast.error(t('composer.modelSwitchFailed', { error: String(error) }));
    } finally {
      setSwitchingModelRef(null);
-     endModelSwitch();
      textareaRef.current?.focus();
    }
- }, [beginModelSwitch, currentAgent, defaultModelRef, effectiveModelRef, endModelSwitch, modelOptions, switchingModelRef, t, updateAgentModel]);
+ }, [currentAgent, defaultModelRef, effectiveModelRef, modelOptions, switchingModelRef, t, updateAgentModel]);
```

> **注意**：`switchingModelRef` 已经提供了轻量 loading 状态，UI 可以用它在模型选择器按钮上渲染 `Loader2` spinner（当前组件已有此状态变量，只需确认 UI 绑定）。

#### 3. 清理死代码（可选，后续 PR）

移除不再使用的 `beginModelSwitch`/`endModelSwitch`：
- `connection-status.ts` store 中的 `modelSwitch` state 及相关方法
- `ConnectionStatusModal.tsx` 中 `reason === 'model'` 分支
- `connection-status.ts` 中的 `modelSwitchActive` 参数

### 收益

- 模型切换零弹窗，静默完成
- Gateway 不会因模型切换而重启（Windows 用户受益最大）
- 改动 ~10 行，风险低

### 风险

- 如果 Gateway 的 hybrid 文件监听延迟较大（>2s），用户可能在切换后短暂看到旧模型响应
  - **缓解**：前端已有 `optimisticModelRef` 立即更新 UI，后端写文件也是同步的；Gateway 文件监听通常在 500ms 内生效

---

## 方案 B：Provider 级别配置变更也去掉强制 reload

### 核心思路

在方案 A 基础上，进一步清理 `provider-runtime-sync.ts` 中不必要的 `scheduleGatewayRefresh()` 调用。

### 需要重启的操作（保留 reload/restart）

| 操作 | 原因 |
|------|------|
| Provider **删除** | 需要从 Gateway 运行时移除 provider 注册，hybrid 文件监听可能不够 |
| **API Key 变更** / 认证模式切换 | 凭证变更可能导致存量连接鉴权失败 |
| Agent **创建/删除** | 涉及 agent registry 结构变更 |
| Channel 绑定/解绑 | 涉及 channel 路由变更 |

### 不需要重启的操作（交给 hybrid 文件监听）

| 操作 | 原因 |
|------|------|
| **模型切换**（Agent 级别） | 仅影响 `openclaw.json` 的 agent modelRef |
| **默认模型切换**（Provider 默认模型） | hybrid 可热加载 |
| **Fallback 模型变更** | hybrid 可热加载 |
| Provider baseUrl / headers 微调 | hybrid 可热加载 |

### 涉及文件

| 文件 | 函数 | 改动 |
|------|------|------|
| `studio-web/server/host-core/services/providers/provider-runtime-sync.ts` | `syncSavedProviderToRuntime` | 移除 `scheduleGatewayRefresh` |
| 同上 | `syncUpdatedProviderToRuntime` | 仅 API key 变更时保留 refresh |
| 同上 | `syncDefaultProviderToRuntime` | 移除 `scheduleGatewayRefresh` |
| `studio-web/server/host-core/services/agents-api.ts` | `create` | 保留 reload（agent 创建需要） |
| 同上 | `update`（重命名） | 移除 reload（仅名称变更） |
| `studio-frontend/electron/services/` | 对应文件 | 同上 |

---

## 方案 C：使用 `x-openclaw-model` header 按请求传模型

### 核心思路

不再将模型选择写入 `openclaw.json`，而是每次 `chat.send` RPC 调用时附带目标 model。这是 OpenClaw 新版 API 支持的**按请求级别模型覆盖**能力。

### 原理

OpenClaw Gateway 的 HTTP API 支持 `x-openclaw-model` header：
> Overrides the backend model for the selected agent. Shared-secret clients may apply it without restriction.

对应到 WebSocket JSON-RPC 的 `chat.send`，需确认参数中是否支持 `model` 字段（需要实测或查阅 OpenClaw RPC 协议文档）。

### 改造点

1. **前端**：`ChatInput.tsx` 切换模型时，仅更新本地状态（Zustand store），不写 `openclaw.json`
2. **`chat-api.ts`**：`chat.send` RPC 参数中添加 `model` 字段，值为当前 agent 选中的 modelRef
3. **模型选择持久化**：仍写入 agent config 文件（用于重启后恢复），但不触发 Gateway reload

### 伪代码

```typescript
// chat-api.ts — sendWithMedia
const rpcParams: Record<string, unknown> = {
  sessionKey,
  message,
  deliver: body.deliver ?? false,
  idempotencyKey,
  model: currentAgentModelRef,  // 新增：按请求指定模型
};
const result = await gatewayManager.rpc('chat.send', rpcParams, 120000);
```

### 收益

- 完全解耦模型选择和 Gateway 配置，切换模型零等待
- 多 Agent 并发场景下每个 Agent 可用不同模型，无冲突
- 无需依赖 Gateway 文件监听延迟

### 风险

- **需要确认** `chat.send` RPC 是否支持 `model` 参数（文档仅确认 HTTP header 方式）
- 如果 RPC 不支持，需改为 HTTP proxy 方式调用 Gateway（改动较大）
- `x-openclaw-model` header 在 identity 模式下需要 `operator.admin` 权限

---

## 实施建议

| 阶段 | 内容 | 优先级 |
|------|------|--------|
| **Phase 1** | 方案 A：移除 `scheduleGatewayReload` + 轻量化弹窗 | P0（立即） |
| **Phase 2** | 方案 B：清理 provider 级别的不必要 reload | P1（下个迭代） |
| **Phase 3** | 方案 C：调研 `chat.send` model 参数，决定是否进一步改造 | P2（观望 OpenClaw RPC 协议演进） |

### Phase 1 实施 checklist

- [ ] `studio-web/server/host-core/services/agents-api.ts` — 删除 L83 `scheduleGatewayReload`
- [ ] `studio-frontend/electron/services/agents-api.ts` — 同上
- [ ] `studio-web/src/pages/Chat/ChatInput.tsx` — 移除 `beginModelSwitch`/`endModelSwitch` 调用
- [ ] `studio-frontend/src/pages/Chat/ChatInput.tsx` — 同上
- [ ] 确认 `switchingModelRef` 在模型按钮上有 spinner 渲染
- [ ] 全流程测试：切换模型 → 发送消息 → 确认使用新模型
- [ ] 在 Windows 上验证：模型切换不触发 Gateway 重启
- [ ] `pnpm run typecheck:web && pnpm run typecheck:server && pnpm run lint:check`