/**
 * TopBar — glass band sitting directly on the connected shell gradient.
 * brand + tenant switcher | (level tabs are rendered below by PlatformTabs) | role / gateway / notify / avatar / settings.
 */
import { Bell, Building2, ChevronDown, Loader2, Settings as SettingsIcon } from 'lucide-react';
import { cn } from '@/lib/utils';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { useGatewayStore } from '@/stores/gateway';
import { useSettingsUiStore } from '@/stores/settings-ui';
import { useTranslation } from 'react-i18next';
import logoSvg from '@/assets/logo.svg';

const TENANTS = ['君言律师事务所', '个人空间'];

export function TopBar() {
  const { t } = useTranslation('common');
  const gatewayStatus = useGatewayStore((s) => s.status);
  const isGatewayRunning = gatewayStatus.state === 'running';
  const isGatewayStarting = gatewayStatus.state === 'starting';
  const openSettings = useSettingsUiStore((s) => s.openSettings);

  return (
    <div className="flex h-12 shrink-0 items-center gap-3 px-4">
      {/* Left: brand + tenant switcher */}
      <div className="flex shrink-0 items-center gap-2.5">
        <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-white shadow-md">
          <img src={logoSvg} alt="JYparalegal" className="h-4 w-auto" />
        </span>
        <span className="truncate whitespace-nowrap text-sm font-semibold text-white">
          JYparalegal
        </span>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              type="button"
              className="shell-glass flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-tiny text-white/90 transition-colors hover:bg-white/20"
            >
              <Building2 className="h-3 w-3" strokeWidth={1.75} />
              <span>{TENANTS[0]}</span>
              <ChevronDown className="h-3 w-3" />
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="start">
            {TENANTS.map((name) => (
              <DropdownMenuItem key={name}>
                <Building2 className="h-3.5 w-3.5" strokeWidth={1.75} />
                <span>{name}</span>
              </DropdownMenuItem>
            ))}
            <DropdownMenuItem>
              <span>＋ 创建新企业…</span>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>

      <div className="min-w-0 flex-1" />

      {/* Right: role / gateway / notify / avatar / settings */}
      <div className="flex shrink-0 items-center gap-2">
        <label className="shell-glass flex cursor-pointer select-none items-center gap-1.5 rounded-lg px-2.5 py-1 text-tiny text-white/90">
          <input type="checkbox" className="h-3 w-3 accent-white" />
          员工视角
        </label>
        <div className="shell-glass flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-tiny">
          {isGatewayStarting ? (
            <Loader2 className="h-2.5 w-2.5 animate-spin text-yellow-300" />
          ) : (
            <span
              className={cn(
                'h-2 w-2 rounded-full',
                isGatewayRunning ? 'bg-green-400 shadow-[0_0_0_3px_rgba(74,222,128,0.25)]' : 'bg-red-400',
              )}
            />
          )}
          <span className="text-white/90">
            {isGatewayStarting
              ? t('gateway.connecting', '连接中')
              : isGatewayRunning
                ? t('gateway.connected', '已连接')
                : t('gateway.disconnected', '未连接')}
          </span>
        </div>
        <button
          type="button"
          className="flex h-8 w-8 items-center justify-center rounded-lg text-white/80 transition-colors hover:bg-white/15 hover:text-white"
          title={t('notifications', '通知')}
          aria-label={t('notifications', '通知')}
        >
          <Bell className="h-4 w-4" strokeWidth={1.75} />
        </button>
        <button
          type="button"
          onClick={() => openSettings()}
          data-testid="topbar-nav-settings"
          className="flex h-8 w-8 items-center justify-center rounded-lg text-white/80 transition-colors hover:bg-white/15 hover:text-white focus-visible:outline-none"
          title={t('settings.title', '设置')}
          aria-label={t('settings.title', '设置')}
        >
          <SettingsIcon className="h-4 w-4" strokeWidth={1.75} />
        </button>
        <button
          type="button"
          className="flex h-7 w-7 items-center justify-center rounded-full bg-white text-xs font-bold text-indigo-700 shadow-[0_0_0_2px_rgba(255,255,255,0.35)]"
          title="许一 · ADMIN"
        >
          许
        </button>
      </div>
    </div>
  );
}
