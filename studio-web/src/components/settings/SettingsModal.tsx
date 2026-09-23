/**
 * Settings Modal
 * Two-pane settings surface: left category navigation + right content.
 * Opened from the top-bar gear icon. Consolidates former sidebar feature
 * pages (models / channels / skills / cron / image generation / dreams)
 * together with application preferences.
 *
 * Mobile: king-kong icon grid → tap to enter sub-page with back/close nav bar.
 * Desktop: two-pane layout (nav sidebar + content).
 */
import { useEffect, useState, type ComponentType, type ReactNode } from 'react';
import type { TFunction } from 'i18next';
import {
  Cpu,
  Bot,
  Network,
  Puzzle,
  Clock,
  ImagePlus,
  Moon,
  SlidersHorizontal,
  Server,
  Terminal,
  Info,
  X,
  ArrowLeft,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Dialog, DialogClose, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { cn } from '@/lib/utils';
import { useSettingsStore } from '@/stores/settings';
import {
  useSettingsUiStore,
  DEFAULT_SETTINGS_SECTION,
  DEV_ONLY_SETTINGS_SECTIONS,
  type SettingsSection,
} from '@/stores/settings-ui';
import { useIsMobile } from '@/hooks/use-is-mobile';

import { Models } from '@/pages/Models';
import { Agents } from '@/pages/Agents';
import { Channels } from '@/pages/Channels';
import { Skills } from '@/pages/Skills';
import { Cron } from '@/pages/Cron';
import { Dreams } from '@/pages/Dreams';
import { ImageGenerationPage } from '@/pages/ImageGeneration';
import { AccountSection } from './sections/AccountSection';
import { AppearanceSection } from './sections/AppearanceSection';
import { GatewaySection } from './sections/GatewaySection';
import { DeveloperSection } from './sections/DeveloperSection';

import { AboutSection } from './sections/AboutSection';
import { SettingsPageHeader } from './primitives';

interface NavItem {
  id: SettingsSection;
  icon: ComponentType<{ className?: string }>;
  /** i18n key resolved against the settings namespace unless prefixed (e.g. 'common:'). */
  labelKey: string;
  /** Only shown when developer mode is unlocked. */
  devOnly?: boolean;
  /** Feature pages manage their own layout and fill the content pane. */
  fill?: boolean;
  /** Gradient class for the icon background. */
  gradientClass?: string;
}

interface NavGroup {
  titleKey: string;
  items: NavItem[];
}

const NAV_GROUPS: NavGroup[] = [
  {
    titleKey: 'nav.groupWorkspace',
    items: [
      { id: 'models', icon: Cpu, labelKey: 'common:sidebar.models', fill: true, gradientClass: 'gradient-models' },
      { id: 'agents', icon: Bot, labelKey: 'common:sidebar.agents', fill: true, gradientClass: 'gradient-agents' },
      { id: 'channels', icon: Network, labelKey: 'common:sidebar.channels', fill: true, gradientClass: 'gradient-channels' },
      { id: 'skills', icon: Puzzle, labelKey: 'common:sidebar.skills', fill: true, gradientClass: 'gradient-skills' },
      { id: 'cron', icon: Clock, labelKey: 'common:sidebar.cronTasks', fill: true, gradientClass: 'gradient-cron' },
      { id: 'imageGeneration', icon: ImagePlus, labelKey: 'common:sidebar.imageGeneration', fill: true, devOnly: true, gradientClass: 'gradient-updates' },
      { id: 'dreams', icon: Moon, labelKey: 'common:sidebar.openClawDreams', fill: true, devOnly: true, gradientClass: 'gradient-about' },
    ],
  },
  {
    titleKey: 'nav.groupApp',
    items: [
      { id: 'general', icon: SlidersHorizontal, labelKey: 'nav.general', gradientClass: 'gradient-general' },
      { id: 'gateway', icon: Server, labelKey: 'gateway.title', gradientClass: 'gradient-gateway' },
      { id: 'developer', icon: Terminal, labelKey: 'developer.title', devOnly: true, gradientClass: 'gradient-developer' },

      { id: 'about', icon: Info, labelKey: 'about.title', gradientClass: 'gradient-about' },
    ],
  },
];

/** Renders the active section body. Feature pages fill; preferences scroll in a padded column. */
function SectionContent({ section, t }: { section: SettingsSection; t: TFunction }) {
  switch (section) {
    case 'models':
      return <Models />;
    case 'agents':
      return <Agents />;
    case 'channels':
      return <Channels />;
    case 'skills':
      return <Skills />;
    case 'cron':
      return <Cron />;
    case 'imageGeneration':
      return <ImageGenerationPage />;
    case 'dreams':
      return <Dreams />;
    case 'general':
      return (
        <PreferencesColumn
          title={t('nav.general')}
          description={t('general.description', '账户、外观与语言等常用偏好设置')}
          icon={SlidersHorizontal}
          gradientClass="gradient-general"
        >
          <AccountSection gradientClass="gradient-general" />
          <AppearanceSection gradientClass="gradient-general" />
        </PreferencesColumn>
      );
    case 'gateway':
      return (
        <PreferencesColumn
          title={t('gateway.title')}
          description={t('gateway.description', '本地网关的运行状态、启动方式与高级开关')}
          icon={Server}
          gradientClass="gradient-gateway"
        >
          <GatewaySection gradientClass="gradient-gateway" />
        </PreferencesColumn>
      );
    case 'developer':
      return (
        <PreferencesColumn
          title={t('developer.title')}
          description={t('developer.description', '仅供开发者使用的代理、诊断与遥测工具')}
          titleTestId="settings-developer-title"
          icon={Terminal}
          gradientClass="gradient-developer"
        >
          <DeveloperSection gradientClass="gradient-developer" />
        </PreferencesColumn>
      );
    case 'about':
      return (
        <PreferencesColumn
          title={t('about.title')}
          description={t('about.description', '版本信息与相关链接')}
          icon={Info}
          gradientClass="gradient-about"
        >
          <AboutSection gradientClass="gradient-about" />
        </PreferencesColumn>
      );
    default:
      return null;
  }
}

function PreferencesColumn({
  title,
  description,
  titleTestId,
  children,
  icon,
  gradientClass,
}: {
  title: ReactNode;
  description?: ReactNode;
  titleTestId?: string;
  children: ReactNode;
  icon?: ComponentType<{ className?: string }>;
  gradientClass?: string;
}) {
  return (
    <div className="h-full overflow-y-auto animate-slide-in">
      <div className="mx-auto max-w-none sm:max-w-3xl px-3 pr-4 pt-4 pb-6 sm:px-6 sm:pr-10 sm:pt-8 sm:pb-8">
        <SettingsPageHeader
          title={title}
          description={description}
          titleTestId={titleTestId}
          icon={icon}
          gradientClass={gradientClass}
        />
        <div className="space-y-6">{children}</div>
      </div>
    </div>
  );
}

export function SettingsModal() {
  const { t } = useTranslation(['settings', 'common']);
  const open = useSettingsUiStore((s) => s.open);
  const section = useSettingsUiStore((s) => s.section);
  const setSection = useSettingsUiStore((s) => s.setSection);
  const closeSettings = useSettingsUiStore((s) => s.closeSettings);
  const devModeUnlocked = useSettingsStore((s) => s.devModeUnlocked);
  const isMobile = useIsMobile();

  // On mobile, show the king-kong icon grid by default. When the user taps an
  // icon we flip this to false and render the sub-page. Back button flips it
  // back to true. (The store `section` always has a value — e.g. 'general' —
  // so we cannot use `!section` to detect grid mode.)
  const [mobileShowGrid, setMobileShowGrid] = useState(true);

  // If the active section becomes unavailable (dev mode turned off), fall back.
  useEffect(() => {
    if (!devModeUnlocked && DEV_ONLY_SETTINGS_SECTIONS.has(section)) {
      setSection(DEFAULT_SETTINGS_SECTION);
    }
  }, [devModeUnlocked, section, setSection]);

  const activeItem = NAV_GROUPS.flatMap((g) => g.items).find((i) => i.id === section);
  const isFill = activeItem?.fill ?? false;

  const handleMobileBack = () => {
    setMobileShowGrid(true);
  };

  const handleMobileSelect = (id: SettingsSection) => {
    setMobileShowGrid(false);
    setSection(id);
  };

  const handleDialogClose = (next: boolean) => {
    if (!next) {
      closeSettings();
      setMobileShowGrid(true);
    }
  };

  // Resolve the active nav item's label for the mobile sub-page title bar.
  const activeNavLabel = activeItem ? t(activeItem.labelKey) : '';

  return (
    <Dialog open={open} onOpenChange={handleDialogClose}>
      <DialogContent
        data-testid="settings-modal"
        hideCloseButton
        className={cn(
          'w-screen h-screen rounded-none p-0 overflow-hidden gap-0',
          isMobile ? 'flex flex-col' : 'flex flex-row',
          'sm:w-[min(1100px,92vw)] sm:max-w-[92vw] sm:h-[85vh] sm:max-h-[85vh] sm:rounded-2xl sm:flex sm:flex-row',
          '!left-0 !top-0 !transform-none',
          'sm:!left-1/2 sm:!top-1/2 sm:!transform sm:!translate-x-[-50%] sm:!translate-y-[-50%]',
        )}
        onOpenAutoFocus={(e) => e.preventDefault()}
      >
        <DialogTitle className="sr-only">{t('title')}</DialogTitle>

        {/* ========== MOBILE: king-kong icon grid (initial view) ========== */}
        {isMobile && mobileShowGrid && (
          <div className="flex flex-col h-full">
            {/* Title bar */}
            <div className="shrink-0 flex items-center justify-between px-4 pt-safe h-12 border-b border-border/50">
              <h2 className="text-base font-bold">{t('title')}</h2>
              <DialogClose className="rounded-md p-1 text-muted-foreground opacity-70 hover:opacity-100 active:bg-black/5 dark:active:bg-white/10">
                <X className="h-5 w-5" />
              </DialogClose>
            </div>
            {/* Icon grid */}
            <div className="flex-1 overflow-y-auto px-4 py-6">
              <div className="grid grid-cols-3 gap-4">
                {NAV_GROUPS.flatMap((g) => g.items)
                  .filter((item) => !item.devOnly || devModeUnlocked)
                  .map((item) => {
                    const Icon = item.icon;
                    return (
                      <button
                        key={item.id}
                        type="button"
                        onClick={() => handleMobileSelect(item.id)}
                        className="flex flex-col items-center gap-2 py-3 active:scale-95 transition-transform"
                      >
                        <div className={cn(
                          'flex h-14 w-14 items-center justify-center rounded-2xl shadow-lg',
                          item.gradientClass || 'gradient-general',
                        )}>
                          <Icon className="h-7 w-7 text-white" />
                        </div>
                        <span className="text-xs font-medium text-foreground text-center leading-tight">
                          {t(item.labelKey)}
                        </span>
                      </button>
                    );
                  })}
              </div>
            </div>
          </div>
        )}

        {/* ========== MOBILE: sub-page (after tapping an icon) ========== */}
        {isMobile && !mobileShowGrid && (
          <div className="flex flex-col h-full">
            {/* Sub-page top nav bar */}
            <div className="shrink-0 flex items-center justify-between px-2 pt-safe h-12 border-b border-border/50">
              <button
                type="button"
                onClick={handleMobileBack}
                className="flex h-10 w-10 items-center justify-center rounded-lg text-foreground/80 active:bg-black/5 dark:active:bg-white/10"
                aria-label="返回"
              >
                <ArrowLeft className="h-5 w-5" />
              </button>
              <span className="truncate text-sm font-semibold px-2">
                {activeNavLabel}
              </span>
              <DialogClose className="flex h-10 w-10 items-center justify-center rounded-lg text-muted-foreground opacity-70 hover:opacity-100 active:bg-black/5 dark:active:bg-white/10">
                <X className="h-5 w-5" />
              </DialogClose>
            </div>
            {/* Content area */}
            <div className="flex-1 min-h-0 overflow-hidden bg-surface-modal">
              <SectionContent section={section} t={t} />
            </div>
          </div>
        )}

        {/* ========== DESKTOP: two-pane layout (hidden on mobile) ========== */}
        <nav role="tablist" aria-label={t('title')} className="hidden sm:relative sm:flex w-[35%] sm:w-60 shrink-0 flex-col gap-4 overflow-y-auto border-r border-border/50 bg-surface-sidebar p-3">
          <div className="px-3 pt-2 pb-1">
            <h2 className="text-base font-bold tracking-tight">{t('title')}</h2>
          </div>
          {NAV_GROUPS.map((group) => {
            const items = group.items.filter((item) => !item.devOnly || devModeUnlocked);
            if (items.length === 0) return null;
            return (
              <div key={group.titleKey} className="flex flex-col gap-0.5">
                <p className="px-3 pb-1.5 text-[10px] font-bold uppercase tracking-widest text-muted-foreground/60">
                  {t(group.titleKey)}
                </p>
                {items.map((item) => {
                  const Icon = item.icon;
                  const isActive = item.id === section;
                  return (
                    <button
                      key={item.id}
                      type="button"
                      role="tab"
                      aria-selected={isActive}
                      data-testid={`settings-nav-${item.id}`}
                      onClick={() => setSection(item.id)}
                      className={cn(
                        'group relative flex items-center gap-3 rounded-xl px-2 py-2 sm:px-3 sm:py-2.5 text-left text-xs sm:text-sm transition-all',
                        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                        isActive
                          ? 'bg-gradient-to-r from-primary/20 to-primary/5 text-primary'
                          : 'text-muted-foreground hover:bg-muted/50 hover:text-foreground',
                      )}
                    >
                      <div className={cn(
                        'flex h-7 w-7 sm:h-8 sm:w-8 items-center justify-center rounded-lg bg-gradient-to-br shadow-lg transition-all',
                        item.gradientClass || 'gradient-general',
                        isActive ? 'scale-110' : 'opacity-70 group-hover:opacity-100'
                      )}>
                        <Icon className="h-3.5 w-3.5 sm:h-4 sm:w-4 text-white" />
                      </div>
                      <span className="flex-1 overflow-hidden text-ellipsis whitespace-nowrap font-medium">
                        {t(item.labelKey)}
                      </span>
                      {isActive && (
                        <div className="h-1.5 w-1.5 rounded-full bg-primary" />
                      )}
                    </button>
                  );
                })}
              </div>
            );
          })}
        </nav>

        {/* Desktop: active section content (hidden on mobile) */}
        <div
          role="tabpanel"
          className={cn(
            'hidden sm:relative sm:flex flex-1 min-h-0 min-w-0 overflow-hidden bg-surface-modal',
            !isFill && 'dark:bg-background',
          )}
        >
          {/* Close button — positioned at dialog top-right corner */}
          <DialogClose
            className="absolute right-2 top-2 z-50 rounded-md p-1 text-muted-foreground opacity-70 transition-opacity hover:opacity-100 hover:bg-black/5 dark:hover:bg-white/10 focus:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label="Close"
          >
            <X className="h-4 w-4" />
          </DialogClose>
          <SectionContent section={section} t={t} />
        </div>
      </DialogContent>
    </Dialog>
  );
}

export default SettingsModal;
