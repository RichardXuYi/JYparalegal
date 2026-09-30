/**
 * openclaw-deps.mjs
 *
 * Shared pnpm dependency-resolution + bundle-layout helpers for the OpenClaw
 * bundlers (bundle-openclaw.mjs and bundle-openclaw-plugins.mjs).
 *
 * Why this exists: pnpm resolves one instance of each dependency *per consumer*
 * via a content-addressable virtual store of symlinks. Flattening a bundle by
 * package name alone ("first version wins") silently drops the other instances,
 * which crashes the gateway/plugin at ESM link time (e.g. htmlparser2@10 needs
 * entities ^7, but the flat top-level slot held openclaw's pinned entities 8.1.0
 * whose removed `fromCodePoint` export broke the link). These helpers reproduce
 * pnpm's own resolution: top-level slots for the root's direct deps, and a
 * private <consumer>/node_modules/ copy for every version conflict.
 *
 * This module has no zx dependency; it imports node:fs / node:path directly so
 * both bundlers can share one canonical implementation.
 */

import fs from 'node:fs';
import path from 'node:path';

/**
 * On Windows, pnpm virtual store paths can exceed MAX_PATH (260 chars). The
 * \\?\ prefix bypasses the limit for Win32 fs calls. No-op on other platforms.
 */
export function normWin(p) {
  if (process.platform !== 'win32') return p;
  if (p.startsWith('\\\\?\\')) return p;
  return '\\\\?\\' + p.replace(/\//g, '\\');
}

/**
 * Given a real package path, find the containing virtual-store node_modules.
 * e.g. .pnpm/chalk@5.4.1/node_modules/chalk -> .pnpm/chalk@5.4.1/node_modules
 */
export function getVirtualStoreNodeModules(realPkgPath) {
  let dir = realPkgPath;
  while (dir !== path.dirname(dir)) {
    if (path.basename(dir) === 'node_modules') return dir;
    dir = path.dirname(dir);
  }
  return null;
}

/**
 * List all package entries in a virtual-store node_modules directory, handling
 * both regular (chalk) and scoped (@clack/prompts) packages.
 * Returns [{ name, fullPath }] using original (non-normWin) paths so callers can
 * feed fullPath back into getVirtualStoreNodeModules()/realpathSync().
 */
export function listPackages(nodeModulesDir) {
  const result = [];
  const nDir = normWin(nodeModulesDir);
  if (!fs.existsSync(nDir)) return result;

  for (const entry of fs.readdirSync(nDir)) {
    if (entry === '.bin') continue;
    const entryPath = path.join(nodeModulesDir, entry);

    if (entry.startsWith('@')) {
      let scopeEntries = [];
      try {
        scopeEntries = fs.readdirSync(normWin(entryPath));
      } catch {
        continue;
      }
      for (const sub of scopeEntries) {
        result.push({ name: `${entry}/${sub}`, fullPath: path.join(entryPath, sub) });
      }
    } else {
      result.push({ name: entry, fullPath: entryPath });
    }
  }
  return result;
}

/** Cached package.json version reader (returns 'unknown' when unreadable). */
export function makeVersionResolver() {
  const cache = new Map();
  return function versionOfPackage(realPath) {
    if (cache.has(realPath)) return cache.get(realPath);
    let version = 'unknown';
    try {
      const pkgJson = JSON.parse(fs.readFileSync(normWin(path.join(realPath, 'package.json')), 'utf8'));
      if (typeof pkgJson.version === 'string') version = pkgJson.version;
    } catch {
      // keep 'unknown'
    }
    cache.set(realPath, version);
    return version;
  };
}

/**
 * BFS-collect every transitive dependency reachable from one or more seed
 * virtual-store node_modules, following pnpm symlinks. Records pnpm's own
 * per-consumer resolution in `depsByPackage` so planLayout can reproduce it.
 *
 * @param {Array<{nodeModulesDir:string, skipPkg:string}>} seeds BFS entry points.
 * @param {object} opts
 * @param {Set<string>} [opts.skipPackages] package names to skip (dev/optional/native).
 * @param {string[]} [opts.skipScopes] name prefixes to skip (e.g. '@types/').
 * @returns {{collected:Map<string,string>, depsByPackage:Map<string,Map<string,string>>, skippedCount:number}}
 *   collected: realPath -> package name; depsByPackage: consumer realPath -> Map<depName, depRealPath>.
 */
export function collectDeps(seeds, opts = {}) {
  const skipPackages = opts.skipPackages ?? new Set();
  const skipScopes = opts.skipScopes ?? [];

  const collected = new Map(); // realPath -> packageName
  const depsByPackage = new Map(); // consumer realPath -> Map<depName, depRealPath>
  const visitedVirtualStores = new Set();
  const queue = [...seeds];
  let skippedCount = 0;

  while (queue.length > 0) {
    const { nodeModulesDir, skipPkg } = queue.shift();
    if (visitedVirtualStores.has(nodeModulesDir)) continue;
    visitedVirtualStores.add(nodeModulesDir);

    // A virtual-store dir belongs to exactly one package; every entry inside it
    // is one of that package's resolved dependencies. Record them so the layout
    // step can reproduce pnpm's per-consumer resolution.
    let ownerDeps = null;
    try {
      const ownerReal = fs.realpathSync(path.join(nodeModulesDir, ...skipPkg.split('/')));
      ownerDeps = depsByPackage.get(ownerReal);
      if (!ownerDeps) {
        ownerDeps = new Map();
        depsByPackage.set(ownerReal, ownerDeps);
      }
    } catch {
      ownerDeps = null;
    }

    for (const { name, fullPath } of listPackages(nodeModulesDir)) {
      if (name === skipPkg) continue;
      if (skipPackages.has(name) || skipScopes.some((s) => name.startsWith(s))) {
        skippedCount++;
        continue;
      }

      let realPath;
      try {
        realPath = fs.realpathSync(fullPath);
      } catch {
        continue; // broken symlink
      }

      if (ownerDeps) ownerDeps.set(name, realPath);

      if (collected.has(realPath)) continue;
      collected.set(realPath, name);

      const depVirtualNM = getVirtualStoreNodeModules(realPath);
      if (depVirtualNM && depVirtualNM !== nodeModulesDir) {
        queue.push({ nodeModulesDir: depVirtualNM, skipPkg: name });
      }
    }
  }

  return { collected, depsByPackage, skippedCount };
}

/**
 * Lay collected packages out under outputNodeModules, reproducing pnpm's
 * resolution: `topLevelClaims` occupy the shared top-level slots first (in
 * order); every other package reuses a top-level instance when pnpm resolved
 * that same instance for it, and otherwise gets a private copy under
 * <consumer>/node_modules/ (where Node resolution would find it in a real
 * install).
 *
 * @param {object} cfg
 * @param {string} cfg.outputNodeModules destination node_modules dir.
 * @param {Map<string,Map<string,string>>} cfg.depsByPackage from collectDeps.
 * @param {(realPath:string)=>string} cfg.versionOfPackage for diagnostics.
 * @param {Array<{realPath:string, name:string, strict?:boolean}>} cfg.topLevelClaims
 *   Ordered top-level claimants. `strict:true` claims that cannot occupy their
 *   slot (a different version holds it) are reported in placementErrors instead
 *   of being silently displaced or dropped.
 * @returns {{copyPlan:Array<{realPath:string,name:string,dest:string}>,
 *            topLevelOwner:Map<string,string>, privateCopies:Array, placementErrors:Array}}
 */
export function planLayout(cfg) {
  const { outputNodeModules, depsByPackage, versionOfPackage, topLevelClaims } = cfg;

  const topLevelOwner = new Map(); // name -> realPath occupying OUTPUT/node_modules/<name>
  const copyPlan = []; // { realPath, name, dest }
  const plannedDests = new Set();
  const privateCopies = [];
  const placementErrors = [];
  const scheduled = new Set(); // `${realPath}\0${dest}` guards dependency cycles
  const planQueue = [];

  function planCopy(realPath, name, dest) {
    if (plannedDests.has(dest)) return;
    plannedDests.add(dest);
    copyPlan.push({ realPath, name, dest });

    const key = `${realPath}\0${dest}`;
    if (scheduled.has(key)) return;
    scheduled.add(key);
    planQueue.push({ realPath, name, dest });
  }

  function claimTopLevel(realPath, name) {
    const held = topLevelOwner.get(name);
    if (held === realPath) return true;
    if (held !== undefined) return false;
    topLevelOwner.set(name, realPath);
    planCopy(realPath, name, path.join(outputNodeModules, ...name.split('/')));
    return true;
  }

  for (const claim of topLevelClaims) {
    if (claimTopLevel(claim.realPath, claim.name)) continue;
    if (claim.strict) {
      placementErrors.push({
        name: claim.name,
        wanted: versionOfPackage(claim.realPath),
        heldBy: versionOfPackage(topLevelOwner.get(claim.name)),
      });
    }
  }

  while (planQueue.length > 0) {
    const consumer = planQueue.shift();
    const consumerDeps = depsByPackage.get(consumer.realPath);
    if (!consumerDeps) continue;

    for (const [depName, depRealPath] of consumerDeps) {
      if (depName === consumer.name) continue;

      const held = topLevelOwner.get(depName);
      if (held === depRealPath) continue; // shares the top-level instance

      if (held === undefined) {
        claimTopLevel(depRealPath, depName); // no conflict: keep the bundle flat
        continue;
      }

      // Conflict: give the consumer a private copy, mirroring Node resolution.
      planCopy(depRealPath, depName, path.join(consumer.dest, 'node_modules', ...depName.split('/')));
      privateCopies.push({
        name: depName,
        version: versionOfPackage(depRealPath),
        consumer: consumer.name,
      });
    }
  }

  return { copyPlan, topLevelOwner, privateCopies, placementErrors };
}

/**
 * Execute a copyPlan fail-closed. Every planned dest is a real package from the
 * pnpm store, so its package.json must exist once the copy succeeds; a missing
 * manifest (Windows cpSync can fail partway on EBUSY/EPERM/long paths) counts as
 * a failure. Returns { copiedCount, failures }; the caller decides whether a
 * non-empty failures list aborts the build (shipping a partial bundle would
 * crash the gateway/plugin at load time).
 */
export function executeCopyPlan(copyPlan, { outputRoot }) {
  let copiedCount = 0;
  const failures = [];
  for (const { realPath, name, dest } of copyPlan) {
    try {
      fs.mkdirSync(normWin(path.dirname(dest)), { recursive: true });
      fs.cpSync(normWin(realPath), normWin(dest), { recursive: true, dereference: true });
      if (!fs.existsSync(normWin(path.join(dest, 'package.json')))) {
        throw new Error('package.json missing after copy');
      }
      copiedCount++;
    } catch (err) {
      failures.push({ name, dest: path.relative(outputRoot, dest), message: err.message });
    }
  }
  return { copiedCount, failures };
}
