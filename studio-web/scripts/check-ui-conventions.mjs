#!/usr/bin/env node
/**
 * UI 规范门禁 —— 守卫法律业务页不再回潮到「原型态」写法。
 *
 * 背景:docs/前端改进/整改清单-2026-09-22.md。业务页此前绕过设计系统,
 * 出现原生 alert()、任意值字号 text-[Npx] 等。批量修复后,本脚本作为 CI 门禁,
 * 防止这些模式重新被写回。仅扫描法律业务页,避免对 fork 的 Agent 壳页误报。
 *
 * 用法:node scripts/check-ui-conventions.mjs(或 pnpm run lint:ui)
 * 退出码:发现违例 = 1,否则 0。
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';

const ROOT = process.cwd();

// 法律业务页 + 平台布局(fork 的 Agent 壳页不在此列)
const SCAN_DIRS = [
  'src/pages/Overview',
  'src/pages/Signing',
  'src/pages/TaskDetail',
  'src/pages/Evidence',
  'src/pages/CompanyManage',
  'src/pages/Templates',
  'src/pages/Compare',
  'src/pages/MockCourt',
  'src/pages/Voice',
  'src/pages/CreateTask',
];

// 平台布局按文件白名单:仅法律相关壳件,fork 的 Agent 会话侧栏 Sidebar.tsx 不在内
const SCAN_FILES = [
  'src/components/layout/SigningSidebar.tsx',
  'src/components/layout/PlatformTabs.tsx',
  'src/components/layout/MainLayout.tsx',
  'src/components/layout/TopBar.tsx',
];

// 规则:id / 说明 / 匹配
const RULES = [
  {
    id: 'no-native-alert',
    message: '禁止原生 alert()/confirm()/prompt():改用 sonner toast 或 ConfirmDialog',
    test: (line) => /(^|[^.\w])(alert|confirm|prompt)\s*\(/.test(line) && !/window\.(alert|confirm|prompt)/.test(line),
  },
  {
    id: 'no-arbitrary-font-size',
    message: '禁止任意值字号 text-[Npx]:改用命名刻度 text-2xs/tiny/meta/subtitle/stat 或 text-xs/sm',
    test: (line) => /text-\[\d+px\]/.test(line),
  },
  {
    id: 'no-json-in-alert',
    message: '禁止把 JSON.stringify 结果直接展示给用户',
    test: (line) => /alert\([^)]*JSON\.stringify/.test(line),
  },
];

function collectFiles(dir) {
  const abs = join(ROOT, dir);
  let entries;
  try {
    entries = readdirSync(abs);
  } catch {
    return [];
  }
  const files = [];
  for (const entry of entries) {
    const full = join(abs, entry);
    const st = statSync(full);
    if (st.isDirectory()) files.push(...collectFiles(relative(ROOT, full)));
    else if (/\.(tsx?|jsx?)$/.test(entry)) files.push(relative(ROOT, full));
  }
  return files;
}

const violations = [];
const files = [
  ...SCAN_DIRS.flatMap(collectFiles),
  ...SCAN_FILES.filter((f) => { try { statSync(join(ROOT, f)); return true; } catch { return false; } }),
];

for (const file of files) {
  const lines = readFileSync(join(ROOT, file), 'utf8').split('\n');
  lines.forEach((line, i) => {
    const trimmed = line.trim();
    if (trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('/*')) return;
    for (const rule of RULES) {
      if (rule.test(line)) {
        violations.push(`${file}:${i + 1}  [${rule.id}]  ${rule.message}\n    ${trimmed.slice(0, 120)}`);
      }
    }
  });
}

if (violations.length > 0) {
  console.error(`\n✖ UI 规范门禁:发现 ${violations.length} 处违例\n`);
  console.error(violations.join('\n'));
  console.error('\n参见 docs/前端改进/整改清单-2026-09-22.md。修复后重跑 pnpm run lint:ui。\n');
  process.exit(1);
}

console.log(`✔ UI 规范门禁通过(扫描 ${files.length} 个业务页文件)`);
