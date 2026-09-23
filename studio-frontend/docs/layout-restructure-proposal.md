# GrandPoem Studio 前端布局重构方案 v3

**日期**: 2026-07-16  
**目标**: 将现有布局调整为"顶部栏 + 左侧栏 + 右上消息区 + 右下完整输入区"四区域布局

---

## 一、当前架构 vs 目标架构

### 当前布局
```
┌─────────────────────────────────────────────────┐
│ TitleBar (仅 Windows, h-10)                      │
├──────────────┬──────────────────────────────────┤
│              │                                  │
│   Sidebar    │        Chat 页面                  │
│   (导航+     │        ┌────────────────────┐    │
│    会话)     │        │  ChatToolbar       │    │
│              │        ├────────────────────┤    │
│              │        │                    │    │
│              │        │  Messages Area     │    │
│              │        │  (消息列表)         │    │
│              │        │                    │    │
│              │        ├────────────────────┤    │
│              │        │  ChatInput         │    │
│              │        │  - 文本输入         │    │
│              │        │  - 文件上传         │    │
│              │        │  - Skill 选择       │    │
│              │        │  - 模型选择         │    │
│              │        │  - Agent 切换       │    │
│              │        └────────────────────┘    │
└──────────────┴──────────────────────────────────┘
```

**问题**: 
- Chat 页面内部自己管理消息区和输入框，布局层级不清晰
- TitleBar 只在 Windows 有，macOS/Linux 没有顶部栏
- Logo/应用名在 Sidebar 顶部，状态指示在 Sidebar 底部，位置分散
- 输入框没有独立出来，无法跨页面复用

### 目标布局
```
┌─────────────────────────────────────────────────┐
│              TopBar (全宽顶部栏, h-12)            │
│  Logo + 应用名    |    全局搜索    |    状态+控制  │
├──────────────┬──────────────────────────────────┤
│              │                                  │
│   Sidebar    │        Main Content              │
│   (导航+     │        (消息内容区)               │
│    会话)     │        - Chat: 消息列表           │
│              │        - 其他页面: 各自内容        │
│              │        - 可滚动                   │
│              │                                  │
│              ├──────────────────────────────────┤
│              │        Bottom Panel              │
│              │        (完整输入区)               │
│              │        ┌────────────────────┐    │
│              │        │  附件预览区         │    │
│              │        │  (文件/图片)        │    │
│              │        ├────────────────────┤    │
│              │        │  文本输入框         │    │
│              │        ├────────────────────┤    │
│              │        │  工具栏             │    │
│              │        │  - 文件上传         │    │
│              │        │  - Skill 选择       │    │
│              │        │  - 模型选择         │    │
│              │        │  - Agent 切换       │    │
│              │        │  - 发送按钮         │    │
│              │        └────────────────────┘    │
└──────────────┴──────────────────────────────────┘
```

**优势**:
- 布局层级清晰：顶部栏 + 左侧栏 + 右上消息 + 右下输入
- 输入区完整保留所有功能（文件上传、Skill、模型、Agent）
- 输入区独立于页面内容，视觉上更清爽
- 所有页面都可以有完整的输入能力（或按需显示）
- 符合现代聊天应用惯例（Slack、Discord、Telegram Desktop）

---

## 二、区域功能定义

### 1. TopBar（顶部栏）
**位置**: 全宽，高度 48px  
**功能**:
- **左侧**: Logo + 应用名称（从 Sidebar 顶部移上来）
- **中间**: 全局搜索框（可选，Phase 2）
- **右侧**: 
  - Gateway 状态指示器（从 Sidebar 底部移上来）
  - 设置入口
  - 窗口控制按钮（Windows）

### 2. Sidebar（左侧栏）
**位置**: 左侧，宽度 240-320px（可折叠至 64px）  
**功能**: 保持现有功能
- 导航菜单（Models / Agents / Channels / Skills / Cron）
- 会话列表（按时间分组）
- 新建聊天按钮

**变化**: 
- 移除顶部的 Logo + 应用名称（移到 TopBar）
- 移除底部的 Gateway 重启指示器（移到 TopBar）

### 3. Main Content（右上 - 消息内容区）
**位置**: 右侧上部，flex-1 占满剩余空间  
**功能**: 当前页面的主体内容
- **Chat 页面**: 消息列表（可滚动）
- **其他页面**: 各自的主体内容（Models 列表、Agents 配置等）

**关键**: 
- 这是 `<Outlet />` 渲染的区域
- 高度自适应，底部到 Bottom Panel 顶部
- 所有页面共享这个布局结构

### 4. Bottom Panel（右下 - 完整输入区）
**位置**: 右侧底部，高度自适应（约 160-240px，含附件预览）  
**功能**: **完整的输入区域，和现有 ChatInput 一样**

#### 组成结构（从上到下）

**A. 附件预览区（可选，有附件时显示）**
- 文件列表（文件名、大小、删除按钮）
- 图片预览（缩略图）
- 拖放区域提示

**B. 文本输入框**
- 多行文本输入（支持 Shift+Enter 换行）
- 粘贴图片自动上传
- 拖放文件自动上传
- 字符计数（可选）

**C. 工具栏（输入框下方）**
- **左侧**: 
  - 📎 文件上传按钮
  - 🧩 Skill 选择器（下拉菜单）
  - 🤖 Agent 切换器（下拉菜单）
- **中间**: 
  - 模型选择器（显示当前模型，可切换）
- **右侧**: 
  - 发送按钮（或停止按钮）
  - 语音输入按钮（可选）

**关键**:
- **所有功能都保留**，和现有 ChatInput 完全一致
- 高度自适应：无附件时约 120px，有附件时更高
- 所有页面都可以显示（或按需隐藏）
- 输入框获得焦点时不改变布局

---

## 三、架构设计

### 核心思路

**方案：在 MainLayout 层面拆分，Chat 页面拆为两部分**

```
MainLayout
├── TopBar (全宽)
├── Sidebar (左侧)
└── RightPane (右侧容器)
    ├── MainContent (上部, flex-1, overflow-auto)
    │   └── <Outlet /> → 渲染 Chat 的消息列表部分
    └── BottomPanel (下部, 自适应高度)
        └── <Outlet /> → 渲染 Chat 的输入框部分
```

**问题**: React Router 的 `<Outlet />` 只能渲染一次，不能拆成两个插槽。

**解决**: 两种方案

#### 方案 A：Chat 页面内部拆分（推荐）
- MainLayout 提供布局框架（TopBar + Sidebar + 右侧容器）
- Chat 页面自己拆成 `ChatMessages` + `ChatInput` 两部分
- MainLayout 的右侧容器用 flex-col，Chat 页面占满并自己分上下

```tsx
// MainLayout.tsx
<div className="flex flex-col flex-1">
  <main className="flex-1 overflow-auto">
    <Outlet />  {/* Chat 页面整体渲染 */}
  </main>
</div>

// Chat/index.tsx
return (
  <div className="flex flex-col h-full">
    <ChatMessages />  {/* flex-1, overflow-auto */}
    <ChatInput />     {/* 自适应高度, shrink-0 */}
  </div>
);
```

**优势**: 改动最小，其他页面不受影响  
**劣势**: 输入框没有真正"独立"出来

#### 方案 B：双 Outlet 插槽（更彻底）
- MainLayout 提供两个命名插槽区域
- Chat 页面通过 React Context 或 Portal 分别渲染到两个区域

```tsx
// MainLayout.tsx
<div className="flex flex-col flex-1">
  <main className="flex-1 overflow-auto">
    <MainContentSlot />  {/* 消息区 */}
  </main>
  <BottomPanelSlot>
    <BottomContentSlot />  {/* 输入框区 */}
  </BottomPanelSlot>
</div>

// Chat/index.tsx
return (
  <>
    <MainContentSlot.Render>
      <ChatMessages />
    </MainContentSlot.Render>
    <BottomContentSlot.Render>
      <ChatInput />
    </BottomContentSlot.Render>
  </>
);
```

**优势**: 输入框真正独立，布局层级清晰  
**劣势**: 需要实现插槽机制，改动较大

#### 方案 C：路由拆分（最彻底）
- Chat 页面拆成两个路由：`/`（消息）和 `/input`（输入）
- 通过 layout route 包裹，共享上下文

**优势**: 最符合 React Router 理念  
**劣势**: 改动最大，需要重构路由和状态管理

**建议**: 先用**方案 A**快速实现，验证布局效果后再决定是否升级到方案 B。

---

## 四、Bottom Panel 详细设计

### 组件结构

```tsx
// BottomPanel.tsx
export function BottomPanel() {
  const [attachments, setAttachments] = useState<FileAttachment[]>([]);
  const [selectedSkill, setSelectedSkill] = useState<string | null>(null);
  const [selectedAgent, setSelectedAgent] = useState<string | null>(null);
  const [selectedModel, setSelectedModel] = useState<string | null>(null);

  return (
    <div className="shrink-0 border-t border-border/60 bg-background">
      {/* 1. 附件预览区（有附件时显示） */}
      {attachments.length > 0 && (
        <AttachmentPreview
          attachments={attachments}
          onRemove={(id) => setAttachments((prev) => prev.filter((a) => a.id !== id))}
        />
      )}

      {/* 2. 文本输入框 */}
      <div className="px-4 py-3">
        <textarea
          placeholder="输入消息... (Shift+Enter 换行)"
          className="w-full min-h-[60px] max-h-[200px] resize-none rounded-lg border border-border bg-surface-input px-3 py-2 text-sm"
          onKeyDown={handleKeyDown}
          onPaste={handlePaste}
          onDrop={handleDrop}
        />
      </div>

      {/* 3. 工具栏 */}
      <div className="flex items-center justify-between px-4 py-2 border-t border-border/40">
        {/* 左侧：文件、Skill、Agent */}
        <div className="flex items-center gap-2">
          {/* 文件上传 */}
          <Button variant="ghost" size="icon" onClick={handleFileUpload}>
            <Paperclip className="h-4 w-4" />
          </Button>

          {/* Skill 选择器 */}
          <SkillSelector
            value={selectedSkill}
            onChange={setSelectedSkill}
          />

          {/* Agent 切换器 */}
          <AgentSelector
            value={selectedAgent}
            onChange={setSelectedAgent}
          />
        </div>

        {/* 中间：模型选择 */}
        <ModelSelector
          value={selectedModel}
          onChange={setSelectedModel}
        />

        {/* 右侧：发送/停止 */}
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            onClick={handleSend}
            disabled={!canSend}
          >
            {isSending ? (
              <StopCircle className="h-4 w-4" />
            ) : (
              <Send className="h-4 w-4" />
            )}
          </Button>
        </div>
      </div>
    </div>
  );
}
```

### 附件预览区

```tsx
// AttachmentPreview.tsx
export function AttachmentPreview({ attachments, onRemove }) {
  return (
    <div className="px-4 py-2 border-b border-border/40 bg-surface-sidebar/50">
      <div className="flex flex-wrap gap-2">
        {attachments.map((attachment) => (
          <div
            key={attachment.id}
            className="flex items-center gap-2 rounded-lg border border-border bg-background px-3 py-2"
          >
            {/* 图片预览 */}
            {attachment.mimeType.startsWith('image/') ? (
              <img
                src={attachment.preview}
                alt={attachment.name}
                className="h-10 w-10 rounded object-cover"
              />
            ) : (
              <FileIcon className="h-8 w-8 text-muted-foreground" />
            )}

            {/* 文件信息 */}
            <div className="flex flex-col">
              <span className="text-xs font-medium">{attachment.name}</span>
              <span className="text-xs text-muted-foreground">
                {formatFileSize(attachment.size)}
              </span>
            </div>

            {/* 删除按钮 */}
            <Button
              variant="ghost"
              size="icon"
              className="h-6 w-6"
              onClick={() => onRemove(attachment.id)}
            >
              <X className="h-3 w-3" />
            </Button>
          </div>
        ))}
      </div>
    </div>
  );
}
```

### Skill 选择器

```tsx
// SkillSelector.tsx
export function SkillSelector({ value, onChange }) {
  const skills = useSkillsStore((s) => s.skills);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm" className="gap-2">
          <Puzzle className="h-4 w-4" />
          <span>{value ? getSkillName(value) : 'Skill'}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent>
        {skills.map((skill) => (
          <DropdownMenuItem
            key={skill.id}
            onClick={() => onChange(skill.id)}
          >
            {skill.name}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
```

### Agent 切换器

```tsx
// AgentSelector.tsx
export function AgentSelector({ value, onChange }) {
  const agents = useAgentsStore((s) => s.agents);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm" className="gap-2">
          <Bot className="h-4 w-4" />
          <span>{value ? getAgentName(value) : 'Agent'}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent>
        {agents.map((agent) => (
          <DropdownMenuItem
            key={agent.id}
            onClick={() => onChange(agent.id)}
          >
            {agent.name}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
```

### 模型选择器

```tsx
// ModelSelector.tsx
export function ModelSelector({ value, onChange }) {
  const models = useProviderStore((s) => s.models);
  const currentModel = value || useChatStore((s) => s.currentModel);

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm" className="gap-2">
          <Cpu className="h-4 w-4" />
          <span>{getModelDisplayName(currentModel)}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent>
        {models.map((model) => (
          <DropdownMenuItem
            key={model.id}
            onClick={() => onChange(model.id)}
          >
            {model.name}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
```

---

## 五、需要修改的文件

### Phase 1: 基础布局（方案 A）

| 文件 | 修改内容 |
|------|---------|
| `src/components/layout/MainLayout.tsx` | 重构为 TopBar + Sidebar + 右侧容器 |
| `src/components/layout/TitleBar.tsx` | 重命名为 `TopBar.tsx`，添加 Logo/状态 |
| `src/components/layout/Sidebar.tsx` | 移除顶部 Logo 和底部 Gateway 指示器 |
| `src/pages/Chat/index.tsx` | 拆分为 `ChatMessages` + `ChatInput` 两部分 |

### 新建文件

| 文件 | 用途 |
|------|------|
| `src/components/layout/TopBar.tsx` | 顶部栏组件（从 TitleBar 扩展） |
| `src/components/chat/BottomPanel.tsx` | 完整输入区容器 |
| `src/components/chat/AttachmentPreview.tsx` | 附件预览组件 |
| `src/components/chat/SkillSelector.tsx` | Skill 选择器 |
| `src/components/chat/AgentSelector.tsx` | Agent 切换器 |
| `src/components/chat/ModelSelector.tsx` | 模型选择器 |

### Phase 2: 可选增强

| 文件 | 修改内容 |
|------|---------|
| `src/stores/settings.ts` | 添加 `bottomPanelHeight` 等状态 |
| `src/components/chat/GlobalSearch.tsx` | 全局搜索框 |

---

## 六、CSS/组件实现方案

### MainLayout.tsx 重构

```tsx
export function MainLayout() {
  const platform = window.electron?.platform;
  const isMac = platform === 'darwin';
  const isWin = platform === 'win32';

  return (
    <div
      data-testid="main-layout"
      data-platform={platform}
      className="flex h-screen overflow-hidden bg-background"
    >
      {/* 整体纵向布局 */}
      <div className="flex flex-col min-h-0 flex-1">
        
        {/* 1. TopBar - 全宽顶部栏 */}
        <TopBar />
        
        {/* 2. 下方主体区域 */}
        <div className="flex min-h-0 flex-1 overflow-hidden">
          
          {/* 左侧 Sidebar */}
          <Sidebar />
          
          {/* 右侧内容区 */}
          <div className="flex flex-col min-h-0 flex-1 overflow-hidden bg-background">
            
            {/* Main Content - 消息/页面内容区 */}
            <main
              data-testid="main-content"
              className={cn(
                'relative min-h-0 flex-1 overflow-auto',
                isMac && 'rounded-tl-2xl border-l border-t border-border/60',
                isWin && 'rounded-tl-2xl border-l border-border/60'
              )}
            >
              {isMac && (
                <div
                  data-testid="mac-main-drag-region"
                  aria-hidden="true"
                  className="drag-region absolute inset-x-0 top-0 z-10"
                  style={{ height: MAC_SIDEBAR_CHROME_HEIGHT }}
                />
              )}
              <Outlet />
            </main>
            
          </div>
        </div>
      </div>
    </div>
  );
}
```

### TopBar.tsx 设计

```tsx
export function TopBar() {
  const platform = window.electron?.platform;
  const isWin = platform === 'win32';
  const gatewayStatus = useGatewayStore((s) => s.status);
  const isGatewayRunning = gatewayStatus.state === 'running';
  const gatewayRestarting = isGatewayRestarting(gatewayStatus);

  return (
    <div
      data-testid="topbar"
      className={cn(
        'drag-region flex h-12 shrink-0 items-center',
        'bg-surface-sidebar border-b border-border/60',
        isWin ? 'px-4' : 'px-6'
      )}
    >
      {/* 左侧：Logo + 应用名 */}
      <div className="flex items-center gap-3 no-drag">
        <img src={logoSvg} alt="GrandPoem Studio" className="h-6 w-auto" />
        <span className="text-sm font-semibold text-foreground/90">
          GrandPoem Studio
        </span>
      </div>

      {/* 中间：全局搜索（Phase 2） */}
      <div className="flex-1 flex justify-center px-8">
        {/* 暂时留空，后续添加搜索框 */}
      </div>

      {/* 右侧：状态 + 控制 */}
      <div className="flex items-center gap-3 no-drag">
        {/* Gateway 状态 */}
        {gatewayRestarting ? (
          <div className="flex items-center gap-2 px-3 py-1 rounded-full text-xs bg-yellow-500/10 text-yellow-700 dark:text-yellow-400">
            <Loader2 className="h-3 w-3 animate-spin" />
            <span>重启中</span>
          </div>
        ) : (
          <div className={cn(
            'flex items-center gap-2 px-3 py-1 rounded-full text-xs',
            isGatewayRunning 
              ? 'bg-green-500/10 text-green-700 dark:text-green-400'
              : 'bg-yellow-500/10 text-yellow-700 dark:text-yellow-400'
          )}>
            <div className={cn(
              'w-2 h-2 rounded-full',
              isGatewayRunning ? 'bg-green-500' : 'bg-yellow-500 animate-pulse'
            )} />
            <span>{isGatewayRunning ? '已连接' : '连接中'}</span>
          </div>
        )}

        {/* 设置按钮 */}
        <NavLink to="/settings" className="text-foreground/70 hover:text-foreground">
          <SettingsIcon className="h-4 w-4" />
        </NavLink>

        {/* Windows 窗口控制 */}
        {isWin && <WindowControls />}
      </div>
    </div>
  );
}
```

### Chat/index.tsx 拆分

```tsx
export function Chat() {
  // ... 现有逻辑 ...

  return (
    <div className="flex flex-col h-full -m-6">
      {/* 上部：消息列表 */}
      <div className="flex-1 min-h-0 overflow-hidden">
        {/* Toolbar */}
        <div className="relative flex shrink-0 items-center justify-end px-4 py-2">
          <div className="drag-region absolute inset-0 z-0" />
          <div className="no-drag relative z-10">
            <ChatToolbar ... />
          </div>
        </div>

        {/* Messages */}
        <div className="relative min-h-0 flex-1 overflow-hidden px-4 py-4">
          {/* 消息列表渲染逻辑 */}
        </div>
      </div>

      {/* 下部：完整输入区（Bottom Panel） */}
      <BottomPanel
        onSend={sendMessage}
        onStop={abortRun}
        disabled={!isGatewayRunning}
        sending={inputRunActive}
        attachments={attachments}
        onAttachmentsChange={setAttachments}
        selectedSkill={selectedSkill}
        onSkillChange={setSelectedSkill}
        selectedAgent={selectedAgent}
        onAgentChange={setSelectedAgent}
        selectedModel={selectedModel}
        onModelChange={setModel}
      />
    </div>
  );
}
```

---

## 七、迁移步骤

### Phase 1: 基础布局（2-3 天）

1. **创建 TopBar.tsx**（0.5 天）
   - 从 TitleBar.tsx 扩展
   - 添加 Logo + 应用名称
   - 添加 Gateway 状态指示器
   - 保留 Windows 窗口控制

2. **修改 MainLayout.tsx**（0.5 天）
   - 替换 TitleBar 为 TopBar
   - 调整 flex 布局为纵向
   - 确保右侧容器正确分上下

3. **精简 Sidebar.tsx**（0.5 天）
   - 移除顶部 Logo + 应用名称
   - 移除底部 Gateway 重启指示器
   - 调整顶部间距

4. **拆分 Chat/index.tsx**（1 天）
   - 将消息列表和输入框分为两个区域
   - 调整高度计算和滚动行为
   - 测试消息列表滚动不影响输入框

5. **跨平台测试**（0.5 天）
   - Windows: 窗口控制、圆角
   - macOS: traffic light、drag region
   - Linux: 原生标题栏兼容

### Phase 2: Bottom Panel 完善（2-3 天）

1. **创建 BottomPanel.tsx**（1 天）
   - 实现完整的输入区域
   - 包含文本输入、附件预览、工具栏
   - 集成现有的 ChatInput 逻辑

2. **拆分选择器组件**（1 天）
   - SkillSelector
   - AgentSelector
   - ModelSelector

3. **实现附件预览**（0.5 天）
   - AttachmentPreview 组件
   - 文件拖放、粘贴上传

4. **集成测试**（0.5 天）
   - 测试所有功能正常
   - 测试高度自适应
   - 测试跨平台兼容

### Phase 3: 可选增强（后续）

1. **全局搜索框**（1-2 天）
   - TopBar 中间添加搜索输入
   - 实现 Cmd+K 命令面板

2. **其他页面适配**（1 天）
   - Models/Agents/Skills 页面可以显示简化版输入框
   - 或者按需隐藏 Bottom Panel

3. **输入框增强**（1 天）
   - 语音输入按钮
   - 表情选择器
   - @提及功能

---

## 八、注意事项

### 兼容性
- **macOS**: TopBar 需要处理 traffic light 位置（左侧留白或隐藏）
- **Windows**: 窗口控制从 TitleBar 迁移到 TopBar，圆角保持不变
- **Linux**: 保持原生标题栏，TopBar 只放应用内容

### 性能
- 消息列表使用虚拟滚动（如果数据量大）
- 附件预览使用缩略图，避免大图片卡顿
- 输入框高度自适应时避免布局抖动

### 用户体验
- 输入区完整保留所有功能，不减少任何能力
- 消息列表滚动不影响输入框位置
- TopBar 状态一目了然（Gateway 连接状态）
- 附件预览清晰，支持删除

### 向后兼容
- 保留 TitleBar.tsx 作为 fallback（可选）
- Sidebar 折叠功能保持不变
- 现有页面路由不受影响
- Chat 页面内部逻辑不变，只是布局调整

---

## 九、总结

**核心变化**:
1. TitleBar → TopBar（功能增强：Logo + 状态 + 控制）
2. Sidebar 精简（移除顶部/底部元素）
3. Chat 页面拆分为消息区 + 完整输入区
4. **Bottom Panel 是完整的输入区域**，包含：
   - 文本输入
   - 附件预览（文件/图片）
   - 工具栏（文件上传、Skill、Agent、模型选择）
   - 发送/停止按钮

**预计工期**: 
- Phase 1（基础布局）: 2-3 天
- Phase 2（Bottom Panel 完善）: 2-3 天
- Phase 3（可选增强）: 3-5 天

**风险**: 低（布局调整，不涉及业务逻辑）

**收益**: 
- 布局层级清晰（顶部栏 + 左侧栏 + 右上消息 + 右下完整输入）
- 输入区功能完整，不减少任何能力
- 视觉上更清爽，符合现代聊天应用惯例
- 为后续功能扩展（全局搜索、命令面板）预留空间

**建议**: 先实现 Phase 1 + Phase 2，验证布局效果后再决定是否做 Phase 3。
