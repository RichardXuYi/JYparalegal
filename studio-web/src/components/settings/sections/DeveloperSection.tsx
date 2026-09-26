/**
 * Developer settings section (developer mode only).
 * Gateway proxy, gateway token, OpenClaw CLI, doctor, and telemetry viewer.
 */
import { useEffect, useMemo, useState } from 'react';
import { RefreshCw, Copy, Terminal, Globe, Key, Wrench, Activity } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { toast } from 'sonner';
import { useSettingsStore } from '@/stores/settings';
import { useShallow } from 'zustand/react/shallow';
import { toUserMessage } from '@/lib/error-message';
import {
  clearUiTelemetry,
  getUiTelemetrySnapshot,
  subscribeUiTelemetry,
  trackUiEvent,
  type UiTelemetryEntry,
} from '@/lib/telemetry';
import { useTranslation } from 'react-i18next';
import { hostApi, type OpenClawDoctorResult } from '@/lib/host-api';
import { hostEvents } from '@/lib/host-events';
import { cn } from '@/lib/utils';
import { SettingsGroup } from '@/components/settings/primitives';

type ControlUiInfo = {
  url: string;
  token: string;
  port: number;
};

interface DeveloperSectionProps {
  gradientClass?: string;
}

export function DeveloperSection({ gradientClass }: DeveloperSectionProps) {
  const { t } = useTranslation('settings');
  const { proxyEnabled, proxyServer, proxyHttpServer, proxyHttpsServer,
    proxyAllServer, proxyBypassRules, setProxyEnabled, setProxyServer,
    setProxyHttpServer, setProxyHttpsServer, setProxyAllServer,
    setProxyBypassRules, devModeUnlocked,
  } = useSettingsStore(useShallow((s) => ({
    proxyEnabled: s.proxyEnabled, proxyServer: s.proxyServer,
    proxyHttpServer: s.proxyHttpServer, proxyHttpsServer: s.proxyHttpsServer,
    proxyAllServer: s.proxyAllServer, proxyBypassRules: s.proxyBypassRules,
    setProxyEnabled: s.setProxyEnabled, setProxyServer: s.setProxyServer,
    setProxyHttpServer: s.setProxyHttpServer, setProxyHttpsServer: s.setProxyHttpsServer,
    setProxyAllServer: s.setProxyAllServer, setProxyBypassRules: s.setProxyBypassRules,
    devModeUnlocked: s.devModeUnlocked,
  })));

  const [controlUiInfo, setControlUiInfo] = useState<ControlUiInfo | null>(null);
  const [openclawCliCommand, setOpenclawCliCommand] = useState('');
  const [openclawCliError, setOpenclawCliError] = useState<string | null>(null);
  const [proxyServerDraft, setProxyServerDraft] = useState(proxyServer);
  const [proxyHttpServerDraft, setProxyHttpServerDraft] = useState(proxyHttpServer);
  const [proxyHttpsServerDraft, setProxyHttpsServerDraft] = useState(proxyHttpsServer);
  const [proxyAllServerDraft, setProxyAllServerDraft] = useState(proxyAllServer);
  const [proxyBypassRulesDraft, setProxyBypassRulesDraft] = useState(proxyBypassRules);
  const [proxyEnabledDraft, setProxyEnabledDraft] = useState(proxyEnabled);
  const [savingProxy, setSavingProxy] = useState(false);
  const [showTelemetryViewer, setShowTelemetryViewer] = useState(false);
  const [telemetryEntries, setTelemetryEntries] = useState<UiTelemetryEntry[]>([]);

  const isWindows = window.electron?.platform === 'win32';
  const showCliTools = true;
  const [doctorRunningMode, setDoctorRunningMode] = useState<'diagnose' | 'fix' | null>(null);
  const [doctorResult, setDoctorResult] = useState<OpenClawDoctorResult | null>(null);

  const handleRunOpenClawDoctor = async (mode: 'diagnose' | 'fix') => {
    setDoctorRunningMode(mode);
    try {
      const result = await hostApi.app.openClawDoctor(mode);
      setDoctorResult(result);
      if (result.success) {
        toast.success(mode === 'fix' ? t('developer.doctorFixSucceeded') : t('developer.doctorSucceeded'));
      } else {
        toast.error(result.error || (mode === 'fix' ? t('developer.doctorFixFailed') : t('developer.doctorFailed')));
      }
    } catch (error) {
      const message = toUserMessage(error) || (mode === 'fix' ? t('developer.doctorFixRunFailed') : t('developer.doctorRunFailed'));
      toast.error(message);
      setDoctorResult({
        mode,
        success: false,
        exitCode: null,
        stdout: '',
        stderr: '',
        command: 'openclaw doctor',
        cwd: '',
        durationMs: 0,
        error: message,
      });
    } finally {
      setDoctorRunningMode(null);
    }
  };

  const handleCopyDoctorOutput = async () => {
    if (!doctorResult) return;
    const payload = [
      `command: ${doctorResult.command}`,
      `cwd: ${doctorResult.cwd}`,
      `exitCode: ${doctorResult.exitCode ?? 'null'}`,
      `durationMs: ${doctorResult.durationMs}`,
      '',
      '[stdout]',
      doctorResult.stdout.trim() || '(empty)',
      '',
      '[stderr]',
      doctorResult.stderr.trim() || '(empty)',
    ].join('\n');

    try {
      await navigator.clipboard.writeText(payload);
      toast.success(t('developer.doctorCopied'));
    } catch (error) {
      toast.error(`${t('developer.copyFailed')}: ${toUserMessage(error)}`);
    }
  };

  const refreshControlUiInfo = async () => {
    try {
      const result = await hostApi.gateway.controlUi();
      if (result.success && result.url && result.token && typeof result.port === 'number') {
        setControlUiInfo({ url: result.url, token: result.token, port: result.port });
      }
    } catch {
      // Ignore refresh errors
    }
  };

  const handleCopyGatewayToken = async () => {
    if (!controlUiInfo?.token) return;
    try {
      await navigator.clipboard.writeText(controlUiInfo.token);
      toast.success(t('developer.tokenCopied'));
    } catch (error) {
      toast.error(`${t('developer.copyFailed')}: ${toUserMessage(error)}`);
    }
  };

  useEffect(() => {
    if (!showCliTools) return;
    let cancelled = false;

    (async () => {
      try {
        const result = await hostApi.openclaw.getCliCommand();
        if (cancelled) return;
        if (result.success && result.command) {
          setOpenclawCliCommand(result.command);
          setOpenclawCliError(null);
        } else {
          setOpenclawCliCommand('');
          setOpenclawCliError(result.error || 'OpenClaw CLI unavailable');
        }
      } catch (error) {
        if (cancelled) return;
        setOpenclawCliCommand('');
        setOpenclawCliError(String(error));
      }
    })();

    return () => { cancelled = true; };
  }, [devModeUnlocked, showCliTools]);

  const handleCopyCliCommand = async () => {
    if (!openclawCliCommand) return;
    try {
      await navigator.clipboard.writeText(openclawCliCommand);
      toast.success(t('developer.cmdCopied'));
    } catch (error) {
      toast.error(`${t('developer.copyFailed')}: ${toUserMessage(error)}`);
    }
  };

  useEffect(() => {
    const unsubscribe = hostEvents.onOpenClawCliInstalled((installedPath) => {
      toast.success(t('developer.cliInstalled', { path: installedPath }));
    });
    return () => { unsubscribe?.(); };
  }, [t]);

  useEffect(() => {
    if (!devModeUnlocked) return;
    // Load the snapshot in a microtask so the state write stays out of the
    // synchronous effect body; the subscription below catches later entries.
    let cancelled = false;
    queueMicrotask(() => {
      if (!cancelled) setTelemetryEntries(getUiTelemetrySnapshot(200));
    });
    const unsubscribe = subscribeUiTelemetry((entry) => {
      setTelemetryEntries((prev) => {
        const next = [...prev, entry];
        if (next.length > 200) {
          next.splice(0, next.length - 200);
        }
        return next;
      });
    });
    return () => {
      cancelled = true;
      unsubscribe();
    };
  }, [devModeUnlocked]);

  // Mirror persisted proxy settings into local drafts during render when the
  // store values change (React-recommended adjust-during-render pattern)
  // instead of setState-in-effect.
  const [prevProxyEnabled, setPrevProxyEnabled] = useState(proxyEnabled);
  if (proxyEnabled !== prevProxyEnabled) {
    setPrevProxyEnabled(proxyEnabled);
    setProxyEnabledDraft(proxyEnabled);
  }

  const [prevProxyServer, setPrevProxyServer] = useState(proxyServer);
  if (proxyServer !== prevProxyServer) {
    setPrevProxyServer(proxyServer);
    setProxyServerDraft(proxyServer);
  }

  const [prevProxyHttpServer, setPrevProxyHttpServer] = useState(proxyHttpServer);
  if (proxyHttpServer !== prevProxyHttpServer) {
    setPrevProxyHttpServer(proxyHttpServer);
    setProxyHttpServerDraft(proxyHttpServer);
  }

  const [prevProxyHttpsServer, setPrevProxyHttpsServer] = useState(proxyHttpsServer);
  if (proxyHttpsServer !== prevProxyHttpsServer) {
    setPrevProxyHttpsServer(proxyHttpsServer);
    setProxyHttpsServerDraft(proxyHttpsServer);
  }

  const [prevProxyAllServer, setPrevProxyAllServer] = useState(proxyAllServer);
  if (proxyAllServer !== prevProxyAllServer) {
    setPrevProxyAllServer(proxyAllServer);
    setProxyAllServerDraft(proxyAllServer);
  }

  const [prevProxyBypassRules, setPrevProxyBypassRules] = useState(proxyBypassRules);
  if (proxyBypassRules !== prevProxyBypassRules) {
    setPrevProxyBypassRules(proxyBypassRules);
    setProxyBypassRulesDraft(proxyBypassRules);
  }

  const proxySettingsDirty = useMemo(() => {
    return (
      proxyEnabledDraft !== proxyEnabled
      || proxyServerDraft.trim() !== proxyServer
      || proxyHttpServerDraft.trim() !== proxyHttpServer
      || proxyHttpsServerDraft.trim() !== proxyHttpsServer
      || proxyAllServerDraft.trim() !== proxyAllServer
      || proxyBypassRulesDraft.trim() !== proxyBypassRules
    );
  }, [
    proxyAllServer,
    proxyAllServerDraft,
    proxyBypassRules,
    proxyBypassRulesDraft,
    proxyEnabled,
    proxyEnabledDraft,
    proxyHttpServer,
    proxyHttpServerDraft,
    proxyHttpsServer,
    proxyHttpsServerDraft,
    proxyServer,
    proxyServerDraft,
  ]);

  const handleSaveProxySettings = async () => {
    setSavingProxy(true);
    try {
      const normalizedProxyServer = proxyServerDraft.trim();
      const normalizedHttpServer = proxyHttpServerDraft.trim();
      const normalizedHttpsServer = proxyHttpsServerDraft.trim();
      const normalizedAllServer = proxyAllServerDraft.trim();
      const normalizedBypassRules = proxyBypassRulesDraft.trim();
      await hostApi.settings.setMany({
        proxyEnabled: proxyEnabledDraft,
        proxyServer: normalizedProxyServer,
        proxyHttpServer: normalizedHttpServer,
        proxyHttpsServer: normalizedHttpsServer,
        proxyAllServer: normalizedAllServer,
        proxyBypassRules: normalizedBypassRules,
      });

      setProxyServer(normalizedProxyServer);
      setProxyHttpServer(normalizedHttpServer);
      setProxyHttpsServer(normalizedHttpsServer);
      setProxyAllServer(normalizedAllServer);
      setProxyBypassRules(normalizedBypassRules);
      setProxyEnabled(proxyEnabledDraft);

      toast.success(t('gateway.proxySaved'));
      trackUiEvent('settings.proxy_saved', { enabled: proxyEnabledDraft });
    } catch (error) {
      toast.error(`${t('gateway.proxySaveFailed')}: ${toUserMessage(error)}`);
    } finally {
      setSavingProxy(false);
    }
  };

  const telemetryStats = useMemo(() => {
    let errorCount = 0;
    let slowCount = 0;
    for (const entry of telemetryEntries) {
      if (entry.event.endsWith('_error') || entry.event.includes('request_error')) {
        errorCount += 1;
      }
      const durationMs = typeof entry.payload.durationMs === 'number'
        ? entry.payload.durationMs
        : Number.NaN;
      if (Number.isFinite(durationMs) && durationMs >= 800) {
        slowCount += 1;
      }
    }
    return { total: telemetryEntries.length, errorCount, slowCount };
  }, [telemetryEntries]);

  const telemetryByEvent = useMemo(() => {
    const map = new Map<string, {
      event: string;
      count: number;
      errorCount: number;
      slowCount: number;
      totalDuration: number;
      timedCount: number;
      lastTs: string;
    }>();

    for (const entry of telemetryEntries) {
      const current = map.get(entry.event) ?? {
        event: entry.event,
        count: 0,
        errorCount: 0,
        slowCount: 0,
        totalDuration: 0,
        timedCount: 0,
        lastTs: entry.ts,
      };

      current.count += 1;
      current.lastTs = entry.ts;

      if (entry.event.endsWith('_error') || entry.event.includes('request_error')) {
        current.errorCount += 1;
      }

      const durationMs = typeof entry.payload.durationMs === 'number'
        ? entry.payload.durationMs
        : Number.NaN;
      if (Number.isFinite(durationMs)) {
        current.totalDuration += durationMs;
        current.timedCount += 1;
        if (durationMs >= 800) {
          current.slowCount += 1;
        }
      }

      map.set(entry.event, current);
    }

    return [...map.values()]
      .sort((a, b) => b.count - a.count)
      .slice(0, 12);
  }, [telemetryEntries]);

  const handleCopyTelemetry = async () => {
    try {
      const serialized = telemetryEntries.map((entry) => JSON.stringify(entry)).join('\n');
      await navigator.clipboard.writeText(serialized);
      toast.success(t('developer.telemetryCopied'));
    } catch (error) {
      toast.error(`${t('common:status.error')}: ${String(error)}`);
    }
  };

  const handleClearTelemetry = () => {
    clearUiTelemetry();
    setTelemetryEntries([]);
    toast.success(t('developer.telemetryCleared'));
  };

  return (
    <div data-testid="settings-developer-section" className="space-y-6">
      {/* Gateway Proxy */}
      <SettingsGroup padded gradientClass={gradientClass} icon={Globe}>
        <div className="space-y-4" data-testid="settings-proxy-section">
          <div className="flex items-center justify-between">
            <div>
              <Label className="text-sm font-medium text-foreground/80">Gateway Proxy</Label>
              <p className="text-meta text-muted-foreground">
                {t('gateway.proxyDesc')}
              </p>
            </div>
            <Switch
              checked={proxyEnabledDraft}
              onCheckedChange={setProxyEnabledDraft}
              data-testid="settings-proxy-toggle"
            />
          </div>

          <div className="flex items-center gap-4">
            <Button
              variant="outline"
              onClick={handleSaveProxySettings}
              disabled={savingProxy || !proxySettingsDirty}
              data-testid="settings-proxy-save-button"
              className="rounded-xl h-10 px-5 bg-transparent border-black/10 dark:border-white/10 hover:bg-black/5 dark:hover:bg-white/5"
            >
              <RefreshCw className={`h-4 w-4 mr-2${savingProxy ? ' animate-spin' : ''}`} />
              {savingProxy ? t('common:status.saving') : t('common:actions.save')}
            </Button>
            <p className="text-xs text-muted-foreground">
              {t('gateway.proxyRestartNote')}
            </p>
          </div>

          {proxyEnabledDraft && (
            <div className="space-y-4 pt-2">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="proxy-server" className="text-meta text-foreground/80">{t('gateway.proxyServer')}</Label>
                  <Input
                    id="proxy-server"
                    value={proxyServerDraft}
                    onChange={(event) => setProxyServerDraft(event.target.value)}
                    placeholder="http://127.0.0.1:7890"
                    className="h-10 rounded-xl bg-black/5 dark:bg-white/5 border-transparent font-mono text-meta"
                  />
                  <p className="text-tiny text-muted-foreground">
                    {t('gateway.proxyServerHelp')}
                  </p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="proxy-http-server" className="text-meta text-foreground/80">{t('gateway.proxyHttpServer')}</Label>
                  <Input
                    id="proxy-http-server"
                    value={proxyHttpServerDraft}
                    onChange={(event) => setProxyHttpServerDraft(event.target.value)}
                    placeholder={proxyServerDraft || 'http://127.0.0.1:7890'}
                    className="h-10 rounded-xl bg-black/5 dark:bg-white/5 border-transparent font-mono text-meta"
                  />
                  <p className="text-tiny text-muted-foreground">
                    {t('gateway.proxyHttpServerHelp')}
                  </p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="proxy-https-server" className="text-meta text-foreground/80">{t('gateway.proxyHttpsServer')}</Label>
                  <Input
                    id="proxy-https-server"
                    value={proxyHttpsServerDraft}
                    onChange={(event) => setProxyHttpsServerDraft(event.target.value)}
                    placeholder={proxyServerDraft || 'http://127.0.0.1:7890'}
                    className="h-10 rounded-xl bg-black/5 dark:bg-white/5 border-transparent font-mono text-meta"
                  />
                  <p className="text-tiny text-muted-foreground">
                    {t('gateway.proxyHttpsServerHelp')}
                  </p>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="proxy-all-server" className="text-meta text-foreground/80">{t('gateway.proxyAllServer')}</Label>
                  <Input
                    id="proxy-all-server"
                    value={proxyAllServerDraft}
                    onChange={(event) => setProxyAllServerDraft(event.target.value)}
                    placeholder={proxyServerDraft || 'socks5://127.0.0.1:7891'}
                    className="h-10 rounded-xl bg-black/5 dark:bg-white/5 border-transparent font-mono text-meta"
                  />
                  <p className="text-tiny text-muted-foreground">
                    {t('gateway.proxyAllServerHelp')}
                  </p>
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="proxy-bypass" className="text-meta text-foreground/80">{t('gateway.proxyBypass')}</Label>
                <Input
                  id="proxy-bypass"
                  value={proxyBypassRulesDraft}
                  onChange={(event) => setProxyBypassRulesDraft(event.target.value)}
                  placeholder="<local>;localhost;127.0.0.1;::1"
                  className="h-10 rounded-xl bg-black/5 dark:bg-white/5 border-transparent font-mono text-meta"
                />
                <p className="text-tiny text-muted-foreground">
                  {t('gateway.proxyBypassHelp')}
                </p>
              </div>

            </div>
          )}
        </div>
      </SettingsGroup>

      <SettingsGroup padded gradientClass={gradientClass} icon={Key}>
        <div className="space-y-4 pt-4">
          <Label className="text-sm font-medium text-foreground/80">{t('developer.gatewayToken')}</Label>
          <p className="text-meta text-muted-foreground">
            {t('developer.gatewayTokenDesc')}
          </p>
          <div className="flex flex-wrap gap-2">
            <Input
              data-testid="settings-developer-gateway-token"
              readOnly
              value={controlUiInfo?.token || ''}
              placeholder={t('developer.tokenUnavailable')}
              className="font-mono text-meta h-10 rounded-xl bg-black/5 dark:bg-white/5 border-transparent flex-1 min-w-[200px]"
            />
            <Button
              type="button"
              variant="outline"
              onClick={refreshControlUiInfo}
              disabled={!devModeUnlocked}
              className="rounded-xl h-10 px-4 bg-transparent border-black/10 dark:border-white/10 hover:bg-black/5 dark:hover:bg-white/5"
            >
              <RefreshCw className="h-4 w-4 mr-2" />
              {t('common:actions.load')}
            </Button>
            <Button
              type="button"
              variant="outline"
              onClick={handleCopyGatewayToken}
              disabled={!controlUiInfo?.token}
              className="rounded-xl h-10 px-4 bg-transparent border-black/10 dark:border-white/10 hover:bg-black/5 dark:hover:bg-white/5"
            >
              <Copy className="h-4 w-4 mr-2" />
              {t('common:actions.copy')}
            </Button>
          </div>
        </div>
      </SettingsGroup>

        {showCliTools && (
          <SettingsGroup padded gradientClass={gradientClass} icon={Terminal}>
          <div className="space-y-3">
            <Label className="text-sm font-medium text-foreground">{t('developer.cli')}</Label>
            <p className="text-meta text-muted-foreground">
              {t('developer.cliDesc')}
            </p>
            {isWindows && (
              <p className="text-xs text-muted-foreground">
                {t('developer.cliPowershell')}
              </p>
            )}
            <div className="flex flex-wrap gap-2">
              <Input
                readOnly
                value={openclawCliCommand}
                placeholder={openclawCliError || t('developer.cmdUnavailable')}
                className="font-mono text-meta h-10 rounded-xl bg-black/5 dark:bg-white/5 border-transparent flex-1 min-w-[200px]"
              />
              <Button
                type="button"
                variant="outline"
                onClick={handleCopyCliCommand}
                disabled={!openclawCliCommand}
                className="rounded-xl h-10 px-4 bg-transparent border-black/10 dark:border-white/10 hover:bg-black/5 dark:hover:bg-white/5"
              >
                <Copy className="h-4 w-4 mr-2" />
                {t('common:actions.copy')}
              </Button>
            </div>
          </div>
          </SettingsGroup>
        )}

        <SettingsGroup padded gradientClass={gradientClass} icon={Wrench}>
        <div className="space-y-4">
          <div className="flex items-center justify-between gap-3">
            <div>
              <Label className="text-sm font-medium text-foreground">{t('developer.doctor')}</Label>
              <p className="text-meta text-muted-foreground mt-1">
                {t('developer.doctorDesc')}
              </p>
            </div>
            <div className="flex gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => void handleRunOpenClawDoctor('diagnose')}
                disabled={doctorRunningMode !== null}
                className="rounded-xl h-10 px-4 bg-transparent border-black/10 dark:border-white/10 hover:bg-black/5 dark:hover:bg-white/5"
              >
                <RefreshCw className={`h-4 w-4 mr-2${doctorRunningMode === 'diagnose' ? ' animate-spin' : ''}`} />
                {doctorRunningMode === 'diagnose' ? t('common:status.running') : t('developer.runDoctor')}
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={() => void handleRunOpenClawDoctor('fix')}
                disabled={doctorRunningMode !== null}
                className="rounded-xl h-10 px-4 bg-transparent border-black/10 dark:border-white/10 hover:bg-black/5 dark:hover:bg-white/5"
              >
                <RefreshCw className={`h-4 w-4 mr-2${doctorRunningMode === 'fix' ? ' animate-spin' : ''}`} />
                {doctorRunningMode === 'fix' ? t('common:status.running') : t('developer.runDoctorFix')}
              </Button>
              <Button
                type="button"
                variant="outline"
                onClick={handleCopyDoctorOutput}
                disabled={!doctorResult}
                className="rounded-xl h-10 px-4 bg-transparent border-black/10 dark:border-white/10 hover:bg-black/5 dark:hover:bg-white/5"
              >
                <Copy className="h-4 w-4 mr-2" />
                {t('common:actions.copy')}
              </Button>
            </div>
          </div>

          {doctorResult && (
            <div className="space-y-3 rounded-2xl border border-black/10 dark:border-white/10 p-5 bg-black/5 dark:bg-white/5">
              <div className="flex flex-wrap gap-2 text-xs">
                <Badge variant={doctorResult.success ? 'secondary' : 'destructive'} className="rounded-full px-3 py-1">
                  {doctorResult.mode === 'fix'
                    ? (doctorResult.success ? t('developer.doctorFixOk') : t('developer.doctorFixIssue'))
                    : (doctorResult.success ? t('developer.doctorOk') : t('developer.doctorIssue'))}
                </Badge>
                <Badge variant="outline" className="rounded-full px-3 py-1">
                  {t('developer.doctorExitCode')}: {doctorResult.exitCode ?? 'null'}
                </Badge>
                <Badge variant="outline" className="rounded-full px-3 py-1">
                  {t('developer.doctorDuration')}: {Math.round(doctorResult.durationMs)}ms
                </Badge>
              </div>
              <div className="space-y-1 text-xs text-muted-foreground font-mono break-all">
                <p>{t('developer.doctorCommand')}: {doctorResult.command}</p>
                <p>{t('developer.doctorWorkingDir')}: {doctorResult.cwd || '-'}</p>
                {doctorResult.error && <p>{t('developer.doctorError')}: {doctorResult.error}</p>}
              </div>
              <div className="grid gap-3 md:grid-cols-2">
                <div className="space-y-2">
                  <p className="text-xs font-semibold text-foreground/80">{t('developer.doctorStdout')}</p>
                  <pre className="max-h-72 overflow-auto rounded-xl border border-black/10 dark:border-white/10 bg-surface-input p-3 text-tiny font-mono whitespace-pre-wrap break-words">
                    {doctorResult.stdout.trim() || t('developer.doctorOutputEmpty')}
                  </pre>
                </div>
                <div className="space-y-2">
                  <p className="text-xs font-semibold text-foreground/80">{t('developer.doctorStderr')}</p>
                  <pre className="max-h-72 overflow-auto rounded-xl border border-black/10 dark:border-white/10 bg-surface-input p-3 text-tiny font-mono whitespace-pre-wrap break-words">
                    {doctorResult.stderr.trim() || t('developer.doctorOutputEmpty')}
                  </pre>
                </div>
              </div>
            </div>
          )}
        </div>
        </SettingsGroup>

        <SettingsGroup padded gradientClass={gradientClass} icon={Activity}>
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <Label className="text-sm font-medium text-foreground">{t('developer.telemetryViewer')}</Label>
              <p className="text-meta text-muted-foreground mt-1">
                {t('developer.telemetryViewerDesc')}
              </p>
            </div>
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => setShowTelemetryViewer((prev) => !prev)}
              className="rounded-full px-5 h-9 bg-transparent border-black/10 dark:border-white/10 hover:bg-black/5 dark:hover:bg-white/5"
            >
              {showTelemetryViewer
                ? t('common:actions.hide')
                : t('common:actions.show')}
            </Button>
          </div>

          {showTelemetryViewer && (
            <div className="space-y-4 rounded-2xl border border-black/10 dark:border-white/10 p-5 bg-black/5 dark:bg-white/5">
              <div className="flex flex-wrap items-center gap-2">
                <Badge variant="secondary" className="rounded-full px-3 py-1 bg-surface-modal border border-black/5 dark:border-white/5">{t('developer.telemetryTotal')}: {telemetryStats.total}</Badge>
                <Badge variant={telemetryStats.errorCount > 0 ? 'destructive' : 'secondary'} className={cn("rounded-full px-3 py-1", telemetryStats.errorCount === 0 && "bg-surface-modal border border-black/5 dark:border-white/5")}>
                  {t('developer.telemetryErrors')}: {telemetryStats.errorCount}
                </Badge>
                <Badge variant={telemetryStats.slowCount > 0 ? 'secondary' : 'outline'} className={cn("rounded-full px-3 py-1", telemetryStats.slowCount === 0 && "bg-surface-modal border border-black/5 dark:border-white/5")}>
                  {t('developer.telemetrySlow')}: {telemetryStats.slowCount}
                </Badge>
                <div className="ml-auto flex gap-2">
                  <Button type="button" variant="outline" size="sm" onClick={handleCopyTelemetry} className="rounded-full h-8 px-4 bg-surface-modal border-black/5 dark:border-white/5 hover:bg-black/5 dark:hover:bg-white/10">
                    <Copy className="h-3.5 w-3.5 mr-1.5" />
                    {t('common:actions.copy')}
                  </Button>
                  <Button type="button" variant="outline" size="sm" onClick={handleClearTelemetry} className="rounded-full h-8 px-4 bg-surface-modal border-black/5 dark:border-white/5 hover:bg-black/5 dark:hover:bg-white/10">
                    {t('common:actions.clear')}
                  </Button>
                </div>
              </div>

              <div className="max-h-80 overflow-auto rounded-xl border border-black/10 dark:border-white/10 bg-surface-modal shadow-inner">
                {telemetryByEvent.length > 0 && (
                  <div className="border-b border-black/5 dark:border-white/5 bg-black/5 dark:bg-white/5 p-3">
                    <p className="mb-3 text-xs font-semibold text-muted-foreground">
                      {t('developer.telemetryAggregated')}
                    </p>
                    <div className="space-y-1.5 text-xs">
                      {telemetryByEvent.map((item) => (
                        <div
                          key={item.event}
                          className="grid grid-cols-[minmax(0,1.6fr)_0.7fr_0.9fr_0.8fr_1fr] gap-2 rounded-lg border border-black/5 dark:border-white/5 bg-surface-modal px-3 py-2"
                        >
                          <span className="truncate font-medium" title={item.event}>{item.event}</span>
                          <span className="text-muted-foreground">n={item.count}</span>
                          <span className="text-muted-foreground">
                            avg={item.timedCount > 0 ? Math.round(item.totalDuration / item.timedCount) : 0}ms
                          </span>
                          <span className="text-muted-foreground">slow={item.slowCount}</span>
                          <span className="text-muted-foreground">err={item.errorCount}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                <div className="space-y-2 p-3 font-mono text-xs">
                  {telemetryEntries.length === 0 ? (
                    <div className="text-muted-foreground text-center py-4">{t('developer.telemetryEmpty')}</div>
                  ) : (
                    telemetryEntries
                      .slice()
                      .reverse()
                      .map((entry) => (
                        <div key={entry.id} className="rounded-lg border border-black/5 dark:border-white/5 bg-black/5 dark:bg-white/5 p-3">
                          <div className="flex items-center justify-between gap-3 mb-2">
                            <span className="font-semibold text-foreground">{entry.event}</span>
                            <span className="text-muted-foreground text-tiny">{entry.ts}</span>
                          </div>
                          <pre className="whitespace-pre-wrap text-tiny text-muted-foreground overflow-x-auto">
                            {JSON.stringify({ count: entry.count, ...entry.payload }, null, 2)}
                          </pre>
                        </div>
                      ))
                  )}
                </div>
              </div>
            </div>
          )}
        </div>
        </SettingsGroup>
    </div>
  );
}

export default DeveloperSection;
