import { cn } from '@/lib/utils';

/** 法大大-style status filter chip row. */
export function StatusFilterChips({
  chips,
  active,
  onSelect,
}: {
  chips: string[];
  active: string;
  onSelect: (chip: string) => void;
}) {
  return (
    <div className="flex flex-wrap gap-2 border-b border-border px-4 py-2.5">
      {chips.map((c) => (
        <button
          key={c}
          type="button"
          onClick={() => onSelect(c)}
          className={cn(
            'rounded-full border px-3 py-1 text-xs transition-colors',
            active === c
              ? 'border-primary/40 bg-primary/15 font-semibold text-primary'
              : 'border-transparent bg-muted text-muted-foreground hover:bg-muted/80',
          )}
        >
          {c}
        </button>
      ))}
    </div>
  );
}
