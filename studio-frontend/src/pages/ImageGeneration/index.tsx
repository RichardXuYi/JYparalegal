import { useTranslation } from 'react-i18next';
import { ImagePlus } from 'lucide-react';
import { ImageGenerationSettings } from '@/components/settings/ImageGenerationSettings';
import { SettingsPageShell } from '@/components/settings/primitives';

export function ImageGenerationPage() {
  const { t } = useTranslation('dashboard');
  return (
    <SettingsPageShell
      testId="image-generation-page"
      title={t('imageGeneration.title')}
      description={t('imageGeneration.description')}
      titleTestId="image-generation-settings-title"
      icon={ImagePlus}
      gradientClass="gradient-updates"
    >
      <ImageGenerationSettings />
    </SettingsPageShell>
  );
}

export default ImageGenerationPage;
