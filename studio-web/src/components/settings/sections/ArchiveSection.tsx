import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { platformGet } from '@/lib/platform-api';

export function ArchiveSection() {
  const { t } = useTranslation('settings');
  const [ready, setReady] = useState<boolean | null>(null);

  useEffect(() => {
    void platformGet<{ available?: boolean }>('/platform/moot/archive')
      .then((status) => setReady(status.available === true))
      .catch(() => setReady(false));
  }, []);

  return (
    <div className="flex max-w-lg flex-col gap-3 p-4">
      <h2 className="text-lg font-semibold">{t('moot.archiveTitle')}</h2>
      <p className="text-sm text-muted-foreground">{t('moot.archiveBody')}</p>
      <p className="rounded-md border border-border bg-background px-3 py-2 text-sm">
        {ready == null ? '…' : ready ? t('moot.archiveReady') : t('moot.archiveUnavailable')}
      </p>
    </div>
  );
}
