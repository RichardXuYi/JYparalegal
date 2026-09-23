/**
 * 法律业务枚举的中文映射与格式化工具——全站唯一来源。
 *
 * 此前 Overview / Signing / TaskDetail 各抄一份 STATUS_LABEL 并漂移
 * (DRAFT 草稿/创建中、EXPIRED 已过期/已逾期、REVOKED 已撤回/已撤销),
 * 部分页面还直接把英文枚举(VERIFIED / PARALLEL / PREP)渲染给用户。
 * 所有展示统一从这里取;未知值给中文兜底,绝不把枚举原文打到界面上。
 */

/** 取标签;命中返回中文,未命中(含空值)返回 fallback,不泄露枚举原文。 */
function label(map: Record<string, string>, value: string | null | undefined, fallback: string): string {
  if (!value) return fallback;
  return map[value] ?? fallback;
}

/* ============================ 签署任务状态 ============================ */

const SIGN_STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  CREATED: '已创建',
  FILLING: '填写中',
  FINALIZING: '定稿中',
  SIGNING: '签署中',
  COMPLETED: '已完成',
  EXPIRED: '已逾期',
  REVOKED: '已撤回',
  TERMINATED: '已终止',
  VOIDING: '作废中',
  VOIDED: '已作废',
  REJECTED: '已拒签',
};

export function signStatusLabel(status: string | null | undefined): string {
  return label(SIGN_STATUS_LABEL, status, '未知状态');
}

/** 状态语义色调。进行中=主色、完成=绿、临期/警告=琥珀、失败类=红、中性终态=灰。 */
export type StatusTone = 'progress' | 'success' | 'warn' | 'danger' | 'neutral';

const SIGN_STATUS_TONE: Record<string, StatusTone> = {
  CREATED: 'progress',
  FILLING: 'progress',
  FINALIZING: 'progress',
  SIGNING: 'progress',
  COMPLETED: 'success',
  EXPIRED: 'danger',
  REJECTED: 'danger',
  REVOKED: 'neutral',
  TERMINATED: 'neutral',
  VOIDING: 'neutral',
  VOIDED: 'neutral',
  DRAFT: 'neutral',
};

export function signStatusTone(status: string | null | undefined): StatusTone {
  if (!status) return 'neutral';
  return SIGN_STATUS_TONE[status] ?? 'neutral';
}

/**
 * 状态芯片样式:亮色 text-*-700、暗色 dark:text-*-400,面板底 bg-*-500/10。
 * 统一一份,避免各页 text-green-600/text-amber-500 在暗色卡片上对比不足。
 */
export const TONE_CHIP_CLASS: Record<StatusTone, string> = {
  progress: 'bg-primary/10 text-primary',
  success: 'bg-green-500/10 text-green-700 dark:text-green-400',
  warn: 'bg-amber-500/10 text-amber-700 dark:text-amber-400',
  danger: 'bg-red-500/10 text-red-700 dark:text-red-400',
  neutral: 'bg-muted text-muted-foreground',
};

export function signStatusChipClass(status: string | null | undefined): string {
  return TONE_CHIP_CLASS[signStatusTone(status)];
}

/* ============================ 参与方 ============================ */

const PARTY_STATUS_LABEL: Record<string, string> = {
  PENDING_FILL: '待填写',
  PENDING_FINALIZE: '待定稿',
  PENDING_SIGN: '待签署',
  SIGNED: '已签署',
  REJECTED: '已拒签',
  EXPIRED: '已过期',
};

export function partyStatusLabel(status: string | null | undefined): string {
  return label(PARTY_STATUS_LABEL, status, '未知状态');
}

const PARTY_ROLE_LABEL: Record<string, string> = {
  INITIATOR: '发起方',
  FILLER: '填写方',
  SIGNER: '签署方',
};

export function partyRoleLabel(role: string | null | undefined): string {
  return label(PARTY_ROLE_LABEL, role, '参与方');
}

/* ============================ 签署模式 / 定稿方式 ============================ */

const SIGN_MODE_LABEL: Record<string, string> = {
  SEQUENTIAL: '顺序签署',
  PARALLEL: '并行签署',
};

export function signModeLabel(mode: string | null | undefined): string {
  return label(SIGN_MODE_LABEL, mode, '—');
}

const FINALIZE_MODE_LABEL: Record<string, string> = {
  AUTO: '自动定稿',
  MANUAL: '手动定稿',
};

export function finalizeModeLabel(mode: string | null | undefined): string {
  return label(FINALIZE_MODE_LABEL, mode, '自动定稿');
}

/* ============================ 业务单据 / 账户 ============================ */

const DOC_SOURCE_LABEL: Record<string, string> = {
  UPLOAD: '上传',
  GENERATE: '生成',
  TEMPLATE: '模板',
};

export function docSourceLabel(source: string | null | undefined): string {
  return label(DOC_SOURCE_LABEL, source, '—');
}

const BILLING_RULE_LABEL: Record<string, string> = {
  PER_DOC: '按文档份数',
  PER_MONTH: '按月订阅',
  PER_TOKEN: '按用量计费',
};

export function billingRuleLabel(rule: string | null | undefined): string {
  return label(BILLING_RULE_LABEL, rule, '—');
}

const PLAN_LABEL: Record<string, string> = {
  FREE: '免费版',
  PRO: '专业版',
  ENTERPRISE: '企业版',
};

export function planLabel(plan: string | null | undefined): string {
  return label(PLAN_LABEL, plan, '当前套餐');
}

/* ============================ 证据 ============================ */

const EVIDENCE_STATUS_LABEL: Record<string, string> = {
  PENDING: '待核验',
  VERIFIED: '已核验',
  REJECTED: '已驳回',
};

export function evidenceStatusLabel(status: string | null | undefined): string {
  return label(EVIDENCE_STATUS_LABEL, status, '待核验');
}

const EVIDENCE_BIZTYPE_LABEL: Record<string, string> = {
  SIGN_TASK: '签署任务',
  MOOT_CASE: '庭审案件',
};

export function evidenceBizTypeLabel(bizType: string | null | undefined): string {
  return label(EVIDENCE_BIZTYPE_LABEL, bizType, '其它');
}

/* ============================ 模拟法庭 ============================ */

const MOOT_PHASE_LABEL: Record<string, string> = {
  PREP: '准备',
  OPENING: '开庭陈述',
  COURT_INVESTIGATION: '法庭调查',
  COURT_DEBATE: '法庭辩论',
  FINAL_STATEMENT: '最后陈述',
  JUDGMENT: '判决',
  CLOSED: '已结束',
};

export function mootPhaseLabel(phase: string | null | undefined): string {
  return label(MOOT_PHASE_LABEL, phase, '未知阶段');
}

const MOOT_CASE_TYPE_LABEL: Record<string, string> = {
  CIVIL: '民事',
  CRIMINAL: '刑事',
  ADMINISTRATIVE: '行政',
};

export function mootCaseTypeLabel(caseType: string | null | undefined): string {
  return label(MOOT_CASE_TYPE_LABEL, caseType, '未知类型');
}

const MOOT_ROLE_LABEL: Record<string, string> = {
  JUDGE: '法官',
  PLAINTIFF: '原告',
  DEFENDANT: '被告',
  PLAINTIFF_AGENT: '原告代理人',
  DEFENDANT_AGENT: '被告代理人',
};

export function mootRoleLabel(role: string | null | undefined): string {
  return label(MOOT_ROLE_LABEL, role, '参与角色');
}

const MOOT_SCORE_DIMENSION_LABEL: Record<string, string> = {
  'legal-basis': '法律依据',
  'fact-argument': '事实论证',
  'procedure': '程序规范',
  'expression': '表达逻辑',
};

export function mootScoreDimensionLabel(dimension: string | null | undefined): string {
  return label(MOOT_SCORE_DIMENSION_LABEL, dimension, '评分维度');
}

/* ============================ 时间格式化 ============================ */

/** ISO(含 T / 毫秒 / 时区)→ `YYYY-MM-DD HH:mm`;空值或非法值给中文兜底,绝不裸渲染 ISO。 */
export function fmtDateTime(value: string | null | undefined, fallback = '—'): string {
  if (!value) return fallback;
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return fallback;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}
