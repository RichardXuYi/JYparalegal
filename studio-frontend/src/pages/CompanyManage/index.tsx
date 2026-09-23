import { useCallback, useEffect, useState } from 'react';
import { Building2 } from 'lucide-react';

type Company = { id: number; name: string; tenantId?: number; unifiedCode?: string | null };
type Member = { id: number; username: string; phone?: string | null; roleName?: string | null };
type Dept = { id: number; name: string };

/** 企业管理：本公司/成员/部门（租户内，/platform/companies）。 */
export default function CompanyManage() {
  const [companies, setCompanies] = useState<Company[]>([]);
  const [listState, setListState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [sel, setSel] = useState<number | null>(null);
  // 派生而非存储：数据与「它是为哪个 sel 取的」一起存，选择变化/清空时自然回到空列表，
  // 无需在 effect 里同步 setState（会触发级联渲染），也顺带避免旧响应覆盖新选择的竞态。
  const [loaded, setLoaded] = useState<{ sel: number; members: Member[]; depts: Dept[] } | null>(null);
  const members = loaded && loaded.sel === sel ? loaded.members : [];
  const depts = loaded && loaded.sel === sel ? loaded.depts : [];

  const load = useCallback(() => {
    void fetch('/platform/companies', { credentials: 'include' }).then((r) => r.json())
      .then((e) => { setCompanies(Array.isArray(e?.data) ? e.data : []); setListState('ready'); })
      .catch(() => { setCompanies([]); setListState('error'); });
  }, []);
  useEffect(load, [load]);
  const reload = () => { setListState('loading'); load(); };

  useEffect(() => {
    if (sel == null) return;
    let cancelled = false;
    const fetchList = async <T,>(url: string): Promise<T[]> => {
      try {
        const e = await fetch(url, { credentials: 'include' }).then((r) => r.json());
        return Array.isArray(e?.data) ? (e.data as T[]) : [];
      } catch {
        return [];
      }
    };
    void (async () => {
      const [m, d] = await Promise.all([
        fetchList<Member>(`/platform/companies/${sel}/members`),
        fetchList<Dept>(`/platform/companies/${sel}/departments`),
      ]);
      if (!cancelled) setLoaded({ sel, members: m, depts: d });
    })();
    return () => { cancelled = true; };
  }, [sel]);

  return (
    <div className="h-full overflow-y-auto p-5">
      <h1 className="mb-4 flex items-center gap-2 text-lg font-semibold"><Building2 className="h-5 w-5 text-primary" />企业管理</h1>
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-[280px_1fr]">
        <div className="rounded-lg border border-border bg-card p-3">
          <h4 className="mb-2 px-1 text-xs font-semibold text-muted-foreground">企业</h4>
          {listState === 'loading' && <div className="p-4 text-center text-sm text-muted-foreground">正在加载…</div>}
          {listState === 'error' && (
            <div className="flex flex-col items-center gap-3 p-4 text-center text-sm">
              <span className="text-muted-foreground">企业加载失败</span>
              <button className="rounded-md border border-border px-3 py-1.5 hover:bg-muted" onClick={reload}>重试</button>
            </div>
          )}
          {listState === 'ready' && companies.length === 0 && <div className="p-4 text-center text-sm text-muted-foreground">暂无企业</div>}
          {listState === 'ready' && companies.map((c) => (
            <button key={c.id} onClick={() => setSel(c.id)}
              className={`mb-1 w-full rounded-md px-3 py-2 text-left text-sm ${sel === c.id ? 'bg-primary/15 text-primary' : 'hover:bg-muted'}`}>
              <div className="truncate">{c.name}</div>
              {c.unifiedCode && <div className="text-xs text-muted-foreground">{c.unifiedCode}</div>}
            </button>
          ))}
        </div>
        <div className="space-y-4">
          <div className="rounded-lg border border-border bg-card p-4">
            <h4 className="mb-3 text-sm font-semibold">成员（{members.length}）</h4>
            {sel == null ? (
              <div className="p-6 text-center text-sm text-muted-foreground">从左侧选择企业</div>
            ) : members.length === 0 ? (
              <div className="p-6 text-center text-sm text-muted-foreground">这家企业还没有成员</div>
            ) : (
              <table className="w-full text-sm">
                <thead><tr className="border-b border-border text-left text-xs text-muted-foreground"><th scope="col" className="py-2">用户名</th><th scope="col" className="py-2">手机号</th><th scope="col" className="py-2">角色</th></tr></thead>
                <tbody>
                  {members.map((m) => (
                    <tr key={m.id} className="border-b border-border/60">
                      <td className="py-2">{m.username}</td>
                      <td className="py-2 text-xs text-muted-foreground">{m.phone ?? '—'}</td>
                      <td className="py-2 text-xs">{m.roleName ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
          <div className="rounded-lg border border-border bg-card p-4">
            <h4 className="mb-3 text-sm font-semibold">部门（{depts.length}）</h4>
            {depts.length === 0 && <div className="p-6 text-center text-sm text-muted-foreground">暂无部门</div>}
            <div className="flex flex-wrap gap-2">
              {depts.map((d) => <span key={d.id} className="rounded-md bg-muted px-3 py-1.5 text-xs">{d.name}</span>)}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
