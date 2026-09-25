/**
 * MobileTopBar Component
 * Slim mobile top bar: hamburger (session drawer) + logo + gateway status + settings.
 * Honors the iOS notch via safe-area padding.
 */
import { Menu, Settings as SettingsIcon } from 'lucide-react';
import { GatewayStatusChip } from '@/components/common/GatewayStatusChip';
import { useSettingsUiStore } from '@/stores/settings-ui';
import { useTranslation } from 'react-i18next';
import logoSvg from '@/assets/logo.svg';

interface MobileTopBarProps {
  onOpenDrawer: () => void;
}

export function MobileTopBar({ onOpenDrawer }: MobileTopBarProps) {
  const { t } = useTranslation('common');
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

        {/* Gateway status chip: opens a details popover, or the failure dialog */}
        <GatewayStatusChip className="rounded-full px-2 py-1 text-xs" />

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
