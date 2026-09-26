/**
 * GatewayManager restart 冷却重试运行时验证（Spec C1 项，双端镜像文件）。
 *
 * 回归场景：restart() 在 governor 冷却期（2.5s）内被再次请求时，旧实现
 * 静默丢弃——快速登出→登录时第二次 scope 重启丢失，Gateway 停在旧用户目录。
 * 修复后 restart() 会等待冷却结束并重试一次。本测试驱动真实 GatewayManager
 * （与桌面端逐字节一致的镜像代码），stop/start 用桩替换以隔离进程副作用。
 *
 * 运行（在 studio-web 目录）：
 *   node node_modules/tsx/dist/cli.mjs --tsconfig server/tsconfig.json \
 *     scripts/verify-manager-restart.ts
 */
import { GatewayManager } from '../server/host-core/gateway/manager';

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

class TestGatewayManager extends GatewayManager {
  stopCalls = 0;
  startCalls = 0;

  async stop(): Promise<void> {
    this.stopCalls += 1;
    await sleep(30);
  }

  async start(): Promise<void> {
    this.startCalls += 1;
    await sleep(30);
  }
}

async function main(): Promise<void> {
  const manager = new TestGatewayManager();

  // 第一次重启立即执行。
  await manager.restart();
  const stopAfterFirst = manager.stopCalls;
  const startAfterFirst = manager.startCalls;
  console.log(`first restart executed (stop=${stopAfterFirst} start=${startAfterFirst})`);

  // 冷却期内（2.5s 内）的第二次重启：修复前被静默丢弃；修复后等待冷却并
  // 重试，最终必须再执行一轮 stop/start。
  const t0 = Date.now();
  await manager.restart();
  const elapsed = Date.now() - t0;
  const stopAfterSecond = manager.stopCalls;
  const startAfterSecond = manager.startCalls;
  console.log(`second restart executed after ${elapsed}ms (stop=${stopAfterSecond} start=${startAfterSecond})`);

  if (stopAfterSecond < stopAfterFirst + 1 || startAfterSecond < startAfterFirst + 1) {
    console.error(
      'FAIL: cooldown-restart was dropped (expected a second stop/start cycle)',
    );
    process.exit(1);
  }
  if (elapsed < 2000) {
    console.error('FAIL: retry did not wait out the cooldown window');
    process.exit(1);
  }
  console.log('PASS: cooldown-suppressed restart retried and executed');
}

main().catch((error) => {
  console.error('FAIL:', error);
  process.exit(1);
});
