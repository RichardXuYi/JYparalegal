/**
 * Appearance settings section.
 * Theme, language, interface mode (device shell).
 */
import { Sun, Moon, Monitor, Palette, Globe, Smartphone, Wand2, Sparkles } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Switch } from '@/components/ui/switch';
import { toast } from 'sonner';
import { useSettingsStore } from '@/stores/settings';
import { useTranslation } from 'react-i18next';
import { SUPPORTED_LANGUAGES } from '@/i18n';
import { cn } from '@/lib/utils';
import { SettingsGroup, SettingRow } from '@/components/settings/primitives';
import { setShellPreference, type ShellPreference } from '@/lib/device-shell';
import { useShellPreference } from '@/hooks/use-device-shell';

/** Shared classes for the segmented pill pickers (theme / language). */
function pill(active: boolean) {
  return cn(
    'rounded-full px-4 h-9',
    active
      ? 'bg-black/5 dark:bg-white/10 text-foreground'
      : 'text-muted-foreground hover:bg-black/5 dark:hover:bg-white/5',
  );
}

interface AppearanceSectionProps {
  gradientClass?: string;
}

export function AppearanceSection({ gradientClass }: AppearanceSectionProps) {
  const { t, i18n } = useTranslation('settings');
  const theme = useSettingsStore((state) => state.theme);
  const setTheme = useSettingsStore((state) => state.setTheme);
  const shellAnimation = useSettingsStore((state) => state.shellAnimation);
  const setShellAnimation = useSettingsStore((state) => state.setShellAnimation);
  const language = useSettingsStore((state) => state.language);
  const setLanguage = useSettingsStore((state) => state.setLanguage);
  const shellPreference = useShellPreference();

  const handleShellPreferenceChange = (next: ShellPreference) => {
    if (next === shellPreference) return;
    setShellPreference(next);
  };

  const handleLanguageChange = (nextLanguage: string) => {
    if (nextLanguage === language) return;
    const translateNext = i18n.getFixedT(nextLanguage, 'settings');
    setLanguage(nextLanguage);
    toast.success(translateNext('appearance.menuLanguageUpdated'));
  };

  return (
    <SettingsGroup title={t('appearance.title')} gradientClass={gradientClass} icon={Palette}>
      <SettingRow
        icon={Palette}
        label={t('appearance.theme')}
        control={
          <div className="flex flex-wrap gap-1.5">
            <Button variant="ghost" size="sm" className={pill(theme === 'light')} onClick={() => setTheme('light')}>
              <Sun className="h-4 w-4 mr-2" />
              {t('appearance.light')}
            </Button>
            <Button variant="ghost" size="sm" className={pill(theme === 'dark')} onClick={() => setTheme('dark')}>
              <Moon className="h-4 w-4 mr-2" />
              {t('appearance.dark')}
            </Button>
            <Button variant="ghost" size="sm" className={pill(theme === 'system')} onClick={() => setTheme('system')}>
              <Monitor className="h-4 w-4 mr-2" />
              {t('appearance.system')}
            </Button>
          </div>
        }
      />
      <SettingRow
        icon={Sparkles}
        label={t('appearance.shellAnimation')}
        description={t('appearance.shellAnimationDesc')}
        control={<Switch checked={shellAnimation} onCheckedChange={setShellAnimation} />}
      />
      <SettingRow
        icon={Globe}
        label={t('appearance.language')}
        control={
          <div className="flex flex-wrap gap-1.5">
            {SUPPORTED_LANGUAGES.map((lang) => (
              <Button
                key={lang.code}
                variant="ghost"
                size="sm"
                className={pill(language === lang.code)}
                onClick={() => handleLanguageChange(lang.code)}
              >
                {lang.label}
              </Button>
            ))}
          </div>
        }
      />
      <SettingRow
        icon={Smartphone}
        label={t('appearance.shellMode')}
        description={t('appearance.shellModeDesc')}
        control={
          <div className="flex flex-wrap gap-1.5">
            <Button variant="ghost" size="sm" className={pill(shellPreference === 'auto')} onClick={() => handleShellPreferenceChange('auto')}>
              <Wand2 className="h-4 w-4 mr-2" />
              {t('appearance.shellAuto')}
            </Button>
            <Button variant="ghost" size="sm" className={pill(shellPreference === 'desktop')} onClick={() => handleShellPreferenceChange('desktop')}>
              <Monitor className="h-4 w-4 mr-2" />
              {t('appearance.shellDesktop')}
            </Button>
            <Button variant="ghost" size="sm" className={pill(shellPreference === 'mobile')} onClick={() => handleShellPreferenceChange('mobile')}>
              <Smartphone className="h-4 w-4 mr-2" />
              {t('appearance.shellMobile')}
            </Button>
          </div>
        }
      />
    </SettingsGroup>
  );
}

export default AppearanceSection;
