/**
 * More Page (mobile)
 * Aggregates low-frequency destinations that don't fit in the bottom tab bar:
 * overflow extension nav items, settings, and account actions.
 */
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ChevronRight, LogOut, Monitor, Settings as SettingsIcon } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';
import { rendererExtensionRegistry } from '@/extensions/registry';
import { useAuthStore } from '@/stores/auth';
import { useSettingsUiStore } from '@/stores/settings-ui';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { setShellPreference } from '@/lib/device-shell';
import { MAX_INLINE_EXTRA_TABS } from '@/mobile/layout/BottomTabBar';

interface MoreRowProps {
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
  destructive?: boolean;
  testId?: string;
}

function MoreRow({ icon, label, onClick, destructive, testId }: MoreRowProps) {
  return (
    <button
      type="button"
      data-testid={testId}
      onClick={onClick}
      className={cn(
        'flex w-full min-h-[52px] items-center gap-3 px-4 text-left text-sm transition-colors',
        'active:bg-black/5 dark:active:bg-white/10',
        destructive ? 'text-destructive' : 'text-foreground/85',
      )}
    >
      <div className="flex shrink-0 items-center justify-center [&_svg]:size-5">{icon}</div>
      <span className="flex-1 truncate">{label}</span>
      {!destructive && <ChevronRight className="h-4 w-4 shrink-0 text-muted-foreground/50" />}
    </button>
  );
}

export function More() {
  const { t } = useTranslation('common');
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const logout = useAuthStore((s) => s.logout);
  const openSettings = useSettingsUiStore((s) => s.openSettings);
  const [logoutDialogOpen, setLogoutDialogOpen] = useState(false);

  // Extension nav items that didn't make it into the bottom tab bar.
  const hiddenRoutes = rendererExtensionRegistry.getHiddenRoutes();
  const overflowNavItems = rendererExtensionRegistry
    .getExtraNavItems()
    .filter((item) => !hiddenRoutes.has(item.to))
    .slice(MAX_INLINE_EXTRA_TABS);

  return (
    <div className="h-full overflow-y-auto overscroll-contain">
      {/* User card */}
      <div className="mx-4 mt-4 flex items-center gap-3 rounded-2xl bg-gradient-to-br from-indigo-500/10 to-violet-500/10 p-4">
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-br from-indigo-500 to-violet-600 text-lg font-bold text-white">
          {(user?.username ?? '?')[0]?.toUpperCase()}
        </div>
        <div className="flex min-w-0 flex-1 flex-col">
          <span className="truncate text-sm font-semibold text-foreground/90">
            {user?.username ?? t('auth.unknownUser', '未登录')}
          </span>
          {(user?.email || user?.role) && (
            <span className="truncate text-xs text-muted-foreground">
              {user?.email ?? user?.role}
            </span>
          )}
        </div>
      </div>

      {/* Overflow extension destinations */}
      {overflowNavItems.length > 0 && (
        <div className="mx-4 mt-4 overflow-hidden rounded-2xl bg-black/[0.03] dark:bg-white/[0.04] divide-y divide-black/5 dark:divide-white/5">
          {overflowNavItems.map((item) => (
            <MoreRow
              key={item.to}
              icon={<item.icon strokeWidth={2} />}
              label={item.labelI18nKey ? t(item.labelI18nKey) : item.label}
              onClick={() => navigate(item.to)}
              testId={item.testId}
            />
          ))}
        </div>
      )}

      {/* Settings & shell switch */}
      <div className="mx-4 mt-4 overflow-hidden rounded-2xl bg-black/[0.03] dark:bg-white/[0.04] divide-y divide-black/5 dark:divide-white/5">
        <MoreRow
          icon={<SettingsIcon strokeWidth={2} />}
          label={t('sidebar.settings')}
          onClick={() => openSettings()}
          testId="more-settings"
        />
        <MoreRow
          icon={<Monitor strokeWidth={2} />}
          label={t('shell.switchToDesktop', '切换到桌面版')}
          onClick={() => setShellPreference('desktop')}
          testId="more-switch-desktop"
        />
      </div>

      {/* Sign out */}
      <div className="mx-4 my-4 overflow-hidden rounded-2xl bg-black/[0.03] dark:bg-white/[0.04]">
        <MoreRow
          icon={<LogOut strokeWidth={2} />}
          label={t('auth.logout', '退出登录')}
          onClick={() => setLogoutDialogOpen(true)}
          destructive
          testId="more-logout"
        />
      </div>

      <ConfirmDialog
        open={logoutDialogOpen}
        title={t('actions.confirm')}
        message={t('auth.logoutConfirm', '确定要退出登录吗？')}
        confirmLabel={t('auth.logout', '退出登录')}
        cancelLabel={t('actions.cancel')}
        variant="destructive"
        onConfirm={async () => {
          setLogoutDialogOpen(false);
          await logout();
        }}
        onCancel={() => setLogoutDialogOpen(false)}
      />
    </div>
  );
}
