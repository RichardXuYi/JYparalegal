import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Button } from '@/components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from '@/components/ui/dialog';
import { dismissNotice, subscribeNotice, type AppNotice } from '@/lib/notice';

/** 全站失败 / 未开通套餐的确认弹窗。成功提示不经过这里。 */
export function NoticeHost() {
  const { t } = useTranslation('common');
  const [notice, setNotice] = useState<AppNotice | null>(null);

  useEffect(() => subscribeNotice(setNotice), []);

  return (
    <Dialog open={notice != null} onOpenChange={(open) => { if (!open) dismissNotice(); }}>
      <DialogContent
        className="w-[calc(100%-2rem)] max-w-md rounded-2xl border bg-white p-6 shadow-xl dark:bg-card"
        onPointerDownOutside={(event) => event.preventDefault()}
        onEscapeKeyDown={(event) => event.preventDefault()}
      >
        <DialogTitle className="text-lg font-semibold">
          {notice?.kind === 'locked' ? t('notice.lockedTitle') : t('notice.errorTitle')}
        </DialogTitle>
        <DialogDescription className="mt-2 text-sm text-muted-foreground">{notice?.message}</DialogDescription>
        <div className="mt-6 flex justify-end">
          <Button type="button" onClick={() => dismissNotice()}>{t('notice.acknowledge')}</Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
