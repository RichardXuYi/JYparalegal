/**
 * Collapsible
 * Lightweight controlled collapsible primitive: a trigger row with a rotating
 * ChevronRight indicator plus an indented content area. Styling matches the
 * hand-rolled session-bucket folding used in the Sidebar so it can be reused
 * for the agent tree and future collapsible sections.
 */
import { ChevronRight } from 'lucide-react';
import { cn } from '@/lib/utils';

interface CollapsibleProps {
  open: boolean;
  onToggle: () => void;
  /** Content rendered inside the trigger row, next to the chevron. */
  trigger: React.ReactNode;
  children: React.ReactNode;
  className?: string;
  triggerClassName?: string;
  contentClassName?: string;
  testId?: string;
}

export function Collapsible({
  open,
  onToggle,
  trigger,
  children,
  className,
  triggerClassName,
  contentClassName,
  testId,
}: CollapsibleProps) {
  return (
    <div data-testid={testId} className={className}>
      <button
        type="button"
        aria-expanded={open}
        data-testid={testId ? `${testId}-toggle` : undefined}
        onClick={onToggle}
        className={cn(
          'flex w-full items-center gap-1 rounded-md px-2.5 py-1 text-left transition-colors',
          'text-muted-foreground/70 hover:bg-black/5 hover:text-muted-foreground dark:hover:bg-white/5',
          triggerClassName,
        )}
      >
        <ChevronRight
          className={cn('h-3 w-3 shrink-0 transition-transform', open && 'rotate-90')}
        />
        {trigger}
      </button>
      {open && <div className={cn('pl-3', contentClassName)}>{children}</div>}
    </div>
  );
}
