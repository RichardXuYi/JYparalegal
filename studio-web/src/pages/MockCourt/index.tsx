import { Scale } from 'lucide-react';
import { LegalPageHeader } from '@/components/legal/LegalPageHeader';

/** 模拟法庭：多角色庭审演练（建设中，AI 驱动版待开发）。 */
export default function MockCourt() {
  return (
    <div className="h-full overflow-y-auto p-5">
      <LegalPageHeader title="模拟法庭" />
      <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-border bg-card px-6 py-24 text-center">
        <Scale className="h-10 w-10 text-muted-foreground/50" aria-hidden />
        <p className="mt-4 text-subtitle font-semibold">即将开发</p>
        <p className="mt-1.5 text-meta text-muted-foreground">
          多角色庭审演练（AI 法官 / 对方代理 / 评分复盘）正在建设中，敬请期待。
        </p>
      </div>
    </div>
  );
}
