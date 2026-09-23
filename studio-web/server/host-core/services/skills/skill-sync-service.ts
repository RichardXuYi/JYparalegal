/**
 * Cross-platform user skill sync (main process).
 *
 * Bidirectionally syncs the current user's *self-managed* skills (those under
 * `~/.openclaw/skills/` that are neither preinstalled nor bundled) with the JY
 * backend. Skill contents are transferred as a JSON file-manifest (each file's
 * raw bytes Base64-encoded) so no zip dependency or multipart handling is
 * needed, and the backend persists them to disk (see UserSkillService).
 *
 * All backend HTTP goes through `authorizedJsonRequest` here in the main
 * process — the renderer only ever calls `invokeHost('skills', 'sync'|...)`.
 */
import { mkdir, readdir, readFile, rm, stat, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { dirname, join, relative } from 'node:path';
import { getOpenClawSkillsDir } from '../../utils/paths';
import { listLocalSkills } from './local-skill-service';
import { authorizedJsonRequest } from '../backend-auth-api';
import type {
  SkillSyncPlanItem,
  SkillSyncResult,
  SkillSyncStatusResult,
} from '@shared/host-api/contract';

const MAX_FILE_BYTES = 2 * 1024 * 1024; // keep in sync with backend UserSkillService
const MAX_TOTAL_BYTES = 20 * 1024 * 1024;
const IGNORE_DIRS = new Set(['node_modules', '.git', '.venv', '__pycache__', '.clawhub', '.DS_Store']);
const IGNORE_FILES = new Set(['.grandpoem-studio-preinstalled.json', '.DS_Store']);
const SLUG_PATTERN = /^[A-Za-z0-9._-]{1,128}$/;

export interface SyncFile {
  path: string;
  contentBase64: string;
  sha256: string;
  size: number;
}

export interface LocalUserSkill {
  slug: string;
  name: string;
  description: string;
  version?: string;
  baseDir: string;
  files: SyncFile[];
  hash: string;
  mtimeMs: number;
}

interface RemoteSkillSummary {
  slug: string;
  name?: string;
  version?: string;
  hash: string;
  sizeBytes?: number;
  updatedAt?: string;
}

interface RemoteSkillDetail {
  slug: string;
  name?: string;
  description?: string;
  version?: string;
  hash?: string;
  files?: { path: string; contentBase64: string }[];
}

/**
 * Canonical skill content hash. MUST match the backend algorithm: sha256 over
 * each file's raw bytes, then sha256 over the path-sorted sequence of
 * `path + "\n" + fileHash + "\n"`.
 */
export function computeSkillHash(files: { path: string; sha256: string }[]): string {
  const sorted = [...files].sort((a, b) => (a.path < b.path ? -1 : a.path > b.path ? 1 : 0));
  const hash = createHash('sha256');
  for (const file of sorted) {
    hash.update(file.path);
    hash.update('\n');
    hash.update(file.sha256);
    hash.update('\n');
  }
  return hash.digest('hex');
}

/**
 * Diff local vs remote skills into a per-slug action plan. Pure function
 * (exported for tests). Conflict resolution is last-write-wins by timestamp.
 */
export function computeSyncPlan(
  local: { slug: string; name?: string; hash: string; mtimeMs?: number }[],
  remote: { slug: string; name?: string; hash: string; updatedAt?: string }[],
): SkillSyncPlanItem[] {
  const remoteBySlug = new Map(remote.map((r) => [r.slug, r]));
  const localBySlug = new Map(local.map((l) => [l.slug, l]));
  const items: SkillSyncPlanItem[] = [];

  for (const l of local) {
    const r = remoteBySlug.get(l.slug);
    if (!r) {
      items.push({ slug: l.slug, name: l.name, action: 'push', reason: 'local-only' });
      continue;
    }
    if (l.hash === r.hash) {
      items.push({ slug: l.slug, name: l.name, action: 'skip', reason: 'in-sync' });
      continue;
    }
    const localTime = l.mtimeMs ?? 0;
    const remoteTime = r.updatedAt ? Date.parse(r.updatedAt) : 0;
    if (localTime >= remoteTime) {
      items.push({ slug: l.slug, name: l.name, action: 'push', reason: 'local-newer' });
    } else {
      items.push({ slug: l.slug, name: l.name, action: 'pull', reason: 'remote-newer' });
    }
  }

  for (const r of remote) {
    if (!localBySlug.has(r.slug)) {
      items.push({ slug: r.slug, name: r.name, action: 'pull', reason: 'remote-only' });
    }
  }

  return items;
}

async function walkSkillFiles(baseDir: string): Promise<{ files: SyncFile[]; totalBytes: number; mtimeMs: number } | null> {
  const files: SyncFile[] = [];
  let totalBytes = 0;
  let mtimeMs = 0;

  const walk = async (dir: string): Promise<boolean> => {
    let entries;
    try {
      entries = await readdir(dir, { withFileTypes: true });
    } catch {
      return true;
    }
    for (const entry of entries) {
      if (entry.isSymbolicLink()) continue;
      const abs = join(dir, entry.name);
      if (entry.isDirectory()) {
        if (IGNORE_DIRS.has(entry.name)) continue;
        const ok = await walk(abs);
        if (!ok) return false;
        continue;
      }
      if (!entry.isFile()) continue;
      if (IGNORE_FILES.has(entry.name)) continue;
      let info;
      try {
        info = await stat(abs);
      } catch {
        continue;
      }
      if (info.size > MAX_FILE_BYTES) continue; // skip oversized single files
      totalBytes += info.size;
      if (totalBytes > MAX_TOTAL_BYTES) return false; // skill too large — abort
      const bytes = await readFile(abs);
      const rel = relative(baseDir, abs).split('\\').join('/');
      files.push({
        path: rel,
        contentBase64: bytes.toString('base64'),
        sha256: createHash('sha256').update(bytes).digest('hex'),
        size: info.size,
      });
      if (info.mtimeMs > mtimeMs) mtimeMs = info.mtimeMs;
    }
    return true;
  };

  const completed = await walk(baseDir);
  if (!completed) return null;
  return { files, totalBytes, mtimeMs };
}

/** Collect the current user's self-managed skills with contents + content hash. */
export async function collectUserManagedSkills(): Promise<LocalUserSkill[]> {
  const records = await listLocalSkills();
  const managed = records.filter(
    (r) => r.source === 'openclaw-managed' && !r.isBundled && Boolean(r.baseDir),
  );

  const result: LocalUserSkill[] = [];
  for (const record of managed) {
    const slug = (record.slug || record.id || '').trim();
    if (!SLUG_PATTERN.test(slug)) continue;
    const scan = await walkSkillFiles(record.baseDir as string);
    if (!scan || scan.files.length === 0) continue;
    result.push({
      slug,
      name: record.name,
      description: record.description,
      version: record.version,
      baseDir: record.baseDir as string,
      files: scan.files,
      hash: computeSkillHash(scan.files),
      mtimeMs: scan.mtimeMs,
    });
  }
  return result;
}

async function listRemoteSkills(): Promise<{ ok: boolean; requiresAuth?: boolean; error?: string; skills: RemoteSkillSummary[] }> {
  const res = await authorizedJsonRequest<{ skills?: RemoteSkillSummary[] }>('/api/user/skills');
  if (!res.ok) {
    return { ok: false, requiresAuth: res.requiresAuth, error: res.error, skills: [] };
  }
  const skills = Array.isArray(res.data?.skills) ? (res.data!.skills as RemoteSkillSummary[]) : [];
  return { ok: true, skills };
}

async function pushSkill(skill: LocalUserSkill): Promise<boolean> {
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
  return res.ok;
}

async function pullSkill(slug: string): Promise<boolean> {
  const res = await authorizedJsonRequest<RemoteSkillDetail>(`/api/user/skills/${encodeURIComponent(slug)}`);
  if (!res.ok || !res.data || !Array.isArray(res.data.files)) return false;

  const skillsRoot = getOpenClawSkillsDir();
  const targetDir = join(skillsRoot, slug);
  // Guard: never write outside the skills root.
  if (!targetDir.startsWith(skillsRoot)) return false;

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

/** Preview the bidirectional sync plan without transferring anything. */
export async function getSkillSyncStatus(): Promise<SkillSyncStatusResult> {
  const [local, remoteRes] = await Promise.all([collectUserManagedSkills(), listRemoteSkills()]);
  if (!remoteRes.ok) {
    return { success: false, requiresAuth: remoteRes.requiresAuth, error: remoteRes.error || '无法获取远端 skill' };
  }
  const items = computeSyncPlan(
    local.map((l) => ({ slug: l.slug, name: l.name, hash: l.hash, mtimeMs: l.mtimeMs })),
    remoteRes.skills.map((r) => ({ slug: r.slug, name: r.name, hash: r.hash, updatedAt: r.updatedAt })),
  );
  return { success: true, items };
}

/** Execute the bidirectional sync. */
export async function runSkillSync(): Promise<SkillSyncResult> {
  const [local, remoteRes] = await Promise.all([collectUserManagedSkills(), listRemoteSkills()]);
  if (!remoteRes.ok) {
    return { success: false, requiresAuth: remoteRes.requiresAuth, error: remoteRes.error || '无法获取远端 skill' };
  }

  const localBySlug = new Map(local.map((l) => [l.slug, l]));
  const items = computeSyncPlan(
    local.map((l) => ({ slug: l.slug, name: l.name, hash: l.hash, mtimeMs: l.mtimeMs })),
    remoteRes.skills.map((r) => ({ slug: r.slug, name: r.name, hash: r.hash, updatedAt: r.updatedAt })),
  );

  let pushed = 0;
  let pulled = 0;
  let skipped = 0;
  let failed = 0;

  for (const item of items) {
    if (item.action === 'skip') {
      skipped += 1;
      continue;
    }
    try {
      if (item.action === 'push') {
        const skill = localBySlug.get(item.slug);
        if (skill && (await pushSkill(skill))) pushed += 1;
        else failed += 1;
      } else if (item.action === 'pull') {
        if (await pullSkill(item.slug)) pulled += 1;
        else failed += 1;
      }
    } catch {
      failed += 1;
    }
  }

  return { success: true, pushed, pulled, skipped, failed, items };
}
