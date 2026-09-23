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
  icon?: ComponentType<{ className?: string }>;
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
  gradientClass,
}: SettingsPageHeaderProps) {
  return (
    <div className={cn('mb-4 sm:mb-8 flex items-start gap-3 sm:gap-6', className)}>
      {Icon && (
        <div className={cn(
          'flex h-10 w-10 sm:h-16 sm:w-16 shrink-0 items-center justify-center rounded-2xl shadow-xl',
          gradientClass || 'gradient-general'
        )}>
          <Icon className="h-5 w-5 sm:h-8 sm:w-8 text-white" />
        </div>
      )}
      <div className="flex-1 min-w-0">
        <h1
          data-testid={titleTestId}
          className="text-xl sm:text-3xl font-bold tracking-tight gradient-text"
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
