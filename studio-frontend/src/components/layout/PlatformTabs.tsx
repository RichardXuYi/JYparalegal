import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { Briefcase, MessageSquare, Scale, LayoutGrid } from 'lucide-react';
import { cn } from '@/lib/utils';
import { LEGAL_TAB_MODULES, isLegalSection, MOOT_ROUTE } from './nav-config';

/**
 * 三级导航(玻璃态,坐在连通渐变壳上)。
 * 第一级:Agent | 智能法务 | 模拟法庭;智能法务展开后才是业务功能标签。
 * IA 单一事实源见 nav-config.ts。
 */
export function PlatformTabs() {
  const location = useLocation();
  const navigate = useNavigate();
  const legalOpen = isLegalSection(location.pathname);

  return (
    <div className="no-drag flex shrink-0 flex-col">
      {/* Level 1: Agent | 智能法务 | 模拟法庭 */}
      <nav aria-label="工作区" className="flex h-12 items-stretch gap-1 px-1">
        <NavLink
          to="/"
          end
          className={({ isActive }) =>
            cn(
              'relative flex items-center gap-1.5 rounded-t-lg px-3.5 text-meta text-white/75 transition-colors',
              'hover:bg-white/10 hover:text-white',
              isActive && 'bg-white/10 font-semibold text-white',
            )
          }
        >
          {({ isActive }) => (
            <>
              <MessageSquare className="h-4 w-4" strokeWidth={1.75} />
              <span>Agent</span>
              {isActive && <span className="absolute inset-x-3 bottom-0 h-[2.5px] rounded-t bg-white" />}
            </>
          )}
        </NavLink>
        <span className="my-3 w-px shrink-0 bg-white/25" aria-hidden="true" />
        <button
          type="button"
          aria-current={legalOpen ? 'page' : undefined}
          onClick={() => {
            if (!legalOpen) navigate('/signing');
          }}
          className={cn(
            'relative flex items-center gap-1.5 rounded-t-lg px-3.5 text-meta text-white/75 transition-colors',
            'hover:bg-white/10 hover:text-white',
            legalOpen && 'bg-white/10 font-semibold text-white',
          )}
        >
          <Briefcase className="h-4 w-4" strokeWidth={1.75} />
          <span>智能法务</span>
          {legalOpen && <span className="absolute inset-x-3 bottom-0 h-[2.5px] rounded-t bg-white" />}
        </button>
        <span className="my-3 w-px shrink-0 bg-white/25" aria-hidden="true" />
        <NavLink
          to={MOOT_ROUTE}
          className={({ isActive }) =>
            cn(
              'relative flex items-center gap-1.5 rounded-t-lg px-3.5 text-meta text-white/75 transition-colors',
              'hover:bg-white/10 hover:text-white',
              isActive && 'bg-white/10 font-semibold text-white',
            )
          }
        >
          {({ isActive }) => (
            <>
              <Scale className="h-4 w-4" strokeWidth={1.75} />
              <span>模拟法庭</span>
              {isActive && <span className="absolute inset-x-3 bottom-0 h-[2.5px] rounded-t bg-white" />}
            </>
          )}
        </NavLink>
      </nav>

      {/* Level 2: legal function tabs (only under 智能法务) */}
      {legalOpen && (
        <nav aria-label="智能法务" className="flex h-10 items-center gap-1 overflow-x-auto px-2 [scrollbar-width:none]">
          {LEGAL_TAB_MODULES.map((m) => {
            const Icon = m.icon;
            return (
              <NavLink
                key={m.route}
                to={m.route}
                className={({ isActive }) =>
                  cn(
                    'flex shrink-0 items-center gap-1.5 rounded-lg px-3 py-1.5 text-meta whitespace-nowrap transition-colors',
                    isActive
                      ? 'shell-glass-strong font-semibold text-white'
                      : 'text-white/70 hover:bg-white/10 hover:text-white',
                  )
                }
              >
                <Icon className="h-3.5 w-3.5" strokeWidth={1.75} />
                <span>{m.label}</span>
              </NavLink>
            );
          })}
          <button
            type="button"
            onClick={() => navigate('/features')}
            className="flex shrink-0 items-center gap-1 rounded-lg px-2.5 py-1.5 text-meta text-white/70 transition-colors hover:bg-white/10 hover:text-white"
          >
            <LayoutGrid className="h-3.5 w-3.5" strokeWidth={1.75} />
            <span>更多</span>
          </button>
        </nav>
      )}
    </div>
  );
}
