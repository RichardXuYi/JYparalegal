/**
 * Settings page shell.
 * Standard container for full-height "fill" settings surfaces: a centered
 * reading column with a fixed hero header and an independently scrolling body.
 * Consolidates the previously duplicated
 * `flex flex-col h-full ... max-w-5xl mx-auto p-10 pt-16` boilerplate.
 */
import type { ComponentType, ReactNode } from 'react';
import { cn } from '@/lib/utils';
import { SettingsPageHeader } from './SettingsPageHeader';

interface SettingsPageShellProps {
  title: ReactNode;
  description?: ReactNode;
  /** Right-aligned header action slot. */
  actions?: ReactNode;
  children: ReactNode;
  /** Applied to the outer wrapper (e.g. data-testid). */
  testId?: string;
  /** Forwarded to the header <h1> for E2E stability. */
  titleTestId?: string;
  /** Icon component for the header. */
  icon?: ComponentType<{ className?: string }>;
  /** Gradient class for the header icon background (e.g., 'gradient-updates'). */
  gradientClass?: string;
  /** Extra classes for the outer wrapper. */
  className?: string;
  /** Extra classes for the scrollable body region. */
  bodyClassName?: string;
  /** Extra classes for the centered column. */
  containerClassName?: string;
}

export function SettingsPageShell({
  title,
  description,
  actions,
  children,
  testId,
  titleTestId,
  icon,
  gradientClass,
  className,
  bodyClassName,
  containerClassName,
}: SettingsPageShellProps) {
  return (
    <div
      data-testid={testId}
      className={cn('flex h-full w-full flex-col overflow-hidden', className)}
    >
      <div
        className={cn(
          'mx-auto flex h-full w-full max-w-5xl flex-col px-4 sm:px-6 sm:pr-12 pt-4 sm:pt-8',
          containerClassName,
        )}
      >
        <SettingsPageHeader
          title={title}
          description={description}
          actions={actions}
          titleTestId={titleTestId}
          icon={icon}
          gradientClass={gradientClass}
        />
        <div
          className={cn(
            '-mr-2 min-h-0 flex-1 overflow-y-auto pr-2 pb-8',
            bodyClassName,
          )}
        >
          {children}
        </div>
      </div>
    </div>
  );
}

export default SettingsPageShell;
