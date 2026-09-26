/**
 * Gateway Surface Derivation
 * Pure decision logic mapping GatewayStatus to the connection UI surface that
 * should be shown, so the rules can be unit tested without rendering.
 */
import type { GatewayFailureInfo, GatewayStatus } from '@/types/gateway';

export type GatewaySurface =
  | { kind: 'banner'; attempt?: number; max?: number; nextRetryAt?: number; failure?: GatewayFailureInfo | null }
  | { kind: 'dialog'; failure: GatewayFailureInfo }
  | null;

/**
 * 启动加载动画的硬上限：到点无论网关什么状态都放行主界面（以非阻塞横幅呈现连接态）。
 * 没有它，一个永不回报状态的网关会把应用永远停在加载页。
 */
export const BOOT_SCREEN_CAP_MS = 45_000;

/**
 * 启动期是否继续全屏加载动画（`InitializingScreen`），而不是把主界面放出来。
 *
 * <p>主界面一旦画出来就必须可用：Gateway 还在 `starting`/`reconnecting` 时放出来，
 * 就只能靠模态去遮一个画好的页面，观感等于卡死。所以加载动画要一直顶到网关就绪、
 * 进入终态（`failed`/`error`/`stopped` 交给对话框与横幅说话）、或本会话已经见过
 * running（之后的波动属于运行期，走非阻塞横幅）。`capElapsed` 是防卡死的硬上限：
 * 到点无条件放行，界面以"主界面 + 横幅"呈现。</p>
 */
export function shouldHoldBootScreen(params: {
  status: GatewayStatus;
  hasSeenRunningThisSession: boolean;
  capElapsed: boolean;
}): boolean {
  const { status, hasSeenRunningThisSession, capElapsed } = params;
  if (capElapsed || hasSeenRunningThisSession) return false;
  return status.state === 'starting' || status.state === 'reconnecting';
}

/**
 * Decide which surface a gateway status maps to:
 * - `failed` with a structured failure → terminal dialog (banner is rendered
 *   underneath by the layout while the dialog is showing).
 * - `failed` without failure info / `error` → non-blocking banner.
 * - `stopped` → nothing global (per-page GatewayNotRunning UI handles it).
 * - `starting`/`reconnecting` → non-blocking banner. Before the boot screen is
 *   released this is only reached via the cap or after a terminal state; once
 *   the session has seen the gateway running, transient dips never block.
 */
export function deriveGatewaySurface(params: {
  status: GatewayStatus;
}): GatewaySurface {
  const { status } = params;

  if (status.state === 'failed') {
    return status.failure
      ? { kind: 'dialog', failure: status.failure }
      : { kind: 'banner' };
  }

  if (status.state === 'error') {
    return { kind: 'banner', failure: status.failure ?? null };
  }

  if (status.state === 'stopped') {
    return null;
  }

  const banner: GatewaySurface = {
    kind: 'banner',
    attempt: status.reconnectAttempts,
    max: status.reconnectMaxAttempts,
    nextRetryAt: status.nextRetryAt,
    failure: status.failure ?? null,
  };

  if (status.state === 'running') {
    // Process alive but not ready → degraded banner; ready → nothing to show.
    return status.gatewayReady === false ? banner : null;
  }

  // starting | reconnecting
  return banner;
}
