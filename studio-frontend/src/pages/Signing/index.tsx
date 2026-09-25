import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { toast } from 'sonner';
import { fmtDateTime, signStatusChipClass, signStatusLabel } from '@/lib/legal-enums';
import { platformGet, platformProbe, platformSend, notifyIfEntitlement } from '@/lib/platform-api';
import { LegalPageHeader } from '@/components/legal/LegalPageHeader';
import { StatusFilterChips } from '@/components/legal/StatusFilterChips';
import { Pagination } from '@/components/legal/Pagination';
import { EntitlementGate } from '@/components/legal/EntitlementGate';

type SignTask = {
  id: number; taskNo: string; title: string; status: string;
  initiatorName?: string; recipientName?: string; completedAt?: string; createdAt: string; expireAt?: string;
};

const STATUS_CHIPS = [
  '全部', '草稿', '已创建', '填写中', '定稿中', '签署中', '已完成', '已拒签', '已逾期', '已撤回', '已终止', '作废中', '已作废',
];

const PAGE_SIZE = 50;

/**
 * 导出 CSV —— 提到模块级：内含 DOM 副作用，
 * 放在组件体内会被 React Compiler 判为「渲染期调用不纯函数」。
 */
function downloadCsv(rows: string[][], filename: string): void {
  const csv = ['主题,任务编号,状态,发件人,收件人,截止时间,发起时间,完成时间',
    ...rows.map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(','))].join('\n');
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = filename.endsWith('.csv') ? filename : `${filename}.csv`;
  a.click();
  URL.revokeObjectURL(a.href);
}

export default function Signing() {
  const [tasks, setTasks] = useState<SignTask[]>([]);
  const [listState, setListState] = useState<'loading' | 'ready' | 'error' | 'locked'>('loading');
  const [params] = useSearchParams();
  const view = params.get('view') ?? 'CREATED_BY_ME';
  // chip/page 与「它们属于哪个 view」一起存；view 变化时派生值自动回到「全部」/第 1 页，
  // 不必在 effect 里同步 setState（那会触发级联渲染）。
  const [chipState, setChipState] = useState<{ view: string; chip: string }>({ view, chip: '全部' });
  const [pageState, setPageState] = useState<{ view: string; page: number }>({ view, page: 1 });
  const chip = chipState.view === view ? chipState.chip : '全部';
  const page = pageState.view === view ? pageState.page : 1;
  const setChip = (c: string) => setChipState({ view, chip: c });
  const setPage = (p: number) => setPageState({ view, page: p });
  const [searchTitle, setSearchTitle] = useState('');
  const [searchRecipient, setSearchRecipient] = useState('');
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const nav = useNavigate();

  // 直接写成 effect（不再手工 useCallback）：React Compiler 无法保留手写 memo 时会整体跳过编译；
  // 顺带加 cancelled 守卫，避免 view 快速切换时旧响应覆盖新数据。
  useEffect(() => {
    let cancelled = false;
    void platformProbe<SignTask[]>(`/platform/sign/tasks?view=${view}`)
      .then((r) => {
        if (cancelled) return;
        if (r.locked) { setTasks([]); setListState('locked'); }
        else if (r.ok) { setTasks(Array.isArray(r.data) ? r.data : []); setListState('ready'); }
        else { setTasks([]); setListState('error'); }
      });
    return () => { cancelled = true; };
  }, [view]);

  const reload = () => {
    setListState('loading');
    void platformProbe<SignTask[]>(`/platform/sign/tasks?view=${view}`)
      .then((r) => {
        if (r.locked) { setTasks([]); setListState('locked'); }
        else if (r.ok) { setTasks(Array.isArray(r.data) ? r.data : []); setListState('ready'); }
        else { setTasks([]); setListState('error'); }
      });
  };

  const openTask = (t: SignTask) => nav(t.status === 'DRAFT' ? `/signing/${t.id}/setup` : `/signing/${t.id}`);

  // 即时筛选（客户端）：输入即过滤，条件变化回到第 1 页
  const filtered = tasks.filter((t) => {
    if (chip !== '全部' && signStatusLabel(t.status) !== chip) return false;
    if (searchTitle && !t.title.toLowerCase().includes(searchTitle.toLowerCase())) return false;
    if (searchRecipient && !(t.recipientName ?? '').toLowerCase().includes(searchRecipient.toLowerCase())) return false;
    return true;
  });

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const paged = filtered.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE);

  const updateTitle = (v: string) => { setSearchTitle(v); setPage(1); };
  const updateRecipient = (v: string) => { setSearchRecipient(v); setPage(1); };
  const clearSearch = () => { setSearchTitle(''); setSearchRecipient(''); setPage(1); };
  const hasSearch = searchTitle !== '' || searchRecipient !== '';

  const csvRows = (list: SignTask[]) => list.map((t) => [
    t.title, t.taskNo, signStatusLabel(t.status), t.initiatorName ?? '', t.recipientName ?? '',
    t.expireAt ? fmtDateTime(t.expireAt) : '', t.createdAt ? fmtDateTime(t.createdAt) : '', t.completedAt ? fmtDateTime(t.completedAt) : '',
  ]);
  const exportCsv = () => {
    if (filtered.length === 0) { toast.error('当前筛选没有可导出的任务'); return; }
    downloadCsv(csvRows(filtered), `签署任务-${fmtDateTime(new Date().toISOString()).slice(0, 10)}`);
  };
  const exportSelected = () => {
    const list = filtered.filter((t) => selected.has(t.id));
    if (list.length === 0) { toast.error('未选择任何任务'); return; }
    downloadCsv(csvRows(list), `签署任务-所选${list.length}条`);
  };

  const downloadTask = async (id: number) => {
    try {
      const d = await platformGet<Record<string, string | undefined>>(`/platform/sign/tasks/${id}/download`);
      const url = d?.fileUrl ?? d?.url ?? d?.downloadUrl;
      if (url) { window.open(url, '_blank', 'noopener'); toast.success('已开始下载'); }
      else toast.error('下载失败');
    } catch (e) {
      if (!notifyIfEntitlement(e)) toast.error(e instanceof Error ? e.message : '网络错误,下载失败');
    }
  };

  const requestCertificate = async (id: number) => {
    try {
      await platformSend(`/platform/sign/tasks/${id}/certificate`, 'POST', {});
      toast.success('出证申请已提交');
    } catch (e) {
      if (!notifyIfEntitlement(e)) toast.error(e instanceof Error ? e.message : '网络错误,出证失败');
    }
  };

  const toggleRow = (id: number) => {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };
  const togglePage = () => {
    setSelected((prev) => {
      const next = new Set(prev);
      const allOn = paged.every((t) => next.has(t.id));
      paged.forEach((t) => (allOn ? next.delete(t.id) : next.add(t.id)));
      return next;
    });
  };

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <div className="px-5 pt-4">
        <LegalPageHeader
          title="签署"
          actions={
            <>
              <button className="rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground hover:bg-muted" onClick={exportCsv}>导出当前筛选</button>
              <button className="rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground" onClick={() => nav('/signing/new')}>＋ 创建任务</button>
            </>
          }
        />
      </div>

      <div className="flex-1 overflow-y-auto px-5 pb-4">
        <div className="rounded-lg border border-border bg-card">
          {/* Status filter chips */}
          <StatusFilterChips chips={STATUS_CHIPS} active={chip} onSelect={(c) => { setChip(c); setPage(1); }} />

          {/* Toolbar */}
          <div className="flex flex-wrap items-center gap-2 px-4 py-3">
            <div className="ml-auto flex items-center gap-2">
              <div className="relative">
                <input className="h-8 w-[200px] rounded-md border border-border bg-card px-2.5 pr-7 text-sm placeholder:text-muted-foreground"
                  placeholder="任务主题" value={searchTitle} onChange={(e) => updateTitle(e.target.value)} />
                {searchTitle && (
                  <button className="absolute right-1.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground" aria-label="清空主题搜索" onClick={() => updateTitle('')}>×</button>
                )}
              </div>
              <div className="relative">
                <input className="h-8 w-[200px] rounded-md border border-border bg-card px-2.5 pr-7 text-sm placeholder:text-muted-foreground"
                  placeholder="收件人名称" value={searchRecipient} onChange={(e) => updateRecipient(e.target.value)} />
                {searchRecipient && (
                  <button className="absolute right-1.5 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground" aria-label="清空收件人搜索" onClick={() => updateRecipient('')}>×</button>
                )}
              </div>
              {hasSearch && (
                <button className="rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground hover:bg-muted" onClick={clearSearch}>清空</button>
              )}
            </div>
          </div>

          {/* 批量操作条:选中时浮出 */}
          {selected.size > 0 && (
            <div className="flex items-center gap-3 border-t border-border bg-primary/5 px-4 py-2 text-sm">
              <span className="text-primary">已选 {selected.size} 条</span>
              <button className="rounded-md border border-border px-3 py-1 text-xs hover:bg-muted" onClick={exportSelected}>导出所选</button>
              <button className="ml-auto rounded-md px-3 py-1 text-xs text-muted-foreground hover:bg-muted" onClick={() => setSelected(new Set())}>取消选择</button>
            </div>
          )}

          {/* Table */}
          <div className="overflow-x-auto">
          <table className="w-full min-w-[900px] text-sm">
            <thead>
              <tr className="border-y border-border bg-muted/40 text-left text-xs text-muted-foreground">
                <th scope="col" className="w-[34px] px-3 py-2"><input type="checkbox" className="rounded" aria-label="全选本页" checked={paged.length > 0 && paged.every((t) => selected.has(t.id))} onChange={togglePage} /></th>
                <th scope="col" className="px-2 py-2">主题</th>
                <th scope="col" className="px-2 py-2">任务编号</th>
                <th scope="col" className="px-2 py-2">状态</th>
                <th scope="col" className="px-2 py-2">发件人</th>
                <th scope="col" className="px-2 py-2">收件人</th>
                <th scope="col" className="px-2 py-2">截止时间</th>
                <th scope="col" className="px-2 py-2">发起时间</th>
                <th scope="col" className="px-2 py-2">完成时间</th>
                <th scope="col" className="px-3 py-2">操作</th>
              </tr>
            </thead>
            <tbody>
              {listState === 'loading' && <tr><td colSpan={10} className="p-10 text-center text-muted-foreground">正在加载…</td></tr>}
              {listState === 'locked' && (
                <tr><td colSpan={10}><EntitlementGate onRetry={reload} /></td></tr>
              )}
              {listState === 'error' && (
                <tr><td colSpan={10} className="p-10 text-center">
                  <div className="flex flex-col items-center gap-3 text-sm">
                    <span className="text-muted-foreground">列表加载失败</span>
                    <button className="rounded-md border border-border px-3 py-1.5 text-sm hover:bg-muted" onClick={reload}>重试</button>
                  </div>
                </td></tr>
              )}
              {listState === 'ready' && paged.length === 0 && <tr><td colSpan={10} className="p-10 text-center text-muted-foreground">暂无数据</td></tr>}
              {listState === 'ready' && paged.map((t) => (
                <tr key={t.id} className="border-b border-border/60 hover:bg-muted/30">
                  <td className="px-3 py-2.5"><input type="checkbox" className="rounded" aria-label={`选择 ${t.title}`} checked={selected.has(t.id)} onChange={() => toggleRow(t.id)} /></td>
                  <td className="cursor-pointer px-2 py-2.5 text-primary hover:underline" onClick={() => openTask(t)}>
                    <span className="line-clamp-1 block max-w-[220px]">{t.title}</span>
                  </td>
                  <td className="px-2 py-2.5 font-mono text-xs text-muted-foreground">{t.taskNo}</td>
                  <td className="px-2 py-2.5">
                    <span className={`rounded px-2 py-0.5 text-xs ${signStatusChipClass(t.status)}`}>
                      {signStatusLabel(t.status)}
                    </span>
                  </td>
                  <td className="max-w-[140px] truncate px-2 py-2.5 text-xs text-muted-foreground" title={t.initiatorName ?? ''}>{t.initiatorName ?? '—'}</td>
                  <td className="max-w-[140px] truncate px-2 py-2.5 text-xs text-muted-foreground" title={t.recipientName ?? ''}>{t.recipientName ?? '—'}</td>
                  <td className="px-2 py-2.5 text-xs text-muted-foreground tabular-nums">{t.expireAt ? fmtDateTime(t.expireAt) : '长期有效'}</td>
                  <td className="px-2 py-2.5 text-xs text-muted-foreground tabular-nums">{t.createdAt ? fmtDateTime(t.createdAt) : '—'}</td>
                  <td className="px-2 py-2.5 text-xs text-muted-foreground tabular-nums">{t.completedAt ? fmtDateTime(t.completedAt) : '—'}</td>
                  <td className="px-3 py-2.5">
                    <div className="flex gap-2.5 text-xs">
                      <button className="text-primary hover:underline" onClick={() => openTask(t)}>
                        {t.status === 'DRAFT' ? '继续编辑' : t.status === 'SIGNING' ? '签署' : '详情'}
                      </button>
                      {t.status === 'COMPLETED' && <button className="text-primary hover:underline" onClick={() => void downloadTask(t.id)}>下载</button>}
                      {t.status === 'COMPLETED' && <button className="text-primary hover:underline" onClick={() => void requestCertificate(t.id)}>出证</button>}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          </div>

          {/* Pagination */}
          <Pagination page={page} totalPages={totalPages} total={filtered.length} pageSize={PAGE_SIZE} onPage={setPage} />
        </div>
      </div>
    </div>
  );
}
