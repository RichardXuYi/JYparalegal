/**
 * Gateway Status Banner
 * Non-blocking, persistent status strip mounted inside the app layouts.
 * Carries every gateway dip that should NOT lock the UI: reconnect backoff
 * (with attempt count and next-retry countdown), degraded readiness, and a
 * compact one-line summary of a terminal failure (with a "details" escape
 * into the full failure dialog).
 */
import { useEffect, useState } from 'react';
import { AlertTriangle, Loader2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';
import { useGatewayStore } from '@/stores/gateway';
import { useGatewayUiStore } from '@/stores/gateway-ui';
import { deriveGatewaySurface } from '@/lib/connection-status';

function formatCountdown(target: number | undefined, now: number | null): string | null {
  if (target === undefined || now === null) return null;
  const seconds = Math.max(0, Math.ceil((target - now) / 1000));
  return String(seconds);
}

export function GatewayStatusBanner() {
  const { t } = useTranslation('common');
  const status = useGatewayStore((s) => s.status);
  const hasSeenRunning = useGatewayStore((s) => s.hasSeenRunningThisSession);
  const startGateway = useGatewayStore((s) => s.start);
  const overlayDismissed = useGatewayUiStore((s) => s.overlayDismissed);
  const overlayGraceExpired = useGatewayUiStore((s) => s.overlayGraceExpired);
  const openFailureDialog = useGatewayUiStore((s) => s.openFailureDialog);

  const surface = deriveGatewaySurface({
    status,
    hasSeenRunningThisSession: hasSeenRunning,
    userDismissedOverlay: overlayDismissed,
    overlayGraceExpired,
  });

  const countdownTarget = surface?.kind === 'banner' ? surface.nextRetryAt : undefined;
  // `now` lives in state (updated by the ticker) so render stays pure. When no
  // countdown is needed the stale value is simply ignored by the renderer.
  const [now, setNow] = useState<number | null>(null);
  useEffect(() => {
    if (countdownTarget === undefined) return undefined;
    const timer = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [countdownTarget]);

  if (surface === null) return null;

  // Terminal failure: compact one-liner; the full dialog renders on top.
  if (surface.kind === 'dialog' || status.state === 'failed') {
    const failure = status.failure;
    const reason = failure
      ? t(failure.reasonKey, { ...failure.reasonParams, defaultValue: t('gateway.failed') })
      : t('gateway.failed');
    return (
      <div
        role="status"
        data-testid="gateway-status-banner"
        className="flex shrink-0 items-center gap-2 border-b border-red-500/30 bg-red-500/10 px-4 py-1.5 text-xs text-red-700 dark:text-red-300"
      >
        <AlertTriangle className="h-3.5 w-3.5 shrink-0" />
        <span className="min-w-0 flex-1 truncate">{reason}</span>
        <button
          type="button"
          onClick={() => void startGateway()}
          className="shrink-0 rounded-md px-2 py-0.5 font-medium hover:bg-red-500/15 focus-visible:outline-none"
        >
          {t('gateway.banner.retry')}
        </button>
        <button
          type="button"
          onClick={() => openFailureDialog(failure?.detectedAt ?? 0)}
          className="shrink-0 rounded-md px-2 py-0.5 font-medium hover:bg-red-500/15 focus-visible:outline-none"
        >
          {t('gateway.banner.details')}
        </button>
      </div>
    );
  }

  if (surface.kind !== 'banner') return null;

  let icon = <AlertTriangle className="h-3.5 w-3.5 shrink-0" />;
  let text: string;
  if (status.state === 'reconnecting' || surface.attempt) {
    const seconds = formatCountdown(surface.nextRetryAt, now);
    text = seconds !== null
      ? t('gateway.banner.reconnecting', {
        attempt: surface.attempt ?? 0,
        max: surface.max ?? '?',
        seconds,
      })
      : t('gateway.banner.reconnectingSimple', {
        attempt: surface.attempt ?? 0,
        max: surface.max ?? '?',
      });
    icon = <Loader2 className="h-3.5 w-3.5 shrink-0 animate-spin" />;
  } else if (status.state === 'starting') {
    text = t('gateway.starting');
    icon = <Loader2 className="h-3.5 w-3.5 shrink-0 animate-spin" />;
  } else {
    // running-but-not-ready, or error.
    text = t('gateway.banner.degraded');
  }

  return (
    <div
      role="status"
      data-testid="gateway-status-banner"
      className={cn(
        'flex shrink-0 items-center gap-2 border-b px-4 py-1.5 text-xs',
        'border-amber-500/30 bg-amber-500/10 text-amber-800 dark:text-amber-300',
      )}
    >
      {icon}
      <span className="min-w-0 flex-1 truncate">{text}</span>
      <button
        type="button"
        onClick={() => void startGateway()}
        className="shrink-0 rounded-md px-2 py-0.5 font-medium hover:bg-amber-500/15 focus-visible:outline-none"
      >
        {t('gateway.banner.retry')}
      </button>
    </div>
  );
}
