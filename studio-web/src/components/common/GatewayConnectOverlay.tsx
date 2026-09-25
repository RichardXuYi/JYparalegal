/**
 * Gateway Connect Overlay
 * Bounded blocking startup overlay shown only while this session has never
 * seen the gateway running. It demotes itself to the persistent
 * GatewayStatusBanner once the grace window expires or the user chooses
 * "continue in background" — an endless spinner that locks the UI is always
 * worse than a visible degraded state.
 */
import { Loader2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useGatewayStore } from '@/stores/gateway';
import { useGatewayUiStore } from '@/stores/gateway-ui';
import { deriveGatewaySurface } from '@/lib/connection-status';
import { useMinLoading } from '@/hooks/use-min-loading';

export function GatewayConnectOverlay() {
  const { t } = useTranslation('common');
  const status = useGatewayStore((s) => s.status);
  const hasSeenRunning = useGatewayStore((s) => s.hasSeenRunningThisSession);
  const overlayDismissed = useGatewayUiStore((s) => s.overlayDismissed);
  const overlayGraceExpired = useGatewayUiStore((s) => s.overlayGraceExpired);
  const dismissOverlay = useGatewayUiStore((s) => s.dismissOverlay);

  const surface = deriveGatewaySurface({
    status,
    hasSeenRunningThisSession: hasSeenRunning,
    userDismissedOverlay: overlayDismissed,
    overlayGraceExpired,
  });
  // Hold the overlay for a minimum duration to avoid a jarring flash when the
  // gateway connects almost immediately.
  const show = useMinLoading(surface?.kind === 'overlay', 600);
  if (!show) return null;

  const isReconnecting = status.state === 'reconnecting' || (status.reconnectAttempts ?? 0) > 0;
  const title = isReconnecting
    ? t('connection.reconnectingTitle')
    : t('connection.connectingTitle');
  const description = isReconnecting
    ? t('connection.reconnectingDesc')
    : t('connection.connectingDesc');

  return (
    <div
      className="fixed inset-0 z-[9000] flex items-center justify-center bg-black/30 dark:bg-black/60"
      data-testid="connection-status-modal"
      role="alertdialog"
      aria-modal="true"
      aria-label={title}
    >
      <div className="flex w-80 max-w-[90vw] flex-col items-center gap-4 rounded-2xl bg-surface-modal p-6 text-center shadow-xl">
        <Loader2 className="h-8 w-8 animate-spin text-primary" />
        <div className="flex flex-col gap-1">
          <h2 className="text-base font-semibold text-foreground">{title}</h2>
          <p className="text-sm text-muted-foreground">{description}</p>
        </div>
        <button
          type="button"
          onClick={dismissOverlay}
          data-testid="connection-status-dismiss"
          className="text-xs text-muted-foreground/70 transition-colors hover:text-foreground focus-visible:outline-none"
        >
          {t('connection.runInBackground')}
        </button>
      </div>
    </div>
  );
}
