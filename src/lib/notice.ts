import { toast } from 'sonner';

export type AppNoticeAction = {
  label: string;
  onClick: () => void;
  primary?: boolean;
};

export type AppNotice = {
  kind: 'error' | 'locked';
  message: string;
  title?: string;
  actions?: AppNoticeAction[];
};

export type ReportFailureOptions = {
  title?: string;
  /** Recovery affordances rendered next to the acknowledge button. */
  actions?: AppNoticeAction[];
};

let current: AppNotice | null = null;
const listeners = new Set<(notice: AppNotice | null) => void>();

function emit(notice: AppNotice | null): void {
  current = notice;
  listeners.forEach((listener) => listener(notice));
}

export function subscribeNotice(listener: (notice: AppNotice | null) => void): () => void {
  listeners.add(listener);
  listener(current);
  return () => { listeners.delete(listener); };
}

export function dismissNotice(): void {
  emit(null);
}

function featureOf(text: string): string | null {
  if (/moot/i.test(text)) return '模拟法庭';
  if (/\/sign\b|api\/sign/i.test(text)) return '签署';
  if (/template/i.test(text)) return '模板';
  if (/evidence/i.test(text)) return '证据';
  if (/review|compare/i.test(text)) return '文档比对';
  if (/knowledge/i.test(text)) return '知识库';
  if (/company|account/i.test(text)) return '账户';
  return null;
}

function humanize(message: string): string {
  const text = message.trim();
  const feature = featureOf(text);
  if (/resource not found/i.test(text)) {
    return feature
      ? `${feature}暂时打不开。后台服务还没连上，请先启动后台再试。`
      : '这个功能暂时打不开，请稍后再试。';
  }
  if (/not a platform path|unknown platform route/i.test(text)) return '这个功能暂时打不开，请稍后再试。';
  if (/request body is missing or malformed/i.test(text)) return '提交的内容不完整，请检查后再试。';
  if (/missing required parameter|must not be null|cannot be null|cannot be empty/i.test(text)) return '还有必填项没填。';
  if (/invalid parameter type|validation failed|illegal argument/i.test(text)) return '填写的内容格式不对，请修改后再试。';
  if (/method not allowed/i.test(text)) return '这个操作现在不能用。';
  if (/access denied|permission denied/i.test(text)) return '没有权限做这个操作。';
  if (/^io error$/i.test(text)) return '读写文件失败，请稍后再试。';
  if (/database error|data access error/i.test(text)) return '数据暂时读不出来，请稍后再试。';
  if (/failed to fetch|networkerror|econnrefused|fetch failed|network error/i.test(text)) {
    return '连不上服务器，请确认后台已启动。';
  }
  if (/timed out|timeout/i.test(text)) return '等得太久了，请再试一次。';
  if (/too many requests|rate limit/i.test(text)) return '操作太频繁，请稍等再试。';
  if (/unauthenticated|no backend token|authentication failed/i.test(text)) return '登录已失效，请重新登录。';
  if (/gateway is unavailable|channel unavailable/i.test(text)) return '助手暂时连不上，请稍后再试。';
  if (/failed to save/i.test(text)) return '这项设置没有保存成功，请稍后再试。';
  if (/failed to reset settings/i.test(text)) return '设置没有恢复成功，请稍后再试。';
  if (/unexpected error|internal server error/i.test(text)) return '系统繁忙，请稍后再试。';
  if (/ILLEGAL_TRANSITION/i.test(text)) return '当前状态不能进行这个操作。';
  if (/api\/[a-z0-9/_-]+/i.test(text) || /\b(TypeError|ReferenceError|SyntaxError)\b/.test(text) || /at com\.jyfc\./.test(text)) {
    return feature
      ? `${feature}没有完成，请稍后再试。`
      : '操作没有完成，请稍后再试。';
  }
  return text;
}

function textOf(reason: unknown, fallback: string): string {
  if (typeof reason === 'string' && reason.trim()) return humanize(reason);
  if (reason instanceof Error && reason.message.trim()) return humanize(reason.message);
  return fallback;
}

function isLocked(reason: unknown): boolean {
  return reason instanceof Error && reason.name === 'EntitlementError';
}

/** 失败或未开通套餐：必须点确认才关闭。 */
export function reportFailure(reason: unknown, fallback = '操作没有完成', options: ReportFailureOptions = {}): void {
  const message = textOf(reason, fallback);
  emit({
    kind: isLocked(reason) ? 'locked' : 'error',
    message,
    title: options.title,
    actions: options.actions,
  });
}

/** 成功：顶部出现后自行消失，不挡住操作。 */
export function reportSuccess(message: string): void {
  toast.message(message, { position: 'top-center', duration: 2200 });
}
