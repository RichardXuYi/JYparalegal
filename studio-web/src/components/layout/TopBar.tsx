/**
 * TopBar — glass band sitting directly on the connected shell gradient.
 * brand + tenant switcher | (level tabs are rendered below by PlatformTabs) | role / gateway / notify / avatar / settings.
 */
import { Bell, Building2, Settings as SettingsIcon } from 'lucide-react';
import { GatewayStatusChip } from '@/components/common/GatewayStatusChip';
import { useSettingsUiStore } from '@/stores/settings-ui';
import { useAuthStore } from '@/stores/auth';
import { useTranslation } from 'react-i18next';
import logoSvg from '@/assets/logo.svg';

export function TopBar() {
  const { t } = useTranslation('common');
  const openSettings = useSettingsUiStore((s) => s.openSettings);
  // 真实登录身份：头像/标题一律取自会话，不再硬编码姓名或角色（与桌面 TopBar 保持一致）。
  const user = useAuthStore((s) => s.user);
  const displayName = user?.username?.trim() ?? '';
  const avatarInitial = displayName ? displayName[0].toUpperCase() : '?';
  const avatarTitle = user
    ? (user.role ? `${displayName} · ${user.role}` : displayName)
    : t('topbar.guest');

  return (
    <div className="flex h-12 shrink-0 items-center gap-3 px-4">
      {/* Left: brand + workspace label */}
      <div className="flex shrink-0 items-center gap-2.5">
        <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-white shadow-md">
          <img src={logoSvg} alt="JYparalegal" className="h-4 w-auto" />
        </span>
        <span className="truncate whitespace-nowrap text-sm font-semibold text-white">
          JYparalegal
        </span>
        {/* 租户/企业名此前为硬编码假数据（"君言律师事务所/个人空间"）。AuthUser 目前不携带
            企业名，故显示中性标签；接入真实企业数据后再替换。 */}
        <span className="shell-glass flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-tiny text-white/90">
          <Building2 className="h-3 w-3" strokeWidth={1.75} />
          <span>{t('topbar.workspace')}</span>
        </span>
      </div>

      <div className="min-w-0 flex-1" />

      {/* Right: role / gateway / notify / avatar / settings */}
      <div className="flex shrink-0 items-center gap-2">
        <label className="shell-glass flex cursor-pointer select-none items-center gap-1.5 rounded-lg px-2.5 py-1 text-tiny text-white/90">
          <input type="checkbox" className="h-3 w-3 accent-white" />
          {t('topbar.employeeView')}
        </label>
        <GatewayStatusChip glass />
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
          title={avatarTitle}
          aria-label={avatarTitle}
        >
          {avatarInitial}
        </button>
      </div>
    </div>
  );
}
