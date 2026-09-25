/** 分页页码窗口:首尾页 + 当前页±1,中间用省略号。当前页始终可见。 */
function pageWindow(current: number, total: number): (number | '…')[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);
  const out: (number | '…')[] = [1];
  if (current > 3) out.push('…');
  for (let i = Math.max(2, current - 1); i <= Math.min(total - 1, current + 1); i++) out.push(i);
  if (current < total - 2) out.push('…');
  out.push(total);
  return out;
}

/** 法大大-style pagination footer. */
export function Pagination({
  page,
  totalPages,
  total,
  pageSize,
  onPage,
}: {
  page: number;
  totalPages: number;
  total: number;
  pageSize: number;
  onPage: (p: number) => void;
}) {
  return (
    <div className="flex flex-wrap items-center justify-end gap-3 px-4 py-3 text-xs text-muted-foreground">
      <span>
        共 {total} 条 · 第 {page} / {totalPages} 页 · {pageSize} 条/页
      </span>
      <div className="flex items-center gap-1">
        <button
          type="button"
          className="rounded px-2 py-1 hover:bg-muted disabled:opacity-40"
          disabled={page <= 1}
          onClick={() => onPage(page - 1)}
          aria-label="上一页"
        >
          ‹
        </button>
        {pageWindow(page, totalPages).map((p, i) =>
          p === '…' ? (
            <span key={`gap-${i}`} className="px-1">
              …
            </span>
          ) : (
            <button
              key={p}
              type="button"
              onClick={() => onPage(p)}
              aria-current={p === page ? 'page' : undefined}
              className={`rounded px-2 py-1 ${p === page ? 'bg-primary text-primary-foreground' : 'hover:bg-muted'}`}
            >
              {p}
            </button>
          ),
        )}
        <button
          type="button"
          className="rounded px-2 py-1 hover:bg-muted disabled:opacity-40"
          disabled={page >= totalPages}
          onClick={() => onPage(page + 1)}
          aria-label="下一页"
        >
          ›
        </button>
      </div>
    </div>
  );
}
