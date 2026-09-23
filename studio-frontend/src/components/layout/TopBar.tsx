/**
 * TopBar Component
 * Full-width top bar with logo, gateway status, and window controls.
 * Replaces the old TitleBar + Sidebar header/footer pattern.
 */
import { useState, useEffect, type ReactNode } from 'react';
import { Minus, Square, X, RectangleHorizontal, Settings as SettingsIcon, Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useGatewayStore } from '@/stores/gateway';
import { useSettingsUiStore } from '@/stores/settings-ui';
import { hostApi } from '@/lib/host-api';
import { useTranslation } from 'react-i18next';
import logoSvg from '@/assets/logo.svg';

export function TopBar({ children }: { children?: ReactNode }) {
  const platform = window.electron?.platform;

  if (platform === 'darwin') {
    return <MacTopBar>{children}</MacTopBar>;
  }

  if (platform === 'win32') {
    return <WindowsTopBar>{children}</WindowsTopBar>;
  }

  // Linux: native chrome, but still show app content bar
  return <LinuxTopBar>{children}</LinuxTopBar>;
}

function MacTopBar({ children }: { children?: ReactNode }) {
  const { t } = useTranslation('common');
  const gatewayStatus = useGatewayStore((s) => s.status);
  const isGatewayRunning = gatewayStatus.state === 'running';
  const isGatewayStarting = gatewayStatus.state === 'starting';
  const openSettings = useSettingsUiStore((s) => s.openSettings);

  return (
    <div className="drag-region flex h-12 shrink-0 items-center px-4">
      {/* Left: traffic light space is handled by sidebar */}
      <div className="flex items-center gap-2.5 no-drag">
        <img src={logoSvg} alt="JYparalegal" className="h-5 w-auto shrink-0" />
        <span className="text-sm font-semibold truncate whitespace-nowrap text-foreground/90">
          JYparalegal
        </span>
      </div>
      <div className="min-w-0 flex-1 overflow-x-auto no-drag">{children}</div>
      <div className="flex items-center gap-2 no-drag">
        {/* Connection status */}
        <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs">
          {isGatewayStarting ? (
            <Loader2 className="h-2 w-2 rounded-full animate-spin text-yellow-500" />
          ) : (
            <div className={cn(
              'h-2 w-2 rounded-full',
              isGatewayRunning ? 'bg-green-500' : 'bg-red-500'
            )} />
          )}
          <span className={cn(
            isGatewayStarting ? 'text-yellow-700 dark:text-yellow-400' :
            isGatewayRunning ? 'text-green-700 dark:text-green-400' : 'text-red-700 dark:text-red-400'
          )}>
            {isGatewayStarting ? t('gateway.connecting', '连接中') :
             isGatewayRunning ? t('gateway.connected', '已连接') : t('gateway.disconnected', '未连接')}
          </span>
        </div>
        <button
          type="button"
          onClick={() => openSettings()}
          data-testid="topbar-nav-settings"
          className="flex h-8 w-8 items-center justify-center rounded-lg text-foreground/70 hover:bg-black/5 hover:text-foreground dark:hover:bg-white/10 focus-visible:outline-none transition-colors"
          title="Settings"
        >
          <SettingsIcon className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}

function WindowsTopBar({ children }: { children?: ReactNode }) {
  const { t } = useTranslation('common');
  const gatewayStatus = useGatewayStore((s) => s.status);
  const isGatewayRunning = gatewayStatus.state === 'running';
  const isGatewayStarting = gatewayStatus.state === 'starting';
  const openSettings = useSettingsUiStore((s) => s.openSettings);

  return (
    <div className="drag-region flex h-12 shrink-0 items-center px-4">
      <div className="flex items-center gap-2.5 no-drag">
        <img src={logoSvg} alt="JYparalegal" className="h-5 w-auto shrink-0" />
        <span className="text-sm font-semibold truncate whitespace-nowrap text-foreground/90">
          JYparalegal
        </span>
      </div>
      <div className="min-w-0 flex-1 overflow-x-auto no-drag">{children}</div>
      <div className="flex items-center gap-2 no-drag">
        {/* Connection status */}
        <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs">
          {isGatewayStarting ? (
            <Loader2 className="h-2 w-2 rounded-full animate-spin text-yellow-500" />
          ) : (
            <div className={cn(
              'h-2 w-2 rounded-full',
              isGatewayRunning ? 'bg-green-500' : 'bg-red-500'
            )} />
          )}
          <span className={cn(
            isGatewayStarting ? 'text-yellow-700 dark:text-yellow-400' :
            isGatewayRunning ? 'text-green-700 dark:text-green-400' : 'text-red-700 dark:text-red-400'
          )}>
            {isGatewayStarting ? t('gateway.connecting', '连接中') :
             isGatewayRunning ? t('gateway.connected', '已连接') : t('gateway.disconnected', '未连接')}
          </span>
        </div>
        <button
          type="button"
          onClick={() => openSettings()}
          data-testid="topbar-nav-settings"
          className="flex h-8 w-8 items-center justify-center rounded-lg text-foreground/70 hover:bg-black/5 hover:text-foreground dark:hover:bg-white/10 focus-visible:outline-none transition-colors"
          title="Settings"
        >
          <SettingsIcon className="h-4 w-4" />
        </button>
        <WindowsWindowControls />
      </div>
    </div>
  );
}

function LinuxTopBar({ children }: { children?: ReactNode }) {
  const { t } = useTranslation('common');
  const gatewayStatus = useGatewayStore((s) => s.status);
  const isGatewayRunning = gatewayStatus.state === 'running';
  const isGatewayStarting = gatewayStatus.state === 'starting';
  const openSettings = useSettingsUiStore((s) => s.openSettings);

  return (
    <div className="flex h-12 shrink-0 items-center px-4">
      <div className="flex items-center gap-2.5">
        <img src={logoSvg} alt="JYparalegal" className="h-5 w-auto shrink-0" />
        <span className="text-sm font-semibold truncate whitespace-nowrap text-foreground/90">
          JYparalegal
        </span>
      </div>
      <div className="min-w-0 flex-1 overflow-x-auto no-drag">{children}</div>
      <div className="flex items-center gap-2">
        {/* Connection status */}
        <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs">
          {isGatewayStarting ? (
            <Loader2 className="h-2 w-2 rounded-full animate-spin text-yellow-500" />
          ) : (
            <div className={cn(
              'h-2 w-2 rounded-full',
              isGatewayRunning ? 'bg-green-500' : 'bg-red-500'
            )} />
          )}
          <span className={cn(
            isGatewayStarting ? 'text-yellow-700 dark:text-yellow-400' :
            isGatewayRunning ? 'text-green-700 dark:text-green-400' : 'text-red-700 dark:text-red-400'
          )}>
            {isGatewayStarting ? t('gateway.connecting', '连接中') :
             isGatewayRunning ? t('gateway.connected', '已连接') : t('gateway.disconnected', '未连接')}
          </span>
        </div>
        <button
          type="button"
          onClick={() => openSettings()}
          data-testid="topbar-nav-settings"
          className="flex h-8 w-8 items-center justify-center rounded-lg text-foreground/70 hover:bg-black/5 hover:text-foreground dark:hover:bg-white/10 focus-visible:outline-none transition-colors"
          title="Settings"
        >
          <SettingsIcon className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}

/** Windows minimize/maximize/close buttons */
function WindowsWindowControls() {
  const [maximized, setMaximized] = useState(false);

  useEffect(() => {
    hostApi.window.isMaximized().then((val) => {
      setMaximized(val);
    });
  }, []);

  return (
    <div className="flex h-full">
      <button
        onClick={() => void hostApi.window.minimize()}
        className="flex h-10 w-10 items-center justify-center text-muted-foreground hover:bg-black/5 hover:text-foreground dark:hover:bg-white/10 transition-colors"
        title="Minimize"
      >
        <Minus className="h-4 w-4" />
      </button>
      <button
        onClick={() => {
          hostApi.window.maximize().then(() => {
            hostApi.window.isMaximized().then((val) => setMaximized(val));
          });
        }}
        className="flex h-10 w-10 items-center justify-center text-muted-foreground hover:bg-black/5 hover:text-foreground dark:hover:bg-white/10 transition-colors"
        title={maximized ? 'Restore' : 'Maximize'}
      >
        {maximized ? <RectangleHorizontal className="h-3.5 w-3.5" /> : <Square className="h-3.5 w-3.5" />}
      </button>
      <button
        onClick={() => void hostApi.window.close()}
        className="flex h-10 w-10 items-center justify-center text-muted-foreground hover:bg-red-500 hover:text-white transition-colors"
        title="Close"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}
