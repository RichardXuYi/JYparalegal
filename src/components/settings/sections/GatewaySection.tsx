/**
 * Gateway settings section.
 * Gateway status/restart/logs, auto-start, developer mode toggle, telemetry toggle.
 */
import { useState } from 'react';
import { RefreshCw, ExternalLink, FileText, Server, Power, Shield, Activity } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { useSettingsStore } from '@/stores/settings';
import { useGatewayStore } from '@/stores/gateway';
import { useTranslation } from 'react-i18next';
import { hostApi } from '@/lib/host-api';
import { cn } from '@/lib/utils';
import { SettingsGroup, SettingRow } from '@/components/settings/primitives';

interface GatewaySectionProps {
  gradientClass?: string;
}

export function GatewaySection({ gradientClass }: GatewaySectionProps) {
  const { t } = useTranslation('settings');
  const { status: gatewayStatus, restart: restartGateway } = useGatewayStore();
  const gatewayAutoStart = useSettingsStore((state) => state.gatewayAutoStart);
  const setGatewayAutoStart = useSettingsStore((state) => state.setGatewayAutoStart);
  const devModeUnlocked = useSettingsStore((state) => state.devModeUnlocked);
  const setDevModeUnlocked = useSettingsStore((state) => state.setDevModeUnlocked);
  const telemetryEnabled = useSettingsStore((state) => state.telemetryEnabled);
  const setTelemetryEnabled = useSettingsStore((state) => state.setTelemetryEnabled);

  const [showLogs, setShowLogs] = useState(false);
  const [logContent, setLogContent] = useState('');

  const handleShowLogs = async () => {
    try {
      const logs = await hostApi.logs.recent(100);
      setLogContent(logs.content);
      setShowLogs(true);
    } catch {
      setLogContent(t('gateway.logsLoadFailed'));
      setShowLogs(true);
    }
  };

  const handleOpenLogDir = async () => {
    try {
      const { dir: logDir } = await hostApi.logs.dir();
      if (logDir) {
        await hostApi.shell.showItemInFolder(logDir);
      }
    } catch {
      // ignore
    }
  };

  return (
    <div className="space-y-6">
      <SettingsGroup gradientClass={gradientClass} icon={Server}>
        <SettingRow
          icon={Activity}
          label={t('gateway.status')}
          description={`${t('gateway.port')}: ${gatewayStatus.port}`}
          align={showLogs ? 'start' : 'center'}
          control={
            <>
              <div className={cn(
                "flex items-center gap-1.5 px-3 py-1.5 rounded-full text-meta font-medium border",
                gatewayStatus.state === 'running' && gatewayStatus.gatewayReady !== false ? "bg-green-500/10 text-green-600 dark:text-green-500 border-green-500/20" :
                  gatewayStatus.state === 'running' ? "bg-red-500/10 text-red-600 dark:text-red-500 border-red-500/20" :
                    gatewayStatus.state === 'error' ? "bg-red-500/10 text-red-600 dark:text-red-500 border-red-500/20" :
                      "bg-black/5 dark:bg-white/5 text-muted-foreground border-transparent"
              )}>
                <div className={cn("w-1.5 h-1.5 rounded-full",
                  gatewayStatus.state === 'running' && gatewayStatus.gatewayReady !== false ? "bg-green-500" :
                    gatewayStatus.state === 'running' ? "bg-red-500" :
                      gatewayStatus.state === 'error' ? "bg-red-500" : "bg-muted-foreground"
                )} />
                {gatewayStatus.state === 'running' && gatewayStatus.gatewayReady === false ? 'starting' : gatewayStatus.state}
              </div>
              <Button variant="outline" size="sm" onClick={restartGateway} className="rounded-full">
                <RefreshCw className="h-3.5 w-3.5 mr-1.5" />
                {t('common:actions.restart')}
              </Button>
              <Button variant="outline" size="sm" onClick={handleShowLogs} className="rounded-full">
                <FileText className="h-3.5 w-3.5 mr-1.5" />
                {t('gateway.logs')}
              </Button>
            </>
          }
        >
          {showLogs && (
            <div className="rounded-xl bg-surface-input p-4 border">
              <div className="flex items-center justify-between mb-3">
                <p className="font-medium text-sm">{t('gateway.appLogs')}</p>
                <div className="flex gap-2">
                  <Button variant="ghost" size="sm" className="h-7 text-xs rounded-full" onClick={handleOpenLogDir}>
                    <ExternalLink className="h-3 w-3 mr-1.5" />
                    {t('gateway.openFolder')}
                  </Button>
                  <Button variant="ghost" size="sm" className="h-7 text-xs rounded-full" onClick={() => setShowLogs(false)}>
                    {t('common:actions.close')}
                  </Button>
                </div>
              </div>
              <pre role="log" aria-live="polite" className="text-xs text-muted-foreground bg-surface-modal p-4 rounded-xl max-h-60 overflow-auto whitespace-pre-wrap font-mono border shadow-inner">
                {logContent || t('chat:noLogs')}
              </pre>
            </div>
          )}
        </SettingRow>
      </SettingsGroup>

      <SettingsGroup gradientClass={gradientClass} icon={Power}>
        <SettingRow
          icon={Power}
          label={t('gateway.autoStart')}
          description={t('gateway.autoStartDesc')}
          control={<Switch checked={gatewayAutoStart} onCheckedChange={setGatewayAutoStart} />}
        />
        <SettingRow
          icon={Shield}
          label={t('advanced.devMode')}
          description={t('advanced.devModeDesc')}
          control={
            <Switch
              checked={devModeUnlocked}
              onCheckedChange={setDevModeUnlocked}
              data-testid="settings-dev-mode-switch"
            />
          }
        />
        <SettingRow
          icon={Activity}
          label={t('advanced.telemetry')}
          description={t('advanced.telemetryDesc')}
          control={<Switch checked={telemetryEnabled} onCheckedChange={setTelemetryEnabled} />}
        />
      </SettingsGroup>
    </div>
  );
}

export default GatewaySection;
