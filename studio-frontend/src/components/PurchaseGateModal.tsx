/**
 * PurchaseGateModal
 *
 * 阶段2 套餐门禁的全局响应：`platform-fetch` 收到后端 402 时派发 `jy:requires-purchase`，
 * 本组件监听并弹出居中的「请购买」提示。CTA「去购买」为阶段3 购买流程预留入口。
 */
import { useEffect, useState } from 'react';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog';

const DEFAULT_MESSAGE = '您没有此功能权限，请购买后使用';

export function PurchaseGateModal() {
  const [open, setOpen] = useState(false);
  const [message, setMessage] = useState(DEFAULT_MESSAGE);

  useEffect(() => {
    const handler = (event: Event) => {
      const detail = (event as CustomEvent<{ message?: string }>).detail;
      setMessage(detail?.message || DEFAULT_MESSAGE);
      setOpen(true);
    };
    window.addEventListener('jy:requires-purchase', handler);
    return () => window.removeEventListener('jy:requires-purchase', handler);
  }, []);

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent className="w-[calc(100%-2rem)] max-w-md rounded-2xl border bg-white p-6 shadow-xl dark:bg-card">
        <DialogTitle className="text-lg font-semibold">需要购买</DialogTitle>
        <DialogDescription className="mt-2 text-sm text-muted-foreground">{message}</DialogDescription>
        <div className="mt-6 flex justify-end gap-2">
          <button
            type="button"
            className="rounded-md border border-border px-3 py-1.5 text-sm"
            onClick={() => setOpen(false)}
          >
            稍后再说
          </button>
          <button
            type="button"
            className="rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground"
            onClick={() => setOpen(false)}
          >
            去购买
          </button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
