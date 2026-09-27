import { cn } from '@/lib/utils';

export interface TodoTab {
  key: string;
  label: string;
  count?: number;
}

export interface TodoItem {
  id: string | number;
  title: string;
  sub?: string;
  actionLabel: string;
  onAction: () => void;
}

/** 法大大-style 待办 panel with sub-tabs and a right-aligned action per row. */
export function TodoList({
  tabs,
  activeTab,
  onTab,
  items,
}: {
  tabs: TodoTab[];
  activeTab: string;
  onTab: (key: string) => void;
  items: TodoItem[];
}) {
  return (
    <div className="flex min-h-0 flex-col rounded-xl border border-border bg-card">
      <div className="flex items-center gap-4 border-b border-border px-4">
        <span className="py-2.5 text-sm font-semibold">待办</span>
        <div className="flex items-center gap-3">
          {tabs.map((tb) => (
            <button
              key={tb.key}
              type="button"
              onClick={() => onTab(tb.key)}
              className={cn(
                'relative py-2.5 text-xs transition-colors',
                activeTab === tb.key
                  ? 'font-semibold text-primary after:absolute after:inset-x-0 after:bottom-0 after:h-0.5 after:rounded-t after:bg-primary'
                  : 'text-muted-foreground hover:text-foreground',
              )}
            >
              {tb.label}
              {typeof tb.count === 'number' && `(${tb.count})`}
            </button>
          ))}
        </div>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto">
        {items.length === 0 && (
          <p className="px-4 py-8 text-center text-sm text-muted-foreground">暂无待办</p>
        )}
        {items.map((it) => (
          <div key={it.id} className="flex items-center gap-3 border-b border-border/60 px-4 py-3 last:border-b-0">
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm text-foreground/90">{it.title}</p>
              {it.sub && <p className="truncate text-tiny text-muted-foreground">{it.sub}</p>}
            </div>
            <button type="button" onClick={it.onAction} className="shrink-0 text-xs text-primary hover:underline">
              {it.actionLabel}
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
