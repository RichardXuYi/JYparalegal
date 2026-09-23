import { NavLink } from 'react-router-dom';

/**
 * 平台顶部标签（V3 IA，docs/06-前端改造与IA-V3.md §1）。
 * Agent 对话(默认) | 总览 | 签署 | 模拟法庭。平台页带左侧栏由各自页面控制；此处仅顶部导航。
 */
const TABS = [
  { to: '/', label: 'Agent 对话', end: true },
  { to: '/overview', label: '总览', end: false },
  { to: '/signing', label: '签署', end: false },
  { to: '/moot', label: '模拟法庭', end: false },
  { to: '/templates', label: '模板', end: false },
  { to: '/evidence', label: '证据', end: false },
  { to: '/company', label: '企业管理', end: false },
];

export function PlatformTabs() {
  return (
    <nav className="flex items-center gap-1">
      {TABS.map((t) => (
        <NavLink
          key={t.to}
          to={t.to}
          end={t.end}
          className={({ isActive }) =>
            `whitespace-nowrap rounded-md px-3 py-1.5 text-sm transition-colors ${
              isActive ? 'bg-primary/15 font-semibold text-primary' : 'text-muted-foreground hover:bg-muted'
            }`
          }
        >
          {t.label}
        </NavLink>
      ))}
    </nav>
  );
}
