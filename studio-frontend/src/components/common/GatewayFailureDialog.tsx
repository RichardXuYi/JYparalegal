/**
 * Gateway Failure Dialog
 * Terminal-failure surface shown when the gateway reaches the `failed` state:
 * a localized reason (from the structured failure carried on GatewayStatus),
 * the readiness tier that refused, the tail of the gateway stderr, and the
 * recovery actions the taxonomy suggested (retry / logs / report / doctor).
 */
import { useEffect, useState } from 'react';
import { AlertCircle, Loader2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { hostApi } from '@/lib/host-api';
import { useGatewayStore } from '@/stores/gateway';
import { useGatewayUiStore } from '@/stores/gateway-ui';
import { useSettingsUiStore } from '@/stores/settings-ui';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from '@/components/ui/dialog';

interface StartupReportLike {
  stderrTail?: string[];
}

export function GatewayFailureDialog() {
  const { t } = useTranslation('common');
  const status = useGatewayStore((s) => s.status);
  const lastError = useGatewayStore((s) => s.lastError);
  const startGateway = useGatewayStore((s) => s.start);
  const openFailureDetectedAt = useGatewayUiStore((s) => s.openFailureDetectedAt);
  const closeFailureDialog = useGatewayUiStore((s) => s.closeFailureDialog);
  const openSettings = useSettingsUiStore((s) => s.openSettings);

  const failure = status.state === 'failed' ? status.failure ?? null : null;
  const open = failure !== null && openFailureDetectedAt === failure.detectedAt;
  const failureKey = failure?.detectedAt ?? null;
  const [doctorBusy, setDoctorBusy] = useState(false);
  // Report tail is keyed by failure episode so a stale fetch from a previous
  // episode can never render under a new failure.
  const [reportTail, setReportTail] = useState<{ key: number; text: string } | null>(null);

  // Surface the last gateway stderr lines from the startup report when the
  // dialog opens; best-effort — the dialog must work without the route.
  useEffect(() => {
    if (!open || failureKey === null) return undefined;
    let cancelled = false;
    hostApi.diagnostics.startupReport()
      .then((report) => {
        if (cancelled || !report) return;
        const tail = (report as StartupReportLike).stderrTail;
        if (Array.isArray(tail) && tail.length > 0) {
          setReportTail({ key: failureKey, text: tail.slice(-6).join('\n') });
        }
      })
      .catch(() => { /* diagnostics route unavailable */ });
    return () => { cancelled = true; };
  }, [open, failureKey]);

  if (!open || failure === null) return null;

  const stderrTail = reportTail !== null && reportTail.key === failureKey ? reportTail.text : null;

  const actions = new Set(failure.suggestedActions);
  const rawError = status.error ?? lastError ?? null;

  const handleRetry = () => {
    closeFailureDialog(failure.detectedAt);
    void startGateway();
  };

  const handleViewLogs = () => {
    closeFailureDialog(failure.detectedAt);
    openSettings('gateway');
  };

  const handleCopyReport = async () => {
    try {
      const report = await hostApi.diagnostics.startupReport();
      if (!report) {
        toast.error(t('gateway.failure.reportFailed'));
        return;
      }
      await navigator.clipboard.writeText(JSON.stringify(report, null, 2));
      toast.success(t('gateway.failure.reportCopied'));
    } catch {
      toast.error(t('gateway.failure.reportFailed'));
    }
  };

  const handleRunDoctor = async () => {
    setDoctorBusy(true);
    toast.info(t('gateway.failure.doctorRunning'));
    try {
      const result = await hostApi.app.openClawDoctor('fix');
      if (result.success) {
        toast.success(t('gateway.failure.doctorDone'));
      } else {
        toast.error(`${t('gateway.failure.doctorFailed')}: ${result.error ?? ''}`.trim());
      }
    } catch (error) {
      toast.error(`${t('gateway.failure.doctorFailed')}: ${String(error)}`);
    } finally {
      setDoctorBusy(false);
    }
  };

  return (
    <Dialog open onOpenChange={(next) => { if (!next) closeFailureDialog(failure.detectedAt); }}>
      <DialogContent
        data-testid="gateway-failure-dialog"
        className="max-w-lg gap-0 p-0"
        aria-describedby="gateway-failure-reason"
      >
        <div className="flex flex-col gap-4 p-6">
          <div className="flex items-start gap-3">
            <AlertCircle className="mt-0.5 h-5 w-5 shrink-0 text-red-500" />
            <div className="flex min-w-0 flex-col gap-1">
              <DialogTitle className="text-base font-semibold">
                {t('gateway.failure.title')}
              </DialogTitle>
              <DialogDescription className="text-sm text-muted-foreground">
                <span id="gateway-failure-reason">
                  {t(failure.reasonKey, {
                    ...failure.reasonParams,
                    defaultValue: t('gateway.failure.unknown'),
                  })}
                </span>
                <span className="mt-1 block text-xs">
                  {t('gateway.failure.tier', {
                    tier: t(`gateway.failure.tierNames.${failure.tier}`),
                  })}
                </span>
              </DialogDescription>
            </div>
          </div>

          {stderrTail !== null && (
            <details className="group rounded-lg border border-border/60 bg-black/5 p-2 dark:bg-white/5" open>
              <summary className="cursor-pointer select-none text-xs font-medium text-muted-foreground">
                {t('gateway.failure.stderrTail')}
              </summary>
              <pre className="mt-2 max-h-40 overflow-auto whitespace-pre-wrap break-all text-[11px] leading-relaxed text-muted-foreground">
                {stderrTail}
              </pre>
            </details>
          )}

          {rawError !== null && (
            <details className="rounded-lg border border-border/60 p-2">
              <summary className="cursor-pointer select-none text-xs font-medium text-muted-foreground">
                {t('gateway.failure.rawError')}
              </summary>
              <pre className="mt-2 max-h-32 overflow-auto whitespace-pre-wrap break-all text-[11px] leading-relaxed text-muted-foreground">
                {rawError}
              </pre>
            </details>
          )}

          <div className="flex flex-wrap items-center justify-end gap-2 pt-1">
            {actions.has('viewLogs') && (
              <button
                type="button"
                onClick={handleViewLogs}
                className="rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-foreground transition-colors hover:bg-black/5 focus-visible:outline-none dark:hover:bg-white/10"
              >
                {t('gateway.failure.viewLogs')}
              </button>
            )}
            {actions.has('copyReport') && (
              <button
                type="button"
                onClick={() => void handleCopyReport()}
                className="rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-foreground transition-colors hover:bg-black/5 focus-visible:outline-none dark:hover:bg-white/10"
              >
                {t('gateway.failure.copyReport')}
              </button>
            )}
            {actions.has('runDoctor') && (
              <button
                type="button"
                disabled={doctorBusy}
                onClick={() => void handleRunDoctor()}
                className="flex items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-foreground transition-colors hover:bg-black/5 focus-visible:outline-none disabled:opacity-60 dark:hover:bg-white/10"
              >
                {doctorBusy && <Loader2 className="h-3 w-3 animate-spin" />}
                {t('gateway.failure.runDoctor')}
              </button>
            )}
            {actions.has('retry') && (
              <button
                type="button"
                data-testid="gateway-failure-retry"
                onClick={handleRetry}
                className="rounded-lg bg-primary px-3 py-1.5 text-xs font-medium text-primary-foreground transition-opacity hover:opacity-90 focus-visible:outline-none"
              >
                {t('gateway.failure.retry')}
              </button>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
