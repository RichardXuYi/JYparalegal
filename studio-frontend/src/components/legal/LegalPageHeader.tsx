import type { ReactNode } from 'react';

/** Consistent header row for legal business pages (title left, actions right). */
export function LegalPageHeader({ title, actions }: { title: string; actions?: ReactNode }) {
  return (
    <div className="flex shrink-0 items-center gap-3 pb-3">
      <h1 className="text-lg font-semibold tracking-tight">{title}</h1>
      <div className="ml-auto flex items-center gap-2">{actions}</div>
    </div>
  );
}
