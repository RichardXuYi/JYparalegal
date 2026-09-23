export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    credentials: 'include',
    ...init,
    headers: init?.body ? { 'Content-Type': 'application/json', ...(init.headers ?? {}) } : init?.headers,
  });
  const env = await res.json().catch(() => null);
  if (!env || env.code !== 0) throw new Error(env?.msg || '请求失败');
  return env.data as T;
}

export function defaultExpireLocal(): string {
  const d = new Date();
  d.setMonth(d.getMonth() + 3);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T23:59`;
}

export function toApiTime(local: string): string | null {
  if (!local) return null;
  return local.length === 16 ? `${local}:00` : local;
}

export type SignRequirement = {
  signatureMode: 'UNLIMITED' | 'STANDARD' | 'HANDWRITE' | 'AI_HANDWRITE';
  wills: Array<'PASSWORD' | 'SMS' | 'FACE'>;
  readToEnd: boolean;
  readSeconds: number | null;
  requireAttachment: boolean;
};

export type PartyDraft = {
  partyType: 'ORG' | 'PERSON';
  externalName: string;
  externalPhone: string;
  externalEmail: string;
  memberUserId: string;
  canFill: boolean;
  canSign: boolean;
  identityCheck: boolean;
  signRequirement: SignRequirement | null;
};

export const EMPTY_REQUIREMENT: SignRequirement = {
  signatureMode: 'UNLIMITED',
  wills: [],
  readToEnd: false,
  readSeconds: null,
  requireAttachment: false,
};

export function emptyParty(): PartyDraft {
  return {
    partyType: 'ORG',
    externalName: '',
    externalPhone: '',
    externalEmail: '',
    memberUserId: '',
    canFill: false,
    canSign: true,
    identityCheck: false,
    signRequirement: null,
  };
}
