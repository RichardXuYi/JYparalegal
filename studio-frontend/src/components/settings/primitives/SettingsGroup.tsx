/**
 * Settings group.
 * A grouped card with glassmorphism effect: an optional gradient header bar
 * with icon + title + description, then a glass-effect surface that hosts
 * stacked SettingRows.
 */
import type { ComponentType, ReactNode } from 'react';
import { cn } from '@/lib/utils';

interface SettingsGroupProps {
  title?: ReactNode;
  description?: ReactNode;
  icon?: ComponentType<{ className?: string }>;
  children: ReactNode;
  className?: string;
  /**
   * When true the card body gets uniform padding for free-form content.
   * When false (default) children are treated as rows and separated by dividers.
   */
  padded?: boolean;
  testId?: string;
  /** Gradient class for the header background (e.g., 'gradient-models'). */
  gradientClass?: string;
}

export function SettingsGroup({
  title,
  description,
  icon: Icon,
  children,
  className,
  padded = false,
  testId,
  gradientClass,
}: SettingsGroupProps) {
  return (
    <section data-testid={testId} className={cn('space-y-3 sm:space-y-4', className)}>
      <div className="overflow-hidden rounded-2xl glass-card">
        {(title || description) && (
          <div className="flex items-center gap-3 px-4 pt-4 pb-3 sm:px-6 sm:pt-5 sm:pb-4 border-b border-border/60">
            {Icon && (
              <div className={cn(
                'flex h-9 w-9 shrink-0 items-center justify-center rounded-xl shadow-sm',
                gradientClass || 'gradient-general'
              )}>
                <Icon className="h-[18px] w-[18px] text-white" />
              </div>
            )}
            <div className="min-w-0">
              {title && <h2 className="text-sm font-semibold text-foreground">{title}</h2>}
              {description && <p className="text-xs text-muted-foreground mt-0.5">{description}</p>}
            </div>
          </div>
        )}
        <div
          className={cn(
            padded ? 'p-3 sm:p-6' : 'p-2 divide-y divide-border/40',
          )}
        >
          {children}
        </div>
      </div>
    </section>
  );
}

export default SettingsGroup;
