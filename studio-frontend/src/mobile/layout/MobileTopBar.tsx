/**
 * MobileTopBar Component
 * Slim mobile top bar: hamburger (session drawer) + logo + gateway status + settings.
 * Honors the iOS notch via safe-area padding.
 */
import { Menu, Settings as SettingsIcon, Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useGatewayStore } from '@/stores/gateway';
import { useSettingsUiStore } from '@/stores/settings-ui';
import { useTranslation } from 'react-i18next';
import logoSvg from '@/assets/logo.svg';

interface MobileTopBarProps {
  onOpenDrawer: () => void;
}

export function MobileTopBar({ onOpenDrawer }: MobileTopBarProps) {
  const { t } = useTranslation('common');
  const gatewayStatus = useGatewayStore((s) => s.status);
  const isGatewayRunning = gatewayStatus.state === 'running';
  const isGatewayStarting = gatewayStatus.state === 'starting';
  const openSettings = useSettingsUiStore((s) => s.openSettings);

  return (
    <header
      data-testid="mobile-top-bar"
      className="pt-safe shrink-0 bg-surface-modal border-b border-black/5 dark:border-white/10"
    >
      <div className="flex h-12 items-center gap-1 px-2">
        {/* Hamburger: opens the session drawer */}
        <button
          type="button"
          data-testid="mobile-open-drawer"
          onClick={onOpenDrawer}
          aria-label={t('sidebar.openSessions', '打开会话列表')}
          className="flex h-11 w-11 items-center justify-center rounded-lg text-foreground/80 active:bg-black/5 dark:active:bg-white/10 transition-colors"
        >
          <Menu className="h-5 w-5" />
        </button>

        {/* Logo + App name */}
        <div className="flex min-w-0 flex-1 items-center gap-2">
          <img src={logoSvg} alt="JYparalegal" className="h-5 w-auto shrink-0" />
          <span className="truncate text-sm font-semibold text-foreground/90">
            JYparalegal
          </span>
        </div>

        {/* Gateway status: compact dot (label hidden on very narrow screens) */}
        <div
          className="flex shrink-0 items-center gap-1.5 px-2 py-1 rounded-full text-xs"
          title={
            isGatewayStarting ? t('gateway.connecting', '连接中') :
            isGatewayRunning ? t('gateway.connected', '已连接') : t('gateway.disconnected', '未连接')
          }
        >
          {isGatewayStarting ? (
            <Loader2 className="h-2 w-2 rounded-full animate-spin text-yellow-500" />
          ) : (
            <div className={cn(
              'h-2 w-2 rounded-full',
              isGatewayRunning ? 'bg-green-500' : 'bg-red-500'
            )} />
          )}
          <span className={cn(
            'hidden min-[380px]:inline transition-opacity duration-200',
            isGatewayStarting ? 'text-yellow-700 dark:text-yellow-400' :
            isGatewayRunning ? 'text-green-700 dark:text-green-400' : 'text-red-700 dark:text-red-400'
          )}>
            {isGatewayStarting ? t('gateway.connecting', '连接中') :
             isGatewayRunning ? t('gateway.connected', '已连接') : t('gateway.disconnected', '未连接')}
          </span>
        </div>

        {/* Settings button */}
        <button
          type="button"
          onClick={() => openSettings()}
          data-testid="topbar-nav-settings"
          className="flex h-11 w-11 items-center justify-center rounded-lg text-foreground/70 active:bg-black/5 dark:active:bg-white/10 transition-colors"
          title={t('sidebar.settings')}
        >
          <SettingsIcon className="h-5 w-5" />
        </button>
      </div>
    </header>
  );
}
