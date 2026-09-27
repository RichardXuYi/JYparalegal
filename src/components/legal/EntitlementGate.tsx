import { Lock } from 'lucide-react';

/**
 * 套餐门禁（402）统一空态：替代「静默空列表」，明确告知未开通并给出出路。
 * 读路径 platformProbe 返回 locked 时渲染本组件。
 */
export function EntitlementGate({ message, onRetry, className }: {
  message?: string;
  onRetry?: () => void;
  className?: string;
}) {
  return (
    <div className={`flex flex-col items-center gap-2 p-10 text-center ${className ?? ''}`}>
      <Lock className="h-8 w-8 text-muted-foreground/50" aria-hidden />
      <div className="text-sm font-medium">当前套餐未包含此功能</div>
      <div className="max-w-[360px] text-meta text-muted-foreground">
        {message ?? '签署、模板、比对、存证等法律功能需升级套餐后使用，请联系管理员开通。'}
      </div>
      {onRetry && (
        <button className="mt-1 rounded-md border border-border px-3 py-1.5 text-sm hover:bg-muted" onClick={onRetry}>
          重试
        </button>
      )}
    </div>
  );
}
