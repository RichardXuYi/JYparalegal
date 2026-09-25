/**
 * Settings Modal
 * Two-pane settings surface: left category navigation + right content.
 * Opened from the top-bar gear icon. Consolidates former sidebar feature
 * pages (models / channels / skills / cron / image generation / dreams)
 * together with application preferences.
 */
import { useEffect, type ComponentType, type ReactNode } from 'react';
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
  Download,
  Info,
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog';
import { cn } from '@/lib/utils';
import { useSettingsStore } from '@/stores/settings';
import {
  useSettingsUiStore,
  DEFAULT_SETTINGS_SECTION,
  DEV_ONLY_SETTINGS_SECTIONS,
  type SettingsSection,
} from '@/stores/settings-ui';

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
import { UpdatesSection } from './sections/UpdatesSection';
import { AboutSection } from './sections/AboutSection';
import { SettingsPageHeader } from './primitives';

interface NavItem {
  id: SettingsSection;
  icon: ComponentType<{ className?: string; strokeWidth?: number }>;
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
      { id: 'updates', icon: Download, labelKey: 'updates.title', gradientClass: 'gradient-updates' },
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
    case 'updates':
      return (
        <PreferencesColumn
          title={t('updates.title')}
          description={t('updates.description', '检查、下载与安装应用更新')}
          icon={Download}
          gradientClass="gradient-updates"
        >
          <UpdatesSection gradientClass="gradient-updates" />
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
      <div className="mx-auto max-w-3xl px-6 pr-10 pt-8 pb-8">
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

  // If the active section becomes unavailable (dev mode turned off), fall back.
  useEffect(() => {
    if (!devModeUnlocked && DEV_ONLY_SETTINGS_SECTIONS.has(section)) {
      setSection(DEFAULT_SETTINGS_SECTION);
    }
  }, [devModeUnlocked, section, setSection]);

  const activeItem = NAV_GROUPS.flatMap((g) => g.items).find((i) => i.id === section);
  const isFill = activeItem?.fill ?? false;

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!next) closeSettings(); }}>
      <DialogContent
        data-testid="settings-modal"
        className="w-[min(1100px,92vw)] max-w-[92vw] h-[85vh] max-h-[85vh] p-0 overflow-hidden gap-0 flex flex-row"
        onOpenAutoFocus={(e) => e.preventDefault()}
      >
        <DialogTitle className="sr-only">{t('title')}</DialogTitle>

        {/* Left: category navigation */}
        <nav role="tablist" aria-label={t('title')} className="shell-gradient flex w-60 shrink-0 flex-col gap-4 overflow-y-auto p-3">
          <div className="px-3 pt-2 pb-1">
            <h2 className="text-base font-bold tracking-tight text-white">{t('title')}</h2>
          </div>
          {NAV_GROUPS.map((group) => {
            const items = group.items.filter((item) => !item.devOnly || devModeUnlocked);
            if (items.length === 0) return null;
            return (
              <div key={group.titleKey} className="flex flex-col gap-0.5">
                <p className="px-3 pb-1.5 text-[10px] font-bold uppercase tracking-widest text-white/55">
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
                        'group relative flex items-center gap-3 rounded-xl px-3 py-2.5 text-left text-sm transition-all',
                        'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white/60',
                        isActive
                          ? 'shell-glass-strong font-semibold text-white'
                          : 'text-white/75 hover:bg-white/10 hover:text-white',
                      )}
                    >
                      <Icon className="h-4 w-4 shrink-0" strokeWidth={1.75} />
                      <span className="flex-1 overflow-hidden text-ellipsis whitespace-nowrap font-medium">
                        {t(item.labelKey)}
                      </span>
                      {isActive && (
                        <div className="h-1.5 w-1.5 rounded-full bg-white" />
                      )}
                    </button>
                  );
                })}
              </div>
            );
          })}
        </nav>

        {/* Right: active section content */}
        <div
          role="tabpanel"
          className={cn(
            'flex-1 min-h-0 min-w-0 overflow-hidden bg-surface-modal',
            !isFill && 'dark:bg-background',
          )}
        >
          <SectionContent section={section} t={t} />
        </div>
      </DialogContent>
    </Dialog>
  );
}

export default SettingsModal;
