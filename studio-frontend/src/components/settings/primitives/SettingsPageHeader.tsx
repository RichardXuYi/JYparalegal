/**
 * Settings page header.
 * Canonical hero header shared by feature pages and preference sections:
 * a large icon with gradient background, gradient title, an optional muted
 * description, and optional right-aligned actions.
 */
import type { ComponentType, ReactNode } from 'react';
import { cn } from '@/lib/utils';

interface SettingsPageHeaderProps {
  title: ReactNode;
  description?: ReactNode;
  /** Right-aligned action slot (buttons, status pills, etc.). */
  actions?: ReactNode;
  className?: string;
  /** Forwarded to the <h1> for E2E stability. */
  titleTestId?: string;
  /** Icon component for the header. */
  icon?: ComponentType<{ className?: string; strokeWidth?: number }>;
  /** Gradient class for the icon background (e.g., 'gradient-models'). */
  gradientClass?: string;
}

export function SettingsPageHeader({
  title,
  description,
  actions,
  className,
  titleTestId,
  icon: Icon,
}: SettingsPageHeaderProps) {
  return (
    <div className={cn('mb-4 sm:mb-8 flex items-start gap-3 sm:gap-6', className)}>
      {Icon && (
        <div className="flex h-10 w-10 sm:h-14 sm:w-14 shrink-0 items-center justify-center rounded-2xl bg-primary/10 text-primary">
          <Icon className="h-5 w-5 sm:h-7 sm:w-7" strokeWidth={1.75} />
        </div>
      )}
      <div className="flex-1 min-w-0">
        <h1
          data-testid={titleTestId}
          className="text-xl sm:text-3xl font-bold tracking-tight text-foreground"
        >
          {title}
        </h1>
        {description && (
          <p className="mt-1.5 sm:mt-2 text-xs sm:text-base text-muted-foreground">{description}</p>
        )}
      </div>
      {actions && <div className="flex shrink-0 items-center gap-3">{actions}</div>}
    </div>
  );
}

export default SettingsPageHeader;
