import { useCallback, useState } from 'react';
import { toast } from 'sonner';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';

type Session = { id: number; status: string; provider: string; startedAt: string };
type Seg = { speaker: string; content: string };

const SPEAKER_LABEL: Record<string, string> = { user: '用户', agent: '助手', judge: '法官' };
const SESSION_STATUS_LABEL: Record<string, string> = { ACTIVE: '会话进行中', STOPPED: '会话已结束', CLOSED: '会话已结束' };

function speakerLabel(speaker: string | null | undefined): string {
  if (!speaker) return '参与方';
  return SPEAKER_LABEL[speaker] ?? SPEAKER_LABEL[speaker.toLowerCase()] ?? '参与方';
}

/** 语音通话：会话生命周期 + 转写双轨留痕。 */
export default function Voice() {
  const [session, setSession] = useState<Session | null>(null);
  const [segs, setSegs] = useState<Seg[]>([]);
  const [stopOpen, setStopOpen] = useState(false);

  const load = useCallback((id: number) => {
    void fetch(`/platform/voice/sessions/${id}`, { credentials: 'include' }).then((r) => r.json())
      .then((e) => { setSession(e?.data?.session ?? null); setSegs(Array.isArray(e?.data?.segments) ? e.data.segments : []); })
      .catch(() => null);
  }, []);

  const start = async () => {
    try {
      const e = await fetch('/platform/voice/sessions', { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: '{}' }).then((r) => r.json());
      if (e?.data?.id) load(e.data.id);
      else toast.error(e?.msg ?? '会话创建失败');
    } catch { toast.error('网络错误,会话创建失败'); }
  };
  const say = async (speaker: string, content: string) => {
    if (!session) return;
    await fetch(`/platform/voice/sessions/${session.id}/transcript`, { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ speaker, content }) });
    load(session.id);
  };
  const stop = async () => {
    if (!session) return;
    try {
      await fetch(`/platform/voice/sessions/${session.id}/stop`, { method: 'POST', credentials: 'include' });
      toast.success('会话已结束');
      load(session.id);
    } catch { toast.error('网络错误,结束会话失败'); }
  };

  return (
    <div className="p-5">
      <h1 className="mb-4 text-lg font-semibold">语音通话</h1>
      <div className="rounded-lg border border-border bg-card p-4 text-sm">
        <div className="mb-3 flex flex-wrap gap-2">
          {!session && <button className="rounded-md bg-primary px-4 py-2 text-primary-foreground" onClick={() => void start()}>开始语音会话</button>}
          {session && session.status === 'ACTIVE' && (
            <>
              <button className="rounded-md bg-primary px-4 py-2 text-primary-foreground" onClick={() => void say('user', '请帮我审查第八条违约金条款')}>模拟用户发言</button>
              <button className="rounded-md border border-border px-4 py-2" onClick={() => void say('agent', '第八条违约金比例超过 30% 上限,建议调整')}>模拟助手回复</button>
              <button className="rounded-md border border-border px-4 py-2 text-red-500" onClick={() => setStopOpen(true)}>结束会话</button>
            </>
          )}
          {session && (
            <span className="ml-auto self-center text-xs text-muted-foreground">
              {SESSION_STATUS_LABEL[session.status] ?? '会话进行中'}
            </span>
          )}
        </div>
        <div className="space-y-1">
          {segs.map((s, i) => (
            <div key={i} className="rounded bg-muted/60 px-2 py-1 text-xs"><b>{speakerLabel(s.speaker)}</b>：{s.content}</div>
          ))}
          {segs.length === 0 && <div className="p-6 text-center text-muted-foreground">录音接通后在此显示转写</div>}
        </div>
        <p className="mt-1 text-xs text-muted-foreground">演示按钮仅用于体验流程,不产生真实通话录音。</p>
      </div>

      <ConfirmDialog
        open={stopOpen}
        title="结束语音会话"
        message="结束后将停止录音与转写,确认结束当前会话?"
        confirmLabel="结束会话"
        cancelLabel="取消"
        variant="destructive"
        onCancel={() => setStopOpen(false)}
        onConfirm={async () => { setStopOpen(false); await stop(); }}
      />
    </div>
  );
}
