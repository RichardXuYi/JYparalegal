import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { Scale, GraduationCap, FileText, Mic, FileUp, Sparkles } from 'lucide-react';
import { billingRuleLabel, fmtDateTime, planLabel, signStatusLabel } from '@/lib/legal-enums';
import { LegalPageHeader } from '@/components/legal/LegalPageHeader';
import { DropZone } from '@/components/legal/DropZone';
import { MAX_UPLOAD_BYTES, fileToBase64 } from '@/lib/file-base64';

type SignTask = {
  id: number;
  taskNo: string;
  title: string;
  status: string;
  createdAt: string;
  completedAt?: string;
};

type Account = {
  plan: string;
  signQuota: number;
  signUsed: number;
  signRemaining: number;
  aiQuotaTokens: number;
  aiUsedTokens: number;
  billingRule: string;
};

type LoadState = 'loading' | 'ready' | 'error';

function openSignTask(nav: (path: string) => void, task: { id: number; status: string }) {
  nav(task.status === 'DRAFT' ? `/signing/${task.id}/setup` : `/signing/${task.id}`);
}

export default function Overview() {
  const [tasks, setTasks] = useState<SignTask[]>([]);
  const [tasksState, setTasksState] = useState<LoadState>('loading');
  const [recent, setRecent] = useState<SignTask[]>([]);
  const [recentState, setRecentState] = useState<LoadState>('loading');
  const [counts, setCounts] = useState<{ kpi: Record<string, number>; menu: Record<string, number> } | null>(null);
  const [account, setAccount] = useState<Account | null>(null);
  const fileRef = useRef<HTMLInputElement>(null);
  const nav = useNavigate();

  const loadTasks = () => {
    void fetch('/platform/sign/tasks?view=PENDING_ME', { credentials: 'include' })
      .then((r) => r.json())
      .then((env) => { setTasks(Array.isArray(env?.data) ? env.data : []); setTasksState('ready'); })
      .catch(() => { setTasks([]); setTasksState('error'); });
  };
  const reloadTasks = () => { setTasksState('loading'); loadTasks(); };

  const loadRecent = () => {
    void fetch('/platform/sign/tasks?view=COMPLETED', { credentials: 'include' })
      .then((r) => r.json())
      .then((env) => { setRecent(Array.isArray(env?.data) ? env.data.slice(0, 4) : []); setRecentState('ready'); })
      .catch(() => { setRecent([]); setRecentState('error'); });
  };
  const reloadRecent = () => { setRecentState('loading'); loadRecent(); };

  useEffect(() => {
    loadTasks();
    loadRecent();
    void fetch('/platform/sign/tasks/inbox/counts', { credentials: 'include' })
      .then((r) => r.json())
      .then((env) => setCounts(env?.data ?? null))
      .catch(() => setCounts(null));
    void fetch('/platform/account/overview', { credentials: 'include' })
      .then((r) => r.json())
      .then((env) => setAccount(env?.data ?? null))
      .catch(() => setAccount(null));
  }, []);

  const uploadFile = async (file: File) => {
    if (file.size > MAX_UPLOAD_BYTES) {
      toast.error(`文件超过 50MB，无法上传：${file.name}`);
      return;
    }
    let b64: string;
    try {
      b64 = await fileToBase64(file);
    } catch (e) {
      toast.error(`读取文件失败：${e instanceof Error ? e.message : file.name}`);
      return;
    }
    const res = await fetch('/platform/sign/tasks/from-file', {
      method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title: file.name.replace(/\.[^.]+$/, ''), fileName: file.name, contentBase64: b64 }),
    }).then((r) => r.json()).catch(() => null);
    const taskId = res?.data?.taskId;
    if (taskId) nav(`/signing/${taskId}/setup`);
    else toast.error(`发起失败：${res?.msg ?? '网络错误'}`);
  };

  const k = counts?.kpi ?? { pendingMine: 0, pendingOthers: 0, expiringSoon: 0, signing: 0 };
  // KPI 不靠颜色堆砌:仅「即将截止」上红(需要立刻行动),待我方用主色,其余中性。
  // 绿色在中文语境读作「完成」,不用于「签署中」这种进行中状态。
  // pendingMine 只数 PENDING_SIGN；待我处理列表还含填写和定稿，角标用列表长度。
  const kpi = [
    { label: '待我方签署', n: k.pendingMine, bar: 'bg-primary', num: 'text-primary', to: '/signing?view=PENDING_ME', note: '' },
    { label: '待他方签署', n: k.pendingOthers, bar: 'bg-muted-foreground/40', num: 'text-foreground', to: '/signing?view=PENDING_OTHERS', note: '' },
    { label: '签署即将截止', n: k.expiringSoon, bar: 'bg-red-500', num: 'text-red-600 dark:text-red-400', to: '/signing?view=EXPIRING_SOON', note: '' },
    { label: '签署中', n: k.signing, bar: 'bg-muted-foreground/40', num: 'text-foreground', to: '/signing?view=ALL_SIGNING', note: '全部非草稿任务' },
  ];

  return (
    <div className="h-full overflow-y-auto p-5">
      <LegalPageHeader title="工作台" />

      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {kpi.map((item) => (
          <button key={item.label} onClick={() => nav(item.to)}
            className="relative overflow-hidden rounded-lg border border-border bg-card p-4 text-left transition-colors hover:border-primary">
            <div className={`absolute left-0 top-0 bottom-0 w-[3px] ${item.bar}`} />
            <div className="text-meta text-muted-foreground">{item.label}</div>
            <div className={`mt-1.5 text-stat font-bold leading-none ${item.num}`}>{item.n}</div>
            {item.note && <div className="mt-1.5 text-tiny text-muted-foreground">{item.note}</div>}
          </button>
        ))}
      </div>

      <div className="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-[1.6fr_1fr]">
        <div className="flex flex-col gap-4">
          <div className="rounded-lg border border-border bg-card">
            <div className="border-b border-border px-4">
              <span className="inline-block border-b-2 border-primary py-3 text-meta font-semibold text-primary">
                待我处理{tasksState === 'ready' && tasks.length > 0 ? ` (${tasks.length})` : ''}
              </span>
            </div>
            {tasksState === 'loading' && (
              <div className="p-8 text-center text-sm text-muted-foreground">正在加载…</div>
            )}
            {tasksState === 'error' && (
              <div className="flex flex-col items-center gap-3 p-8 text-center text-sm">
                <span className="text-muted-foreground">待办加载失败</span>
                <button className="rounded-md border border-border px-3 py-1.5 text-sm hover:bg-muted" onClick={reloadTasks}>重试</button>
              </div>
            )}
            {tasksState === 'ready' && tasks.length === 0 && (
              <div className="p-8 text-center text-sm text-muted-foreground">没有待你处理的任务</div>
            )}
            {tasksState === 'ready' && tasks.map((t) => (
              <div key={t.id} className="flex items-center gap-3 border-b border-border/60 px-4 py-3 text-sm last:border-b-0">
                <span className="rounded bg-primary/15 px-2 py-0.5 text-xs font-medium text-primary">{signStatusLabel(t.status)}</span>
                <span className="flex-1 truncate">{t.title}</span>
                <button className="text-meta text-primary hover:underline" onClick={() => openSignTask(nav, t)}>去签署 ›</button>
              </div>
            ))}
            <div className="grid gap-3 p-4 sm:grid-cols-[1fr_auto] sm:items-stretch">
              <DropZone
                hint="上传文件开始发起签署 · PDF / DOCX / XLSX · ≤50MB"
                onDropFiles={(files) => { const f = files[0]; if (f) void uploadFile(f); }}
                actions={[
                  { label: '上传文件', icon: FileUp, onClick: () => fileRef.current?.click() },
                  { label: '交给 Agent 审查', icon: Sparkles, onClick: () => nav('/') },
                ]}
              />
              <input ref={fileRef} type="file" className="hidden" accept=".pdf,.doc,.docx,.xls,.xlsx"
                onChange={(e) => { const f = e.target.files?.[0]; if (f) void uploadFile(f); e.target.value = ''; }} />
              <button
                type="button"
                className="rounded-lg border border-border bg-card px-4 py-3 text-sm font-medium text-primary hover:border-primary"
                onClick={() => nav('/templates')}
              >
                用模板发起
              </button>
            </div>
          </div>

          <div className="rounded-lg border border-border bg-card">
            <div className="border-b border-border px-4 py-3 text-meta font-semibold">最近完成</div>
            {recentState === 'loading' && (
              <div className="p-6 text-center text-sm text-muted-foreground">正在加载…</div>
            )}
            {recentState === 'error' && (
              <div className="flex flex-col items-center gap-3 p-6 text-center text-sm">
                <span className="text-muted-foreground">加载失败</span>
                <button className="rounded-md border border-border px-3 py-1.5 text-sm hover:bg-muted" onClick={reloadRecent}>重试</button>
              </div>
            )}
            {recentState === 'ready' && recent.length === 0 && (
              <div className="p-6 text-center text-sm text-muted-foreground">还没有已完成的任务</div>
            )}
            {recentState === 'ready' && recent.map((t) => (
              <button
                key={t.id}
                type="button"
                onClick={() => openSignTask(nav, t)}
                className="flex w-full items-center gap-3 border-b border-border/60 px-4 py-3 text-left text-sm last:border-b-0 hover:bg-muted/40"
              >
                <span className="flex-1 truncate">{t.title}</span>
                <span className="shrink-0 text-tiny text-muted-foreground">{signStatusLabel(t.status)}</span>
                <span className="shrink-0 text-tiny text-muted-foreground">{fmtDateTime(t.completedAt || t.createdAt)}</span>
              </button>
            ))}
          </div>
        </div>

        <div className="flex flex-col gap-4">
          <div className="rounded-lg border border-border bg-card p-4">
            <h4 className="mb-3 flex items-center gap-1.5 text-meta font-semibold">能力入口</h4>
            <div className="grid grid-cols-2 gap-2.5">
              <button className="rounded-lg border border-border p-3 text-left text-meta hover:border-primary" onClick={() => nav('/')}>
                <Scale className="h-5 w-5 text-primary" />
                <div className="mt-1 font-medium">法律 Agent</div>
                <div className="mt-0.5 text-tiny text-muted-foreground">审查 / 抽取 / 起草 / 查任务</div>
              </button>
              <button className="rounded-lg border border-border p-3 text-left text-meta hover:border-primary" onClick={() => nav('/moot')}>
                <GraduationCap className="h-5 w-5 text-primary" />
                <div className="mt-1 font-medium">模拟法庭</div>
                <div className="mt-0.5 text-tiny text-muted-foreground">多角色庭审演练 · 即将开发</div>
              </button>
              <button className="rounded-lg border border-border p-3 text-left text-meta hover:border-primary" onClick={() => nav('/compare')}>
                <FileText className="h-5 w-5 text-primary" />
                <div className="mt-1 font-medium">智能比对</div>
                <div className="mt-0.5 text-tiny text-muted-foreground">两版合同差异核对</div>
              </button>
              <button className="rounded-lg border border-border p-3 text-left text-meta hover:border-primary" onClick={() => nav('/voice')}>
                <Mic className="h-5 w-5 text-primary" />
                <div className="mt-1 font-medium">语音通话</div>
                <div className="mt-0.5 text-tiny text-muted-foreground">实时语音与转写留痕 · 即将开发</div>
              </button>
            </div>
          </div>

          <div className="rounded-lg border border-border bg-card p-4">
            <h4 className="mb-3 text-meta font-semibold">账户概况</h4>
            <div className="space-y-0 text-meta">
              <div className="flex justify-between border-b border-dashed border-border py-1.5">
                <span className="text-muted-foreground">当前版本</span><span>{planLabel(account?.plan)}</span>
              </div>
              <div className="flex justify-between border-b border-dashed border-border py-1.5">
                <span className="text-muted-foreground">可用签署份数</span>
                <span>{account ? `${account.signRemaining.toLocaleString('zh-CN')} / ${account.signQuota.toLocaleString('zh-CN')} 份` : '—'}</span>
              </div>
              <div className="flex justify-between border-b border-dashed border-border py-1.5">
                <span className="text-muted-foreground">已用签署</span><span>{account?.signUsed ?? 0} 份</span>
              </div>
              <div className="flex justify-between border-b border-dashed border-border py-1.5">
                <span className="text-muted-foreground">计费规则</span><span>{billingRuleLabel(account?.billingRule)}</span>
              </div>
              <div className="flex justify-between py-1.5">
                <span className="text-muted-foreground">AI 剩余</span>
                {account && account.aiQuotaTokens > 0
                  ? <span>{(account.aiQuotaTokens - account.aiUsedTokens).toLocaleString('zh-CN')} tokens</span>
                  : <span className="rounded bg-amber-500/15 px-2 py-0.5 text-xs text-amber-700 dark:text-amber-400">待开通</span>}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
