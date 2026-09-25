/**
 * 平台法律域统一请求客户端——全站 /platform/** 调用的唯一收口。
 *
 * 重构后端新增 EntitlementInterceptor：FREE 套餐访问签署/模板/比对/证据等
 * 门禁路径会返回 HTTP 402 + {code:402,msg}。此前各页面裸 fetch 只认 code===0，
 * 402 会静默渲染成空数据。这里统一识别 402 并抛 EntitlementError，
 * 读路径用 platformProbe 渲染「未开通」态，写路径用 notifyIfEntitlement 提示。
 */
import { toast } from 'sonner';

export class EntitlementError extends Error {
  constructor(msg = '当前套餐未包含此功能，请升级后使用') {
    super(msg);
    this.name = 'EntitlementError';
  }
}

export class ApiError extends Error {
  code: number;
  constructor(code: number, msg: string) {
    super(msg);
    this.name = 'ApiError';
    this.code = code;
  }
}

type Envelope = { code?: number; msg?: string; data?: unknown };

async function unwrap(res: Response, fallbackMsg: string): Promise<unknown> {
  const env = (await res.json().catch(() => null)) as Envelope | null;
  if (res.status === 402 || env?.code === 402) throw new EntitlementError(env?.msg ?? undefined);
  if (!env || env.code !== 0) throw new ApiError(env?.code ?? res.status, env?.msg || fallbackMsg);
  return env.data;
}

/** 读：返回 data；402 抛 EntitlementError；其它非 0 抛 ApiError。 */
export async function platformGet<T>(path: string): Promise<T> {
  const res = await fetch(path, { credentials: 'include' });
  return (await unwrap(res, '请求失败')) as T;
}

/** 写：POST/PUT/PATCH/DELETE 通用，body 为空时不带 Content-Type。 */
export async function platformSend<T>(path: string, method: string, body?: unknown): Promise<T> {
  const res = await fetch(path, {
    method,
    credentials: 'include',
    headers: body == null ? undefined : { 'Content-Type': 'application/json' },
    body: body == null ? undefined : JSON.stringify(body),
  });
  return (await unwrap(res, '操作失败')) as T;
}

export type ProbeResult<T> = { ok: boolean; locked: boolean; data: T | null; msg?: string };

/** 探测：列表页用，不抛异常，返回 {ok, locked, data} 便于渲染 加载/就绪/未开通/失败 四态。 */
export async function platformProbe<T>(path: string): Promise<ProbeResult<T>> {
  try {
    return { ok: true, locked: false, data: await platformGet<T>(path) };
  } catch (e) {
    if (e instanceof EntitlementError) return { ok: false, locked: true, data: null, msg: e.message };
    return { ok: false, locked: false, data: null, msg: e instanceof Error ? e.message : '请求失败' };
  }
}

/** 动作类操作的 402 统一提示；返回 true 表示已处理（是 EntitlementError）。 */
export function notifyIfEntitlement(e: unknown): boolean {
  if (e instanceof EntitlementError) {
    toast.error(e.message);
    return true;
  }
  return false;
}

// 全局兜底：任何被吞掉/冒泡到顶层的 402 都给出提示，避免静默失败。
if (typeof window !== 'undefined') {
  window.addEventListener('unhandledrejection', (ev) => {
    if (ev.reason instanceof EntitlementError) {
      toast.error(ev.reason.message);
      ev.preventDefault();
    }
  });
}
