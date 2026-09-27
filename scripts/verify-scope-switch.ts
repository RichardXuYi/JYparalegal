/**
 * 桌面端 scope 切换重启链运行时验证（Spec C2 项）。
 *
 * 回归场景：快速 登出→登录（或连续多次切换）时，旧实现 fire-and-forget 调
 * restart()，第二次重启会被 governor 冷却静默丢弃或 join 进在途重启，导致
 * Gateway 停留在上一个用户的 OpenClaw 目录。修复后各次切换在串行链上排队，
 * 每次 scope 变更最终都有对应重启执行，且永不并发。
 *
 * 运行（在 studio-frontend 目录）：
 *   node node_modules/tsx/dist/cli.mjs --tsconfig tsconfig.node.json \
 *     scripts/verify-scope-switch.ts
 *
 * 测试将 USERPROFILE 重定向到临时目录，claim 文件（user-scope.json）不会
 * 触碰真实用户配置。
 */
import { mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { applyScopeChange, waitForScopeRestartChainForTest } from '../electron/services/backend-auth-api';

const tmpHome = join(process.env.TEMP ?? '/tmp', `jy-scope-test-${Date.now()}`);
mkdirSync(tmpHome, { recursive: true });
process.env.USERPROFILE = tmpHome;

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

let restartCount = 0;
let inFlight = 0;
let overlap = 0;

const gatewayManager = {
  getStatus: () => ({ state: 'running' as const }),
  restart: async (): Promise<void> => {
    restartCount += 1;
    if (inFlight > 0) overlap += 1;
    inFlight += 1;
    await sleep(40);
    inFlight -= 1;
  },
};

const ctx = { gatewayManager } as unknown as Parameters<typeof applyScopeChange>[0];

async function main(): Promise<void> {
  // 前置：用户 1 首次登录认领 legacy 目录（目录不变 → 无重启需求），随后
  // 用户 2 登录进入 u2 目录。这些都是单次切换，链上各执行一次重启。
  applyScopeChange(ctx, 1); // claims legacy dir (no dir change)
  await waitForScopeRestartChainForTest();
  applyScopeChange(ctx, 2); // legacy → u2
  await waitForScopeRestartChainForTest();
  const baseline = restartCount;

  // 场景 1（真实竞态）：u2 登出（u2→legacy）+ 用户 3 登录（legacy→u3）紧接
  // 发生。旧实现下第二次重启被 governor 冷却丢弃或 join 吞掉，Gateway 会
  // 停在错误目录；修复后两次变更都必须在串行链上各自落地。
  applyScopeChange(ctx, null); // u2 → legacy
  applyScopeChange(ctx, 3); // legacy → u3
  await waitForScopeRestartChainForTest();
  const delta1 = restartCount - baseline;
  console.log(`scenario 1 (logout→login rapid switch): restarts=${delta1}`);
  if (delta1 < 2) {
    console.error('FAIL: rapid scope changes lost restarts (expected >= 2)');
    process.exit(1);
  }

  // 场景 2：同样节奏再来一轮，链必须保持收敛且不重叠。
  const before2 = restartCount;
  applyScopeChange(ctx, null); // u3 → legacy
  applyScopeChange(ctx, 4); // legacy → u4
  await waitForScopeRestartChainForTest();
  const delta2 = restartCount - before2;
  console.log(`scenario 2 (second rapid switch): restarts=${delta2}`);
  if (delta2 < 2) {
    console.error('FAIL: second rapid switch did not converge (expected >= 2 restarts)');
    process.exit(1);
  }

  if (overlap > 0) {
    console.error(`FAIL: ${overlap} overlapping restart(s) detected — chain not serialized`);
    process.exit(1);
  }

  console.log(`PASS: scope-change restart chain serialized and converged (total restarts=${restartCount}, overlap=0)`);
}

main().catch((error) => {
  console.error('FAIL:', error);
  process.exit(1);
});
