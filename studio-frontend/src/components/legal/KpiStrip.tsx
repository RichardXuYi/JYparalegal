import { cn } from '@/lib/utils';

export interface KpiItem {
  label: string;
  value: number | string;
  tone?: 'default' | 'primary' | 'warn' | 'danger';
}

const TONE_CLASS: Record<NonNullable<KpiItem['tone']>, string> = {
  default: 'text-foreground',
  primary: 'text-primary',
  warn: 'text-yellow-600 dark:text-yellow-400',
  danger: 'text-red-600 dark:text-red-400',
};

/** 法大大-style KPI stat strip: a row of big numbers with captions. */
export function KpiStrip({ items }: { items: KpiItem[] }) {
  return (
    <div className="grid shrink-0 grid-cols-2 divide-border/60 rounded-xl border border-border bg-card sm:grid-cols-4 sm:divide-x">
      {items.map((it) => (
        <div key={it.label} className="flex flex-col items-center gap-1 px-4 py-4">
          <span className="text-tiny text-muted-foreground">{it.label}</span>
          <span className={cn('text-stat font-semibold tabular-nums leading-none', TONE_CLASS[it.tone ?? 'default'])}>
            {it.value}
          </span>
        </div>
      ))}
    </div>
  );
}
