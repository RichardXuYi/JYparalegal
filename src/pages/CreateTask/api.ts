import { platformGet, platformSend } from '@/lib/platform-api';

export { EntitlementError } from '@/lib/platform-api';

/** 统一走平台请求客户端：402 抛 EntitlementError，其它非 0 抛 ApiError。 */
export async function api<T>(path: string, init?: RequestInit): Promise<T> {
  const method = init?.method ?? 'GET';
  if (method === 'GET') return platformGet<T>(path);
  const body = init?.body != null ? JSON.parse(String(init.body)) : undefined;
  return platformSend<T>(path, method, body);
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
