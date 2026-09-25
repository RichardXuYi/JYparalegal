import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'sonner';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog';
import { Textarea } from '@/components/ui/textarea';
import { EntitlementGate } from '@/components/legal/EntitlementGate';
import { EntitlementError, platformGet, platformSend, notifyIfEntitlement } from '@/lib/platform-api';
import {
  docSourceLabel,
  finalizeModeLabel,
  fmtDateTime,
  maskEmail,
  maskPhone,
  partyRoleLabel,
  partyStatusLabel,
  signModeLabel,
  signStatusChipClass,
  signStatusLabel,
} from '@/lib/legal-enums';

type SignTask = {
  id: number; taskNo: string; title: string; status: string; signMode: string;
  createdBy: number; createdAt: string; expireAt?: string; finalizeMode?: string; certAuthority?: string;
  providerFlowId?: string | null;
};
type Party = {
  id: number; userId: number | null; memberUserId?: number | null;
  partyRole: string; partyStatus: string; signOrder: number;
  externalName: string | null;
  externalPhone?: string | null;
  externalEmail?: string | null;
  signedAt: string | null; canFill?: boolean; canSign?: boolean;
};
type Invite = { id: number; taskId: number; partyId: number; inviteStatus: string };
type BizDoc = { id: number; docName?: string; fileName?: string; sha256?: string | null; source?: string | null; createdAt?: string };
type Approval = { id?: number; approvalNo?: string; status?: string; createdAt?: string; approverName?: string };

/** 内部签署方=绑定了 studio 用户，可在本站「去签署」；外部签署方走 e签宝 短信/邮件链接。 */
const isInternal = (p: Party) => p.userId != null || p.memberUserId != null;

/** Date → `<input type="datetime-local">` 的本地值(YYYY-MM-DDTHH:mm)。 */
function toLocalInput(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** datetime-local 值 → backend LocalDateTime 契约串(补秒;ISO 的 Z 后缀 backend 不识别)。 */
function toApiTime(local: string): string {
  return local.length === 16 ? `${local}:00` : local;
}

export default function TaskDetail() {
  const { id } = useParams();
  const [task, setTask] = useState<SignTask | null>(null);
  const [parties, setParties] = useState<Party[]>([]);
  const [invites, setInvites] = useState<Invite[]>([]);
  const [me, setMe] = useState<number | null>(null);
  const [tab, setTab] = useState<'detail' | 'biz'>('detail');
  const [bizdocs, setBizdocs] = useState<{ docs: BizDoc[]; contractDocs: BizDoc[]; approvals: Approval[] } | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [locked, setLocked] = useState(false);
  const nav = useNavigate();

  // 危险操作弹窗状态
  const [rejectOpen, setRejectOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [extendOpen, setExtendOpen] = useState(false);
  const [extendValue, setExtendValue] = useState('');
  const [revokeOpen, setRevokeOpen] = useState(false);
  const [voidOpen, setVoidOpen] = useState(false);

  const load = useCallback(() => {
    void platformGet<SignTask>(`/platform/sign/tasks/${id}`)
      .then((t) => { setTask(t ?? null); setLoadError(false); setLocked(false); })
      .catch((e) => {
        if (e instanceof EntitlementError) { setTask(null); setLoadError(false); setLocked(true); }
        else { setTask(null); setLoadError(true); }
      })
      .finally(() => setLoading(false));
    void platformGet<Party[]>(`/platform/sign/tasks/${id}/parties`)
      .then((d) => setParties(Array.isArray(d) ? d : [])).catch(() => setParties([]));
    void platformGet<Invite[]>('/platform/sign/invites')
      .then((d) => setInvites(Array.isArray(d) ? d : [])).catch(() => setInvites([]));
    void fetch('/auth/me', { credentials: 'include' }).then((r) => r.json())
      .then((m) => setMe(m?.user?.id ?? m?.id ?? null)).catch(() => setMe(null));
    void platformGet<{ docs: BizDoc[]; contractDocs: BizDoc[]; approvals: Approval[] }>(`/platform/sign/tasks/${id}/bizdocs`)
      .then((d) => setBizdocs(d ?? null)).catch(() => setBizdocs(null));
  }, [id]);
  useEffect(load, [load]);
  const reload = () => { setLoading(true); setLoadError(false); load(); };

  // 签署中静默轮询（完成态只来自 e签宝回调），页面隐藏时暂停
  useEffect(() => {
    if (task?.status !== 'SIGNING') return;
    const timer = setInterval(() => {
      if (document.visibilityState === 'visible') load();
    }, 10000);
    return () => clearInterval(timer);
  }, [task?.status, load]);

  /** 统一提交:成功轻提示并刷新,失败提示原因——不再 alert、不再 dump JSON。 */
  const post = async (path: string, body?: unknown, okMsg?: string): Promise<boolean> => {
    try {
      await platformSend(path, 'POST', body ?? {});
      if (okMsg) toast.success(okMsg);
      load();
      return true;
    } catch (e) {
      if (!notifyIfEntitlement(e)) toast.error(e instanceof Error ? e.message : '网络错误,操作失败');
      return false;
    }
  };

  const downloadContract = async () => {
    try {
      const d = await platformGet<Record<string, string | undefined>>(`/platform/sign/tasks/${id}/download`);
      const url = d?.fileUrl ?? d?.url ?? d?.downloadUrl;
      if (url) {
        window.open(url, '_blank', 'noopener');
        toast.success('已开始下载合同文件');
      } else {
        toast.error('下载失败,请稍后重试');
      }
    } catch (e) {
      if (!notifyIfEntitlement(e)) toast.error(e instanceof Error ? e.message : '网络错误,下载失败');
    }
  };

  const requestCertificate = async () => {
    try {
      await platformSend(`/platform/sign/tasks/${id}/certificate`, 'POST', {});
      toast.success('出证申请已提交');
    } catch (e) {
      if (!notifyIfEntitlement(e)) toast.error(e instanceof Error ? e.message : '网络错误,出证失败');
    }
  };

  const copyHash = async (sha: string) => {
    try {
      await navigator.clipboard.writeText(sha);
      toast.success('完整哈希已复制');
    } catch {
      toast.error('复制失败,请手动选择');
    }
  };

  const openExtend = () => {
    const base = task?.expireAt ? new Date(task.expireAt) : new Date();
    const start = Number.isNaN(base.getTime()) ? new Date() : base;
    start.setDate(start.getDate() + 7);
    setExtendValue(toLocalInput(start));
    setExtendOpen(true);
  };

  // 加载中 / 失败 / 404 三态分开,不再用 null 初始值闪「任务不存在」
  if (loading) return <div className="p-8 text-center text-sm text-muted-foreground">正在加载…</div>;
  if (locked) {
    return (
      <div className="flex h-full items-center justify-center p-8">
        <EntitlementGate onRetry={reload} />
      </div>
    );
  }
  if (loadError) {
    return (
      <div className="flex flex-col items-center gap-3 p-8 text-center text-sm">
        <span className="text-muted-foreground">任务加载失败</span>
        <button className="rounded-md border border-border px-3 py-1.5 text-sm hover:bg-muted" onClick={reload}>重试</button>
      </div>
    );
  }
  if (!task) return <div className="p-8 text-center text-sm text-muted-foreground">任务不存在或无权访问</div>;

  const myParty = parties.find((p) => p.userId === me);
  const myInvite = invites.find((i) => i.taskId === task.id);
  const canSign = myParty && myParty.partyStatus === 'PENDING_SIGN' && task.status === 'SIGNING';
  const isInitiator = task.createdBy === me;
  const canFinishFill = task.status === 'FILLING' && (isInitiator || (myParty?.canFill && myParty.partyStatus === 'PENDING_FILL'));
  const hasContractFile = (bizdocs?.contractDocs.length ?? 0) > 0 || (bizdocs?.docs.length ?? 0) > 0;

  const goSign = async () => {
    if (!myParty) return;
    try {
      const d = await platformSend<{ signUrl?: string; message?: string }>(
        `/platform/sign/tasks/${id}/parties/${myParty.id}/sign`, 'POST', {},
      );
      const url = d?.signUrl;
      if (url) {
        window.open(url, '_blank', 'noopener');
        toast.success('已打开 e签宝签署页。签完后回到本页刷新状态');
      } else {
        toast.message(d?.message ?? '尚未配置 e签宝应用，不能完成具有法律效力的签署');
      }
    } catch (e) {
      if (!notifyIfEntitlement(e)) toast.error(e instanceof Error ? e.message : '网络错误,无法打开签署页');
    }
  };

  return (
    <div className="flex h-full flex-col overflow-hidden">
      {/* Back link */}
      <div className="px-5 pt-3">
        <button className="text-meta text-muted-foreground hover:text-foreground" onClick={() => nav('/signing')}>‹ 返回签署列表</button>
      </div>

      <div className="flex-1 overflow-y-auto px-5 py-3">
        <div className="rounded-lg border border-border bg-card">
          {/* Top bar with title + actions */}
          <div className="flex flex-wrap items-center gap-2 border-b border-border px-4 py-3">
            <span className="flex-1 truncate font-semibold text-subtitle">{task.title}</span>
            {task.status === 'SIGNING' && (
              <button className="rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground hover:bg-muted" onClick={() => load()}>刷新状态</button>
            )}
            <button className="rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground hover:bg-muted" onClick={() => nav('/')}>去审查</button>
            {task.status === 'COMPLETED' && (
              <>
                <button className="rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground hover:bg-muted" onClick={() => void requestCertificate()}>
                  申请出证
                </button>
                <button className="rounded-md border border-border px-3 py-1.5 text-sm text-muted-foreground hover:bg-muted" onClick={() => void downloadContract()}>
                  下载
                </button>
              </>
            )}
            {isInitiator && task.status === 'EXPIRED' && (
              <button className="rounded-md border border-border px-3 py-1.5 text-sm" onClick={openExtend}>延期</button>
            )}
            {isInitiator && task.status === 'DRAFT' && (
              <button className="rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground" onClick={() => nav(`/signing/${id}/setup`)}>继续编辑</button>
            )}
            {isInitiator && ['CREATED', 'FILLING', 'FINALIZING', 'SIGNING'].includes(task.status) && (
              <button className="rounded-md border border-border px-3 py-1.5 text-sm text-red-500" onClick={() => setRevokeOpen(true)}>撤回</button>
            )}
          </div>

          {/* Two-column body: file preview + info panel */}
          <div className="grid grid-cols-1 lg:grid-cols-[1fr_340px] min-h-[500px]">
            {/* Left: real file or explicit empty state (no fabricated document) */}
            <div className="flex items-center justify-center border-border bg-muted/20 p-6 lg:border-r">
              {hasContractFile ? (
                <div className="w-full max-w-[500px] rounded-md border border-border bg-card p-8 text-center shadow-sm">
                  <p className="text-sm text-muted-foreground">合同文件已关联</p>
                  <button className="mt-4 rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground" onClick={() => void downloadContract()}>
                    下载并查看合同
                  </button>
                </div>
              ) : (
                <div className="w-full max-w-[500px] rounded-md border border-dashed border-border bg-card p-8 text-center shadow-sm">
                  <p className="text-sm font-medium">尚未上传合同文件</p>
                  <p className="mt-1 text-meta text-muted-foreground">上传后可在此预览合同正文</p>
                  {task.status === 'DRAFT' && isInitiator && (
                    <button className="mt-4 rounded-md border border-border px-4 py-2 text-sm hover:bg-muted" onClick={() => nav(`/signing/${id}/compose`)}>
                      去上传
                    </button>
                  )}
                </div>
              )}
            </div>

            {/* Right: tabs + info */}
            <div className="flex flex-col">
              <div className="flex border-b border-border">
                <button onClick={() => setTab('detail')}
                  className={`flex-1 py-2.5 text-meta ${tab === 'detail' ? 'border-b-2 border-primary font-semibold text-primary' : 'text-muted-foreground'}`}>
                  详情
                </button>
                <button onClick={() => setTab('biz')}
                  className={`flex-1 py-2.5 text-meta ${tab === 'biz' ? 'border-b-2 border-primary font-semibold text-primary' : 'text-muted-foreground'}`}>
                  业务单据
                </button>
              </div>

              {tab === 'detail' ? (
                <div className="flex-1 overflow-y-auto">
                  {/* Task info */}
                  <div className="p-4">
                    <h5 className="mb-2 text-xs font-semibold text-muted-foreground">任务信息</h5>
                    <div className="space-y-0 text-meta">
                      <div className="flex justify-between border-b border-dashed border-border py-1.5">
                        <span className="text-muted-foreground">状态</span>
                        <span className={`rounded px-2 py-0.5 text-xs ${signStatusChipClass(task.status)}`}>{signStatusLabel(task.status)}</span>
                      </div>
                      <div className="flex justify-between border-b border-dashed border-border py-1.5">
                        <span className="text-muted-foreground">任务编号</span><span className="font-mono text-xs">{task.taskNo}</span>
                      </div>
                      {task.providerFlowId && (
                        <div className="flex justify-between border-b border-dashed border-border py-1.5">
                          <span className="text-muted-foreground">签署流程号</span><span className="font-mono text-xs">{task.providerFlowId}</span>
                        </div>
                      )}
                      <div className="flex justify-between border-b border-dashed border-border py-1.5">
                        <span className="text-muted-foreground">发起时间</span><span>{fmtDateTime(task.createdAt)}</span>
                      </div>
                      <div className="flex justify-between border-b border-dashed border-border py-1.5">
                        <span className="text-muted-foreground">截止时间</span><span>{task.expireAt ? fmtDateTime(task.expireAt) : '长期有效'}</span>
                      </div>
                      <div className="flex justify-between border-b border-dashed border-border py-1.5">
                        <span className="text-muted-foreground">定稿方式</span><span>{finalizeModeLabel(task.finalizeMode)}</span>
                      </div>
                      <div className="flex justify-between border-b border-dashed border-border py-1.5">
                        <span className="text-muted-foreground">证书机构</span><span>{task.certAuthority ?? '不限制'}</span>
                      </div>
                      <div className="flex justify-between py-1.5">
                        <span className="text-muted-foreground">签署模式</span><span>{signModeLabel(task.signMode)}</span>
                      </div>
                    </div>
                  </div>

                  {/* Signing flow timeline */}
                  <div className="border-t border-border p-4">
                    <h5 className="mb-3 text-xs font-semibold text-muted-foreground">签署流程（{signModeLabel(task.signMode)}）</h5>
                    {parties.length === 0 && <div className="text-meta text-muted-foreground">暂无参与方</div>}
                    {parties.map((p, i) => (
                      <div key={p.id} className="relative flex gap-2.5 pb-3.5 last:pb-0">
                        <span className={`mt-1 h-[9px] w-[9px] shrink-0 rounded-full ${
                          p.partyStatus === 'SIGNED' ? 'bg-green-500' : p.partyStatus === 'REJECTED' ? 'bg-red-500' : 'bg-muted-foreground/40'
                        }`} />
                        {i < parties.length - 1 && <span className="absolute left-[3.5px] top-[13px] bottom-0 w-[2px] bg-border" />}
                        <div className="text-meta">
                          <div>
                            {p.externalName ?? '未命名参与方'}
                            <span className="ml-1.5 text-tiny text-muted-foreground">{partyRoleLabel(p.partyRole)}</span>
                            <span className={`ml-1.5 rounded px-1 text-tiny ${isInternal(p) ? 'bg-primary/10 text-primary' : 'bg-muted text-muted-foreground'}`}>
                              {isInternal(p) ? '内部' : '外部'}
                            </span>
                            {' · '}
                            {p.partyRole === 'FILLER' && p.partyStatus === 'SIGNED' ? '已填写' : partyStatusLabel(p.partyStatus)}
                          </div>
                          <div className="mt-0.5 text-tiny text-muted-foreground">
                            {isInternal(p)
                              ? (p.signedAt ? fmtDateTime(p.signedAt) : '待签署')
                              : (p.partyStatus === 'SIGNED' && p.signedAt
                                ? fmtDateTime(p.signedAt)
                                : `签署链接已发送至 ${maskPhone(p.externalPhone) || maskEmail(p.externalEmail) || '其手机/邮箱'}`)}
                          </div>
                        </div>
                      </div>
                    ))}
                    {task.status === 'COMPLETED' && (
                      <div className="relative flex gap-2.5 pt-1">
                        <span className="mt-1 h-[9px] w-[9px] shrink-0 rounded-full bg-muted-foreground/40" />
                        <div className="text-meta text-muted-foreground">
                          <div>生成签署证书 · 完成</div>
                          <div className="mt-0.5 text-tiny">可下载带证书 PDF</div>
                        </div>
                      </div>
                    )}
                  </div>

                  {/* Action buttons */}
                  <div className="border-t border-border p-4">
                    <div className="flex flex-wrap gap-2">
                      {canFinishFill && <button className="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground" onClick={() => void post(`/platform/sign/tasks/${id}/finish-fill`, undefined, '已完成填写')}>完成填写</button>}
                      {isInitiator && task.status === 'FINALIZING' && <button className="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground" onClick={() => void post(`/platform/sign/tasks/${id}/confirm-final`, undefined, '已确认定稿')}>确认定稿</button>}
                      {myInvite && <button className="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground" onClick={() => void post(`/platform/sign/invites/${myInvite.id}/accept`, undefined, '已接收邀请')}>接收邀请</button>}
                      {canSign && myParty && (
                        <>
                          <button className="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground" onClick={() => void goSign()}>去签署</button>
                          <button className="rounded-md border border-border px-4 py-2 text-sm text-red-500" onClick={() => { setRejectReason(''); setRejectOpen(true); }}>拒签</button>
                        </>
                      )}
                      {isInitiator && task.status === 'SIGNING' && !canSign && (
                        <div className="flex items-center gap-2.5 text-meta text-muted-foreground">
                          <span>
                            等待 {parties.filter((p) => p.partyStatus === 'PENDING_SIGN').length} 位签署方完成，签好后自动更新
                          </span>
                          <button className="rounded-md border border-border px-3 py-1.5 text-sm hover:bg-muted" onClick={() => load()}>刷新</button>
                        </div>
                      )}
                      {isInitiator && task.status === 'COMPLETED' && (
                        <button className="rounded-md border border-border px-4 py-2 text-sm" onClick={() => setVoidOpen(true)}>申请解约</button>
                      )}
                    </div>
                  </div>
                </div>
              ) : (
                <div className="flex-1 overflow-y-auto p-4">
                  <h5 className="mb-2 text-xs font-semibold text-muted-foreground">业务单据</h5>
                  {(!bizdocs || (bizdocs.docs.length === 0 && bizdocs.contractDocs.length === 0 && bizdocs.approvals.length === 0)) && (
                    <div className="rounded-lg border border-dashed border-border p-8 text-center text-meta text-muted-foreground">
                      暂无关联业务单据
                      <div className="mt-1 text-xs">上传合同文件后自动关联（签署文档 / 审批单）</div>
                    </div>
                  )}
                  {bizdocs && bizdocs.docs.length > 0 && (
                    <div className="mb-3">
                      <h6 className="mb-1.5 text-tiny font-semibold text-muted-foreground">签署文档（{bizdocs.docs.length}）</h6>
                      {bizdocs.docs.map((d) => (
                        <div key={d.id} className="flex items-center gap-2 rounded-md bg-muted/50 px-3 py-2 text-meta">
                          <span className="flex-1 truncate">{d.docName ?? d.fileName ?? '未命名文档'}</span>
                          {d.sha256 && (
                            <span className="group inline-flex items-center gap-1 font-mono text-2xs text-muted-foreground" title={d.sha256}>
                              {d.sha256.slice(0, 12)}…
                              <button className="rounded px-1 text-2xs text-primary opacity-0 transition-opacity hover:underline focus-visible:opacity-100 group-hover:opacity-100"
                                aria-label="复制完整 SHA-256" onClick={() => void copyHash(d.sha256 as string)}>复制</button>
                            </span>
                          )}
                          <span className="rounded bg-muted px-1.5 py-0.5 text-2xs">{docSourceLabel(d.source)}</span>
                        </div>
                      ))}
                    </div>
                  )}
                  {bizdocs && bizdocs.contractDocs.length > 0 && (
                    <div className="mb-3">
                      <h6 className="mb-1.5 text-tiny font-semibold text-muted-foreground">合同文档（{bizdocs.contractDocs.length}）</h6>
                      {bizdocs.contractDocs.map((d) => (
                        <div key={d.id} className="rounded-md bg-muted/50 px-3 py-2 text-meta">{d.docName ?? '未命名合同'}</div>
                      ))}
                    </div>
                  )}
                  {bizdocs && bizdocs.approvals.length > 0 && (
                    <div>
                      <h6 className="mb-1.5 text-tiny font-semibold text-muted-foreground">审批单（{bizdocs.approvals.length}）</h6>
                      {bizdocs.approvals.map((a, i) => (
                        <div key={a.id ?? i} className="mb-1.5 rounded-md bg-muted/50 px-3 py-2 text-meta">
                          <div className="flex justify-between">
                            <span className="font-mono text-xs">{a.approvalNo ?? '暂无审批单号'}</span>
                            <span className="text-muted-foreground">{a.status ? signStatusLabel(a.status) : '—'}</span>
                          </div>
                          <div className="mt-0.5 text-tiny text-muted-foreground">
                            {a.approverName ? `${a.approverName} · ` : ''}{a.createdAt ? fmtDateTime(a.createdAt) : '暂无时间'}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* 拒签:必须填写理由(理由进入留痕证据链,不可由系统代填) */}
      <Dialog open={rejectOpen} onOpenChange={(o) => { if (!o) setRejectOpen(false); }}>
        <DialogContent className="w-[calc(100%-2rem)] max-w-md rounded-2xl border bg-white p-6 shadow-xl dark:bg-card">
          <DialogTitle className="text-lg font-semibold">拒签</DialogTitle>
          <DialogDescription className="mt-2 text-sm text-muted-foreground">
            请填写拒签理由。该理由将作为签署留痕的一部分,不可为空。
          </DialogDescription>
          <Textarea
            className="mt-4 min-h-[96px]"
            placeholder="例如:第三条付款期限与约定不符,需修订后再签"
            value={rejectReason}
            onChange={(e) => setRejectReason(e.target.value)}
          />
          <div className="mt-6 flex justify-end gap-2">
            <button className="rounded-md border border-border px-4 py-2 text-sm hover:bg-muted" onClick={() => setRejectOpen(false)}>取消</button>
            <button
              className="rounded-md bg-destructive px-4 py-2 text-sm text-destructive-foreground disabled:opacity-50"
              disabled={!rejectReason.trim() || !myParty}
              onClick={() => {
                if (!myParty || !rejectReason.trim()) return;
                setRejectOpen(false);
                void post(`/platform/sign/tasks/${id}/parties/${myParty.id}/reject`, { reason: rejectReason.trim() }, '已提交拒签');
              }}
            >
              确认拒签
            </button>
          </div>
        </DialogContent>
      </Dialog>

      {/* 延期:用户选择新的截止日期,不再写死 */}
      <Dialog open={extendOpen} onOpenChange={(o) => { if (!o) setExtendOpen(false); }}>
        <DialogContent className="w-[calc(100%-2rem)] max-w-md rounded-2xl border bg-white p-6 shadow-xl dark:bg-card">
          <DialogTitle className="text-lg font-semibold">延长签署截止</DialogTitle>
          <DialogDescription className="mt-2 text-sm text-muted-foreground">
            选择新的截止时间(默认为原截止日 +7 天)。
          </DialogDescription>
          <input
            type="datetime-local"
            className="mt-4 h-9 w-full rounded-md border border-border bg-background px-3 text-sm"
            min={toLocalInput(new Date())}
            value={extendValue}
            onChange={(e) => setExtendValue(e.target.value)}
          />
          <div className="mt-6 flex justify-end gap-2">
            <button className="rounded-md border border-border px-4 py-2 text-sm hover:bg-muted" onClick={() => setExtendOpen(false)}>取消</button>
            <button
              className="rounded-md bg-primary px-4 py-2 text-sm text-primary-foreground disabled:opacity-50"
              disabled={!extendValue}
              onClick={() => {
                if (!extendValue) return;
                setExtendOpen(false);
                void post(`/platform/sign/tasks/${id}/extend`, { expireAt: toApiTime(extendValue) }, '已延长截止时间');
              }}
            >
              确认延期
            </button>
          </div>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={voidOpen}
        title="申请解约"
        message="将向 e签宝发起解约。对方确认后，任务变为已作废，原已签文件仍可下载。"
        confirmLabel="申请解约"
        cancelLabel="取消"
        variant="destructive"
        onCancel={() => setVoidOpen(false)}
        onConfirm={async () => {
          setVoidOpen(false);
          await post(`/platform/sign/tasks/${id}/void`, { reason: '申请解约' }, '已提交解约');
        }}
      />

      {/* 撤回:二次确认 */}
      <ConfirmDialog
        open={revokeOpen}
        title="撤回签署任务"
        message="撤回后该任务将终止,参与方无法继续签署。确认撤回?"
        confirmLabel="确认撤回"
        cancelLabel="取消"
        variant="destructive"
        onCancel={() => setRevokeOpen(false)}
        onConfirm={async () => {
          setRevokeOpen(false);
          await post(`/platform/sign/tasks/${id}/revoke`, undefined, '已撤回');
        }}
      />
    </div>
  );
}
