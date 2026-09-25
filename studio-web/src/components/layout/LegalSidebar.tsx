import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { Plus } from 'lucide-react';
import { cn } from '@/lib/utils';
import { moduleForPath, type SidebarEntry } from './nav-config';

type Counts = { menu: Record<string, number> };
type Account = {
  plan?: string;
  signQuota?: number;
  signUsed?: number;
  signRemaining?: number;
};

/**
 * Contextual left nav for the 智能法务 workbench. Config-driven from
 * nav-config.ts; sits transparently on the connected shell gradient
 * (white CTA + glass items + bottom glass quota card).
 */
export function LegalSidebar() {
  const nav = useNavigate();
  const location = useLocation();
  const module = moduleForPath(location.pathname);
  const [counts, setCounts] = useState<Counts | null>(null);
  const [account, setAccount] = useState<Account | null>(null);

  const isSigning = module?.viewDriven ?? false;

  useEffect(() => {
    if (!isSigning) return;
    void fetch('/platform/sign/tasks/inbox/counts', { credentials: 'include' })
      .then((r) => r.json())
      .then((env) => setCounts(env?.data ?? null))
      .catch(() => setCounts(null));
  }, [isSigning, location.pathname, location.search]);

  useEffect(() => {
    void fetch('/platform/account/overview', { credentials: 'include' })
      .then((r) => r.json())
      .then((env) => setAccount(env?.data ?? null))
      .catch(() => setAccount(null));
  }, []);

  if (!module) return null;

  const activeView = new URLSearchParams(location.search).get('view') ?? 'CREATED_BY_ME';
  const isDetail = /^\/signing\/\d+/.test(location.pathname);

  const entryActive = (e: SidebarEntry) => {
    if (module.viewDriven) return !isDetail && activeView === e.view;
    return location.pathname === module.route || location.pathname.startsWith(`${module.route}/`);
  };

  const clickEntry = (e: SidebarEntry) => {
    if (module.viewDriven && e.view) nav(`/signing?view=${e.view}`);
    else nav(module.route);
  };

  const quota = account?.signQuota ?? 0;
  const used = account?.signUsed ?? 0;
  const pct = quota > 0 ? Math.min(100, Math.round((used / quota) * 100)) : 0;

  return (
    <aside className="flex h-full w-[232px] flex-col overflow-hidden px-2.5 pb-3 pt-1">
      {/* Primary CTA (white button on gradient) */}
      <button
        type="button"
        onClick={() => nav(module.cta.to)}
        className="shell-cta mb-2.5 flex h-9 shrink-0 items-center justify-center gap-1.5 rounded-lg text-meta font-semibold"
      >
        <Plus className="h-4 w-4" strokeWidth={2} />
        {module.cta.label}
      </button>

      {/* Grouped nav */}
      <nav className="flex-1 overflow-y-auto overflow-x-hidden [scrollbar-width:thin]">
        {module.groups.map((group) => (
          <div key={group.title} className="mb-2">
            <p className="px-2.5 pb-1 pt-2 text-tiny font-medium tracking-wide text-white/55">
              {group.title}
            </p>
            {group.entries.map((e) => {
              const active = entryActive(e);
              const badgeNum = e.badge === 'pendingMe' ? (counts?.menu?.pendingMe ?? 0) : 0;
              const countNum = e.count === 'allSigning' ? (counts?.menu?.allSigning ?? 0) : 0;
              return (
                <button
                  key={e.key}
                  type="button"
                  onClick={() => clickEntry(e)}
                  className={cn(
                    'flex w-full items-center gap-2 rounded-lg px-2.5 py-1.5 text-left text-meta transition-colors',
                    active
                      ? 'shell-glass-strong font-semibold text-white'
                      : 'text-white/75 hover:bg-white/10 hover:text-white',
                  )}
                >
                  <span className="flex-1 overflow-hidden text-ellipsis whitespace-nowrap">{e.label}</span>
                  {badgeNum > 0 && (
                    <span className="flex h-[17px] min-w-[17px] items-center justify-center rounded-full bg-red-500 px-1 text-2xs text-white">
                      {badgeNum}
                    </span>
                  )}
                  {countNum > 0 && <span className="text-tiny text-white/60">{countNum}</span>}
                </button>
              );
            })}
          </div>
        ))}
      </nav>

      {/* Bottom glass quota card */}
      <div className="shell-card mt-2 shrink-0 rounded-xl p-2.5 text-white/85">
        <div className="mb-1.5 flex items-center justify-between text-tiny">
          <span>签署配额</span>
          <span className="text-white/60">
            {account ? `${used} / ${quota} 份` : '—'}
          </span>
        </div>
        <div className="h-1.5 overflow-hidden rounded-full bg-white/20">
          <div className="h-full rounded-full bg-white" style={{ width: `${pct}%` }} />
        </div>
      </div>
    </aside>
  );
}
