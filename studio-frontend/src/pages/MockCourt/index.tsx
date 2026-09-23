import { useCallback, useEffect, useState } from 'react';
import { GraduationCap } from 'lucide-react';
import {
  mootCaseTypeLabel,
  mootPhaseLabel,
  mootRoleLabel,
  mootScoreDimensionLabel,
} from '@/lib/legal-enums';

type MootCase = { id: number; title: string; caseType: string; phase: string; status: string };
type Detail = {
  case: MootCase;
  roles: { id: number; roleType: string; userId: number | null; agentFlag: boolean }[];
  phases: { fromPhase: string | null; toPhase: string; triggerType: string }[];
  minutes: { speaker: string; content: string }[];
  scores: { dimension: string; score: number; rationale: string }[];
};

const NEXT_PHASE: Record<string, string | undefined> = {
  PREP: 'OPENING', OPENING: 'COURT_INVESTIGATION', COURT_INVESTIGATION: 'COURT_DEBATE',
  COURT_DEBATE: 'FINAL_STATEMENT', FINAL_STATEMENT: 'JUDGMENT', JUDGMENT: 'CLOSED',
};

const TRIGGER_LABEL: Record<string, string> = {
  USER: '用户推进', NEW_EVIDENCE: '新证据', AUTO: '自动',
};

/** 模拟法庭：案件/角色/阶段状态机（含反向跳转）/笔录/评分，接 /api/moot。 */
export default function MockCourt() {
  const [cases, setCases] = useState<MootCase[]>([]);
  const [listState, setListState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [sel, setSel] = useState<number | null>(null);
  // 派生而非存储：详情与「它是为哪个 sel 取的」一起存，切换/清空时自然为 null，
  // 不在 effect 里同步 setState；cancelled 标记避免旧响应覆盖新选择。
  const [loadedDetail, setLoadedDetail] = useState<{ sel: number; data: Detail | null } | null>(null);
  const [tick, setTick] = useState(0);
  const detail = loadedDetail && loadedDetail.sel === sel ? loadedDetail.data : null;

  const load = useCallback(() => {
    void fetch('/platform/moot/cases', { credentials: 'include' }).then((r) => r.json())
      .then((e) => { setCases(Array.isArray(e?.data) ? e.data : []); setListState('ready'); })
      .catch(() => { setCases([]); setListState('error'); });
  }, []);
  useEffect(load, [load]);
  const reload = () => { setListState('loading'); load(); };
  useEffect(() => {
    if (sel == null) return;
    let cancelled = false;
    void fetch(`/platform/moot/cases/${sel}`, { credentials: 'include' }).then((r) => r.json())
      .then((e) => { if (!cancelled) setLoadedDetail({ sel, data: (e?.data as Detail) ?? null }); })
      .catch(() => { if (!cancelled) setLoadedDetail({ sel, data: null }); });
    return () => { cancelled = true; };
  }, [sel, tick]);

  const post = async (path: string, body: unknown) => {
    await fetch(path, { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
    load();
    // 原来这里写 setSel(sel)（同值 set），React 会 bail out、effect 不重跑 → 详情其实不刷新。
    // 改用 tick 触发重新拉取。
    setTick((t) => t + 1);
  };

  const create = () => void post('/platform/moot/cases', { title: '未命名庭审演练', caseType: 'CIVIL' });

  return (
    <div className="p-5">
      <div className="mb-4 flex items-center gap-4 rounded-xl bg-gradient-to-r from-primary to-purple-600 p-6 text-white">
        <GraduationCap className="h-9 w-9 shrink-0" />
        <div>
          <h2 className="text-xl font-semibold">模拟法庭</h2>
          <p className="mt-1 text-sm opacity-90">多角色庭审演练,支持阶段推进、笔录与评分复盘</p>
          <p className="mt-0.5 text-xs opacity-75">演练环境,记录不会生成正式文书</p>
        </div>
        <button className="ml-auto rounded-md bg-white px-4 py-2 text-sm font-semibold text-primary" onClick={create}>＋ 新建庭审演练</button>
      </div>
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[280px_1fr]">
        <div className="rounded-lg border border-border bg-card p-3">
          <h4 className="mb-2 text-sm font-semibold">案件列表</h4>
          {listState === 'loading' && <div className="p-4 text-center text-sm text-muted-foreground">正在加载…</div>}
          {listState === 'error' && (
            <div className="flex flex-col items-center gap-3 p-4 text-center text-sm">
              <span className="text-muted-foreground">案件加载失败</span>
              <button className="rounded-md border border-border px-3 py-1.5 hover:bg-muted" onClick={reload}>重试</button>
            </div>
          )}
          {listState === 'ready' && cases.length === 0 && <div className="p-4 text-center text-sm text-muted-foreground">暂无案件</div>}
          {listState === 'ready' && cases.map((c) => (
            <button key={c.id} onClick={() => setSel(c.id)}
              className={`mb-1 w-full rounded-md px-3 py-2 text-left text-sm ${sel === c.id ? 'bg-primary/15 text-primary' : 'hover:bg-muted'}`}>
              <div className="truncate">{c.title}</div>
              <div className="text-xs text-muted-foreground">{mootPhaseLabel(c.phase)} · {mootCaseTypeLabel(c.caseType)}</div>
            </button>
          ))}
        </div>
        <div className="rounded-lg border border-border bg-card p-4">
          {!detail && <div className="p-8 text-center text-sm text-muted-foreground">选择或新建案件开始演练</div>}
          {detail && (
            <div className="space-y-4 text-sm">
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-semibold">{detail.case.title}</span>
                <span className="rounded bg-muted px-2 py-0.5 text-xs">{mootPhaseLabel(detail.case.phase)}</span>
                {NEXT_PHASE[detail.case.phase] && (
                  <button className="rounded-md bg-primary px-3 py-1.5 text-xs text-primary-foreground"
                    onClick={() => void post(`/platform/moot/cases/${detail.case.id}/phase`, { to: NEXT_PHASE[detail.case.phase], trigger: 'USER' })}>
                    推进到{mootPhaseLabel(NEXT_PHASE[detail.case.phase])}
                  </button>
                )}
                {detail.case.phase === 'COURT_DEBATE' && (
                  <button className="rounded-md border border-border px-3 py-1.5 text-xs"
                    onClick={() => void post(`/platform/moot/cases/${detail.case.id}/phase`, { to: 'COURT_INVESTIGATION', trigger: 'NEW_EVIDENCE', reason: '新证据' })}>
                    返回法庭调查（新证据）
                  </button>
                )}
                <button className="rounded-md border border-border px-3 py-1.5 text-xs"
                  onClick={() => void post(`/platform/moot/cases/${detail.case.id}/roles`, { roleType: 'JUDGE', agentFlag: true })}>＋ AI 法官</button>
                <button className="rounded-md border border-border px-3 py-1.5 text-xs"
                  onClick={() => void post(`/platform/moot/cases/${detail.case.id}/minutes`, { speaker: 'judge', content: '现在开庭' })}>＋ 笔录条目</button>
                <button className="rounded-md border border-border px-3 py-1.5 text-xs"
                  onClick={() => void post(`/platform/moot/cases/${detail.case.id}/scores`, { dimension: 'legal-basis', score: 8, rationale: '正确引用了民法典相关条款' })}>＋ 评分</button>
              </div>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                <div>
                  <h5 className="mb-1 text-xs font-semibold text-muted-foreground">角色</h5>
                  {detail.roles.map((r) => <div key={r.id} className="rounded bg-muted/60 px-2 py-1 text-xs">{mootRoleLabel(r.roleType)}{r.agentFlag ? '（AI）' : ''}</div>)}
                </div>
                <div>
                  <h5 className="mb-1 text-xs font-semibold text-muted-foreground">阶段事件</h5>
                  {detail.phases.map((p, i) => <div key={i} className="rounded bg-muted/60 px-2 py-1 text-xs">{p.fromPhase ? mootPhaseLabel(p.fromPhase) : '—'} → {mootPhaseLabel(p.toPhase)}（{TRIGGER_LABEL[p.triggerType] ?? '推进'}）</div>)}
                </div>
                <div>
                  <h5 className="mb-1 text-xs font-semibold text-muted-foreground">评分（可解释）</h5>
                  {detail.scores.map((s, i) => <div key={i} className="rounded bg-muted/60 px-2 py-1 text-xs">{mootScoreDimensionLabel(s.dimension)}：{s.score} 分 · {s.rationale}</div>)}
                </div>
              </div>
              <div>
                <h5 className="mb-1 text-xs font-semibold text-muted-foreground">庭审笔录</h5>
                {detail.minutes.length === 0 && <div className="rounded bg-muted/40 px-2 py-1 text-xs text-muted-foreground">暂无笔录</div>}
                {detail.minutes.map((m, i) => <div key={i} className="rounded bg-muted/60 px-2 py-1 text-xs"><b>{mootRoleLabel(m.speaker?.toUpperCase())}</b>：{m.content}</div>)}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
