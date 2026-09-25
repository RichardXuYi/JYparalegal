import { useState } from 'react';
import { toast } from 'sonner';
import { LegalPageHeader } from '@/components/legal/LegalPageHeader';

type Seg = { type: 'same' | 'add' | 'del'; text: string };

/** 智能比对：两版合同句子级 LCS diff（/platform/review/compare）。 */
export default function Compare() {
  const [a, setA] = useState('');
  const [b, setB] = useState('');
  const [segs, setSegs] = useState<Seg[] | null>(null);
  const [counts, setCounts] = useState<{ added: number; deleted: number; sameCount: number } | null>(null);
  const [busy, setBusy] = useState(false);

  const run = async () => {
    if (!a.trim() || !b.trim()) { toast.error('请先粘贴或上传两个版本的合同文本'); return; }
    setBusy(true);
    const res = await fetch('/platform/review/compare', {
      method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ textA: a, textB: b }),
    }).then((r) => r.json()).catch(() => null);
    setBusy(false);
    if (res?.code === 0) {
      setSegs(Array.isArray(res.data?.segments) ? res.data.segments : []);
      setCounts(res.data?.counts ?? null);
    } else {
      setSegs(null); setCounts(null);
      toast.error(`比对失败：${res?.msg ?? '网络错误'}`);
    }
  };

  return (
    <div className="h-full overflow-y-auto p-5">
      <LegalPageHeader
        title="合同比对"
        actions={
          <>
            {counts && (
              <span className="rounded-full bg-muted px-2.5 py-0.5 text-tiny text-muted-foreground">
                新增 {counts.added} 处 · 删除 {counts.deleted} 处
              </span>
            )}
            <button className="rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground" disabled={busy} onClick={() => void run()}>
              {busy ? '比对中…' : '开始比对'}
            </button>
          </>
        }
      />
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        <div>
          <h4 className="mb-1 text-xs font-semibold text-muted-foreground">版本 A</h4>
          <textarea className="min-h-[200px] w-full rounded-md border border-border bg-card p-3 font-mono text-xs" placeholder="粘贴或上传合同版本 A" value={a} onChange={(e) => setA(e.target.value)} />
        </div>
        <div>
          <h4 className="mb-1 text-xs font-semibold text-muted-foreground">版本 B</h4>
          <textarea className="min-h-[200px] w-full rounded-md border border-border bg-card p-3 font-mono text-xs" placeholder="粘贴或上传合同版本 B" value={b} onChange={(e) => setB(e.target.value)} />
        </div>
      </div>
      {segs && (
        <div className="mt-4 rounded-lg border border-border bg-card p-4">
          <h4 className="mb-2 text-sm font-semibold">差异结果</h4>
          <div className="space-y-1 text-sm leading-relaxed">
            {segs.map((s, i) => (
              <span key={i} className={
                s.type === 'add' ? 'rounded bg-green-500/15 px-1 text-green-700 dark:text-green-400'
                : s.type === 'del' ? 'rounded bg-red-500/15 px-1 text-red-700 dark:text-red-400 line-through'
                : 'text-foreground'
              }>{s.text}</span>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
