#!/usr/bin/env zx

/**
 * bundle-openclaw-plugins.mjs
 *
 * Build a self-contained mirror of OpenClaw third-party plugins for packaging.
 * Current plugins:
 *   - @soimy/dingtalk -> build/openclaw-plugins/dingtalk
 *   - @wecom/wecom-openclaw-plugin -> build/openclaw-plugins/wecom
 *   - @openclaw/discord -> build/openclaw-plugins/discord
 *   - @openclaw/qqbot -> build/openclaw-plugins/qqbot
 *   - @openclaw/whatsapp -> build/openclaw-plugins/whatsapp
 *   - @tencent-weixin/openclaw-weixin -> build/openclaw-plugins/openclaw-weixin
 *
 * The output plugin directory contains:
 *   - plugin source files (index.ts, openclaw.plugin.json, package.json, ...)
 *   - plugin runtime node_modules/ (flattened direct + transitive deps)
 */

import 'zx/globals';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  collectDeps,
  executeCopyPlan,
  getVirtualStoreNodeModules,
  makeVersionResolver,
  planLayout,
} from './openclaw-deps.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');
const OUTPUT_ROOT = path.join(ROOT, 'build', 'openclaw-plugins');
const NODE_MODULES = path.join(ROOT, 'node_modules');

const PLUGINS = [
  { npmName: '@soimy/dingtalk', pluginId: 'dingtalk' },
  { npmName: '@wecom/wecom-openclaw-plugin', pluginId: 'wecom' },
  { npmName: '@larksuite/openclaw-lark', pluginId: 'feishu-openclaw-plugin' },
  { npmName: '@openclaw/discord', pluginId: 'discord' },
  { npmName: '@openclaw/qqbot', pluginId: 'qqbot' },
  { npmName: '@openclaw/whatsapp', pluginId: 'whatsapp' },
  { npmName: '@tencent-weixin/openclaw-weixin', pluginId: 'openclaw-weixin' },
];

function bundleOnePlugin({ npmName, pluginId }) {
  const pkgPath = path.join(NODE_MODULES, ...npmName.split('/'));
  if (!fs.existsSync(pkgPath)) {
    throw new Error(`Missing dependency "${npmName}". Run pnpm install first.`);
  }

  const realPluginPath = fs.realpathSync(pkgPath);
  const outputDir = path.join(OUTPUT_ROOT, pluginId);

  echo`? Bundling plugin ${npmName} -> ${outputDir}`;

  if (fs.existsSync(outputDir)) {
    fs.rmSync(outputDir, { recursive: true, force: true });
  }
  fs.mkdirSync(outputDir, { recursive: true });

  // 1) Copy plugin package itself
  fs.cpSync(realPluginPath, outputDir, { recursive: true, dereference: true });

  // 2) Collect transitive deps from the pnpm virtual store, recording pnpm's
  //    per-consumer resolution. The previous first-wins flatten (skip a package
  //    name once seen) silently dropped the second instance whenever a plugin's
  //    tree resolved two versions of the same name -- the exact entities/
  //    htmlparser2 class of failure that broke the gateway bundle at ESM link
  //    time. collectDeps + planLayout reproduce pnpm's layout instead.
  const rootVirtualNM = getVirtualStoreNodeModules(realPluginPath);
  if (!rootVirtualNM) {
    throw new Error(`Cannot resolve virtual store node_modules for ${npmName}`);
  }

  // Skip peerDependencies -- they're provided by the host openclaw gateway.
  const skipPackages = new Set(['typescript', '@playwright/test']);
  try {
    const pluginPkg = JSON.parse(fs.readFileSync(path.join(outputDir, 'package.json'), 'utf8'));
    for (const peer of Object.keys(pluginPkg.peerDependencies || {})) {
      skipPackages.add(peer);
    }
  } catch { /* ignore */ }

  const { collected, depsByPackage, skippedCount } = collectDeps(
    [{ nodeModulesDir: rootVirtualNM, skipPkg: npmName }],
    { skipPackages, skipScopes: ['@types/'] },
  );

  // 3) Lay deps out under plugin/node_modules: the plugin's own direct deps own
  //    the top-level slots; every conflicting version becomes a private copy
  //    under <consumer>/node_modules/, where Node resolution would find it.
  const outputNodeModules = path.join(outputDir, 'node_modules');
  fs.mkdirSync(outputNodeModules, { recursive: true });

  const versionOfPackage = makeVersionResolver();
  const pluginDeps = depsByPackage.get(realPluginPath) ?? new Map();
  const topLevelClaims = [];
  for (const [depName, depRealPath] of pluginDeps) {
    topLevelClaims.push({ realPath: depRealPath, name: depName });
  }

  const { copyPlan, privateCopies } = planLayout({
    outputNodeModules,
    depsByPackage,
    versionOfPackage,
    topLevelClaims,
  });

  const { copiedCount, failures } = executeCopyPlan(copyPlan, { outputRoot: outputDir });
  if (failures.length > 0) {
    // Fail closed: a plugin missing a resolved runtime dep crashes at load time,
    // so a copy error must abort the build rather than ship a partial mirror.
    echo`   [ERROR] ${pluginId}: failed to copy ${failures.length} dependency package(s):`;
    for (const f of failures) echo`      - ${f.dest}: ${f.message}`;
    throw new Error(`Plugin bundle incomplete for ${pluginId}`);
  }

  const manifestPath = path.join(outputDir, 'openclaw.plugin.json');
  if (!fs.existsSync(manifestPath)) {
    throw new Error(`Missing openclaw.plugin.json in bundled plugin output: ${pluginId}`);
  }

  // 4) Patch plugin ID mismatch: some npm packages hardcode a different ID in
  //    their JS output than what openclaw.plugin.json declares.  The Gateway
  //    validates that these match, so we fix it post-copy.
  patchPluginId(outputDir, pluginId);

  echo`   [OK] ${pluginId}: copied ${copiedCount} deps `
    + `(${collected.size} discovered, ${privateCopies.length} private copies, ${skippedCount} skipped)`;
  for (const copy of privateCopies) {
    echo`      ${copy.name}@${copy.version} <- ${copy.consumer}`;
  }
}

/**
 * Patch plugin entry JS files so the exported `id` matches openclaw.plugin.json.
 * Some plugins (e.g. wecom) ship with a hardcoded ID in their compiled output
 * that differs from the manifest, causing a Gateway "plugin id mismatch" error.
 */
function patchPluginId(pluginDir, expectedId) {
  const manifestPath = path.join(pluginDir, 'openclaw.plugin.json');
  if (!fs.existsSync(manifestPath)) return;

  const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  const manifestId = manifest.id;
  if (manifestId !== expectedId) {
    echo`   [WARN]  Manifest ID "${manifestId}" doesn't match expected "${expectedId}", skipping patch`;
    return;
  }

  // Read the package.json to find the main entry point
  const pkgJsonPath = path.join(pluginDir, 'package.json');
  if (!fs.existsSync(pkgJsonPath)) return;

  const pkg = JSON.parse(fs.readFileSync(pkgJsonPath, 'utf8'));
  const entryFiles = [pkg.main, pkg.module].filter(Boolean);

  // Known ID mismatches to patch.  Keys are the wrong ID found in compiled JS,
  // values are the correct ID (must match openclaw.plugin.json).
  const ID_FIXES = {
    'wecom-openclaw-plugin': 'wecom',
  };

  for (const entry of entryFiles) {
    const entryPath = path.join(pluginDir, entry);
    if (!fs.existsSync(entryPath)) continue;

    let content = fs.readFileSync(entryPath, 'utf8');
    let patched = false;

    for (const [wrongId, correctId] of Object.entries(ID_FIXES)) {
      if (correctId !== expectedId) continue;
      // Replace  id: "wecom-openclaw-plugin"  or  id: 'wecom-openclaw-plugin'
      const pattern = new RegExp(`(\\bid\\s*:\\s*)(["'])${wrongId.replace(/-/g, '\\-')}\\2`, 'g');
      const replaced = content.replace(pattern, `$1$2${correctId}$2`);
      if (replaced !== content) {
        content = replaced;
        patched = true;
        echo`   [FIX] Patching plugin ID in ${entry}: "${wrongId}" -> "${correctId}"`;
      }
    }

    if (patched) {
      fs.writeFileSync(entryPath, content, 'utf8');
    }
  }
}

echo`? Bundling OpenClaw plugin mirrors...`;
fs.mkdirSync(OUTPUT_ROOT, { recursive: true });

for (const plugin of PLUGINS) {
  bundleOnePlugin(plugin);
}

echo`[OK] Plugin mirrors ready: ${OUTPUT_ROOT}`;
