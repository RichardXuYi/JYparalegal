/**
 * Connection Status Modal
 * Global blocking overlay that surfaces slow connection operations:
 *  - initial gateway connection on startup
 *  - reconnection after a network/service restart
 * It auto-dismisses once the operation completes. A "continue in background"
 * escape hatch prevents the UI from being permanently locked if the gateway
 * stalls while starting.
 */
import { useState } from 'react';
import { Loader2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useGatewayStore } from '@/stores/gateway';
import { deriveConnectionReason, type ConnectionReason } from '@/lib/connection-status';
import { useMinLoading } from '@/hooks/use-min-loading';

export function ConnectionStatusModal() {
  const { t } = useTranslation('common');
  const status = useGatewayStore((s) => s.status);

  const rawReason = deriveConnectionReason({ status });
  // Keep the overlay visible for a minimum duration to avoid a jarring flash on
  // very fast operations.
  const show = useMinLoading(rawReason !== null, 600);

  // Snapshot the last active reason/label so content keeps rendering during the
  // min-loading tail after `rawReason` has already cleared. Manual dismiss is a
  // safety net that resets whenever a new active cycle begins.
  const [snapshot, setSnapshot] = useState<{ reason: ConnectionReason }>({
    reason: null,
  });
  const [manuallyHidden, setManuallyHidden] = useState(false);
  const [prevReason, setPrevReason] = useState<ConnectionReason>(null);

  // Adjust derived state during render (React-recommended pattern) rather than
  // in an effect, avoiding setState-in-effect cascades.
  if (rawReason !== prevReason) {
    setPrevReason(rawReason);
    if (rawReason !== null) {
      setSnapshot({ reason: rawReason });
      setManuallyHidden(false);
    }
  }

  if (!show || manuallyHidden || snapshot.reason === null) {
    return null;
  }

  let title: string;
  let description: string;
  if (snapshot.reason === 'reconnecting') {
    title = t('connection.reconnectingTitle', '正在重新连接…');
    description = t('connection.reconnectingDesc', '正在尝试重新连接 Gateway，请稍候。');
  } else {
    title = t('connection.connectingTitle', '正在连接…');
    description = t('connection.connectingDesc', '正在建立与 Gateway 的连接，请稍候。');
  }

  return (
    <div
      className="fixed inset-0 z-[9000] flex items-center justify-center bg-black/40"
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
          onClick={() => setManuallyHidden(true)}
          data-testid="connection-status-dismiss"
          className="text-xs text-muted-foreground/70 hover:text-foreground focus-visible:outline-none transition-colors"
        >
          {t('connection.runInBackground', '在后台继续')}
        </button>
      </div>
    </div>
  );
}
