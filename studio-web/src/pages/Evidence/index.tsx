import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';
import { evidenceStatusLabel } from '@/lib/legal-enums';

type Ev = { id: number; bizType: string; bizId: number | null; name: string; evidenceType: string | null; source: string | null; sha256: string | null; status: string; note: string | null };

const TYPES = ['合同原件', '往来邮件', '聊天记录', '发票', '验收单', '其他'];

const EVIDENCE_STATUS_CLASS: Record<string, string> = {
  VERIFIED: 'bg-green-500/10 text-green-700 dark:text-green-400',
  REJECTED: 'bg-red-500/10 text-red-700 dark:text-red-400',
};

/** 证据管理：登记/哈希留痕/核验状态流转（/platform/evidence）。 */
export default function Evidence() {
  const [list, setList] = useState<Ev[]>([]);
  const [listState, setListState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [bizType, setBizType] = useState('');
  const [form, setForm] = useState({ name: '', evidenceType: TYPES[0], source: '上传', bizType: 'SIGN_TASK', bizId: '', content: '' });

  const load = useCallback(() => {
    const qs = bizType ? `?bizType=${bizType}` : '';
    void fetch(`/platform/evidence${qs}`, { credentials: 'include' }).then((r) => r.json())
      .then((e) => { setList(Array.isArray(e?.data) ? e.data : []); setListState('ready'); })
      .catch(() => { setList([]); setListState('error'); });
  }, [bizType]);
  useEffect(load, [load]);
  const reload = () => { setListState('loading'); load(); };

  const create = async () => {
    try {
      const res = await fetch('/platform/evidence', {
        method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...form, bizId: form.bizId ? Number(form.bizId) : null }),
      }).then((r) => r.json());
      if (res?.code === 0) {
        toast.success('证据已登记');
        setForm({ ...form, name: '', content: '' });
        load();
      } else {
        toast.error(res?.msg ?? '登记失败');
      }
    } catch {
      toast.error('网络错误,登记失败');
    }
  };

  const setStatus = async (id: number, status: string) => {
    try {
      const res = await fetch(`/platform/evidence/${id}/status`, {
        method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status }),
      }).then((r) => r.json());
      if (res?.code === 0) {
        toast.success(status === 'VERIFIED' ? '已核验通过' : '已驳回');
        load();
      } else {
        toast.error(res?.msg ?? '操作失败');
      }
    } catch {
      toast.error('网络错误,操作失败');
    }
  };

  const copyHash = async (sha: string) => {
    try {
      await navigator.clipboard.writeText(sha);
      toast.success('完整哈希已复制');
    } catch {
      toast.error('复制失败,请手动选择');
    }
  };

  return (
    <div className="h-full overflow-y-auto p-5">
      <h1 className="mb-4 text-lg font-semibold">证据管理</h1>
      <div className="mb-4 grid gap-2 rounded-lg border border-border bg-card p-4 text-sm sm:grid-cols-3">
        <input className="h-8 rounded-md border border-border bg-card px-2.5" placeholder="证据名称" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
        <select className="h-8 rounded-md border border-border bg-card px-2.5" value={form.evidenceType} onChange={(e) => setForm({ ...form, evidenceType: e.target.value })}>
          {TYPES.map((t) => <option key={t}>{t}</option>)}
        </select>
        <input className="h-8 rounded-md border border-border bg-card px-2.5" placeholder="关联业务ID（可选）" value={form.bizId} onChange={(e) => setForm({ ...form, bizId: e.target.value })} />
        <textarea className="min-h-[60px] rounded-md border border-border bg-card p-2.5 text-xs sm:col-span-2" placeholder="证据内容（用于计算 SHA-256 留痕）" value={form.content} onChange={(e) => setForm({ ...form, content: e.target.value })} />
        <button className="h-8 rounded-md bg-primary px-4 text-sm text-primary-foreground" onClick={() => void create()}>登记证据</button>
      </div>

      <div className="mb-2 flex gap-2 text-sm">
        <button className={`rounded-md px-3 py-1 ${bizType === '' ? 'bg-primary/15 font-semibold text-primary' : 'text-muted-foreground'}`} onClick={() => setBizType('')}>全部</button>
        <button className={`rounded-md px-3 py-1 ${bizType === 'SIGN_TASK' ? 'bg-primary/15 font-semibold text-primary' : 'text-muted-foreground'}`} onClick={() => setBizType('SIGN_TASK')}>签署任务</button>
        <button className={`rounded-md px-3 py-1 ${bizType === 'MOOT_CASE' ? 'bg-primary/15 font-semibold text-primary' : 'text-muted-foreground'}`} onClick={() => setBizType('MOOT_CASE')}>庭审案件</button>
      </div>

      <div className="rounded-lg border border-border bg-card">
        <table className="w-full text-sm">
          <thead><tr className="border-b border-border bg-muted/40 text-left text-xs text-muted-foreground">
            <th className="px-4 py-2">名称</th><th className="px-2 py-2">类型</th><th className="px-2 py-2">SHA-256</th><th className="px-2 py-2">状态</th><th className="px-4 py-2">操作</th>
          </tr></thead>
          <tbody>
            {listState === 'loading' && <tr><td colSpan={5} className="p-8 text-center text-muted-foreground">正在加载…</td></tr>}
            {listState === 'error' && (
              <tr><td colSpan={5} className="p-8 text-center">
                <div className="flex flex-col items-center gap-3 text-sm">
                  <span className="text-muted-foreground">证据加载失败</span>
                  <button className="rounded-md border border-border px-3 py-1.5 text-sm hover:bg-muted" onClick={reload}>重试</button>
                </div>
              </td></tr>
            )}
            {listState === 'ready' && list.length === 0 && <tr><td colSpan={5} className="p-8 text-center text-muted-foreground">暂无证据</td></tr>}
            {listState === 'ready' && list.map((e) => (
              <tr key={e.id} className="border-b border-border/60">
                <td className="px-4 py-2.5">{e.name}</td>
                <td className="px-2 py-2.5 text-xs text-muted-foreground">{e.evidenceType}</td>
                <td className="px-2 py-2.5 font-mono text-2xs text-muted-foreground">
                  {e.sha256 ? (
                    <span className="group inline-flex items-center gap-1" title={e.sha256}>
                      {e.sha256.slice(0, 16)}…
                      <button className="rounded px-1 text-2xs text-primary opacity-0 transition-opacity hover:underline focus-visible:opacity-100 group-hover:opacity-100"
                        aria-label="复制完整 SHA-256" onClick={() => void copyHash(e.sha256 as string)}>复制</button>
                    </span>
                  ) : '—'}
                </td>
                <td className="px-2 py-2.5">
                  <span className={`rounded px-2 py-0.5 text-xs ${EVIDENCE_STATUS_CLASS[e.status] ?? 'bg-muted text-muted-foreground'}`}>{evidenceStatusLabel(e.status)}</span>
                </td>
                <td className="px-4 py-2.5">
                  <div className="flex gap-2 text-xs">
                    {e.status !== 'VERIFIED' && <button className="text-primary" onClick={() => void setStatus(e.id, 'VERIFIED')}>核验通过</button>}
                    {e.status !== 'REJECTED' && <button className="text-red-500" onClick={() => void setStatus(e.id, 'REJECTED')}>驳回</button>}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
