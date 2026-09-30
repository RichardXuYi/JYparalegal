import { useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { LegalPageHeader } from '@/components/legal/LegalPageHeader';
import { ApiError, EntitlementError, platformGet, platformSend } from '@/lib/platform-api';
import { reportFailure, reportSuccess } from '@/lib/notice';

type View = 'cases' | 'files' | 'court';
type CaseRow = {
  id: number;
  stance: string;
  summary: string;
  status: string;
  signTaskId?: number | null;
  hearingId?: number | null;
};
type FileRow = {
  id: number;
  fileName: string;
  parseStatus: string;
  sha256: string;
  chunks: number;
  sizeBytes?: number;
};
type Slot = { id: number; seq: number; role_code: string; status: string; plan_text: string };
type Utterance = { id: number; slot_id: number; role_code: string; speaker: string; body: string };
type Retrieval = { slotId: number; fileName: string; seq: number; excerpt: string };
type HearingView = {
  hearing: { id: number; status: string };
  slots: Slot[];
  utterances: Utterance[];
  retrievals: Retrieval[];
  failedSessions?: string[];
};

function isDesktop(): boolean {
  return typeof window !== 'undefined' && 'electron' in window;
}

function formatSize(value: number | undefined): string {
  if (!value) return '—';
  if (value < 1024) return `${value} B`;
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`;
  return `${(value / 1024 / 1024).toFixed(1)} MB`;
}

function missingClauses(body: string, excerpts: string[]): string[] {
  const found = body.match(/第\s*\d+\s*条/g) ?? [];
  return [...new Set(found.map((item) => item.replace(/\s/g, '')))].filter(
    (clause) => !excerpts.some((excerpt) => excerpt.replace(/\s/g, '').includes(clause)),
  );
}

async function readSse(res: Response, onEvent: (event: string, payload: Record<string, unknown>) => void): Promise<void> {
  const apply = (chunk: string) => {
    for (const part of chunk.split('\n\n')) {
      const event = /event: (\S+)/.exec(part)?.[1];
      const dataLine = part.split('\n').find((line) => line.startsWith('data: '));
      if (!event || !dataLine) continue;
      onEvent(event, JSON.parse(dataLine.slice(6)) as Record<string, unknown>);
    }
  };
  if (!res.body) {
    apply(await res.text());
    return;
  }
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  for (;;) {
    const chunk = await reader.read();
    if (chunk.done) break;
    buffer += decoder.decode(chunk.value, { stream: true });
    const parts = buffer.split('\n\n');
    buffer = parts.pop() ?? '';
    apply(`${parts.join('\n\n')}\n\n`);
  }
  if (buffer.trim()) apply(`${buffer}\n\n`);
}

export default function MockCourt() {
  const { t } = useTranslation('settings');
  const roleLabel = (code: string) => {
    const key = {
      JUDGE: 'judge',
      JUROR_A: 'jurorA',
      JUROR_B: 'jurorB',
      OURS: 'ours',
      OPPONENT: 'opponent',
      CLERK: 'clerk',
    }[code];
    return key ? t(`moot.${key}`) : code;
  };
  const parseLabel = (status: string) => {
    if (status === 'PARSED') return t('moot.parsed');
    if (status === 'FAILED') return t('moot.failed');
    if (status === 'NO_TEXT') return t('moot.noText');
    return status;
  };
  const [view, setView] = useState<View>('cases');
  const [cases, setCases] = useState<CaseRow[]>([]);
  const [active, setActive] = useState<CaseRow | null>(null);
  const [files, setFiles] = useState<FileRow[]>([]);
  const [preview, setPreview] = useState<{ title: string; text: string } | null>(null);
  const [summary, setSummary] = useState('买卖合同。我方交付设备，主张余款。');
  const [stance, setStance] = useState('PLAINTIFF');
  const [hearing, setHearing] = useState<HearingView | null>(null);
  const [selfSpeak, setSelfSpeak] = useState(false);
  const [humanSlot, setHumanSlot] = useState<number | null>(null);
  const [humanText, setHumanText] = useState('');
  const [interjectText, setInterjectText] = useState('');
  const [live, setLive] = useState('');
  const [busy, setBusy] = useState(false);
  const [tasks, setTasks] = useState<Array<{ id: number; title?: string; taskNo?: string }>>([]);
  const [signTaskId, setSignTaskId] = useState('');

  function applyEvent(event: string, payload: Record<string, unknown>) {
    if (event === 'delta' && typeof payload.text === 'string') setLive(payload.text);
    if (event === 'await-human' && typeof payload.slotId === 'number') setHumanSlot(payload.slotId);
    if (event === 'slot-committed' && Array.isArray(payload.slots)) {
      setHearing(payload as unknown as HearingView);
      setLive('');
      setHumanSlot(null);
    }
    if (event === 'hearing-closed') {
      setHearing(null);
      setView('files');
    }
    if (event === 'error') reportFailure(new Error(typeof payload.msg === 'string' ? payload.msg : t('moot.speakFail')));
  }

  async function refreshCases() {
    const rows = await platformGet<CaseRow[]>('/platform/moot/cases');
    setCases(rows);
    return rows;
  }

  async function loadHearing(row: CaseRow) {
    try {
      setActive(row);
      setFiles(await platformGet<FileRow[]>(`/platform/moot/cases/${row.id}/files`));
      if (row.status === 'HEARING' && row.hearingId) {
        setHearing(await platformGet<HearingView>(`/platform/moot/hearings/${row.hearingId}`));
        setView('court');
        return;
      }
      setView('files');
    } catch (e) {
      reportFailure(e);
    }
  }

  useEffect(() => {
    let stop = false;
    void platformGet<CaseRow[]>('/platform/moot/cases').then(async (rows) => {
      if (stop) return;
      setCases(rows);
      const liveCase = rows.find((row) => row.status === 'HEARING' && row.hearingId);
      if (!liveCase) return;
      setActive(liveCase);
      setFiles(await platformGet<FileRow[]>(`/platform/moot/cases/${liveCase.id}/files`));
      if (stop) return;
      setHearing(await platformGet<HearingView>(`/platform/moot/hearings/${liveCase.hearingId}`));
      setView('court');
    }).catch((e: unknown) => {
      if (!stop) reportFailure(e);
    });
    void platformGet<Array<{ id: number; title?: string; taskNo?: string }>>('/platform/sign/tasks?view=ALL_SIGNING')
      .then((rows) => { if (!stop) setTasks(rows); })
      .catch(() => { if (!stop) setTasks([]); });
    return () => { stop = true; };
  }, [t]);

  useEffect(() => {
    const hearingId = hearing?.hearing.id;
    if (!hearingId || isDesktop()) return undefined;
    const source = new EventSource(`/platform/moot/hearings/${hearingId}/events`);
    const onEvent = (event: string, payload: Record<string, unknown>) => {
      if (event === 'delta' && typeof payload.text === 'string') setLive(payload.text);
      if (event === 'await-human' && typeof payload.slotId === 'number') setHumanSlot(payload.slotId);
      if (event === 'slot-committed' && Array.isArray(payload.slots)) {
        setHearing(payload as unknown as HearingView);
        setLive('');
        setHumanSlot(null);
      }
      if (event === 'hearing-closed') {
        setHearing(null);
        setView('files');
      }
      if (event === 'error') reportFailure(new Error(typeof payload.msg === 'string' ? payload.msg : t('moot.speakFail')));
    };
    const listen = (event: string) => (message: MessageEvent<string>) => {
      try {
        onEvent(event, JSON.parse(message.data) as Record<string, unknown>);
      } catch {
        reportFailure(new Error(t('moot.speakFail')));
      }
    };
    const names = ['delta', 'await-human', 'slot-committed', 'hearing-closed', 'error'];
    const handlers = names.map((name) => {
      const handler = listen(name);
      source.addEventListener(name, handler);
      return { name, handler };
    });
    return () => {
      for (const item of handlers) source.removeEventListener(item.name, item.handler);
      source.close();
    };
  }, [hearing?.hearing.id, t]);

  async function createCase() {
    try {
      const created = await platformSend<CaseRow>('/platform/moot/cases', 'POST', {
        stance,
        summary,
        signTaskId: signTaskId ? Number(signTaskId) : null,
      });
      setActive(created);
      setFiles([]);
      setView('files');
      await refreshCases();
      reportSuccess(t('moot.created'));
    } catch (e) {
      reportFailure(e);
    }
  }

  async function upload(file: File) {
    if (!active) return;
    const body = new FormData();
    body.append('file', file);
    const res = await fetch(`/platform/moot/cases/${active.id}/files`, {
      method: 'POST',
      credentials: 'include',
      body,
    });
    const env = await res.json().catch(() => null) as { code?: number; msg?: string } | null;
    if (res.status === 402 || env?.code === 402) throw new EntitlementError(env?.msg);
    if (!res.ok || !env || env.code !== 0) throw new ApiError(env?.code ?? res.status, env?.msg || t('moot.speakFail'));
    setFiles(await platformGet<FileRow[]>(`/platform/moot/cases/${active.id}/files`));
    reportSuccess(t('moot.uploaded'));
  }

  async function openPreview(file: FileRow) {
    if (!active) return;
    try {
      const detail = await platformGet<{ fileName: string; text: string; contentBase64?: string }>(
        `/platform/moot/cases/${active.id}/files/${file.id}/content?raw=true`,
      );
      if (detail.contentBase64) {
        const bin = atob(detail.contentBase64);
        const bytes = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i += 1) bytes[i] = bin.charCodeAt(i);
        const url = URL.createObjectURL(new Blob([bytes]));
        window.open(url, '_blank');
      }
      setPreview({ title: detail.fileName, text: detail.text });
    } catch (e) {
      reportFailure(e);
    }
  }

  async function startHearing() {
    if (!active) return;
    try {
      const opened = await platformSend<HearingView>(`/platform/moot/cases/${active.id}/open`, 'POST', {
        humanRoles: selfSpeak ? ['OURS'] : [],
      });
      setHearing(opened);
      setHumanSlot(null);
      setActive({ ...active, status: 'HEARING', hearingId: opened.hearing.id });
      setView('court');
      await refreshCases();
      reportSuccess(t('moot.hearingOpened'));
    } catch (e) {
      reportFailure(e);
    }
  }

  async function nextSlot() {
    if (!hearing) return;
    setBusy(true);
    setLive('');
    try {
      const res = await fetch(`/platform/moot/hearings/${hearing.hearing.id}/step`, {
        method: 'POST',
        credentials: 'include',
        headers: { Accept: 'text/event-stream' },
      });
      if (res.status === 402) {
        const env = await res.json().catch(() => null) as { msg?: string } | null;
        throw new EntitlementError(env?.msg);
      }
      if (res.status === 409) {
        reportFailure(new Error(t('moot.busyOther')));
        return;
      }
      if (!res.ok) throw new ApiError(res.status, t('moot.speakFail'));
      await readSse(res, applyEvent);
      setHearing(await platformGet<HearingView>(`/platform/moot/hearings/${hearing.hearing.id}`));
    } catch (e) {
      reportFailure(e instanceof ApiError || e instanceof EntitlementError ? e : new Error(t('moot.slotFail')));
      setHearing(await platformGet<HearingView>(`/platform/moot/hearings/${hearing.hearing.id}`).catch(() => hearing));
    } finally {
      setBusy(false);
    }
  }

  async function sendHuman() {
    if (!hearing || humanSlot == null || !humanText.trim()) return;
    try {
      const data = await platformSend<HearingView>(`/platform/moot/hearings/${hearing.hearing.id}/turns/commit`, 'POST', {
        slotId: humanSlot,
        body: humanText.trim(),
        speaker: 'HUMAN',
      });
      setHumanText('');
      setHumanSlot(null);
      setHearing(data);
      reportSuccess(t('moot.submitted'));
    } catch (e) {
      reportFailure(e);
    }
  }

  async function sendInterject() {
    if (!hearing || !interjectText.trim()) return;
    try {
      const data = await platformSend<HearingView>(`/platform/moot/hearings/${hearing.hearing.id}/turns/interject`, 'POST', {
        body: interjectText.trim(),
      });
      setInterjectText('');
      setHearing(data);
      reportSuccess(t('moot.interjected'));
    } catch (e) {
      reportFailure(e);
    }
  }

  async function closeHearing() {
    if (!hearing) return;
    try {
      const closed = await platformSend<HearingView>(`/platform/moot/hearings/${hearing.hearing.id}/cleanup`, 'POST');
      setHearing(null);
      setView('files');
      await refreshCases();
      if (closed.failedSessions && closed.failedSessions.length > 0) {
        reportFailure(new Error(t('moot.deleteFailed', { keys: closed.failedSessions.join('、') })));
        return;
      }
      reportSuccess(t('moot.hearingClosed'));
    } catch (e) {
      reportFailure(e);
    }
  }

  const parsed = files.some((file) => file.parseStatus === 'PARSED');
  const currentSlot = hearing?.slots.find((slot) => slot.status === 'SPEAKING')
    ?? hearing?.slots.find((slot) => slot.status === 'PENDING');
  const currentSnippets = hearing && currentSlot
    ? hearing.retrievals.filter((row) => row.slotId === currentSlot.id)
    : [];
  const canInterject = hearing?.hearing.status === 'OPEN'
    && humanSlot == null
    && !hearing.slots.some((slot) => slot.status === 'SPEAKING')
    && hearing.utterances.length > 0;

  // 发言引用了检索片段里缺失的法条：原来在每条发言下内联红字提示，现在改走统一失败弹窗。
  // 签名去重（SSE 每次推送都会重渲染），同一批缺失只上报一次。
  const missingClausesReport = (hearing?.utterances ?? [])
    .map((item) => {
      const excerpts = (hearing?.retrievals ?? [])
        .filter((row) => row.slotId === item.slot_id)
        .map((row) => row.excerpt);
      return missingClauses(item.body, excerpts);
    })
    .filter((list) => list.length > 0)
    .map((list) => t('moot.missing', { list: list.join('、') }))
    .join('；');
  const notifiedMissingRef = useRef<string | null>(null);
  useEffect(() => {
    if (!missingClausesReport) {
      notifiedMissingRef.current = null;
      return;
    }
    if (notifiedMissingRef.current === missingClausesReport) return;
    notifiedMissingRef.current = missingClausesReport;
    reportFailure(missingClausesReport);
  }, [missingClausesReport]);

  return (
    <div className="flex h-full flex-col gap-3 overflow-hidden p-5">
      <LegalPageHeader title={t('moot.title')} actions={active ? <span className="text-meta text-muted-foreground">{t('moot.caseNo', { id: active.id })}</span> : undefined} />
      <p className="rounded-lg border border-border bg-card px-3 py-2 text-meta text-muted-foreground">{t('moot.banner')}</p>
      <div className="flex gap-2">
        <button type="button" className="rounded-full border border-border px-3 py-1 text-meta" onClick={() => setView('cases')}>{t('moot.tabCases')}</button>
        <button type="button" className="rounded-full border border-border px-3 py-1 text-meta disabled:opacity-40" disabled={!active} onClick={() => setView('files')}>{t('moot.tabFiles')}</button>
        <button type="button" className="rounded-full border border-border px-3 py-1 text-meta disabled:opacity-40" disabled={!hearing} onClick={() => setView('court')}>{t('moot.tabCourt')}</button>
      </div>

      {view === 'cases' && (
        <div className="grid min-h-0 flex-1 gap-3 md:grid-cols-[1fr_280px]">
          <div className="overflow-auto rounded-lg border border-border bg-card">
            <table className="w-full text-left text-sm">
              <thead><tr className="text-meta text-muted-foreground"><th className="p-2">{t('moot.colCase')}</th><th>{t('moot.colStance')}</th><th>{t('moot.colStatus')}</th><th /></tr></thead>
              <tbody>
                {cases.map((row) => (
                  <tr key={row.id} className="border-t border-border">
                    <td className="p-2">{row.id}</td>
                    <td>{row.stance === 'PLAINTIFF' ? t('moot.plaintiff') : t('moot.defendant')}</td>
                    <td>{row.status}</td>
                    <td><button type="button" className="rounded-md border border-border px-2 py-1" onClick={() => void loadHearing(row)}>{t('moot.open')}</button></td>
                  </tr>
                ))}
                {cases.length === 0 && <tr><td className="p-3 text-muted-foreground" colSpan={4}>{t('moot.emptyCases')}</td></tr>}
              </tbody>
            </table>
          </div>
          <form className="flex flex-col gap-2 rounded-lg border border-border bg-card p-3" onSubmit={(e) => { e.preventDefault(); void createCase(); }}>
            <label className="text-meta text-muted-foreground">{t('moot.signedOptional')}
              <select className="mt-1 w-full rounded-md border border-border bg-background px-2 py-1" value={signTaskId} onChange={(e) => setSignTaskId(e.target.value)}>
                <option value="">{t('moot.noLink')}</option>
                {tasks.map((task) => <option key={task.id} value={task.id}>{task.taskNo || task.title || task.id}</option>)}
              </select>
            </label>
            <label className="text-meta text-muted-foreground">{t('moot.stance')}
              <select className="mt-1 w-full rounded-md border border-border bg-background px-2 py-1" value={stance} onChange={(e) => setStance(e.target.value)}>
                <option value="PLAINTIFF">{t('moot.plaintiff')}</option>
                <option value="DEFENDANT">{t('moot.defendant')}</option>
              </select>
            </label>
            <label className="text-meta text-muted-foreground">{t('moot.summary')}
              <textarea className="mt-1 w-full rounded-md border border-border bg-background px-2 py-1" maxLength={1500} value={summary} onChange={(e) => setSummary(e.target.value)} />
            </label>
            <button type="submit" className="rounded-md bg-primary px-3 py-2 text-sm text-primary-foreground">{t('moot.create')}</button>
          </form>
        </div>
      )}

      {view === 'files' && active && (
        <div className="flex min-h-0 flex-1 flex-col gap-3">
          <div className="flex items-center gap-2">
            <label className="rounded-md border border-border px-3 py-1 text-sm">
              {t('moot.upload')}
              <input className="hidden" type="file" onChange={(e) => { const file = e.target.files?.[0]; if (file) void upload(file).catch((err: unknown) => reportFailure(err)); }} />
            </label>
            <span className="text-meta text-muted-foreground">{parsed ? t('moot.canOpen') : t('moot.cannotOpen')}</span>
            <label className="ml-auto flex items-center gap-2 text-meta text-muted-foreground">
              <input type="checkbox" checked={selfSpeak} onChange={(e) => setSelfSpeak(e.target.checked)} />
              {t('moot.selfSpeak')}
            </label>
            <button type="button" disabled={!parsed} className="rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-40" onClick={() => void startHearing()}>{t('moot.start')}</button>
          </div>
          <div className="overflow-auto rounded-lg border border-border bg-card">
            <table className="w-full text-left text-sm">
              <thead><tr className="text-meta text-muted-foreground"><th className="p-2">{t('moot.colFile')}</th><th>{t('moot.colParse')}</th><th>{t('moot.colChunks')}</th><th>{t('moot.colSize')}</th><th>{t('moot.colHash')}</th></tr></thead>
              <tbody>
                {files.map((file) => (
                  <tr key={file.id} className="cursor-pointer border-t border-border" onClick={() => void openPreview(file)}>
                    <td className="p-2">{file.fileName}</td>
                    <td>{parseLabel(file.parseStatus)}</td>
                    <td>{file.chunks}</td>
                    <td>{formatSize(file.sizeBytes)}</td>
                    <td className="font-mono text-meta">{file.sha256.slice(0, 8)}…</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {view === 'court' && hearing && (
        <div className="flex min-h-0 flex-1 flex-col gap-3">
          <div className="rounded-lg border border-border bg-card px-3 py-2 text-meta">
            <div>{currentSlot ? t('moot.floor', { role: roleLabel(currentSlot.role_code) }) : t('moot.floorIdle')}</div>
            <div className="text-muted-foreground">{t('moot.muted')}</div>
          </div>
          <div className="grid gap-2 md:grid-cols-3">
            <div className="rounded-lg border border-border bg-card p-2 text-meta">
              <div className="font-medium">{t('moot.ours')}</div>
              <div className="text-muted-foreground">{active?.stance === 'DEFENDANT' ? t('moot.defendant') : t('moot.plaintiff')}</div>
            </div>
            <div className="rounded-lg border border-border bg-card p-2 text-meta">
              <div className="font-medium">{t('moot.judge')}</div>
              <div className="text-muted-foreground">{t('moot.jurorA')} · {t('moot.jurorB')} · {t('moot.jurorNote')}</div>
              <div className="text-muted-foreground">{t('moot.clerk')}</div>
            </div>
            <div className="rounded-lg border border-border bg-card p-2 text-meta">
              <div className="font-medium">{t('moot.opponent')}</div>
              <div className="text-muted-foreground">{active?.stance === 'DEFENDANT' ? t('moot.plaintiff') : t('moot.defendant')}</div>
            </div>
          </div>
          <div className="flex flex-wrap gap-2">
            {hearing.slots.map((slot) => (
              <div key={slot.id} className={`rounded-md border px-2 py-1 text-meta ${slot.status === 'SPEAKING' ? 'border-primary bg-card' : 'border-border bg-card'}`}>
                <div>{slot.seq} {slot.plan_text}</div>
                <div className="text-muted-foreground">{roleLabel(slot.role_code)} · {slot.status}</div>
              </div>
            ))}
          </div>
          <div className="grid min-h-0 flex-1 gap-3 md:grid-cols-[1fr_280px]">
            <div className="overflow-auto rounded-lg border border-border bg-card p-3">
              {live ? <article className="mb-3 rounded-md border border-border p-3 text-sm">{live}</article> : null}
              {hearing.utterances.map((item) => (
                <article key={item.id} className="mb-3 rounded-md border border-border p-3">
                  <div className="mb-1 text-meta text-muted-foreground">{item.speaker === 'HUMAN' ? t('moot.humanSpeaker') : roleLabel(item.role_code)}</div>
                  <p>{item.body}</p>
                </article>
              ))}
            </div>
            <div className="overflow-auto rounded-lg border border-border bg-card p-3 text-sm">
              <h2 className="mb-2 font-semibold">{t('moot.summary')}</h2>
              <p className="mb-3 text-muted-foreground">{active?.summary}</p>
              <p className="mb-3 text-meta text-muted-foreground">{t('moot.stance')}：{active?.stance === 'DEFENDANT' ? t('moot.defendant') : t('moot.plaintiff')}</p>
              <h2 className="mb-2 font-semibold">{t('moot.snippets')}</h2>
              {currentSnippets.map((row, index) => (
                <p key={`${row.slotId}-${index}`} className="mb-2 border-l-2 border-border pl-2">
                  <span className="block text-meta text-muted-foreground">{row.fileName} · {row.seq}</span>
                  {row.excerpt.slice(0, 180)}
                </p>
              ))}
            </div>
          </div>
          {humanSlot != null && (
            <div className="flex gap-2">
              <input className="flex-1 rounded-md border border-border bg-background px-2 py-1" value={humanText} onChange={(e) => setHumanText(e.target.value)} placeholder={t('moot.humanPlaceholder')} />
              <button type="button" className="rounded-md bg-primary px-3 py-1 text-primary-foreground" onClick={() => void sendHuman()}>{t('moot.submitHuman')}</button>
            </div>
          )}
          {canInterject && (
            <div className="flex gap-2">
              <input className="flex-1 rounded-md border border-border bg-background px-2 py-1" value={interjectText} onChange={(e) => setInterjectText(e.target.value)} placeholder={t('moot.interjectPlaceholder')} />
              <button type="button" className="rounded-md border border-border px-3 py-1" onClick={() => void sendInterject()}>{t('moot.interject')}</button>
            </div>
          )}
          <div className="flex gap-2">
            <button type="button" disabled={busy || humanSlot != null} className="rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-40" onClick={() => void nextSlot()}>{t('moot.next')}</button>
            <button type="button" className="rounded-md border border-border px-3 py-1.5 text-sm" onClick={() => void closeHearing()}>{t('moot.close')}</button>
          </div>
        </div>
      )}

      {preview && (
        <div className="fixed inset-0 flex items-center justify-center bg-black/40 p-6" onClick={() => setPreview(null)}>
          <div className="max-h-[80vh] w-full max-w-xl overflow-auto rounded-lg border border-border bg-card p-4" onClick={(e) => e.stopPropagation()}>
            <h2 className="mb-2 font-semibold">{preview.title}</h2>
            <p className="whitespace-pre-wrap text-sm">{preview.text}</p>
          </div>
        </div>
      )}
    </div>
  );
}
