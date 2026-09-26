/**
 * About settings section.
 * App name, version, and external links.
 */
import { Info } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { hostApi } from '@/lib/host-api';
import { useUpdateStore } from '@/stores/update';
import { useTranslation } from 'react-i18next';
import { SettingsGroup } from '@/components/settings/primitives';

interface AboutSectionProps {
  gradientClass?: string;
}

export function AboutSection({ gradientClass }: AboutSectionProps) {
  const { t } = useTranslation('settings');
  const currentVersion = useUpdateStore((state) => state.currentVersion);

  return (
    <SettingsGroup padded gradientClass={gradientClass} icon={Info}>
      <div className="space-y-3 text-sm text-muted-foreground">
        <p>
          <strong className="text-foreground font-semibold">{t('about.appName')}</strong> - {t('about.tagline')}
        </p>
        <p>{t('about.basedOn')}</p>
        <p>{t('about.version', { version: currentVersion })}</p>
        <div className="flex gap-4 pt-3">
          <Button
            variant="link"
            className="h-auto p-0 text-sm text-blue-500 hover:text-blue-600 font-medium"
            onClick={() => hostApi.shell.openExternal('https://grandpoem.com')}
          >
            {t('about.docs')}
          </Button>
          <Button
            variant="link"
            className="h-auto p-0 text-sm text-blue-500 hover:text-blue-600 font-medium"
            onClick={() => hostApi.shell.openExternal('https://github.com/GrandPoem-Inc/GrandPoemStudio')}
          >
            {t('about.github')}
          </Button>
          <Button
            variant="link"
            className="h-auto p-0 text-sm text-blue-500 hover:text-blue-600 font-medium"
            onClick={() => hostApi.shell.openExternal('mailto:grandpoem@outlook.com')}
          >
            {t('about.faq')}
          </Button>
        </div>
      </div>
    </SettingsGroup>
  );
}

export default AboutSection;
