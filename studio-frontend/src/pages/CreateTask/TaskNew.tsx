import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { api } from './api';

/** 打开创建器时先落一条草稿，再进入任务单。刷新本页会再开一条草稿。 */
export default function TaskNew() {
  const nav = useNavigate();
  const started = useRef(false);

  useEffect(() => {
    if (started.current) return;
    started.current = true;
    void api<{ id: number }>('/platform/sign/tasks', {
      method: 'POST',
      body: JSON.stringify({ title: '未命名任务' }),
    })
      .then((task) => nav(`/signing/${task.id}/setup`, { replace: true }))
      .catch((e: Error) => {
        toast.error(e.message || '创建任务失败');
        nav('/signing', { replace: true, state: { error: e.message } });
      });
  }, [nav]);

  return <div className="p-8 text-sm text-muted-foreground">正在创建草稿…</div>;
}
