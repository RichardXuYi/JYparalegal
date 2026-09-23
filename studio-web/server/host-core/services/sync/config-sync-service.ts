/**
 * Cross-platform user config sync (main process).
 *
 * Generalizes the skill-sync file-manifest pattern to the user's full portable
 * configuration. Unlike skill sync (bidirectional, last-write-wins), this engine
 * is explicitly DIRECTIONAL — the UI drives `upload` (local → cloud) and
 * `download` (cloud → local) so the user always knows which side wins. Sensitive
 * credentials (provider API keys, OAuth tokens, sqlite sessions) are never part
 * of any scope.
 *
 * Scopes:
 *   - `agents`      agent-profile bundles: each agent workspace's SOUL/IDENTITY/
 *                   USER/AGENTS/MEMORY.md + memory/**.md (persona + long memory)
 *   - `skills`      the user's self-managed skills (same protocol as skill-sync)
 *   - `preferences` a whitelist of non-sensitive app settings (theme/language/…)
 *
 * The exact same file runs on desktop (studio-frontend) and inside a studio-web
 * host worker; `clientType()` tags uploads with their origin. All backend HTTP
 * goes through `authorizedJsonRequest` in the main process.
 */
import { mkdir, readFile, readdir, stat, writeFile, rm } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { dirname, join, relative } from 'node:path';
import { expandPath, getOpenClawConfigDir, getOpenClawSkillsDir } from '../../utils/paths';
import { listAgentsSnapshot } from '../../utils/agent-config';
import { getAllSettings, setSetting, type AppSettings } from '../../utils/store';
import { authorizedJsonRequest } from '../backend-auth-api';
import { collectUserManagedSkills, computeSkillHash } from '../skills/skill-sync-service';
import type {
  SyncRunResult,
  SyncScope,
  SyncScopeOutcome,
  SyncScopeStatus,
  SyncStatusResult,
} from '@shared/host-api/contract';

const MAX_FILE_BYTES = 2 * 1024 * 1024; // keep in sync with backend UserSyncBundleService
const MAX_TOTAL_BYTES = 20 * 1024 * 1024;

const AGENT_BUNDLE_KIND = 'agent-profile';
const AGENT_PERSONA_FILES = ['SOUL.md', 'IDENTITY.md', 'USER.md', 'AGENTS.md', 'MEMORY.md'];
const AGENT_MEMORY_DIR = 'memory';
const SLUG_PATTERN = /^[A-Za-z0-9._-]{1,128}$/;

const PREFERENCES_NAMESPACE = 'preferences';
const PREFERENCES_KEY = 'app';
/**
 * Non-sensitive settings that are safe to carry across devices. Deliberately
 * EXCLUDES machine-specific / sensitive keys: gatewayToken, machineId, gateway
 * port/auto-start, all proxy.*, telemetry, install flags, devModeUnlocked.
 */
const PREFERENCE_KEYS: (keyof AppSettings)[] = [
  'theme',
  'language',
  'startMinimized',
  'launchAtStartup',
  'updateChannel',
  'autoCheckUpdate',
  'autoDownloadUpdate',
  'autoSyncSkills',
  'sidebarCollapsed',
  'selectedBundles',
  'enabledSkills',
  'disabledSkills',
];

interface SyncFile {
  path: string;
  contentBase64: string;
  sha256: string;
  size: number;
}

interface LocalBundle {
  slug: string;
  files: SyncFile[];
  hash: string;
}

interface RemoteBundleSummary {
  kind?: string;
  slug: string;
  hash: string;
  updatedAt?: string;
}

interface RemoteBundleDetail {
  slug: string;
  hash?: string;
  files?: { path: string; contentBase64: string }[];
}

interface RemoteConfigEntry {
  namespace: string;
  key: string;
  contentJson: string;
  hash: string;
  updatedAt?: string;
}

interface RemoteSkillSummary {
  slug: string;
  name?: string;
  version?: string;
  hash: string;
  updatedAt?: string;
}

interface RemoteSkillDetail {
  slug: string;
  files?: { path: string; contentBase64: string }[];
}

type RemoteFetch<T> = { ok: boolean; requiresAuth?: boolean; error?: string; value: T };

/** Tag uploads with their origin so the backend/other clients can attribute them. */
function clientType(): string {
  return process.env.JY_WORKER_SCOPE ? 'web' : 'desktop';
}

function sha256(input: Buffer | string): string {
  return createHash('sha256').update(input).digest('hex');
}

/** Aggregate a set of (slug, hash) into one stable scope-level fingerprint. */
function aggregateHash(items: { slug: string; hash: string }[]): string | null {
  if (items.length === 0) return null;
  const hash = createHash('sha256');
  for (const item of [...items].sort((a, b) => (a.slug < b.slug ? -1 : a.slug > b.slug ? 1 : 0))) {
    hash.update(item.slug);
    hash.update('\n');
    hash.update(item.hash);
    hash.update('\n');
  }
  return hash.digest('hex');
}

function sanitizeSlug(value: string): string | null {
  const trimmed = (value ?? '').trim();
  return SLUG_PATTERN.test(trimmed) ? trimmed : null;
}

// --- local collectors -------------------------------------------------------

async function readSyncFile(baseDir: string, abs: string): Promise<SyncFile | null> {
  let info;
  try {
    info = await stat(abs);
  } catch {
    return null;
  }
  if (!info.isFile() || info.size > MAX_FILE_BYTES) return null;
  const bytes = await readFile(abs);
  const rel = relative(baseDir, abs).split('\\').join('/');
  return { path: rel, contentBase64: bytes.toString('base64'), sha256: sha256(bytes), size: info.size };
}

async function collectPersonaFiles(workspace: string): Promise<SyncFile[]> {
  const files: SyncFile[] = [];
  let total = 0;

  for (const name of AGENT_PERSONA_FILES) {
    const file = await readSyncFile(workspace, join(workspace, name));
    if (file) {
      files.push(file);
      total += file.size;
    }
  }

  // memory/**/*.md — long-term memory logs, text only.
  const stack = [join(workspace, AGENT_MEMORY_DIR)];
  while (stack.length > 0) {
    const dir = stack.pop() as string;
    let entries;
    try {
      entries = await readdir(dir, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const entry of entries) {
      if (entry.isSymbolicLink()) continue;
      const abs = join(dir, entry.name);
      if (entry.isDirectory()) {
        stack.push(abs);
        continue;
      }
      if (!entry.isFile() || !entry.name.toLowerCase().endsWith('.md')) continue;
      const file = await readSyncFile(workspace, abs);
      if (!file) continue;
      total += file.size;
      if (total > MAX_TOTAL_BYTES) return files; // stop before we exceed the bundle cap
      files.push(file);
    }
  }

  return files;
}

/** Map of local agent slug → absolute workspace dir (for download targets). */
async function getAgentWorkspaces(): Promise<Map<string, string>> {
  const map = new Map<string, string>();
  let snapshot;
  try {
    snapshot = await listAgentsSnapshot();
  } catch {
    return map;
  }
  for (const agent of snapshot.agents) {
    const slug = sanitizeSlug(agent.id);
    if (slug && agent.workspace) map.set(slug, expandPath(agent.workspace));
  }
  return map;
}

async function collectAgentBundles(): Promise<LocalBundle[]> {
  const workspaces = await getAgentWorkspaces();
  const bundles: LocalBundle[] = [];
  for (const [slug, workspace] of workspaces) {
    const files = await collectPersonaFiles(workspace);
    if (files.length === 0) continue;
    bundles.push({ slug, files, hash: computeSkillHash(files) });
  }
  return bundles;
}

function collectPreferences(settings: AppSettings): { contentJson: string; hash: string } {
  const subset: Record<string, unknown> = {};
  for (const key of PREFERENCE_KEYS) subset[key] = settings[key];
  // Array replacer fixes key order → deterministic hash across runs/devices.
  const contentJson = JSON.stringify(subset, PREFERENCE_KEYS as string[]);
  return { contentJson, hash: sha256(contentJson) };
}

// --- remote helpers ---------------------------------------------------------

async function listRemoteBundles(kind: string): Promise<RemoteFetch<RemoteBundleSummary[]>> {
  const res = await authorizedJsonRequest<{ bundles?: RemoteBundleSummary[] }>(
    `/api/user/bundles?kind=${encodeURIComponent(kind)}`,
  );
  if (!res.ok) return { ok: false, requiresAuth: res.requiresAuth, error: res.error, value: [] };
  const bundles = Array.isArray(res.data?.bundles) ? (res.data!.bundles as RemoteBundleSummary[]) : [];
  return { ok: true, value: bundles };
}

async function getRemoteBundle(kind: string, slug: string): Promise<RemoteBundleDetail | null> {
  const res = await authorizedJsonRequest<RemoteBundleDetail>(
    `/api/user/bundles/${encodeURIComponent(kind)}/${encodeURIComponent(slug)}`,
  );
  return res.ok && res.data ? res.data : null;
}

async function pushBundle(kind: string, slug: string, hash: string, files: SyncFile[]): Promise<{ ok: boolean; error?: string }> {
  const res = await authorizedJsonRequest(`/api/user/bundles/${encodeURIComponent(kind)}/${encodeURIComponent(slug)}`, {
    method: 'PUT',
    body: { hash, clientType: clientType(), files: files.map((f) => ({ path: f.path, contentBase64: f.contentBase64 })) },
  });
  return { ok: res.ok, error: res.error };
}

async function getRemoteConfig(namespace: string, key: string): Promise<RemoteFetch<RemoteConfigEntry | null>> {
  const res = await authorizedJsonRequest<RemoteConfigEntry>(
    `/api/user/config/${encodeURIComponent(namespace)}/${encodeURIComponent(key)}`,
  );
  if (res.ok) return { ok: true, value: res.data ?? null };
  if (res.status === 404) return { ok: true, value: null }; // no remote yet — not an error
  return { ok: false, requiresAuth: res.requiresAuth, error: res.error, value: null };
}

async function pushConfig(namespace: string, key: string, contentJson: string, hash: string): Promise<{ ok: boolean; error?: string }> {
  const res = await authorizedJsonRequest(`/api/user/config/${encodeURIComponent(namespace)}/${encodeURIComponent(key)}`, {
    method: 'PUT',
    body: { contentJson, hash, clientType: clientType() },
  });
  return { ok: res.ok, error: res.error };
}

async function listRemoteSkills(): Promise<RemoteFetch<RemoteSkillSummary[]>> {
  const res = await authorizedJsonRequest<{ skills?: RemoteSkillSummary[] }>('/api/user/skills');
  if (!res.ok) return { ok: false, requiresAuth: res.requiresAuth, error: res.error, value: [] };
  const skills = Array.isArray(res.data?.skills) ? (res.data!.skills as RemoteSkillSummary[]) : [];
  return { ok: true, value: skills };
}

// --- filesystem write helpers (download) ------------------------------------

function backupRoot(): string {
  const stamp = new Date().toISOString().replace(/[:.]/g, '-');
  return join(getOpenClawConfigDir(), '.sync-backup', stamp);
}

/** Copy an existing directory's persona/memory files into the backup root. */
async function backupPersona(workspace: string, slug: string, backupDir: string): Promise<void> {
  const files = await collectPersonaFiles(workspace);
  for (const file of files) {
    const dest = join(backupDir, 'agents', slug, file.path);
    if (!dest.startsWith(backupDir)) continue;
    await mkdir(dirname(dest), { recursive: true });
    await writeFile(dest, Buffer.from(file.contentBase64, 'base64'));
  }
}

/** Overwrite an agent workspace's persona/memory files from a remote bundle. */
async function writeBundleToWorkspace(workspace: string, detail: RemoteBundleDetail): Promise<void> {
  if (!Array.isArray(detail.files)) return;
  // Clear the memory dir first so deleted remote logs don't linger; persona
  // files are overwritten in place (never delete the whole workspace — it holds
  // more than synced files).
  await rm(join(workspace, AGENT_MEMORY_DIR), { recursive: true, force: true });
  for (const file of detail.files) {
    const rel = (file.path || '').split('\\').join('/');
    if (!rel || rel.startsWith('/') || rel.includes('..')) continue;
    const dest = join(workspace, rel);
    if (!dest.startsWith(workspace)) continue;
    await mkdir(dirname(dest), { recursive: true });
    await writeFile(dest, Buffer.from(file.contentBase64 ?? '', 'base64'));
  }
}

async function pullSkillToDisk(slug: string, backupDir: string): Promise<boolean> {
  const res = await authorizedJsonRequest<RemoteSkillDetail>(`/api/user/skills/${encodeURIComponent(slug)}`);
  if (!res.ok || !res.data || !Array.isArray(res.data.files)) return false;

  const skillsRoot = getOpenClawSkillsDir();
  const targetDir = join(skillsRoot, slug);
  if (!targetDir.startsWith(skillsRoot)) return false;

  // Back up the existing skill dir before overwriting.
  const existing = await collectPersonaBackup(targetDir);
  for (const file of existing) {
    const dest = join(backupDir, 'skills', slug, file.path);
    if (!dest.startsWith(backupDir)) continue;
    await mkdir(dirname(dest), { recursive: true });
    await writeFile(dest, Buffer.from(file.contentBase64, 'base64'));
  }

  await rm(targetDir, { recursive: true, force: true });
  await mkdir(targetDir, { recursive: true });
  for (const file of res.data.files) {
    const rel = (file.path || '').split('\\').join('/');
    if (!rel || rel.startsWith('/') || rel.includes('..')) continue;
    const dest = join(targetDir, rel);
    if (!dest.startsWith(targetDir)) continue;
    await mkdir(dirname(dest), { recursive: true });
    await writeFile(dest, Buffer.from(file.contentBase64 ?? '', 'base64'));
  }
  return true;
}

/** Recursively read every file under a dir as sync files (for backups). */
async function collectPersonaBackup(baseDir: string): Promise<SyncFile[]> {
  const files: SyncFile[] = [];
  const stack = [baseDir];
  while (stack.length > 0) {
    const dir = stack.pop() as string;
    let entries;
    try {
      entries = await readdir(dir, { withFileTypes: true });
    } catch {
      continue;
    }
    for (const entry of entries) {
      if (entry.isSymbolicLink()) continue;
      const abs = join(dir, entry.name);
      if (entry.isDirectory()) {
        stack.push(abs);
        continue;
      }
      const file = await readSyncFile(baseDir, abs);
      if (file) files.push(file);
    }
  }
  return files;
}

// --- status -----------------------------------------------------------------

export async function getConfigSyncStatus(scopes?: SyncScope[]): Promise<SyncStatusResult> {
  const wanted = new Set<SyncScope>(scopes && scopes.length > 0 ? scopes : ['agents', 'skills', 'preferences']);
  const results: SyncScopeStatus[] = [];
  let requiresAuth = false;

  if (wanted.has('agents')) {
    const [local, remote] = await Promise.all([collectAgentBundles(), listRemoteBundles(AGENT_BUNDLE_KIND)]);
    if (remote.requiresAuth) requiresAuth = true;
    const localHash = aggregateHash(local.map((b) => ({ slug: b.slug, hash: b.hash })));
    const remoteHash = remote.ok ? aggregateHash(remote.value.map((b) => ({ slug: b.slug, hash: b.hash }))) : null;
    results.push({
      scope: 'agents',
      localHash,
      remoteHash,
      inSync: localHash === remoteHash,
      localItems: local.length,
      remoteItems: remote.value.length,
    });
  }

  if (wanted.has('skills')) {
    const [local, remote] = await Promise.all([collectUserManagedSkills(), listRemoteSkills()]);
    if (remote.requiresAuth) requiresAuth = true;
    const localHash = aggregateHash(local.map((s) => ({ slug: s.slug, hash: s.hash })));
    const remoteHash = remote.ok ? aggregateHash(remote.value.map((s) => ({ slug: s.slug, hash: s.hash }))) : null;
    results.push({
      scope: 'skills',
      localHash,
      remoteHash,
      inSync: localHash === remoteHash,
      localItems: local.length,
      remoteItems: remote.value.length,
    });
  }

  if (wanted.has('preferences')) {
    const [settings, remote] = await Promise.all([getAllSettings(), getRemoteConfig(PREFERENCES_NAMESPACE, PREFERENCES_KEY)]);
    if (remote.requiresAuth) requiresAuth = true;
    const local = collectPreferences(settings);
    const remoteHash = remote.value?.hash ?? null;
    results.push({
      scope: 'preferences',
      localHash: local.hash,
      remoteHash,
      inSync: local.hash === remoteHash,
      remoteUpdatedAt: remote.value?.updatedAt,
    });
  }

  if (requiresAuth) return { success: false, requiresAuth: true, error: '请先登录' };
  return { success: true, scopes: results };
}

// --- upload (local → cloud) -------------------------------------------------

async function uploadScope(scope: SyncScope): Promise<SyncScopeOutcome> {
  try {
    if (scope === 'agents') {
      const local = await collectAgentBundles();
      let pushed = 0;
      const failed: string[] = [];
      for (const bundle of local) {
        const res = await pushBundle(AGENT_BUNDLE_KIND, bundle.slug, bundle.hash, bundle.files);
        if (res.ok) pushed += 1;
        else failed.push(`${bundle.slug}: ${res.error ?? '上传失败'}`);
      }
      if (failed.length > 0) return { scope, ok: false, pushed, error: failed.join('; ') };
      return { scope, ok: true, pushed };
    }
    if (scope === 'skills') {
      const local = await collectUserManagedSkills();
      let pushed = 0;
      const failed: string[] = [];
      for (const skill of local) {
        const res = await authorizedJsonRequest(`/api/user/skills/${encodeURIComponent(skill.slug)}`, {
          method: 'PUT',
          body: {
            name: skill.name,
            description: skill.description,
            version: skill.version,
            hash: skill.hash,
            files: skill.files.map((f) => ({ path: f.path, contentBase64: f.contentBase64 })),
          },
        });
        if (res.ok) pushed += 1;
        else failed.push(`${skill.slug}: ${res.error || `HTTP ${res.status}`}`);
      }
      if (failed.length > 0) return { scope, ok: false, pushed, error: failed.join('; ') };
      return { scope, ok: true, pushed };
    }
    // preferences
    const settings = await getAllSettings();
    const local = collectPreferences(settings);
    const res = await pushConfig(PREFERENCES_NAMESPACE, PREFERENCES_KEY, local.contentJson, local.hash);
    return { scope, ok: res.ok, pushed: res.ok ? 1 : 0, error: res.ok ? undefined : (res.error ?? '上传失败') };
  } catch (error) {
    return { scope, ok: false, error: error instanceof Error ? error.message : String(error) };
  }
}

export async function uploadConfig(scopes: SyncScope[]): Promise<SyncRunResult> {
  const results: SyncScopeOutcome[] = [];
  for (const scope of scopes) {
    results.push(await uploadScope(scope));
  }
  return { success: results.every((r) => r.ok), results };
}

// --- download (cloud → local) -----------------------------------------------

async function downloadScope(scope: SyncScope, backupDir: string): Promise<SyncScopeOutcome> {
  try {
    if (scope === 'agents') {
      const [remote, workspaces] = await Promise.all([listRemoteBundles(AGENT_BUNDLE_KIND), getAgentWorkspaces()]);
      if (!remote.ok) return { scope, ok: false, error: remote.requiresAuth ? '请先登录' : remote.error };
      let pulled = 0;
      for (const summary of remote.value) {
        const workspace = workspaces.get(summary.slug);
        if (!workspace) continue; // no local agent with this id — can't materialize config
        const detail = await getRemoteBundle(AGENT_BUNDLE_KIND, summary.slug);
        if (!detail) continue;
        await backupPersona(workspace, summary.slug, backupDir);
        await writeBundleToWorkspace(workspace, detail);
        pulled += 1;
      }
      return { scope, ok: true, pulled };
    }
    if (scope === 'skills') {
      const remote = await listRemoteSkills();
      if (!remote.ok) return { scope, ok: false, error: remote.requiresAuth ? '请先登录' : remote.error };
      let pulled = 0;
      for (const summary of remote.value) {
        const slug = sanitizeSlug(summary.slug);
        if (!slug) continue;
        if (await pullSkillToDisk(slug, backupDir)) pulled += 1;
      }
      return { scope, ok: true, pulled };
    }
    // preferences
    const remote = await getRemoteConfig(PREFERENCES_NAMESPACE, PREFERENCES_KEY);
    if (!remote.ok) return { scope, ok: false, error: remote.requiresAuth ? '请先登录' : remote.error };
    if (!remote.value) return { scope, ok: true, pulled: 0 };
    let parsed: Partial<AppSettings>;
    try {
      parsed = JSON.parse(remote.value.contentJson) as Partial<AppSettings>;
    } catch {
      return { scope, ok: false, error: '远端偏好格式无效' };
    }
    // Back up current preferences before overwriting.
    const current = collectPreferences(await getAllSettings());
    const dest = join(backupDir, 'preferences.json');
    if (dest.startsWith(backupDir)) {
      await mkdir(backupDir, { recursive: true });
      await writeFile(dest, current.contentJson, 'utf8');
    }
    for (const key of PREFERENCE_KEYS) {
      if (Object.prototype.hasOwnProperty.call(parsed, key)) {
        await setSetting(key, parsed[key] as never);
      }
    }
    return { scope, ok: true, pulled: 1 };
  } catch (error) {
    return { scope, ok: false, error: error instanceof Error ? error.message : String(error) };
  }
}

export async function downloadConfig(scopes: SyncScope[]): Promise<SyncRunResult> {
  const backupDir = backupRoot();
  const results: SyncScopeOutcome[] = [];
  for (const scope of scopes) {
    results.push(await downloadScope(scope, backupDir));
  }
  return { success: results.every((r) => r.ok), results };
}
