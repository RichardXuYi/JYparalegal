import type { LanguageCode } from '../language';

// EN
import enCommon from './locales/en/common.json';
import enSettings from './locales/en/settings.json';
import enDashboard from './locales/en/dashboard.json';
import enChat from './locales/en/chat.json';
import enChannels from './locales/en/channels.json';
import enAgents from './locales/en/agents.json';
import enSkills from './locales/en/skills.json';
import enCron from './locales/en/cron.json';
import enDreams from './locales/en/dreams.json';
import enMenu from './locales/en/menu.json';

// ZH
import zhCommon from './locales/zh/common.json';
import zhSettings from './locales/zh/settings.json';
import zhDashboard from './locales/zh/dashboard.json';
import zhChat from './locales/zh/chat.json';
import zhChannels from './locales/zh/channels.json';
import zhAgents from './locales/zh/agents.json';
import zhSkills from './locales/zh/skills.json';
import zhCron from './locales/zh/cron.json';
import zhDreams from './locales/zh/dreams.json';
import zhMenu from './locales/zh/menu.json';

// DE
import deCommon from './locales/de/common.json';
import deSettings from './locales/de/settings.json';
import deDashboard from './locales/de/dashboard.json';
import deChat from './locales/de/chat.json';
import deChannels from './locales/de/channels.json';
import deAgents from './locales/de/agents.json';
import deSkills from './locales/de/skills.json';
import deCron from './locales/de/cron.json';
import deDreams from './locales/de/dreams.json';
import deMenu from './locales/de/menu.json';

// FR
import frCommon from './locales/fr/common.json';
import frSettings from './locales/fr/settings.json';
import frDashboard from './locales/fr/dashboard.json';
import frChat from './locales/fr/chat.json';
import frChannels from './locales/fr/channels.json';
import frAgents from './locales/fr/agents.json';
import frSkills from './locales/fr/skills.json';
import frCron from './locales/fr/cron.json';
import frDreams from './locales/fr/dreams.json';
import frMenu from './locales/fr/menu.json';

export const I18N_NAMESPACES = [
  'common',
  'settings',
  'dashboard',
  'chat',
  'channels',
  'agents',
  'skills',
  'cron',
  'dreams',
  'menu',
] as const;

export const I18N_RESOURCES = {
  en: {
    common: enCommon,
    settings: enSettings,
    dashboard: enDashboard,
    chat: enChat,
    channels: enChannels,
    agents: enAgents,
    skills: enSkills,
    cron: enCron,
    dreams: enDreams,
    menu: enMenu,
  },
  zh: {
    common: zhCommon,
    settings: zhSettings,
    dashboard: zhDashboard,
    chat: zhChat,
    channels: zhChannels,
    agents: zhAgents,
    skills: zhSkills,
    cron: zhCron,
    dreams: zhDreams,
    menu: zhMenu,
  },
  de: {
    common: deCommon,
    settings: deSettings,
    dashboard: deDashboard,
    chat: deChat,
    channels: deChannels,
    agents: deAgents,
    skills: deSkills,
    cron: deCron,
    dreams: deDreams,
    menu: deMenu,
  },
  fr: {
    common: frCommon,
    settings: frSettings,
    dashboard: frDashboard,
    chat: frChat,
    channels: frChannels,
    agents: frAgents,
    skills: frSkills,
    cron: frCron,
    dreams: frDreams,
    menu: frMenu,
  },
} as const;

export type MenuLabels = typeof enMenu;

export const MENU_LABELS: Record<LanguageCode, MenuLabels> = {
  en: enMenu,
  zh: zhMenu,
  de: deMenu,
  fr: frMenu,
};
