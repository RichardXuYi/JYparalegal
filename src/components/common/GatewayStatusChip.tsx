/**
 * Gateway Status Chip
 * Clickable connection-state pill shared by the desktop TopBar and the mobile
 * top bar. A terminal failure opens the failure dialog directly; any other
 * state opens a lightweight popover with the live facts (state, port, uptime,
 * readiness) and a manual retry. This removes the old silent red dot.
 */
import { useState } from 'react';
import { AlertTriangle, Loader2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';
import { useGatewayStore } from '@/stores/gateway';
import { useGatewayUiStore } from '@/stores/gateway-ui';

interface GatewayStatusChipProps {
  /** Glass-on-dark styling for the desktop shell header. */
  glass?: boolean;
  className?: string;
}

function formatUptime(uptimeSeconds: number | undefined, t: (key: string, options?: Record<string, unknown>) => string): string {
  if (uptimeSeconds === undefined) return t('gateway.popover.valueUnknown');
  if (uptimeSeconds < 60) return t('gateway.popover.valueSeconds', { seconds: uptimeSeconds });
  const minutes = Math.floor(uptimeSeconds / 60);
  if (minutes < 60) return t('gateway.popover.valueMinutes', { minutes });
  return t('gateway.popover.valueHours', { hours: Math.floor(minutes / 60) });
}

export function GatewayStatusChip({ glass = false, className }: GatewayStatusChipProps) {
  const { t } = useTranslation('common');
  const status = useGatewayStore((s) => s.status);
  const startGateway = useGatewayStore((s) => s.start);
  const openFailureDialog = useGatewayUiStore((s) => s.openFailureDialog);
  const [popoverOpen, setPopoverOpen] = useState(false);

  const isFailed = status.state === 'failed';
  const isReady = status.state === 'running' && status.gatewayReady !== false;
  const isBusy = status.state === 'starting' || status.state === 'reconnecting';

  const label = isFailed
    ? t('gateway.failed')
    : isReady
      ? t('gateway.connected')
      : status.state === 'starting'
        ? t('gateway.starting')
        : status.state === 'reconnecting'
          ? t('gateway.reconnecting')
          : t('gateway.disconnected');

  const handleClick = () => {
    if (isFailed) {
      openFailureDialog(status.failure?.detectedAt ?? 0);
      return;
    }
    setPopoverOpen((open) => !open);
  };

  return (
    <div className="relative shrink-0">
      <button
        type="button"
        onClick={handleClick}
        data-testid="gateway-status-chip"
        aria-expanded={popoverOpen}
        title={label}
        className={cn(
          'flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-tiny transition-opacity hover:opacity-90 focus-visible:outline-none',
          glass ? 'shell-glass text-white/90' : 'text-foreground/80',
          className,
        )}
      >
        {isFailed ? (
          <AlertTriangle className="h-3 w-3 text-red-400" />
        ) : isBusy ? (
          <Loader2 className={cn('h-2.5 w-2.5 animate-spin', glass ? 'text-yellow-300' : 'text-yellow-600')} />
        ) : (
          <span
            className={cn(
              'h-2 w-2 rounded-full',
              isReady
                ? (glass ? 'bg-green-400 shadow-[0_0_0_3px_rgba(74,222,128,0.25)]' : 'bg-green-500')
                : 'bg-red-400',
            )}
          />
        )}
        <span className={glass ? 'text-white/90' : undefined}>{label}</span>
      </button>

      {popoverOpen && (
        <>
          {/* Click-away layer keeps the popover honest without a portal. */}
          <button
            type="button"
            aria-hidden
            tabIndex={-1}
            onClick={() => setPopoverOpen(false)}
            className="fixed inset-0 z-40 cursor-default"
          />
          <div
            role="dialog"
            aria-label={t('gateway.popover.title')}
            data-testid="gateway-status-popover"
            className="absolute right-0 top-full z-50 mt-1.5 w-56 rounded-xl border border-border/60 bg-surface-modal p-3 text-foreground shadow-xl"
          >
            <p className="text-xs font-semibold">{t('gateway.popover.title')}</p>
            <dl className="mt-2 space-y-1 text-xs">
              <div className="flex justify-between gap-2">
                <dt className="text-muted-foreground">{t('gateway.popover.state')}</dt>
                <dd className="truncate font-medium">{status.state}</dd>
              </div>
              <div className="flex justify-between gap-2">
                <dt className="text-muted-foreground">{t('gateway.popover.port')}</dt>
                <dd className="font-medium">{status.port}</dd>
              </div>
              <div className="flex justify-between gap-2">
                <dt className="text-muted-foreground">{t('gateway.popover.uptime')}</dt>
                <dd className="font-medium">{formatUptime(status.uptime, t)}</dd>
              </div>
              {status.state === 'running' && status.gatewayReady === false && (
                <div className="flex justify-between gap-2">
                  <dt className="text-muted-foreground">{t('gateway.popover.ready')}</dt>
                  <dd className="font-medium text-amber-600 dark:text-amber-400">
                    {t('gateway.popover.valueNo')}
                  </dd>
                </div>
              )}
            </dl>
            <div className="mt-3 flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={() => setPopoverOpen(false)}
                className="rounded-md px-2 py-1 text-xs text-muted-foreground hover:bg-black/5 focus-visible:outline-none dark:hover:bg-white/10"
              >
                {t('gateway.failure.close')}
              </button>
              {!isReady && !isFailed && (
                <button
                  type="button"
                  onClick={() => {
                    setPopoverOpen(false);
                    void startGateway();
                  }}
                  className="rounded-md bg-primary px-2.5 py-1 text-xs font-medium text-primary-foreground hover:opacity-90 focus-visible:outline-none"
                >
                  {t('gateway.banner.retry')}
                </button>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
