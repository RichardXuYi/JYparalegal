import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { api } from './api';

type DocRow = { id: number; fileName?: string; sizeBytes?: number };

const ACCEPT = '.doc,.docx,.wps,.pdf,.xls,.xlsx,.jpg,.jpeg,.bmp,.png,.rtf';

export default function TaskCompose() {
  const { id } = useParams();
  const nav = useNavigate();
  const [docs, setDocs] = useState<DocRow[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    void api<DocRow[]>(`/platform/sign/tasks/${id}/docs`)
      .then(setDocs)
      .catch((e: Error) => setError(e.message));
  }, [id]);
  useEffect(load, [load]);

  const upload = async (file: File) => {
    if (file.size > 50 * 1024 * 1024) {
      setError('单个文件不能超过 50MB');
      return;
    }
    setBusy(true);
    setError('');
    try {
      const contentBase64 = await fileToBase64(file);
      await api(`/platform/sign/tasks/${id}/docs`, {
        method: 'POST',
        body: JSON.stringify({ fileName: file.name, contentBase64 }),
      });
      load();
    } catch (e) {
      setError(e instanceof Error ? e.message : '上传失败');
    } finally {
      setBusy(false);
    }
  };

  const submit = async () => {
    setBusy(true);
    setError('');
    try {
      await api(`/platform/sign/tasks/${id}/submit`, { method: 'POST', body: '{}' });
      nav(`/signing/${id}`);
    } catch (e) {
      setError(e instanceof Error ? e.message : '提交失败');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <div className="flex items-center gap-3 border-b border-border px-5 py-3">
        <div className="flex-1">
          <div className="text-sm font-semibold">制作文件</div>
          <div className="text-xs text-muted-foreground">文件 {docs.length}/50 · 单文件不超过 50MB。控件拖放在下一档。</div>
        </div>
        <button className="rounded-md border border-border px-3 py-1.5 text-sm" onClick={() => nav(`/signing/${id}/setup`)}>上一步</button>
        <button className="rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50" disabled={busy} onClick={() => void submit()}>确认提交</button>
      </div>
      {error && <div className="mx-5 mt-3 rounded-md bg-red-500/10 px-3 py-2 text-sm text-red-600 dark:text-red-400">{error}</div>}
      <div className="flex flex-1 items-center justify-center p-8">
        <div className="w-full max-w-lg rounded-lg border border-dashed border-border bg-card p-8 text-center">
          <div className="text-sm text-muted-foreground">拖入或选择文件。支持 doc、docx、wps、pdf、xls、xlsx、jpg、jpeg、bmp、png、rtf。</div>
          <div className="mt-1 text-xs text-muted-foreground">推荐上传 PDF；其它格式将由 e签宝自动转换为 PDF，个别文件可能转换失败。</div>
          <label className="mt-4 inline-block cursor-pointer rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground">
            {busy ? '处理中…' : '从本地上传'}
            <input type="file" accept={ACCEPT} className="hidden" disabled={busy}
              onChange={(e) => {
                const file = e.target.files?.[0];
                e.target.value = '';
                if (file) void upload(file);
              }} />
          </label>
          <ul className="mt-6 space-y-2 text-left text-sm">
            {docs.map((d) => (
              <li key={d.id} className="flex justify-between rounded-md bg-muted/50 px-3 py-2">
                <span className="truncate">{d.fileName ?? `文件 #${d.id}`}</span>
                <span className="text-xs text-muted-foreground">{d.sizeBytes ? `${Math.ceil(d.sizeBytes / 1024)} KB` : ''}</span>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}

function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      const raw = String(reader.result ?? '');
      const comma = raw.indexOf(',');
      resolve(comma >= 0 ? raw.slice(comma + 1) : raw);
    };
    reader.onerror = () => reject(new Error('读取文件失败'));
    reader.readAsDataURL(file);
  });
}
