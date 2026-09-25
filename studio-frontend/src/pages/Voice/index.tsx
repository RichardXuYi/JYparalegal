import { Mic } from 'lucide-react';
import { LegalPageHeader } from '@/components/legal/LegalPageHeader';

/** 语音转录：实时语音与转写留痕（建设中，ASR 通道待接入）。 */
export default function Voice() {
  return (
    <div className="h-full overflow-y-auto p-5">
      <LegalPageHeader title="语音转录" />
      <div className="flex flex-col items-center justify-center rounded-lg border border-dashed border-border bg-card px-6 py-24 text-center">
        <Mic className="h-10 w-10 text-muted-foreground/50" aria-hidden />
        <p className="mt-4 text-subtitle font-semibold">即将开发</p>
        <p className="mt-1.5 text-meta text-muted-foreground">
          语音通话与实时转写正在建设中，敬请期待。
        </p>
      </div>
    </div>
  );
}
