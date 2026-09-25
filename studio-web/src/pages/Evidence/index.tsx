import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';
import { LegalPageHeader } from '@/components/legal/LegalPageHeader';
import { fmtDateTime } from '@/lib/legal-enums';

type Row = { taskId: number; taskNo: string; title: string; hasEvidence: boolean; createdAt: string | null };
type AntInfo = { available?: boolean; message?: string; provider?: string; [k: string]: unknown };

const FIELD_LABEL: Record<string, string> = {
  fileHash: '文件哈希',
  antTxHash: '上链交易哈希',
  transactionId: '交易 ID',
  blockHeight: '区块高度',
  blockTime: '上链时间',
  timestamp: '时间戳',
  verifyResult: '核验结果',
  verifyStatus: '核验状态',
  status: '状态',
};

/** 证据存证：存证来自 e签宝签署流程的蚂蚁链上链记录（非自建存证库），经 CP 获取与核验。 */
export default function Evidence() {
  const [rows, setRows] = useState<Row[]>([]);
  const [listState, setListState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [open, setOpen] = useState<{ taskId: number; data: AntInfo } | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);

  const load = useCallback(() => {
    void fetch('/platform/evidence', { credentials: 'include' }).then((r) => r.json())
      .then((e) => { setRows(Array.isArray(e?.data) ? e.data : []); setListState('ready'); })
      .catch(() => { setRows([]); setListState('error'); });
  }, []);
  useEffect(load, [load]);
  const reload = () => { setListState('loading'); load(); };

  const view = async (taskId: number) => {
    setOpen({ taskId, data: {} });
    try {
      const e = await fetch(`/platform/evidence/${taskId}/antchain`, { credentials: 'include' }).then((r) => r.json());
      setOpen({ taskId, data: (e?.data as AntInfo) ?? { available: false, message: e?.msg ?? '获取失败' } });
    } catch {
      setOpen({ taskId, data: { available: false, message: '网络错误' } });
    }
  };

  const verify = async (taskId: number) => {
    setBusyId(taskId);
    try {
      const e = await fetch(`/platform/evidence/${taskId}/antchain/verify`, {
        method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: '{}',
      }).then((r) => r.json());
      const d = (e?.data as AntInfo) ?? { available: false, message: e?.msg ?? '核验失败' };
      if (d.available === false) toast.error(String(d.message ?? '核验失败'));
      else toast.success('核验已完成');
      setOpen({ taskId, data: d });
    } catch {
      toast.error('网络错误,核验失败');
    } finally {
      setBusyId(null);
    }
  };

  const detailEntries = open && open.data.available !== false
    ? Object.entries(open.data).filter(([k]) => k !== 'available' && k !== 'provider' && k !== 'message')
    : [];

  return (
    <div className="h-full overflow-y-auto p-5">
      <LegalPageHeader title="证据存证" />
      <p className="mb-3 text-meta text-muted-foreground">
        存证来源为 e签宝签署流程的蚂蚁链上链记录(非自建存证库)。仅已完成并已送签的任务可查看与核验。
      </p>
      <div className="rounded-lg border border-border bg-card">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border bg-muted/40 text-left text-xs text-muted-foreground">
              <th className="px-4 py-2">主题</th>
              <th className="px-2 py-2">任务编号</th>
              <th className="px-2 py-2">存证</th>
              <th className="px-2 py-2">完成时间</th>
              <th className="px-4 py-2">操作</th>
            </tr>
          </thead>
          <tbody>
            {listState === 'loading' && <tr><td colSpan={5} className="p-8 text-center text-muted-foreground">正在加载…</td></tr>}
            {listState === 'error' && (
              <tr><td colSpan={5} className="p-8 text-center">
                <div className="flex flex-col items-center gap-3 text-sm">
                  <span className="text-muted-foreground">加载失败</span>
                  <button className="rounded-md border border-border px-3 py-1.5 hover:bg-muted" onClick={reload}>重试</button>
                </div>
              </td></tr>
            )}
            {listState === 'ready' && rows.length === 0 && (
              <tr><td colSpan={5} className="p-8 text-center text-muted-foreground">暂无可存证的已完成签署任务</td></tr>
            )}
            {listState === 'ready' && rows.map((r) => (
              <tr key={r.taskId} className="border-b border-border/60">
                <td className="px-4 py-2.5">{r.title}</td>
                <td className="px-2 py-2.5 font-mono text-xs text-muted-foreground">{r.taskNo}</td>
                <td className="px-2 py-2.5">
                  <span className={`rounded px-2 py-0.5 text-xs ${r.hasEvidence ? 'bg-green-500/10 text-green-700 dark:text-green-400' : 'bg-muted text-muted-foreground'}`}>
                    {r.hasEvidence ? '已上链' : '无存证'}
                  </span>
                </td>
                <td className="px-2 py-2.5 text-xs text-muted-foreground tabular-nums">{r.createdAt ? fmtDateTime(r.createdAt) : '—'}</td>
                <td className="px-4 py-2.5">
                  <div className="flex gap-2 text-xs">
                    <button className="text-primary hover:underline disabled:opacity-50" disabled={!r.hasEvidence} onClick={() => void view(r.taskId)}>查看存证</button>
                    <button className="text-primary hover:underline disabled:opacity-50" disabled={!r.hasEvidence || busyId === r.taskId} onClick={() => void verify(r.taskId)}>
                      {busyId === r.taskId ? '核验中…' : '核验'}
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {open && (
        <div className="mt-4 rounded-lg border border-border bg-card p-4 text-sm">
          <h4 className="mb-2 font-semibold">存证详情</h4>
          {open.data.available === false ? (
            <p className="text-meta text-muted-foreground">{String(open.data.message ?? '存证不可用')}</p>
          ) : (
            <div className="space-y-0">
              <div className="flex justify-between border-b border-dashed border-border py-1.5 text-meta">
                <span className="text-muted-foreground">存证服务商</span>
                <span>{String(open.data.provider ?? 'e签宝')}</span>
              </div>
              {detailEntries.map(([k, v]) => (
                <div key={k} className="flex justify-between gap-4 border-b border-dashed border-border py-1.5 text-meta">
                  <span className="shrink-0 text-muted-foreground">{FIELD_LABEL[k] ?? k}</span>
                  <span className="break-all text-right font-mono text-xs">{v == null || v === '' ? '—' : String(v)}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
