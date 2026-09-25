import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { LegalPageHeader } from '@/components/legal/LegalPageHeader';

type Tpl = { id: number; title: string; category: string | null; body: string | null; variables: string | null; status: string };

/** 合同模板库：列表/新建/变量渲染/用模板发起签署（/platform/templates）。 */
export default function Templates() {
  const [list, setList] = useState<Tpl[]>([]);
  const [listState, setListState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [sel, setSel] = useState<Tpl | null>(null);
  const [vars, setVars] = useState<Record<string, string>>({});
  const [rendered, setRendered] = useState<string | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ title: '', category: '', body: '', variables: '' });
  const nav = useNavigate();

  const load = useCallback(() => {
    void fetch('/platform/templates', { credentials: 'include' }).then((r) => r.json())
      .then((e) => { setList(Array.isArray(e?.data) ? e.data : []); setListState('ready'); })
      .catch(() => { setList([]); setListState('error'); });
  }, []);
  useEffect(load, [load]);
  const reload = () => { setListState('loading'); load(); };

  const create = async () => {
    if (!form.title.trim()) { toast.error('请填写模板标题'); return; }
    try {
      const res = await fetch('/platform/templates', {
        method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...form, variables: form.variables.split(',').map((s) => s.trim()).filter(Boolean) }),
      }).then((r) => r.json());
      if (res?.code === 0) { toast.success('模板已保存'); setShowCreate(false); load(); }
      else toast.error(res?.msg ?? '保存失败');
    } catch { toast.error('网络错误,保存失败'); }
  };

  const render = async () => {
    if (!sel) return;
    try {
      const res = await fetch(`/platform/templates/${sel.id}/render`, {
        method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ variables: vars }),
      }).then((r) => r.json());
      if (res?.code === 0) {
        const d = res.data;
        const text = typeof d === 'string' ? d : (d?.content ?? d?.body ?? d?.text ?? '');
        setRendered(text || '（渲染结果为空）');
      } else {
        setRendered(null);
        toast.error(`渲染失败：${res?.msg ?? '未知原因'}`);
      }
    } catch {
      setRendered(null);
      toast.error('网络错误,渲染失败');
    }
  };

  // 注意：这是个普通 async 函数，不是 Hook —— 名字带 use 前缀会被 rules-of-hooks 误判，故命名为 startSigning*。
  const startSigningFromTemplate = async () => {
    if (!sel) return;
    try {
      const res = await fetch('/platform/sign/tasks', {
        method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ title: sel.title }),
      }).then((r) => r.json());
      if (res?.data?.id) nav(`/signing/${res.data.id}`);
      else toast.error(res?.msg ?? '发起签署失败');
    } catch { toast.error('网络错误,发起签署失败'); }
  };

  return (
    <div className="h-full overflow-y-auto p-5">
      <LegalPageHeader
        title="合同模板库"
        actions={
          <button className="rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground" onClick={() => setShowCreate(!showCreate)}>＋ 新建模板</button>
        }
      />

      {showCreate && (
        <div className="mb-4 grid gap-3 rounded-lg border border-border bg-card p-4 text-sm">
          <label className="block">
            <span className="text-xs text-muted-foreground">标题 <span className="text-red-500">*</span></span>
            <input className="mt-1 h-8 w-full rounded-md border border-border bg-card px-2.5" placeholder="例如:采购合同" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
          </label>
          <label className="block">
            <span className="text-xs text-muted-foreground">分类</span>
            <input className="mt-1 h-8 w-full rounded-md border border-border bg-card px-2.5" placeholder="例如:采购 / 劳动 / 保密" value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} />
          </label>
          <label className="block">
            <span className="text-xs text-muted-foreground">正文</span>
            <textarea className="mt-1 min-h-[100px] w-full rounded-md border border-border bg-card p-2.5 text-sm leading-relaxed" placeholder="粘贴合同正文,用 {{变量名}} 标记可替换字段" value={form.body} onChange={(e) => setForm({ ...form, body: e.target.value })} />
          </label>
          <label className="block">
            <span className="text-xs text-muted-foreground">变量</span>
            <input className="mt-1 h-8 w-full rounded-md border border-border bg-card px-2.5" placeholder="逗号分隔,例如:partyA,partyB,penalty" value={form.variables} onChange={(e) => setForm({ ...form, variables: e.target.value })} />
            <span className="mt-1 block text-xs text-muted-foreground">每个变量对应正文中的 {'{{变量名}}'} 占位</span>
          </label>
          <button className="w-fit rounded-md bg-primary px-4 py-1.5 text-sm text-primary-foreground" onClick={() => void create()}>保存模板</button>
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[280px_1fr]">
        <div className="rounded-lg border border-border bg-card p-3">
          {listState === 'loading' && <div className="p-4 text-center text-sm text-muted-foreground">正在加载…</div>}
          {listState === 'error' && (
            <div className="flex flex-col items-center gap-3 p-4 text-center text-sm">
              <span className="text-muted-foreground">模板加载失败</span>
              <button className="rounded-md border border-border px-3 py-1.5 hover:bg-muted" onClick={reload}>重试</button>
            </div>
          )}
          {listState === 'ready' && list.length === 0 && <div className="p-4 text-center text-sm text-muted-foreground">暂无模板</div>}
          {listState === 'ready' && list.map((t) => (
            <button key={t.id} onClick={() => { setSel(t); setRendered(null); setVars({}); }}
              className={`mb-1 w-full rounded-md px-3 py-2 text-left text-sm ${sel?.id === t.id ? 'bg-primary/15 text-primary' : 'hover:bg-muted'}`}>
              <div className="truncate">{t.title}</div>
              <div className="text-xs text-muted-foreground">{t.category ?? '未分类'}</div>
            </button>
          ))}
        </div>
        <div className="rounded-lg border border-border bg-card p-4 text-sm">
          {!sel && <div className="p-8 text-center text-muted-foreground">选择或新建模板</div>}
          {sel && (
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <span className="font-semibold">{sel.title}</span>
                <span className="rounded bg-muted px-2 py-0.5 text-xs">{sel.category}</span>
                <button className="ml-auto rounded-md bg-primary px-3 py-1.5 text-xs text-primary-foreground" onClick={() => void startSigningFromTemplate()}>用此模板发起签署</button>
              </div>
              <pre className="max-h-[220px] overflow-auto rounded-md bg-muted/40 p-3 font-mono text-xs whitespace-pre-wrap">{sel.body}</pre>
              <div className="grid grid-cols-3 gap-2">
                {(JSON.parse(sel.variables ?? '[]') as string[]).map((v) => (
                  <input key={v} className="h-8 rounded-md border border-border bg-card px-2.5 text-xs" placeholder={v}
                    value={vars[v] ?? ''} onChange={(e) => setVars({ ...vars, [v]: e.target.value })} />
                ))}
              </div>
              <button className="rounded-md border border-border px-3 py-1.5 text-xs" onClick={() => void render()}>渲染预览</button>
              {rendered && <pre className="max-h-[200px] overflow-auto rounded-md bg-muted/40 p-3 font-mono text-xs whitespace-pre-wrap">{rendered}</pre>}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
