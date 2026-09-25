import type { LucideIcon } from 'lucide-react';
import {
  Building2,
  FileDiff,
  FileText,
  LayoutDashboard,
  Mic,
  Scale,
  ScrollText,
  Shield,
} from 'lucide-react';

/**
 * Single source of truth for the 智能法务 (legal) workbench IA.
 * Consumed by PlatformTabs (level-2 tabs) and LegalSidebar (left nav) so the
 * two can never drift. Order = usage priority (matches studio-frontend).
 */

export interface SidebarEntry {
  key: string;
  label: string;
  /** For view-driven modules (signing): the `?view=` value this entry selects. */
  view?: string;
  /** Show the pending-me red badge (signing inbox counts). */
  badge?: 'pendingMe';
  /** Show the all-signing count (signing inbox counts). */
  count?: 'allSigning';
}

export interface SidebarGroup {
  title: string;
  entries: SidebarEntry[];
}

export interface LegalModule {
  route: string;
  label: string;
  icon: LucideIcon;
  cta: { label: string; to: string };
  groups: SidebarGroup[];
  /** Entries navigate via `/signing?view=` instead of plain route. */
  viewDriven?: boolean;
  /** Hidden from the level-2 tab row (reachable via the 更多 menu). */
  overflow?: boolean;
}

export const LEGAL_MODULES: LegalModule[] = [
  {
    route: '/signing',
    label: '签署',
    icon: FileText,
    cta: { label: '创建任务', to: '/signing/new' },
    viewDriven: true,
    groups: [
      {
        title: '我的签署',
        entries: [
          { key: 'CREATED_BY_ME', label: '我创建的', view: 'CREATED_BY_ME' },
          { key: 'RECEIVED', label: '我收到的', view: 'RECEIVED' },
          { key: 'CC_TO_ME', label: '抄送我的', view: 'CC_TO_ME' },
          { key: 'PENDING_RECEIVE', label: '待我接收', view: 'PENDING_RECEIVE' },
          { key: 'PENDING_ME', label: '待我处理', view: 'PENDING_ME', badge: 'pendingMe' },
          { key: 'PENDING_OTHERS', label: '待他人处理', view: 'PENDING_OTHERS' },
          { key: 'EXPIRING_SOON', label: '即将截止', view: 'EXPIRING_SOON' },
          { key: 'COMPLETED', label: '签署完成', view: 'COMPLETED' },
        ],
      },
      {
        title: '记录 / 管理',
        entries: [
          { key: 'BATCH_SENT', label: '批量发送记录', view: 'BATCH_SENT' },
          { key: 'PROCESS_CENTER', label: '处理中心', view: 'PROCESS_CENTER' },
          { key: 'ALL_SIGNING', label: '全部签署', view: 'ALL_SIGNING', count: 'allSigning' },
        ],
      },
    ],
  },
  {
    route: '/moot',
    label: '模拟法庭',
    icon: Scale,
    cta: { label: '即将开发', to: '/moot' },
    groups: [{ title: '模拟法庭', entries: [{ key: 'moot', label: '演练工作台（即将开发）' }] }],
  },
  {
    route: '/overview',
    label: '工作台',
    icon: LayoutDashboard,
    cta: { label: '新建签署任务', to: '/signing/new' },
    groups: [{ title: '工作台', entries: [{ key: 'overview', label: '总览看板' }] }],
  },
  {
    route: '/templates',
    label: '模板',
    icon: ScrollText,
    cta: { label: '新建模板', to: '/templates' },
    groups: [{ title: '模板', entries: [{ key: 'templates', label: '模板库' }] }],
  },
  {
    route: '/evidence',
    label: '证据',
    icon: Shield,
    cta: { label: '上传存证', to: '/evidence' },
    groups: [{ title: '证据', entries: [{ key: 'evidence', label: '存证列表' }] }],
  },
  {
    route: '/company',
    label: '企业管理',
    icon: Building2,
    cta: { label: '邀请成员', to: '/company' },
    groups: [{ title: '企业管理', entries: [{ key: 'company', label: '成员与部门' }] }],
  },
  {
    route: '/voice',
    label: '语音转录',
    icon: Mic,
    cta: { label: '即将开发', to: '/voice' },
    overflow: true,
    groups: [{ title: '语音转录', entries: [{ key: 'voice', label: '转录会话（即将开发）' }] }],
  },
  {
    route: '/compare',
    label: '文档对比',
    icon: FileDiff,
    cta: { label: '新建对比', to: '/compare' },
    overflow: true,
    groups: [{ title: '文档对比', entries: [{ key: 'compare', label: '对比任务' }] }],
  },
];

/** Level-2 tabs shown inline; overflow modules go into the 更多 menu. */
export const LEGAL_TAB_MODULES = LEGAL_MODULES.filter((m) => !m.overflow);
export const LEGAL_OVERFLOW_MODULES = LEGAL_MODULES.filter((m) => m.overflow);

const LEGAL_PREFIXES = LEGAL_MODULES.map((m) => m.route);

export function isLegalSection(pathname: string): boolean {
  return LEGAL_PREFIXES.some((p) => pathname === p || pathname.startsWith(`${p}/`));
}

export function moduleForPath(pathname: string): LegalModule | undefined {
  return LEGAL_MODULES.find((m) => pathname === m.route || pathname.startsWith(`${m.route}/`));
}
