/**
 * TopBar Component
 * studio-web 是纯浏览器壳:单条顶栏 = 品牌 | 平台导航(children) | 网关状态 + 设置。
 * 不再渲染桌面窗口控件(最小化/最大化/关闭)——浏览器自有窗口 chrome,这些在 web 下无意义。
 */
import type { ReactNode } from 'react';
import { Settings as SettingsIcon, Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useGatewayStore } from '@/stores/gateway';
import { useSettingsUiStore } from '@/stores/settings-ui';
import { useTranslation } from 'react-i18next';
import logoSvg from '@/assets/logo.svg';

export function TopBar({ children }: { children?: ReactNode }) {
  const { t } = useTranslation('common');
  const gatewayStatus = useGatewayStore((s) => s.status);
  const isGatewayRunning = gatewayStatus.state === 'running';
  const isGatewayStarting = gatewayStatus.state === 'starting';
  const openSettings = useSettingsUiStore((s) => s.openSettings);

  return (
    <div className="flex h-12 shrink-0 items-center gap-3 px-4">
      {/* Left: brand */}
      <div className="flex shrink-0 items-center gap-2.5">
        <img src={logoSvg} alt="JYparalegal" className="h-5 w-auto shrink-0" />
        <span className="truncate whitespace-nowrap text-sm font-semibold text-foreground/90">
          JYparalegal
        </span>
      </div>

      {/* Center: platform navigation (injected by MainLayout) */}
      <div className="min-w-0 flex-1 overflow-x-auto">{children}</div>

      {/* Right: gateway status + settings */}
      <div className="flex shrink-0 items-center gap-2">
        <div className="flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs">
          {isGatewayStarting ? (
            <Loader2 className="h-2 w-2 animate-spin rounded-full text-yellow-500" />
          ) : (
            <div className={cn('h-2 w-2 rounded-full', isGatewayRunning ? 'bg-green-500' : 'bg-red-500')} />
          )}
          <span className={cn(
            isGatewayStarting ? 'text-yellow-700 dark:text-yellow-400'
              : isGatewayRunning ? 'text-green-700 dark:text-green-400'
                : 'text-red-700 dark:text-red-400',
          )}>
            {isGatewayStarting ? t('gateway.connecting', '连接中')
              : isGatewayRunning ? t('gateway.connected', '已连接')
                : t('gateway.disconnected', '未连接')}
          </span>
        </div>
        <button
          type="button"
          onClick={() => openSettings()}
          data-testid="topbar-nav-settings"
          className="flex h-8 w-8 items-center justify-center rounded-lg text-foreground/70 transition-colors hover:bg-black/5 hover:text-foreground focus-visible:outline-none dark:hover:bg-white/10"
          title={t('settings.title', '设置')}
          aria-label={t('settings.title', '设置')}
        >
          <SettingsIcon className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}
