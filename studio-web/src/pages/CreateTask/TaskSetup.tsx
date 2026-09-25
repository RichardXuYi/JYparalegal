import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog';
import { Switch } from '@/components/ui/switch';
import { EntitlementGate } from '@/components/legal/EntitlementGate';
import {
  EMPTY_REQUIREMENT, EntitlementError, type PartyDraft, type SignRequirement,
  api, defaultExpireLocal, emptyParty, toApiTime,
} from './api';

type Task = {
  id: number; title: string; status: string; signMode: string; finalizeMode: string;
  expireAt?: string | null; contractDueAt?: string | null; contractDueEndAt?: string | null;
  quotaSnapshot?: number | null; companyId?: number | null;
};
type PartyRow = PartyDraft & {
  signRequirementJson?: string | null;
};

const GUIDE = [
  ['填写任务单', '设置任务主题、截止时间,添加收件人并指定填写 / 签署权限'],
  ['上传并提交', '进入制作台上传合同文件,确认无误后发送给收件人'],
];

/** 当前本地时间,用于 datetime-local 的 min(截止不可选过去)。 */
function nowLocal(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}

const inputCls = 'mt-1 h-9 w-full rounded-md border border-border bg-background px-3 text-sm';

const PHONE_RE = /^1[3-9]\d{9}$/;
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/**
 * 提交前逐参与方校验，与后端 buildParty + e签宝硬约束对齐：
 * 外部个人签署方手机号必须 11 位（联调中 10 位号被 e签宝拒），手机/邮箱至少其一。
 * requireComplete=false 用于「保存草稿」：只校验已填内容的格式，不强制齐全。
 */
function validateParties(ps: PartyDraft[], requireComplete: boolean): string | null {
  const rows = ps.filter((p) => p.externalName.trim() || p.externalPhone.trim() || p.externalEmail.trim());
  if (requireComplete && rows.length === 0) return '请至少添加一名收件人';
  for (const p of rows) {
    const name = p.externalName.trim();
    if (!name) return '请填写收件人名称';
    if (name.length > 64) return `「${name}」名称过长（最多 64 字）`;
    if (!p.canFill && !p.canSign) return `「${name}」需勾选填写或签署`;
    const phone = p.externalPhone.trim();
    if (phone && !PHONE_RE.test(phone)) return `「${name}」手机号格式不正确（需 11 位大陆手机号）`;
    const email = p.externalEmail.trim();
    if (email && !EMAIL_RE.test(email)) return `「${name}」邮箱格式不正确`;
    const isExternalPsn = p.partyType === 'PERSON' && !p.memberUserId.trim();
    if (requireComplete && isExternalPsn && !phone && !email) {
      return `「${name}」需填写手机号或邮箱（用于接收 e签宝签署链接）`;
    }
  }
  if (requireComplete && !rows.some((p) => p.canSign)) return '至少需要一名签署方';
  return null;
}

export default function TaskSetup() {
  const { id } = useParams();
  const nav = useNavigate();
  const [title, setTitle] = useState('');
  const [expireAt, setExpireAt] = useState(defaultExpireLocal);
  const [dueAt, setDueAt] = useState('');
  const [dueEnd, setDueEnd] = useState('');
  const [dueRange, setDueRange] = useState(false);
  const [sequential, setSequential] = useState(false);
  const [finalizeMode, setFinalizeMode] = useState<'AUTO' | 'MANUAL'>('AUTO');
  const [parties, setParties] = useState<PartyDraft[]>([emptyParty()]);
  const [quota, setQuota] = useState<number | null>(null);
  const [guide, setGuide] = useState(() => localStorage.getItem('jy.sign.createGuide') !== '1');
  const [guideStep, setGuideStep] = useState(0);
  const [reqIndex, setReqIndex] = useState<number | null>(null);
  const [error, setError] = useState('');
  const [locked, setLocked] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    void api<Task>(`/platform/sign/tasks/${id}`).then((t) => {
      if (t.status !== 'DRAFT') {
        nav(`/signing/${t.id}`, { replace: true });
        return;
      }
      setTitle(t.title === '未命名任务' ? '' : t.title);
      if (t.expireAt) setExpireAt(t.expireAt.slice(0, 16));
      if (t.contractDueAt) setDueAt(t.contractDueAt.slice(0, 10));
      if (t.contractDueEndAt) {
        setDueEnd(t.contractDueEndAt.slice(0, 10));
        setDueRange(true);
      }
      setSequential(t.signMode === 'SEQUENTIAL');
      setFinalizeMode(t.finalizeMode === 'MANUAL' ? 'MANUAL' : 'AUTO');
      if (t.quotaSnapshot != null) setQuota(t.quotaSnapshot);
    }).catch((e: unknown) => {
      if (e instanceof EntitlementError) setLocked(true);
      else setError(e instanceof Error ? e.message : '加载失败');
    });
    void api<PartyRow[]>(`/platform/sign/tasks/${id}/parties`).then((rows) => {
      if (rows.length === 0) return;
      setParties(rows.map((p) => ({
        partyType: p.partyType === 'PERSON' ? 'PERSON' : 'ORG',
        externalName: p.externalName ?? '',
        externalPhone: p.externalPhone ?? '',
        externalEmail: p.externalEmail ?? '',
        memberUserId: p.memberUserId ? String(p.memberUserId) : '',
        canFill: Boolean(p.canFill),
        canSign: p.canSign !== false,
        identityCheck: Boolean(p.identityCheck),
        signRequirement: parseRequirement(p.signRequirementJson),
      })));
    }).catch(() => undefined);
    void api<{ signRemaining?: number }>('/platform/account/overview')
      .then((o) => { if (typeof o.signRemaining === 'number') setQuota(o.signRemaining); })
      .catch(() => setQuota(null));
  }, [id, nav]);

  const save = async () => {
    const verr = validateParties(parties, false);
    if (verr) { setError(verr); throw new Error(verr); }
    setBusy(true);
    setError('');
    try {
      await api(`/platform/sign/tasks/${id}`, {
        method: 'PATCH',
        body: JSON.stringify({
          title: title.trim(),
          expireAt: toApiTime(expireAt),
          // 合同到期日用日期选择器(无时分);提交时补成当日边界,保持后端 datetime 契约
          contractDueAt: toApiTime(dueAt.length === 10 ? `${dueAt}T00:00` : dueAt),
          contractDueEndAt: dueRange ? toApiTime(dueEnd.length === 10 ? `${dueEnd}T23:59` : dueEnd) : null,
          signMode: sequential ? 'SEQUENTIAL' : 'PARALLEL',
          finalizeMode,
        }),
      });
      await api(`/platform/sign/tasks/${id}/parties`, {
        method: 'PUT',
        body: JSON.stringify({
          parties: parties.filter((p) => p.externalName.trim()).map((p) => ({
            ...p,
            memberUserId: p.memberUserId.trim() || null,
            externalPhone: p.externalPhone.trim() || null,
            externalEmail: p.externalEmail.trim() || null,
          })),
        }),
      });
    } catch (e) {
      if (e instanceof EntitlementError) setLocked(true);
      else setError(e instanceof Error ? e.message : '保存失败');
      throw e;
    } finally {
      setBusy(false);
    }
  };

  const next = async () => {
    setSubmitted(true);
    setError('');
    if (!title.trim()) { setError('请填写任务主题'); return; }
    const verr = validateParties(parties, true);
    if (verr) { setError(verr); return; }
    try {
      await save();
      nav(`/signing/${id}/compose`);
    } catch {
      /* save already set error */
    }
  };

  const editing = reqIndex != null ? parties[reqIndex] : null;
  const titleError = submitted && !title.trim();
  const minTime = nowLocal();

  if (locked) {
    return (
      <div className="flex h-full items-center justify-center p-8">
        <EntitlementGate />
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <div className="flex items-center gap-3 border-b border-border px-5 py-3">
        <button className="text-sm text-muted-foreground" onClick={() => nav('/signing')}>关闭</button>
        <div className="flex-1">
          <div className="text-sm font-semibold">创建任务</div>
          <div className="text-xs text-muted-foreground">
            剩余签署份数 {quota == null ? '暂不可用' : quota}
          </div>
        </div>
        <button className="rounded-md border border-border px-3 py-1.5 text-sm" disabled={busy} onClick={() => void save().catch(() => undefined)}>保存</button>
        <button className="rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground disabled:opacity-50" disabled={busy} onClick={() => void next()}>下一步</button>
      </div>
      {error && <div className="mx-5 mt-3 rounded-md bg-red-500/10 px-3 py-2 text-sm text-red-600 dark:text-red-400">{error}</div>}
      <div className="flex-1 overflow-y-auto px-5 py-4">
        <section className="mb-4 rounded-lg border border-border bg-card p-4">
          <h2 className="mb-3 text-sm font-semibold">基本信息</h2>
          <label className="mb-3 block text-sm">
            <span>任务主题 <span className="text-red-500">*</span></span>
            <input className={`${inputCls} ${titleError ? 'border-red-500' : ''}`} maxLength={50}
              placeholder="请输入任务主题" value={title} onChange={(e) => setTitle(e.target.value)} />
            {titleError && <span className="mt-1 block text-xs text-red-500">任务主题不能为空</span>}
          </label>
          <div className="grid gap-3 md:grid-cols-2">
            <label className="block text-sm">
              <span>签署截止时间 <span className="text-red-500">*</span></span>
              <input type="datetime-local" min={minTime} className={inputCls}
                value={expireAt} onChange={(e) => setExpireAt(e.target.value)} />
            </label>
            <div className="block text-sm">
              <span>合同到期日</span>
              <input type="date" className={inputCls}
                value={dueAt} onChange={(e) => setDueAt(e.target.value)} />
              <span className="mt-1.5 flex items-center gap-2 text-xs text-muted-foreground">
                <input type="checkbox" checked={dueRange} onChange={(e) => setDueRange(e.target.checked)} />
                是一个时间段(有结束日期)
              </span>
              {dueRange && (
                <>
                  <span className="mt-1 block text-xs text-muted-foreground">结束日期</span>
                  <input type="date" min={dueAt || undefined} className={inputCls}
                    value={dueEnd} onChange={(e) => setDueEnd(e.target.value)} />
                </>
              )}
            </div>
          </div>
        </section>

        <section className="mb-4 rounded-lg border border-border bg-card p-4">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-semibold">收件人 <span className="text-red-500">*</span></h2>
            <label className="flex items-center gap-2 text-sm">
              <Switch checked={sequential} onCheckedChange={setSequential} />
              顺序签署
            </label>
          </div>
          <div className="space-y-3">
            {parties.map((p, i) => (
              <div key={i} className="rounded-md border border-border p-3">
                <div className="mb-2 flex items-center justify-between">
                  <span className="text-xs font-medium text-muted-foreground">收件人 {i + 1}</span>
                  {parties.length > 1 && (
                    <button className="text-xs text-red-500 hover:underline" onClick={() => setParties(parties.filter((_, j) => j !== i))}>删除</button>
                  )}
                </div>
                <div className="grid gap-3 md:grid-cols-2">
                  <label className="block text-sm">
                    <span className="text-xs text-muted-foreground">类型</span>
                    <select className={inputCls} value={p.partyType}
                      onChange={(e) => update(i, { partyType: e.target.value as 'ORG' | 'PERSON' })}>
                      <option value="ORG">企业</option>
                      <option value="PERSON">个人</option>
                    </select>
                  </label>
                  <label className="block text-sm">
                    <span className="text-xs text-muted-foreground">名称 <span className="text-red-500">*</span></span>
                    <input className={inputCls} placeholder={p.partyType === 'ORG' ? '企业全称' : '个人姓名'}
                      value={p.externalName} onChange={(e) => update(i, { externalName: e.target.value })} />
                  </label>
                  <label className="block text-sm">
                    <span className="text-xs text-muted-foreground">手机号</span>
                    <input className={inputCls} placeholder="用于接收签署通知"
                      value={p.externalPhone} onChange={(e) => update(i, { externalPhone: e.target.value })} />
                  </label>
                  <label className="block text-sm">
                    <span className="text-xs text-muted-foreground">邮箱</span>
                    <input className={inputCls} placeholder="用于接收签署通知"
                      value={p.externalEmail} onChange={(e) => update(i, { externalEmail: e.target.value })} />
                  </label>
                </div>
                {/* 成员指定:后端成员搜索接口就绪前不暴露裸用户 ID 输入 */}
                <div className="mt-3 flex flex-wrap items-center gap-x-5 gap-y-2 border-t border-border pt-3">
                  <span className="flex items-center gap-2 text-sm">
                    <Switch checked={p.canFill} onCheckedChange={(v) => update(i, { canFill: v })} />填写
                  </span>
                  <span className="flex items-center gap-2 text-sm">
                    <Switch checked={p.canSign} onCheckedChange={(v) => update(i, { canSign: v })} />签署
                  </span>
                  <span className="flex items-center gap-2 text-sm" title="该配置即将支持，当前不会生效">
                    <Switch checked={p.identityCheck} disabled onCheckedChange={(v) => update(i, { identityCheck: v })} />身份校验
                    <span className="text-tiny text-muted-foreground">（即将支持）</span>
                  </span>
                  <button className="ml-auto rounded-md border border-border px-3 py-1.5 text-sm hover:bg-muted" onClick={() => setReqIndex(i)}>签署要求</button>
                </div>
              </div>
            ))}
          </div>
          <div className="mt-3 flex gap-2">
            <button className="rounded-md border border-border px-3 py-1.5 text-sm" onClick={() => setParties([...parties, { ...emptyParty(), partyType: 'PERSON' }])}>添加个人</button>
            <button className="rounded-md border border-border px-3 py-1.5 text-sm" onClick={() => setParties([...parties, emptyParty()])}>添加企业</button>
          </div>
          <p className="mt-2 text-xs leading-5 text-muted-foreground">
            签署人姓名需与其 e签宝实名信息一致；个人手机号需为 11 位大陆号码。外部签署人（非本站成员）通过 e签宝短信/邮件链接完成签署，手机号与邮箱至少填写一项。
          </p>
        </section>

        <section className="mb-4 rounded-lg border border-border bg-card p-4">
          <h2 className="mb-3 text-sm font-semibold">定稿方式</h2>
          <div className="flex gap-4 text-sm">
            <label className="flex items-center gap-2"><input type="radio" checked={finalizeMode === 'AUTO'} onChange={() => setFinalizeMode('AUTO')} />自动定稿</label>
            <label className="flex items-center gap-2"><input type="radio" checked={finalizeMode === 'MANUAL'} onChange={() => setFinalizeMode('MANUAL')} />手动定稿</label>
          </div>
          <p className="mt-2 text-xs text-muted-foreground">发起审批与定稿审批仍只保留入口，本档不跑审批流。</p>
        </section>
        <p className="text-xs leading-5 text-muted-foreground">
          电子签名不适用于婚姻、收养、监护等人身关系文书，停止供水、供电、供气等公共服务文书，以及行政法规规定的其他情形。
        </p>
      </div>

      {/* 创建引导:两步(填任务单 → 上传提交),Radix Dialog 提供焦点陷阱 / Esc / aria */}
      <Dialog open={guide} onOpenChange={(o) => { if (!o) { localStorage.setItem('jy.sign.createGuide', '1'); setGuide(false); } }}>
        <DialogContent className="w-[calc(100%-2rem)] max-w-md rounded-2xl border bg-white p-6 shadow-xl dark:bg-card">
          <div className="mb-3 flex gap-1">
            {GUIDE.map((_, i) => (
              <button key={i} aria-label={`第 ${i + 1} 步`} className={`h-1 flex-1 rounded ${i === guideStep ? 'bg-primary' : 'bg-muted'}`} onClick={() => setGuideStep(i)} />
            ))}
          </div>
          <DialogTitle className="text-lg font-semibold">{guideStep + 1}. {GUIDE[guideStep][0]}</DialogTitle>
          <DialogDescription className="mt-2 text-sm text-muted-foreground">{GUIDE[guideStep][1]}</DialogDescription>
          <div className="mt-6 flex justify-end gap-2">
            <button className="rounded-md border border-border px-3 py-1.5 text-sm" onClick={() => { localStorage.setItem('jy.sign.createGuide', '1'); setGuide(false); }}>关闭</button>
            {guideStep < GUIDE.length - 1
              ? <button className="rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground" onClick={() => setGuideStep(guideStep + 1)}>下一步</button>
              : <button className="rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground" onClick={() => { localStorage.setItem('jy.sign.createGuide', '1'); setGuide(false); }}>知道了</button>}
          </div>
        </DialogContent>
      </Dialog>

      {editing && reqIndex != null && (
        <RequirementDialog
          value={editing.signRequirement ?? EMPTY_REQUIREMENT}
          onCancel={() => setReqIndex(null)}
          onOk={(value) => {
            update(reqIndex, { signRequirement: value });
            setReqIndex(null);
          }}
        />
      )}
    </div>
  );

  function update(index: number, patch: Partial<PartyDraft>) {
    setParties((prev) => prev.map((p, i) => (i === index ? { ...p, ...patch } : p)));
  }
}

function parseRequirement(raw?: string | null): SignRequirement | null {
  if (!raw) return null;
  try {
    return JSON.parse(raw) as SignRequirement;
  } catch {
    return null;
  }
}

function RequirementDialog({ value, onOk, onCancel }: {
  value: SignRequirement;
  onOk: (v: SignRequirement) => void;
  onCancel: () => void;
}) {
  const [draft, setDraft] = useState(value);
  const toggleWill = (code: SignRequirement['wills'][number]) => {
    setDraft({
      ...draft,
      wills: draft.wills.includes(code) ? draft.wills.filter((w) => w !== code) : [...draft.wills, code],
    });
  };
  return (
    <Dialog open onOpenChange={(o) => { if (!o) onCancel(); }}>
      <DialogContent className="w-[calc(100%-2rem)] max-w-md rounded-2xl border bg-white p-6 shadow-xl dark:bg-card">
        <DialogTitle className="text-lg font-semibold">设置签署要求</DialogTitle>
        <DialogDescription className="sr-only">配置签名方式、签署意愿校验与阅读要求</DialogDescription>
        {/* 后端尚未把 signRequirement 映射到 e签宝(见联调方案附录 D),置灰避免用户误以为已生效 */}
        <div className="mt-3 rounded-md bg-amber-500/10 px-3 py-2 text-xs text-amber-700 dark:text-amber-400">
          以下配置即将支持，当前不会生效
        </div>
        <div className="pointer-events-none mt-3 opacity-50">
        <div className="mb-3 text-sm">
          签名方式
          <div className="mt-1 flex flex-wrap gap-3">
            {([['UNLIMITED', '不限制'], ['STANDARD', '标准签名'], ['HANDWRITE', '手绘签名'], ['AI_HANDWRITE', 'AI手绘签名']] as const).map(([code, label]) => (
              <label key={code} className="flex items-center gap-1">
                <input type="radio" disabled checked={draft.signatureMode === code} onChange={() => setDraft({ ...draft, signatureMode: code })} />{label}
              </label>
            ))}
          </div>
        </div>
        <div className="mb-3 text-sm">
          签署意愿方式
          <div className="mt-1 flex flex-wrap gap-3">
            {([['PASSWORD', '签署密码'], ['SMS', '短信验证'], ['FACE', '人脸识别']] as const).map(([code, label]) => (
              <label key={code} className="flex items-center gap-1">
                <input type="checkbox" disabled checked={draft.wills.includes(code)} onChange={() => toggleWill(code)} />{label}
              </label>
            ))}
          </div>
        </div>
        <label className="mb-2 flex items-center gap-2 text-sm">
          <input type="checkbox" disabled checked={draft.readToEnd} onChange={(e) => setDraft({ ...draft, readToEnd: e.target.checked })} />
          所有文件需阅读至末页才可提交签署
        </label>
        <label className="mb-2 flex items-center gap-2 text-sm">
          <input type="checkbox" disabled checked={draft.readSeconds != null} onChange={(e) => setDraft({ ...draft, readSeconds: e.target.checked ? 5 : null })} />
          阅读
          <input type="number" min={1} max={3600} className="h-8 w-16 rounded-md border border-border px-2"
            disabled value={draft.readSeconds ?? 5}
            onChange={(e) => setDraft({ ...draft, readSeconds: Number(e.target.value) })} />
          秒后才可提交签署
        </label>
        <label className="mb-4 flex items-center gap-2 text-sm">
          <input type="checkbox" disabled checked={draft.requireAttachment} onChange={(e) => setDraft({ ...draft, requireAttachment: e.target.checked })} />
          要求上传附件
        </label>
        </div>
        <div className="flex justify-end gap-2">
          <button className="rounded-md border border-border px-3 py-1.5 text-sm" onClick={onCancel}>取消</button>
          <button className="rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground" onClick={() => onOk(draft)}>确定</button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
