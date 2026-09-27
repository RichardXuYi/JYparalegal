import AdmZip from 'adm-zip';
import { cp, mkdir, mkdtemp, rename, rm, stat, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { getOpenClawSkillsDir } from '../../utils/paths';
import { isInsideRoot, parseFrontmatter } from './local-skill-service';
import { logger } from '../../utils/logger';
import type { SkillUploadPayload, SkillUploadResult } from '@shared/host-api/contract';

// Raw upload limit (before base64 decoding). Keeps well below the studio-web
// Fastify 16MB body limit once base64 overhead (~33%) is accounted for.
const MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
// Zip safety limits, aligned with skill-sync-service constants.
const MAX_ZIP_ENTRIES = 200;
const MAX_FILE_BYTES = 2 * 1024 * 1024;
const MAX_TOTAL_BYTES = 20 * 1024 * 1024;
const SLUG_PATTERN = /^[A-Za-z0-9._-]{1,128}$/;

type ValidatedManifest = { slug: string };

function normalizeSlugFromName(name: string): string {
  return name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9-]+/g, '-')
    .replace(/-{2,}/g, '-')
    .replace(/^-+|-+$/g, '');
}

/**
 * Validates SKILL.md content: the frontmatter must declare a non-empty `name`
 * and `description`. Returns the normalized slug derived from the name, or
 * null when the content is not a valid skill manifest.
 */
function validateSkillManifest(content: string): ValidatedManifest | null {
  // Tolerate a UTF-8 BOM from Windows-authored files before frontmatter parsing.
  const frontmatter = parseFrontmatter(content.replace(/^\uFEFF/, ''));
  const name = typeof frontmatter.name === 'string' ? frontmatter.name.trim() : '';
  const description = typeof frontmatter.description === 'string' ? frontmatter.description.trim() : '';
  if (!name || !description) return null;
  const slug = normalizeSlugFromName(name);
  if (!slug || !SLUG_PATTERN.test(slug)) return null;
  return { slug };
}

async function pathExists(targetPath: string): Promise<boolean> {
  try {
    await stat(targetPath);
    return true;
  } catch {
    return false;
  }
}

/**
 * Atomically moves a fully validated staging directory into the skills root.
 * Falls back to copy+remove when rename crosses filesystem boundaries.
 */
async function moveIntoPlace(stagingDir: string, targetDir: string): Promise<void> {
  await mkdir(dirname(targetDir), { recursive: true });
  try {
    await rename(stagingDir, targetDir);
  } catch {
    await cp(stagingDir, targetDir, { recursive: true });
    await rm(stagingDir, { recursive: true, force: true });
  }
}

function isSymlinkEntry(entry: AdmZip.IZipEntry): boolean {
  // Unix mode lives in the upper 16 bits of the external attributes;
  // 0o120000 marks a symbolic link.
  const unixMode = (entry.attr >>> 16) & 0o170000;
  return unixMode === 0o120000;
}

function isUnsafeEntryName(entryName: string): boolean {
  if (!entryName) return true;
  if (entryName.startsWith('/') || entryName.startsWith('\\')) return true;
  if (/^[a-zA-Z]:/.test(entryName)) return true;
  const segments = entryName.split(/[\\/]/);
  return segments.some((segment) => segment === '..');
}

async function finalizeSkillDir(
  stagingDir: string,
  slug: string,
  overwrite: boolean,
): Promise<SkillUploadResult> {
  const skillsRoot = getOpenClawSkillsDir();
  const targetDir = resolve(skillsRoot, slug);
  if (!isInsideRoot(skillsRoot, targetDir)) {
    await rm(stagingDir, { recursive: true, force: true });
    return { success: false, code: 'zip_unsafe', error: 'Resolved skill path escapes the skills directory' };
  }
  if (await pathExists(targetDir)) {
    if (!overwrite) {
      await rm(stagingDir, { recursive: true, force: true });
      return { success: false, code: 'exists', slug };
    }
    await rm(targetDir, { recursive: true, force: true });
  }
  await moveIntoPlace(stagingDir, targetDir);
  logger.info(`[skill-upload] Installed skill "${slug}" to ${targetDir}`);
  return { success: true, slug };
}

async function uploadMarkdownSkill(content: Buffer, overwrite: boolean): Promise<SkillUploadResult> {
  const manifest = validateSkillManifest(content.toString('utf8'));
  if (!manifest) {
    return { success: false, code: 'invalid_skill', error: 'SKILL.md frontmatter must declare name and description' };
  }
  const stagingDir = await mkdtemp(join(tmpdir(), 'skill-upload-'));
  try {
    await writeFile(join(stagingDir, 'SKILL.md'), content);
  } catch (error) {
    await rm(stagingDir, { recursive: true, force: true });
    throw error;
  }
  return finalizeSkillDir(stagingDir, manifest.slug, overwrite);
}

async function uploadZipSkill(content: Buffer, overwrite: boolean): Promise<SkillUploadResult> {
  let zip: AdmZip;
  try {
    zip = new AdmZip(content);
  } catch {
    return { success: false, code: 'invalid_skill', error: 'Unable to parse zip archive' };
  }

  // Pre-validate the central directory before anything touches the disk.
  const entries = zip.getEntries();
  if (entries.length === 0 || entries.length > MAX_ZIP_ENTRIES) {
    return { success: false, code: 'zip_unsafe', error: 'Zip archive is empty or has too many entries' };
  }
  let totalBytes = 0;
  for (const entry of entries) {
    if (isUnsafeEntryName(entry.entryName) || isSymlinkEntry(entry)) {
      return { success: false, code: 'zip_unsafe', error: `Unsafe zip entry: ${entry.entryName}` };
    }
    if (entry.isDirectory) continue;
    if (entry.header.size > MAX_FILE_BYTES) {
      return { success: false, code: 'too_large', error: `Zip entry exceeds size limit: ${entry.entryName}` };
    }
    totalBytes += entry.header.size;
    if (totalBytes > MAX_TOTAL_BYTES) {
      return { success: false, code: 'too_large', error: 'Zip archive uncompressed size exceeds limit' };
    }
  }

  // Locate SKILL.md at the archive root, or inside a single wrapper directory
  // (which gets stripped so the skill lands directly under `<slug>/`).
  const fileEntries = entries.filter((entry) => !entry.isDirectory);
  let wrapperPrefix = '';
  let manifestEntry = fileEntries.find((entry) => entry.entryName === 'SKILL.md');
  if (!manifestEntry) {
    const topLevelDirs = new Set(
      entries
        .map((entry) => entry.entryName.split(/[\\/]/)[0] ?? '')
        .filter(Boolean),
    );
    if (topLevelDirs.size === 1) {
      const [onlyDir] = [...topLevelDirs];
      manifestEntry = fileEntries.find((entry) => entry.entryName.replace(/\\/g, '/') === `${onlyDir}/SKILL.md`);
      if (manifestEntry) wrapperPrefix = `${onlyDir}/`;
    }
  }
  if (!manifestEntry) {
    return { success: false, code: 'invalid_skill', error: 'Zip archive does not contain SKILL.md' };
  }
  const manifest = validateSkillManifest(manifestEntry.getData().toString('utf8'));
  if (!manifest) {
    return { success: false, code: 'invalid_skill', error: 'SKILL.md frontmatter must declare name and description' };
  }

  // Extract into a temp staging directory; every resolved path is re-checked
  // against the staging root (defense in depth against zip-slip).
  const stagingDir = await mkdtemp(join(tmpdir(), 'skill-upload-'));
  try {
    for (const entry of fileEntries) {
      const normalizedName = entry.entryName.replace(/\\/g, '/');
      if (wrapperPrefix && !normalizedName.startsWith(wrapperPrefix)) {
        await rm(stagingDir, { recursive: true, force: true });
        return { success: false, code: 'zip_unsafe', error: `Zip entry outside skill directory: ${entry.entryName}` };
      }
      const relativePath = normalizedName.slice(wrapperPrefix.length);
      if (!relativePath) continue;
      const destination = resolve(stagingDir, relativePath);
      if (!isInsideRoot(stagingDir, destination)) {
        await rm(stagingDir, { recursive: true, force: true });
        return { success: false, code: 'zip_unsafe', error: `Zip entry escapes extraction root: ${entry.entryName}` };
      }
      await mkdir(dirname(destination), { recursive: true });
      await writeFile(destination, entry.getData());
    }
  } catch (error) {
    await rm(stagingDir, { recursive: true, force: true });
    throw error;
  }
  return finalizeSkillDir(stagingDir, manifest.slug, overwrite);
}

export async function uploadSkill(payload: SkillUploadPayload): Promise<SkillUploadResult> {
  const fileName = (payload.fileName || '').trim().toLowerCase();
  const overwrite = payload.overwrite === true;
  const contentBase64 = payload.contentBase64 || '';

  // Estimate the decoded size before allocating the buffer.
  const estimatedBytes = Math.floor(contentBase64.length * 3 / 4);
  if (estimatedBytes > MAX_UPLOAD_BYTES) {
    return { success: false, code: 'too_large', error: 'Uploaded file exceeds the 10MB limit' };
  }

  let content: Buffer;
  try {
    content = Buffer.from(contentBase64, 'base64');
  } catch {
    return { success: false, code: 'invalid_skill', error: 'Invalid file content' };
  }
  if (content.length === 0) {
    return { success: false, code: 'invalid_skill', error: 'Uploaded file is empty' };
  }
  if (content.length > MAX_UPLOAD_BYTES) {
    return { success: false, code: 'too_large', error: 'Uploaded file exceeds the 10MB limit' };
  }

  try {
    if (fileName.endsWith('.md')) {
      return await uploadMarkdownSkill(content, overwrite);
    }
    if (fileName.endsWith('.zip')) {
      return await uploadZipSkill(content, overwrite);
    }
    return { success: false, code: 'unsupported_type', error: 'Only .md and .zip files are supported' };
  } catch (error) {
    logger.error('[skill-upload] Upload failed:', error);
    return { success: false, error: error instanceof Error ? error.message : 'Skill upload failed' };
  }
}
