/**
 * Updates settings section.
 * Update controls and auto-check toggle.
 */
import { Download, RefreshCw } from 'lucide-react';
import { Switch } from '@/components/ui/switch';
import { useSettingsStore } from '@/stores/settings';
import { UpdateSettings } from '@/components/settings/UpdateSettings';
import { useTranslation } from 'react-i18next';
import { SettingsGroup, SettingRow } from '@/components/settings/primitives';

interface UpdatesSectionProps {
  gradientClass?: string;
}

export function UpdatesSection({ gradientClass }: UpdatesSectionProps) {
  const { t } = useTranslation('settings');
  const autoCheckUpdate = useSettingsStore((state) => state.autoCheckUpdate);
  const setAutoCheckUpdate = useSettingsStore((state) => state.setAutoCheckUpdate);

  return (
    <div className="space-y-6">
      <SettingsGroup padded gradientClass={gradientClass} icon={Download}>
        <UpdateSettings />
      </SettingsGroup>

      <SettingsGroup gradientClass={gradientClass} icon={RefreshCw}>
        <SettingRow
          icon={RefreshCw}
          label={t('updates.autoCheck')}
          description={t('updates.autoCheckDesc')}
          control={<Switch checked={autoCheckUpdate} onCheckedChange={setAutoCheckUpdate} />}
        />
      </SettingsGroup>
    </div>
  );
}

export default UpdatesSection;
