/**
 * Gateway Boot Screen
 *
 * Launcher-style full-screen overlay shown from the moment the shell paints
 * until the gateway first runs this session. The app deliberately does not
 * reveal a half-ready UI with a "connecting" banner — opening the window shows
 * this animated boot screen (live elapsed seconds, honest indeterminate
 * progress), and the workspace appears only when the engine is ready.
 *
 * Scope guard: `hasSeenRunningThisSession` confines it to the boot phase.
 * Once the gateway has run, later dips (crash → reconnect, degraded readiness)
 * surface through the failure dialog / status chip — never a banner.
 *
 * Copy adapts to the cold-cache flag (`status.firstRun`): the first launch
 * after install needs a one-time engine warm-up (~1 min); warm boots finish in
 * a few seconds. A skip button appears after a short grace period so users are
 * never trapped if they want in immediately.
 *
 * Runtime provisioning: when this build does not bundle the OpenClaw runtime
 * (download mode) — or the installed one does not match the pinned version —
 * this screen also owns the "prepare the runtime" flow: consent, determinate
 * download progress, cancel/retry, and importing a locally supplied archive.
 * See docs/startup-performance-plan.md.
 */
import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useGatewayStore } from '@/stores/gateway';
import { hostApi, type RuntimeProgress, type RuntimeStatus } from '@/lib/host-api';
import { hostEvents } from '@/lib/host-events';
import { toUserMessage } from '@/lib/error-message';

const SKIP_BUTTON_DELAY_S = 8;

const RUNTIME_RUNNING_STATES: RuntimeStatus['state'][] = [
  'downloading',
  'verifying',
  'extracting',
  'activating',
];

function formatBytes(bytes: number | null | undefined): string {
  if (!bytes || bytes <= 0) return '0 MB';
  const mb = bytes / 1024 / 1024;
  if (mb >= 1024) return `${(mb / 1024).toFixed(2)} GB`;
  return `${mb.toFixed(1)} MB`;
}

/**
 * Runtime provisioning state for the boot screen: initial status, live progress
 * events, and the handful of actions the user can take.
 */
function useRuntimeSetup() {
  const [status, setStatus] = useState<RuntimeStatus | null>(null);
  const [progress, setProgress] = useState<RuntimeProgress | null>(null);
  const [manifestSize, setManifestSize] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      try {
        const next = await hostApi.runtime.status();
        if (cancelled) return;
        setStatus(next);
        setProgress(next.progress ?? null);
        if (!next.ready && next.manifestPresent) {
          const manifest = await hostApi.runtime.manifest();
          if (!cancelled && manifest.success) setManifestSize(manifest.manifest?.size ?? null);
        }
      } catch {
        // Status is best-effort: a failure here must not break the boot screen.
      }
    })();

    const offProgress = hostEvents.onRuntimeProgress((payload) => setProgress(payload));
    const offState = hostEvents.onRuntimeStateChanged((payload) => {
      setStatus(payload);
      setProgress(payload.progress ?? null);
      if (payload.state === 'failed') setError(payload.error ?? null);
      if (payload.state === 'ready') setError(null);
    });

    return () => {
      cancelled = true;
      offProgress?.();
      offState?.();
    };
  }, []);

  const start = useCallback(async (consent?: boolean) => {
    setBusy(true);
    setError(null);
    try {
      const result = await hostApi.runtime.install(consent);
      setStatus(result.status);
      if (!result.success && result.error && result.error !== 'cancelled') {
        setError(result.error);
      }
    } catch (caught) {
      setError(toUserMessage(caught) || 'runtime install failed');
    } finally {
      setBusy(false);
    }
  }, []);

  const cancel = useCallback(async () => {
    try {
      const result = await hostApi.runtime.cancel();
      setStatus(result.status);
    } catch {
      // ignore
    }
  }, []);

  const importArchive = useCallback(async () => {
    setError(null);
    try {
      const picked = await hostApi.dialog.open({
        title: 'OpenClaw runtime archive',
        properties: ['openFile'],
        filters: [{ name: 'Runtime archive', extensions: ['gz', 'tgz', 'tar'] }],
      });
      const filePath = picked.canceled ? '' : picked.filePaths?.[0] ?? '';
      if (!filePath) return;
      setBusy(true);
      const result = await hostApi.runtime.importArchive(filePath);
      setStatus(result.status);
      if (!result.success && result.error) setError(result.error);
    } catch (caught) {
      setError(toUserMessage(caught) || 'import failed');
    } finally {
      setBusy(false);
    }
  }, []);

  const running = busy || (status ? RUNTIME_RUNNING_STATES.includes(status.state) : false);
  const needed = status !== null && !status.ready && status.state !== 'unknown';

  return { status, progress, manifestSize, running, needed, error, start, cancel, importArchive };
}

export function GatewayBootScreen() {
  const { t } = useTranslation('common');
  const state = useGatewayStore((s) => s.status.state);
  const firstRun = useGatewayStore((s) => s.status.firstRun);
  const hasSeenRunningThisSession = useGatewayStore((s) => s.hasSeenRunningThisSession);
  const [dismissed, setDismissed] = useState(false);
  const [elapsedSeconds, setElapsedSeconds] = useState(0);
  const runtime = useRuntimeSetup();

  // Mounted for as long as the boot episode lasts (from shell paint while the
  // manager still reports `stopped`, through `starting`, until the gateway
  // runs — or fails), so the timer counts the whole wait.
  useEffect(() => {
    const timer = setInterval(() => setElapsedSeconds((s) => s + 1), 1000);
    return () => clearInterval(timer);
  }, []);

  const active = !hasSeenRunningThisSession
    && (state === 'stopped' || state === 'starting' || state === 'reconnecting');
  if (!active || dismissed) return null;

  // While the runtime is being provisioned the gateway cannot start, so the
  // boot screen shows that flow instead of an open-ended "starting" spinner.
  const runtimeActive = runtime.needed || runtime.running;
  const progress = runtime.progress;
  const percent = progress && progress.totalBytes && progress.totalBytes > 0
    ? Math.min(100, Math.round((progress.receivedBytes / progress.totalBytes) * 100))
    : null;

  const runtimePhaseLabel = ((): string => {
    if (!progress) return t('gateway.runtime.preparing');
    switch (progress.phase) {
      case 'downloading':
        return t('gateway.runtime.downloading', {
          received: formatBytes(progress.receivedBytes),
          total: progress.totalBytes ? formatBytes(progress.totalBytes) : '—',
        });
      case 'verifying':
        return t('gateway.runtime.verifying');
      case 'extracting':
        return t('gateway.runtime.extracting', { count: progress.extractedEntries });
      case 'activating':
        return t('gateway.runtime.activating');
      default:
        return t('gateway.runtime.preparing');
    }
  })();

  // Skip becomes available after a short grace period so users actually see
  // the boot state before bailing into the not-yet-ready app.
  const canSkip = elapsedSeconds >= SKIP_BUTTON_DELAY_S;

  return (
    <div
      role="status"
      data-testid="gateway-boot-screen"
      className="fixed inset-0 z-[99999] flex items-center justify-center overflow-hidden"
      style={{ animation: 'boot-screen-fade-in 300ms ease-out' }}
    >
      {/* Background gradient - matching InitializingScreen / Login page */}
      <div className="absolute inset-0 bg-gradient-to-br from-slate-50 via-blue-50 to-purple-50 dark:from-[hsl(15,60%,8%)] dark:via-[hsl(20,70%,12%)] dark:to-[hsl(25,65%,8%)]" />

      {/* Decorative blurs */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div
          className="absolute -top-40 -right-40 w-96 h-96 bg-gradient-to-br from-orange-400/20 to-red-400/20 rounded-full blur-3xl animate-pulse"
        />
        <div
          className="absolute -bottom-40 -left-40 w-96 h-96 bg-gradient-to-tr from-red-400/20 to-orange-400/20 rounded-full blur-3xl animate-pulse"
          style={{ animationDelay: '1.5s' }}
        />
      </div>

      {/* Content */}
      <div className="relative flex flex-col items-center gap-8 px-8">
        {/* Brand logo */}
        <div className="flex items-center gap-4">
          <div className="relative">
            <div className="w-16 h-16 rounded-2xl bg-gradient-to-br from-blue-600 via-blue-500 to-purple-600 flex items-center justify-center shadow-2xl shadow-blue-500/30 relative z-10 dark:from-orange-600 dark:via-red-500 dark:to-red-600 dark:shadow-red-500/30">
              <span className="text-white font-bold text-3xl tracking-tight">JY</span>
            </div>
            <div className="absolute -inset-1.5 bg-gradient-to-br from-blue-600/30 to-purple-600/30 rounded-2xl blur-lg animate-pulse dark:from-orange-600/30 dark:to-red-600/30" />
          </div>
          <div>
            <h1 className="text-2xl font-bold bg-gradient-to-r from-blue-600 to-purple-600 bg-clip-text text-transparent dark:from-orange-500 dark:to-red-500">
              JYparalegal
            </h1>
            <p className="mt-0.5 text-sm text-slate-500 dark:text-slate-400">
              {t('gateway.boot.tagline')}
            </p>
          </div>
        </div>

        {runtimeActive ? (
          <div className="w-[26rem] space-y-4 rounded-2xl border border-slate-200/70 bg-white/70 p-5 text-center shadow-sm dark:border-slate-700/60 dark:bg-slate-900/40">
            {percent === null ? (
              <div className="h-1.5 w-full rounded-full bg-slate-200/80 dark:bg-slate-700/60 overflow-hidden">
                <div
                  className="h-full w-1/3 rounded-full bg-gradient-to-r from-blue-500 to-purple-500 dark:from-orange-500 dark:to-red-500"
                  style={{ animation: 'boot-screen-slide 1.6s ease-in-out infinite' }}
                />
              </div>
            ) : (
              <div className="h-1.5 w-full rounded-full bg-slate-200/80 dark:bg-slate-700/60 overflow-hidden">
                <div
                  className="h-full rounded-full bg-gradient-to-r from-blue-500 to-purple-500 transition-[width] duration-300 dark:from-orange-500 dark:to-red-500"
                  style={{ width: `${percent}%` }}
                />
              </div>
            )}

            <p className="text-sm font-medium text-slate-700 dark:text-slate-200">
              {runtime.running ? runtimePhaseLabel : t('gateway.runtime.needsDownload', {
                size: runtime.manifestSize ? formatBytes(runtime.manifestSize) : '—',
              })}
            </p>

            {!runtime.status?.manifestPresent && (
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {t('gateway.runtime.noManifest')}
              </p>
            )}

            <p className="text-xs text-slate-500 dark:text-slate-400">
              {runtime.status?.installedVersion
                ? t('gateway.runtime.versionInfo', {
                  installed: runtime.status.installedVersion,
                  desired: runtime.status.desiredVersion ?? '—',
                })
                : t('gateway.runtime.versionWanted', {
                  desired: runtime.status?.desiredVersion ?? '—',
                })}
            </p>

            {runtime.error && (
              <p className="text-xs text-red-600 dark:text-red-400 break-words">{runtime.error}</p>
            )}

            <div className="flex flex-wrap items-center justify-center gap-2">
              {runtime.running && (
                <button
                  type="button"
                  onClick={() => void runtime.cancel()}
                  className="rounded-xl border border-slate-300/70 px-4 py-1.5 text-sm text-slate-600 hover:bg-slate-200/60 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700/60"
                >
                  {t('gateway.runtime.cancel')}
                </button>
              )}

              {!runtime.running && runtime.status?.manifestPresent && runtime.status.consent !== 'granted' && (
                <>
                  <button
                    type="button"
                    disabled={runtime.status.state === 'ready'}
                    onClick={() => void runtime.start(true)}
                    className="rounded-xl bg-blue-600 px-4 py-1.5 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
                  >
                    {t('gateway.runtime.consentAccept')}
                  </button>
                  <button
                    type="button"
                    onClick={() => void runtime.start(false)}
                    className="rounded-xl border border-slate-300/70 px-4 py-1.5 text-sm text-slate-600 hover:bg-slate-200/60 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700/60"
                  >
                    {t('gateway.runtime.consentDecline')}
                  </button>
                </>
              )}

              {!runtime.running && (runtime.error || runtime.status?.consent !== 'granted') && runtime.status?.manifestPresent && (
                <button
                  type="button"
                  onClick={() => void runtime.start(true)}
                  className="rounded-xl border border-slate-300/70 px-4 py-1.5 text-sm text-slate-600 hover:bg-slate-200/60 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700/60"
                >
                  {t('gateway.runtime.retry')}
                </button>
              )}

              {!runtime.running && (
                <button
                  type="button"
                  onClick={() => void runtime.importArchive()}
                  className="rounded-xl border border-slate-300/70 px-4 py-1.5 text-sm text-slate-600 hover:bg-slate-200/60 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700/60"
                >
                  {t('gateway.runtime.import')}
                </button>
              )}
            </div>

            {runtime.status?.consent === 'denied' && !runtime.error && (
              <p className="text-xs text-slate-500 dark:text-slate-400">
                {t('gateway.runtime.declined')}
              </p>
            )}
          </div>
        ) : (
          <>
            {/* Indeterminate progress bar: honest (no fake %) but visibly moving */}
            <div className="w-64 h-1.5 rounded-full bg-slate-200/80 dark:bg-slate-700/60 overflow-hidden">
              <div
                className="h-full w-1/3 rounded-full bg-gradient-to-r from-blue-500 to-purple-500 dark:from-orange-500 dark:to-red-500"
                style={{ animation: 'boot-screen-slide 1.6s ease-in-out infinite' }}
              />
            </div>

            {/* Three-dot pulse */}
            <div className="flex items-center gap-2">
              {[0, 1, 2].map((i) => (
                <div
                  key={i}
                  className="w-2.5 h-2.5 rounded-full bg-gradient-to-br from-blue-500 to-purple-500 dark:from-orange-500 dark:to-red-500"
                  style={{
                    animation: 'init-dot-pulse 1.4s ease-in-out infinite',
                    animationDelay: `${i * 0.16}s`,
                  }}
                />
              ))}
            </div>

            {/* Text */}
            <div className="space-y-2 text-center max-w-md">
              <p className="text-base font-medium text-slate-700 dark:text-slate-200">
                {t('gateway.boot.title')}
              </p>
              <p className="text-sm tabular-nums text-slate-500 dark:text-slate-400">
                {t('gateway.boot.elapsed', { seconds: elapsedSeconds })}
              </p>
              <p className="text-sm text-slate-500 dark:text-slate-400">
                {firstRun ? t('gateway.boot.hintFirstRun') : t('gateway.boot.hintWarm')}
              </p>
            </div>
          </>
        )}

        {canSkip && (
          <button
            type="button"
            onClick={() => setDismissed(true)}
            className="rounded-xl border border-slate-300/70 px-4 py-1.5 text-sm text-slate-600 hover:bg-slate-200/60 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700/60"
          >
            {t('gateway.boot.skip')}
          </button>
        )}
      </div>

      <style>{`
        @keyframes boot-screen-fade-in {
          from { opacity: 0; }
          to { opacity: 1; }
        }
        @keyframes boot-screen-slide {
          0% { transform: translateX(-110%); }
          100% { transform: translateX(320%); }
        }
      `}</style>
    </div>
  );
}
