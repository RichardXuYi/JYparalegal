/**
 * TopBar — glass band sitting directly on the connected shell gradient.
 * brand + tenant switcher | drag region | role / gateway / notify / settings / avatar (+ Windows window controls).
 */
import { useState, useEffect } from 'react';
import { Bell, Building2, Minus, Square, X, RectangleHorizontal, Settings as SettingsIcon, Sun, Moon } from 'lucide-react';
import { GatewayStatusChip } from '@/components/common/GatewayStatusChip';
import { useSettingsUiStore } from '@/stores/settings-ui';
import { useSettingsStore } from '@/stores/settings';
import { useAuthStore } from '@/stores/auth';
import { hostApi } from '@/lib/host-api';
import { useTranslation } from 'react-i18next';
import logoSvg from '@/assets/logo.svg';

export function TopBar() {
  const { t } = useTranslation('common');
  const platform = window.electron?.platform;
  const openSettings = useSettingsUiStore((s) => s.openSettings);
  const theme = useSettingsStore((s) => s.theme);
  const setTheme = useSettingsStore((s) => s.setTheme);
  // 真实登录身份：头像/标题一律取自会话，不再硬编码姓名或角色。
  const user = useAuthStore((s) => s.user);
  const displayName = user?.username?.trim() ?? '';
  const avatarInitial = displayName ? displayName[0].toUpperCase() : '?';
  const avatarTitle = user
    ? (user.role ? `${displayName} · ${user.role}` : displayName)
    : t('topbar.guest');

  const toggleTheme = () => {
    if (theme === 'light') setTheme('dark');
    else if (theme === 'dark') setTheme('system');
    else setTheme('light');
  };

  const ThemeIcon = theme === 'dark' ? Moon : Sun;
  const themeTitle = theme === 'light' ? t('appearance.light', '浅色') : theme === 'dark' ? t('appearance.dark', '深色') : t('appearance.system', '跟随系统');

  return (
    <div className="drag-region flex h-12 shrink-0 items-center gap-3 px-4">
      {/* Left: brand + tenant switcher */}
      <div className="no-drag flex shrink-0 items-center gap-2.5">
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

      <div className="min-w-0 flex-1 self-stretch" />

      {/* Right: role / gateway / notify / settings / avatar (+ window controls) */}
      <div className="no-drag flex shrink-0 items-center gap-2">
        <button
          type="button"
          onClick={toggleTheme}
          className="shell-glass flex cursor-pointer select-none items-center gap-1.5 rounded-lg px-2.5 py-1 text-tiny text-white/90 hover:bg-white/15 transition-colors"
          title={themeTitle}
          aria-label={themeTitle}
        >
          <ThemeIcon className="h-3.5 w-3.5" strokeWidth={1.75} />
        </button>
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
          className="flex h-7 w-7 items-center justify-center rounded-full bg-white text-xs font-bold text-indigo-700 dark:text-red-600 shadow-[0_0_0_2px_rgba(255,255,255,0.35)]"
          title={avatarTitle}
          aria-label={avatarTitle}
        >
          {avatarInitial}
        </button>
        {platform === 'win32' && <WindowsWindowControls />}
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
    <div className="ml-1 flex h-full">
      <button
        onClick={() => void hostApi.window.minimize()}
        className="flex h-9 w-11 items-center justify-center text-white/80 hover:bg-white/15 hover:text-white transition-colors"
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
        className="flex h-9 w-11 items-center justify-center text-white/80 hover:bg-white/15 hover:text-white transition-colors"
        title={maximized ? 'Restore' : 'Maximize'}
      >
        {maximized ? <RectangleHorizontal className="h-3.5 w-3.5" /> : <Square className="h-3.5 w-3.5" />}
      </button>
      <button
        onClick={() => void hostApi.window.close()}
        className="flex h-9 w-11 items-center justify-center text-white/80 hover:bg-red-500 hover:text-white transition-colors"
        title="Close"
      >
        <X className="h-4 w-4" />
      </button>
    </div>
  );
}
