import type { LucideIcon } from 'lucide-react';
import { FileUp } from 'lucide-react';

export interface DropZoneAction {
  label: string;
  icon?: LucideIcon;
  onClick: () => void;
}

/** 法大大-style big dashed drop-zone with action cards. */
export function DropZone({
  hint,
  actions,
  onDropFiles,
}: {
  hint: string;
  actions: DropZoneAction[];
  onDropFiles?: (files: FileList) => void;
}) {
  const list = actions;

  return (
    <div
      className="flex min-h-[180px] flex-col items-center justify-center gap-4 rounded-xl border-2 border-dashed border-primary/30 bg-primary/5 p-6"
      onDragOver={(e) => {
        if (onDropFiles) e.preventDefault();
      }}
      onDrop={(e) => {
        if (!onDropFiles) return;
        e.preventDefault();
        if (e.dataTransfer.files?.length) onDropFiles(e.dataTransfer.files);
      }}
    >
      <div className="flex flex-wrap items-center justify-center gap-3">
        {list.map((a) => {
          const Icon = a.icon ?? FileUp;
          return (
            <button
              key={a.label}
              type="button"
              onClick={a.onClick}
              className="flex w-[120px] flex-col items-center gap-2 rounded-lg border border-border bg-card px-3 py-4 text-xs text-foreground/80 shadow-sm transition-colors hover:border-primary/40 hover:text-primary"
            >
              <Icon className="h-5 w-5 text-primary" strokeWidth={1.75} />
              {a.label}
            </button>
          );
        })}
      </div>
      <p className="text-sm font-medium text-foreground/80">{hint}</p>
    </div>
  );
}
