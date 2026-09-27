/**
 * BottomTabBar Component
 * Fixed bottom tab navigation for the mobile shell.
 * Tabs: Chat + first extension nav items inline + More. Extension items beyond
 * the inline budget live on the "More" page.
 */
import { NavLink } from 'react-router-dom';
import { MessageSquare, MoreHorizontal } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { cn } from '@/lib/utils';
import { rendererExtensionRegistry } from '@/extensions/registry';

/** Extension nav items shown inline in the tab bar; the rest go to "More". */
export const MAX_INLINE_EXTRA_TABS = 2;

interface TabItemProps {
  to: string;
  icon: React.ReactNode;
  label: string;
  testId?: string;
}

function TabItem({ to, icon, label, testId }: TabItemProps) {
  return (
    <NavLink
      to={to}
      data-testid={testId}
      className={({ isActive }) =>
        cn(
          'flex min-h-[52px] flex-1 flex-col items-center justify-center gap-0.5 rounded-lg transition-colors',
          'active:bg-black/5 dark:active:bg-white/10',
          isActive
            ? 'text-orange-600 dark:text-orange-400 font-medium'
            : 'text-foreground/60',
        )
      }
    >
      <div className="flex items-center justify-center [&_svg]:size-5">{icon}</div>
      <span className="text-[10px] leading-tight">{label}</span>
    </NavLink>
  );
}

export function BottomTabBar() {
  const { t } = useTranslation('common');
  const hiddenRoutes = rendererExtensionRegistry.getHiddenRoutes();
  const extraNavItems = rendererExtensionRegistry
    .getExtraNavItems()
    .filter((item) => !hiddenRoutes.has(item.to));
  const inlineExtras = extraNavItems.slice(0, MAX_INLINE_EXTRA_TABS);

  return (
    <nav
      data-testid="bottom-tab-bar"
      className="pb-safe shrink-0 border-t border-black/5 dark:border-white/10 bg-surface-modal"
    >
      <div className="flex items-stretch px-1">
        <TabItem
          to="/"
          icon={<MessageSquare strokeWidth={2} />}
          label={t('tabs.chat', '聊天')}
          testId="tab-chat"
        />
        {inlineExtras.map((item) => (
          <TabItem
            key={item.to}
            to={item.to}
            icon={<item.icon strokeWidth={2} />}
            label={item.labelI18nKey ? t(item.labelI18nKey) : item.label}
            testId={item.testId}
          />
        ))}
        <TabItem
          to="/more"
          icon={<MoreHorizontal strokeWidth={2} />}
          label={t('tabs.more', '更多')}
          testId="tab-more"
        />
      </div>
    </nav>
  );
}
