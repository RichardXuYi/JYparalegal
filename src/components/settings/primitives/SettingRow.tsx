/**
 * Setting row.
 * One labelled setting: optional icon + title + optional description on the
 * left and a control slot on the right. Designed to stack inside a
 * SettingsGroup. Features hover animation with subtle slide effect.
 */
import type { ComponentType, ReactNode } from 'react';
import { cn } from '@/lib/utils';

interface SettingRowProps {
  label: ReactNode;
  description?: ReactNode;
  /** Right-aligned control (Switch, Button, status pill, ...). */
  control?: ReactNode;
  /** Full-width content rendered below the label/control line. */
  children?: ReactNode;
  className?: string;
  /** Vertical alignment of the control relative to the label block. */
  align?: 'center' | 'start';
  /** Optional icon displayed on the left side. */
  icon?: ComponentType<{ className?: string; strokeWidth?: number }>;
}

export function SettingRow({
  label,
  description,
  control,
  children,
  className,
  align = 'center',
  icon: Icon,
}: SettingRowProps) {
  return (
    <div className={cn(
      'flex flex-col p-3 sm:p-4 rounded-xl transition-colors duration-200',
      'sm:hover:bg-muted/40',
      className
    )}>
      <div className={cn('flex justify-between gap-4', align === 'start' ? 'items-start' : 'items-center')}>
        <div className={cn('flex gap-3 flex-1', align === 'start' ? 'items-start' : 'items-center')}>
          {Icon && (
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <Icon className="h-4 w-4" strokeWidth={1.75} />
            </div>
          )}
          <div className="flex-1 min-w-0">
            <div className="text-sm font-medium text-foreground">{label}</div>
            {description && (
              <p className="text-xs text-muted-foreground mt-0.5">{description}</p>
            )}
          </div>
        </div>
        {control && (
          <div className="flex shrink-0 flex-wrap items-center gap-2">{control}</div>
        )}
      </div>
      {children && <div className="mt-3 w-full">{children}</div>}
    </div>
  );
}

export default SettingRow;
