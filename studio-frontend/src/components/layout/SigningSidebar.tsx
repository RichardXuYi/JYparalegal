import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

type Counts = { menu: Record<string, number> };

const MENU_ITEMS = [
  { group: '我的签署' },
  { key: 'CREATED_BY_ME', label: '我创建的', view: 'CREATED_BY_ME' },
  { key: 'RECEIVED', label: '我收到的', view: 'RECEIVED' },
  { key: 'CC_TO_ME', label: '抄送我的', view: 'CC_TO_ME' },
  { key: 'PENDING_RECEIVE', label: '待我接收', view: 'PENDING_RECEIVE' },
  { key: 'PENDING_ME', label: '待我处理', view: 'PENDING_ME', badge: true },
  { key: 'PENDING_OTHERS', label: '待他人处理', view: 'PENDING_OTHERS' },
  { key: 'EXPIRING_SOON', label: '即将截止', view: 'EXPIRING_SOON' },
  { key: 'COMPLETED', label: '签署完成', view: 'COMPLETED' },
  { group: '记录 / 管理' },
  { key: 'BATCH_SENT', label: '批量发送记录', view: 'BATCH_SENT' },
  { key: 'PROCESS_CENTER', label: '处理中心', view: 'PROCESS_CENTER' },
  { key: 'ALL_SIGNING', label: '全部签署', view: 'ALL_SIGNING', count: true },
];

export function SigningSidebar() {
  const nav = useNavigate();
  const location = useLocation();
  const [counts, setCounts] = useState<Counts | null>(null);

  useEffect(() => {
    void fetch('/platform/sign/tasks/inbox/counts', { credentials: 'include' })
      .then((r) => r.json())
      .then((env) => setCounts(env?.data ?? null))
      .catch(() => setCounts(null));
  }, [location.pathname]);

  const activeView = new URLSearchParams(location.search).get('view') ?? 'CREATED_BY_ME';
  const isDetail = location.pathname.match(/^\/signing\/\d+/);

  const click = (view: string) => {
    nav(`/signing?view=${view}`);
  };

  return (
    <div className="flex h-full w-[220px] flex-col overflow-hidden border-r border-border bg-card">
      <div className="p-3">
        <button
          className="w-full rounded-md bg-primary px-3 py-2 text-sm font-semibold text-primary-foreground"
          onClick={() => nav('/signing/new')}
        >
          ＋ 创建任务
        </button>
      </div>
      <nav className="flex-1 overflow-y-auto px-2 pb-3">
        {MENU_ITEMS.map((item, i) => {
          if ('group' in item) {
            return (
              <div key={i} className="px-3 pb-1 pt-3 text-tiny font-medium tracking-wide text-muted-foreground">
                {item.group}
              </div>
            );
          }
          const m = item as { key: string; label: string; view: string; badge?: boolean; count?: boolean };
          const isActive = !isDetail && activeView === m.view;
          const badgeNum = m.badge ? (counts?.menu?.pendingMe ?? 0) : 0;
          const countNum = m.count ? (counts?.menu?.allSigning ?? 0) : 0;
          return (
            <button
              key={m.key}
              onClick={() => click(m.view)}
              className={`flex w-full items-center gap-2 rounded-md px-3 py-2 text-left text-meta transition-colors ${
                isActive
                  ? 'bg-primary/15 font-semibold text-primary'
                  : 'text-muted-foreground hover:bg-muted'
              }`}
            >
              <span className="flex-1">{m.label}</span>
              {badgeNum > 0 && (
                <span className="flex h-[17px] min-w-[17px] items-center justify-center rounded-full bg-red-500 px-1 text-2xs text-white">
                  {badgeNum}
                </span>
              )}
              {countNum > 0 && (
                <span className="text-tiny text-muted-foreground">{countNum}</span>
              )}
            </button>
          );
        })}
      </nav>
    </div>
  );
}
