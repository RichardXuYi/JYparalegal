import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';
import { LegalPageHeader } from '@/components/legal/LegalPageHeader';
import { platformGet, platformSend, notifyIfEntitlement } from '@/lib/platform-api';

type Company = { id: number; name: string; tenantId?: number; unifiedCode?: string | null; esignOrgId?: string | null };
type Member = { id: number; username: string; phone?: string | null; roleName?: string | null };
type Dept = { id: number; name: string };

/** 企业管理：本公司/成员/部门 + 企业章机构号绑定（租户内，/platform/companies）。 */
export default function CompanyManage() {
  const [companies, setCompanies] = useState<Company[]>([]);
  const [listState, setListState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [sel, setSel] = useState<number | null>(null);
  // 派生而非存储：数据与「它是为哪个 sel 取的」一起存，选择变化/清空时自然回到空列表，
  // 无需在 effect 里同步 setState（会触发级联渲染），也顺带避免旧响应覆盖新选择的竞态。
  const [loaded, setLoaded] = useState<{ sel: number; members: Member[]; depts: Dept[] } | null>(null);
  // 机构号输入框：与「它是为哪个 sel 编辑的」一起存，选择变化时派生值自然回到当前企业的机构号。
  const [orgEdit, setOrgEdit] = useState<{ sel: number; value: string } | null>(null);
  const [saving, setSaving] = useState(false);
  const members = loaded && loaded.sel === sel ? loaded.members : [];
  const depts = loaded && loaded.sel === sel ? loaded.depts : [];
  const selCompany = companies.find((c) => c.id === sel);
  const orgInput = orgEdit && orgEdit.sel === sel ? orgEdit.value : (selCompany?.esignOrgId ?? '');
  const setOrgInput = (v: string) => { if (sel != null) setOrgEdit({ sel, value: v }); };

  const load = useCallback(() => {
    void platformGet<Company[]>('/platform/companies')
      .then((d) => { setCompanies(Array.isArray(d) ? d : []); setListState('ready'); })
      .catch(() => { setCompanies([]); setListState('error'); });
  }, []);
  useEffect(load, [load]);
  const reload = () => { setListState('loading'); load(); };

  useEffect(() => {
    if (sel == null) return;
    let cancelled = false;
    const fetchList = async <T,>(url: string): Promise<T[]> => {
      try {
        const d = await platformGet<T[]>(url);
        return Array.isArray(d) ? d : [];
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

  const bindOrg = async () => {
    if (sel == null) return;
    setSaving(true);
    try {
      await platformSend(`/platform/companies/${sel}/esign-org-id`, 'PUT', { esignOrgId: orgInput.trim() });
      toast.success('企业机构号已更新');
      load();
    } catch (e) {
      if (!notifyIfEntitlement(e)) toast.error(e instanceof Error ? e.message : '网络错误,更新失败');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="h-full overflow-y-auto p-5">
      <LegalPageHeader title="企业管理" />
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
          <div className="rounded-lg border border-border bg-card p-4">
            <h4 className="mb-3 text-sm font-semibold">企业章（e签宝机构号）</h4>
            {sel == null ? (
              <div className="p-6 text-center text-sm text-muted-foreground">从左侧选择企业</div>
            ) : (
              <div className="space-y-2">
                <p className="text-meta text-muted-foreground">
                  绑定该企业在 e签宝 的机构号（orgId）后，本企业参与方的签署将用企业章完成；留空则退回个人签署（手机号/邮箱）。
                </p>
                <div className="flex items-center gap-2">
                  <input
                    className="h-8 flex-1 rounded-md border border-border bg-card px-2.5 font-mono text-xs"
                    placeholder="e签宝机构号（orgId）"
                    value={orgInput}
                    onChange={(e) => setOrgInput(e.target.value)}
                  />
                  <button
                    className="h-8 rounded-md bg-primary px-4 text-sm text-primary-foreground disabled:opacity-50"
                    disabled={saving}
                    onClick={() => void bindOrg()}
                  >
                    {saving ? '保存中…' : '绑定'}
                  </button>
                </div>
                <div className="text-xs text-muted-foreground">
                  当前：{selCompany?.esignOrgId ? <span className="font-mono">{selCompany.esignOrgId}</span> : '未绑定（个人签署）'}
                </div>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
